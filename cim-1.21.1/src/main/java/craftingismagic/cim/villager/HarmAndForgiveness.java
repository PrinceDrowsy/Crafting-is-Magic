package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Box;

import craftingismagic.cim.Cim;
import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;

/**
 * Hurting a pony matters - how much depends on whether it was an accident or cruelty (design doc: Harm &
 * forgiveness). Built to lean forgiving: clumsy players shouldn't be branded villains for bad aim.
 * <ul>
 * <li><b>An accident:</b> a single stray hit, or harm the player only caused indirectly. A little lost.</li>
 * <li><b>Intent:</b> hitting the same pony again soon after, or striking one down. A lot lost, a memory
 * of it (which gossip carries - friends and family who hear trust you less), bystanders who saw, and
 * a mark of cruelty on the player (for the darker voice and the villain route).</li>
 * <li><b>Apologizing</b> (a conversation request): each apology counts for less than the last; a pony
 * hurt over and over stops believing it. Forgiveness follows personality - Gentle and Peppy ponies
 * forgive at once (maybe too easily), Cranky ones need a day to themselves first, Grudge Holders
 * longer. Forgiving is repair: the hurt becomes something forgiven, and some of what was lost
 * returns - never more than was lost, so harm and apology can't be farmed.</li>
 * </ul>
 * Numbers are first-pass, to tune in play.
 */
public final class HarmAndForgiveness {
	public static final Identifier ACCIDENT = Identifier.of("cim", "accident");
	public static final Identifier HURT = Identifier.of("cim", "hurt");
	public static final Identifier FORGIVEN = Identifier.of("cim", "forgiven");
	/** Held by the player: they were cruel to someone. What the darker voice and the villain route read. */
	public static final Identifier CRUELTY = Identifier.of("cim", "cruelty");

	/** A second hit on the same pony within this long reads as intent, not an accident. */
	private static final long INTENT_WINDOW = 200;
	private static final double WITNESS_RANGE = 16;
	private static final long DAY = 24000L;

	/**
	 * What's between a pony and one player: how often hurt, how often they've apologized, whether
	 * something is still unforgiven, and what was lost (the most forgiveness can give back).
	 */
	public record Ledger(int hurts, int apologies, long lastHit, long lastHurt, boolean unforgiven, boolean cruel, int lostFondness,
			int lostTrust) {
		static final Ledger EMPTY = new Ledger(0, 0, -1, -1, false, false, 0, 0);
		public static final Codec<Ledger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("hurts", 0).forGetter(Ledger::hurts),
				Codec.INT.optionalFieldOf("apologies", 0).forGetter(Ledger::apologies),
				Codec.LONG.optionalFieldOf("last_hit", -1L).forGetter(Ledger::lastHit),
				Codec.LONG.optionalFieldOf("last_hurt", -1L).forGetter(Ledger::lastHurt),
				Codec.BOOL.optionalFieldOf("unforgiven", false).forGetter(Ledger::unforgiven),
				Codec.BOOL.optionalFieldOf("cruel", false).forGetter(Ledger::cruel),
				Codec.INT.optionalFieldOf("lost_fondness", 0).forGetter(Ledger::lostFondness),
				Codec.INT.optionalFieldOf("lost_trust", 0).forGetter(Ledger::lostTrust)
		).apply(instance, Ledger::new));
	}

	public static final AttachmentType<Map<UUID, Ledger>> LEDGER = AttachmentRegistry.create(Identifier.of(Cim.MOD_ID, "harm_ledger"),
			builder -> builder.persistent(Codec.unboundedMap(Uuids.STRING_CODEC, Ledger.CODEC)).copyOnDeath());

	private HarmAndForgiveness() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof VillagerEntity pony && !blocked && taken > 0 && source.getAttacker() instanceof ServerPlayerEntity player) {
				hit(pony, player, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof VillagerEntity pony && source.getAttacker() instanceof ServerPlayerEntity player
					&& pony.getWorld() instanceof ServerWorld world) {
				// Striking a pony down is never an accident to those who saw it.
				witnessed(pony, player, world, EmotionIntensity.STRONG);
				MemoryStore.add(player, new Memory(CRUELTY, Optional.of(pony.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.INDELIBLE,
						Optional.empty(), EmotionIntensity.CLEAR, world.getTime()));
			}
		});
		// Word gets around: hearing that the player hurt someone makes a pony trust them less.
		MemoryStore.onNewMemory((holder, memory) -> {
			if (holder instanceof VillagerEntity && memory.type().equals(HURT) && memory.knowledge() == MemoryKnowledge.HEARD
					&& memory.subject().isPresent()) {
				FriendshipAttachment.addFeeling(holder, memory.subject().get(), Feeling.TRUST, -4);
				FriendshipAttachment.addFeeling(holder, memory.subject().get(), Feeling.FONDNESS, -2);
			}
		});
	}

	// ---- Harm ----

	private static void hit(VillagerEntity pony, ServerPlayerEntity player, DamageSource source) {
		long now = pony.getWorld().getTime();
		Ledger ledger = get(pony, player.getUuid());
		// Struck by the player or something they shot - not harm they only set in motion.
		boolean direct = source.getSource() == player || source.getSource() instanceof net.minecraft.entity.projectile.ProjectileEntity;
		boolean intent = direct && ledger.lastHit() >= 0 && now - ledger.lastHit() <= INTENT_WINDOW;
		if (intent) {
			hurt(pony, player, false);
		} else {
			harm(pony, player, false, 3, 2, 0);
			MemoryStore.add(pony, new Memory(ACCIDENT, Optional.of(player.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.TRIVIAL,
					Optional.of(Emotion.FEAR), EmotionIntensity.FAINT, now));
		}
		Ledger after = get(pony, player.getUuid());
		set(pony, player.getUuid(), new Ledger(after.hurts(), after.apologies(), now, after.lastHurt(), after.unforgiven(), after.cruel(),
				after.lostFondness(), after.lostTrust()));
	}

	/**
	 * Real hurt, on purpose - a body blow or, from dialogue ({@code ~ hurt cruel}), a cruel word. With
	 * {@code words}, nobody else sees it happen; the pony still remembers, and gossip still carries it.
	 */
	public static void hurt(Entity pony, ServerPlayerEntity player, boolean words) {
		long now = pony.getWorld().getTime();
		harm(pony, player, true, 12, 18, 4);
		MemoryStore.add(pony, new Memory(HURT, Optional.of(player.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.MAJOR,
				Optional.of(Emotion.ANGER), EmotionIntensity.CLEAR, now));
		MemoryStore.add(player, new Memory(CRUELTY, Optional.of(pony.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.MAJOR,
				Optional.empty(), EmotionIntensity.CLEAR, now));
		if (!words && pony.getWorld() instanceof ServerWorld world) {
			witnessed(pony, player, world, EmotionIntensity.FAINT);
		}
	}

	/** A small hurt from dialogue ({@code ~ hurt minor}): a careless word, forgivable like an accident. */
	public static void slight(Entity pony, ServerPlayerEntity player) {
		harm(pony, player, false, 4, 3, 0);
	}

	private static void harm(Entity pony, ServerPlayerEntity player, boolean cruel, int fondness, int trust, int respect) {
		UUID you = player.getUuid();
		FriendshipAttachment.addFeeling(pony, you, Feeling.FONDNESS, -fondness);
		FriendshipAttachment.addFeeling(pony, you, Feeling.TRUST, -trust);
		if (respect > 0) FriendshipAttachment.addFeeling(pony, you, Feeling.RESPECT, -respect);
		Ledger ledger = get(pony, you);
		set(pony, you, new Ledger(ledger.hurts() + 1, ledger.apologies(), ledger.lastHit(), pony.getWorld().getTime(), true,
				ledger.cruel() || cruel, ledger.lostFondness() + fondness, ledger.lostTrust() + trust));
	}

	/** Ponies nearby who saw it remember what the player did - and are a little afraid. */
	private static void witnessed(Entity victim, ServerPlayerEntity player, ServerWorld world, EmotionIntensity fear) {
		Box around = victim.getBoundingBox().expand(WITNESS_RANGE);
		for (VillagerEntity witness : world.getEntitiesByClass(VillagerEntity.class, around, pony -> pony.isAlive() && pony != victim)) {
			if (!witness.canSee(victim)) continue;
			MemoryStore.add(witness, new Memory(HURT, Optional.of(player.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.NOTABLE,
					Optional.of(Emotion.FEAR), fear, world.getTime()));
			FriendshipAttachment.addFeeling(witness, player.getUuid(), Feeling.TRUST, fear == EmotionIntensity.STRONG ? -20 : -6);
		}
	}

	/** A memory of harm (fear or anger) - never a bond, whatever its importance. */
	public static boolean isHarm(Memory memory) {
		return memory.emotion().filter(e -> e == Emotion.FEAR || e == Emotion.ANGER).isPresent();
	}

	// ---- Forgiveness ----

	/** Something between them is still unforgiven. */
	public static boolean owesApology(Entity pony, UUID player) {
		return get(pony, player).unforgiven();
	}

	/** Too soon: a Cranky pony needs a day to themselves, a Grudge Holder three - a quick sorry won't land yet. */
	public static boolean tooSoon(Entity pony, UUID player) {
		Ledger ledger = get(pony, player);
		return ledger.unforgiven() && pony.getWorld().getTime() - ledger.lastHurt() < cooldown(pony);
	}

	/** They'd still believe a sorry - someone hurt over and over eventually stops. */
	public static boolean believed(Entity pony, UUID player) {
		return credibility(pony, player) >= 0.15f;
	}

	/**
	 * The pony accepts the apology: the hurt becomes something forgiven, and some of what was lost
	 * comes back - less each time they've had to forgive.
	 */
	public static void forgive(Entity pony, ServerPlayerEntity player) {
		UUID you = player.getUuid();
		Ledger ledger = get(pony, you);
		if (!ledger.unforgiven() || tooSoon(pony, you) || !believed(pony, you)) return;
		float credibility = credibility(pony, you);
		int fondness = Math.round(ledger.lostFondness() * credibility * 0.6f);
		int trust = Math.round(ledger.lostTrust() * credibility * 0.5f);
		FriendshipAttachment.addFeeling(pony, you, Feeling.FONDNESS, fondness);
		FriendshipAttachment.addFeeling(pony, you, Feeling.TRUST, trust);
		MemoryStore.remove(pony, memory -> memory.isAbout(you) && (memory.type().equals(HURT) || memory.type().equals(ACCIDENT)));
		MemoryStore.add(pony, new Memory(FORGIVEN, Optional.of(you), MemoryKnowledge.WITNESSED, MemoryImportance.NOTABLE, Optional.empty(),
				EmotionIntensity.CLEAR, player.getWorld().getTime()));
		set(pony, you, new Ledger(ledger.hurts(), ledger.apologies() + 1, ledger.lastHit(), ledger.lastHurt(), false, ledger.cruel(), 0, 0));
	}

	/** How much a sorry is worth now: less with every apology before it, less again after cruelty; personality colors it. */
	private static float credibility(Entity pony, UUID player) {
		Ledger ledger = get(pony, player);
		float credibility = 1f / (1 + ledger.apologies());
		if (ledger.cruel()) credibility *= 0.6f;
		if (PersonalityAttachment.hasPersonality(pony)) {
			Personality personality = PersonalityAttachment.getPersonality(pony);
			if (personality.quirks().contains(Quirk.GRUDGE_HOLDER)) credibility *= 0.6f;
			// Gentle and Peppy ponies want to believe you - perhaps too easily.
			if (personality.baseType() == PersonalityType.GENTLE || personality.baseType() == PersonalityType.PEPPY) {
				credibility = Math.min(1f, credibility + 0.2f);
			}
		}
		return credibility;
	}

	private static long cooldown(Entity pony) {
		if (!PersonalityAttachment.hasPersonality(pony)) return 0;
		Personality personality = PersonalityAttachment.getPersonality(pony);
		if (personality.quirks().contains(Quirk.GRUDGE_HOLDER)) return 3 * DAY;
		return personality.baseType() == PersonalityType.CRANKY ? DAY : 0;
	}

	// ---- Storage ----

	public static Ledger get(Entity pony, UUID player) {
		return pony.hasAttached(LEDGER) ? pony.getAttached(LEDGER).getOrDefault(player, Ledger.EMPTY) : Ledger.EMPTY;
	}

	private static void set(Entity pony, UUID player, Ledger ledger) {
		Map<UUID, Ledger> all = pony.hasAttached(LEDGER) ? new HashMap<>(pony.getAttached(LEDGER)) : new HashMap<>();
		all.put(player, ledger);
		pony.setAttached(LEDGER, all);
	}
}
