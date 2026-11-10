package craftingismagic.cim.villager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.GossipRules;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.story.StoryFacts;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;

/**
 * Gossip that actually travels (design doc: The memory system). When two ponies stop to chat - vanilla's
 * own moment for villager gossip, so it happens at the village's natural pace, between ponies who are
 * really together, and never by scanning - one may tell the other what they've seen or heard.
 * <ul>
 * <li><b>Personality decides who blabs:</b> a Gossip quirk almost always talks; Peppy and Refined ponies
 * love news; the Steadfast are discreet. Anyone talks more freely to family and friends.</li>
 * <li><b>Stories blur as they travel:</b> each retelling is one hop further from what happened, a
 * little fainter - unless a blabber makes it bigger.</li>
 * <li><b>Gentle news</b> (a death; see {@link GossipRules}) is told even by the discreet and never
 * exaggerated, and only those close to whoever it's about feel anything when they hear it.</li>
 * </ul>
 */
public final class VillagerGossip {
	/** A pony shares a round of stories at most this often - news spreads over a day, not in a second. */
	private static final long TELL_INTERVAL = 600;
	private static final int STORIES_PER_CHAT = 2;
	/** Talks more freely to family and friends. */
	private static final float CLOSE_BONUS = 0.35f;
	private static final float GENTLE_CHANCE = 0.9f;

	private static final Map<UUID, Long> lastTold = new HashMap<>();

	private VillagerGossip() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> lastTold.remove(entity.getUuid()));
	}

	/** Two ponies stopped to chat (vanilla's {@code talkWithVillager}): {@code teller} may share a story or two. */
	public static void chat(VillagerEntity teller, VillagerEntity listener, ServerWorld world) {
		long now = world.getTime();
		Long last = lastTold.get(teller.getUuid());
		if (last != null && now - last < TELL_INTERVAL) return;
		lastTold.put(teller.getUuid(), now);
		tell(teller, listener, world, false);
	}

	/**
	 * {@code teller} tells {@code listener} what they know - up to a couple of stories, the weightiest
	 * and freshest first. {@code everything}: every story they'd ever tell, ignoring chance (debug).
	 * Returns how many were told.
	 */
	public static int tell(VillagerEntity teller, VillagerEntity listener, ServerWorld world, boolean everything) {
		boolean close = areClose(world, teller, listener);
		Random random = teller.getRandom();
		List<Memory> stories = MemoryStore.getAll(teller).stream()
				.filter(story -> worthTelling(story, listener, close))
				.sorted(Comparator.comparing(Memory::importance).reversed().thenComparing(Comparator.comparingLong(Memory::time).reversed()))
				.toList();
		int told = 0;
		for (Memory story : stories) {
			if (!everything && told >= STORIES_PER_CHAT) break;
			GossipRules.Rule rule = GossipRules.rule(story);
			float chance = rule.gentle() ? GENTLE_CHANCE : talkativeness(teller) + (close ? CLOSE_BONUS : 0f);
			if (!everything && random.nextFloat() >= chance) continue;
			MemoryStore.add(listener, retold(story, rule, teller, listener, random));
			told++;
		}
		return told;
	}

	// ---- Telling the player (Village Gossip in conversation) ----

	/**
	 * The story this pony would tell the player now, if any - the weightiest and freshest first, so
	 * a line's condition, its names, and its effect all agree on one story.
	 * <ul>
	 * <li>{@code aboutPlayer}: something they've <i>heard</i> about the player (your deeds getting
	 * around) that they haven't mentioned to you yet.</li>
	 * <li>otherwise: news about another pony the player doesn't know yet - and whose name the pony can
	 * say.</li>
	 * </ul>
	 * Gentle news (a death) isn't village gossip; it's broken through {@code news(...)} instead.
	 */
	public static Optional<Memory> storyFor(ServerPlayerEntity player, Entity pony, boolean aboutPlayer, Optional<Identifier> type) {
		UUID you = player.getUuid();
		return MemoryStore.getAll(pony).stream()
				.filter(story -> type.isEmpty() || story.type().equals(type.get()))
				.filter(story -> story.knowledge() != MemoryKnowledge.LEGEND && story.subject().isPresent())
				.filter(story -> {
					GossipRules.Rule rule = GossipRules.rule(story);
					return rule.spreads() && !rule.gentle();
				})
				.filter(story -> aboutPlayer
						? story.isAbout(you) && story.hops() >= 1 && !StoryFacts.hasRelationship(pony, you, mentioned(story))
						: !story.isAbout(you) && !story.isAbout(pony.getUuid()) && name(player.getServer(), story.subject().get()).isPresent()
								&& MemoryStore.getAll(player).stream()
										.noneMatch(known -> known.type().equals(story.type()) && known.subject().equals(story.subject())))
				.sorted(Comparator.comparing(Memory::importance).reversed().thenComparing(Comparator.comparingLong(Memory::time).reversed()))
				.findFirst();
	}

	/**
	 * The pony tells the player the story: news about another pony becomes the player's own heard
	 * memory (so it's never told twice); something about the player is marked as mentioned.
	 */
	public static void tellPlayer(ServerPlayerEntity player, Entity pony, boolean aboutPlayer, Optional<Identifier> type) {
		storyFor(player, pony, aboutPlayer, type).ifPresent(story -> {
			if (aboutPlayer) {
				StoryFacts.setRelationship(pony, player.getUuid(), mentioned(story));
			} else {
				MemoryStore.add(player, new Memory(story.type(), story.subject(), MemoryKnowledge.HEARD, story.importance(), Optional.empty(),
						EmotionIntensity.CLEAR, story.time(), story.hops() + 1, Optional.of(pony.getUuid())));
			}
		});
	}

	/** The relationship fact that says this pony already told the player what they'd heard about them. */
	private static Identifier mentioned(Memory story) {
		return Identifier.of("cim", "relationship/mentioned/" + story.type().getNamespace() + "/" + story.type().getPath());
	}

	/** The name of whoever this is, if they're somewhere in the world right now. */
	public static Optional<String> name(MinecraftServer server, UUID who) {
		for (ServerWorld world : server.getWorlds()) {
			Entity entity = world.getEntity(who);
			if (entity != null) return Optional.of(entity.getName().getString());
		}
		return Optional.empty();
	}

	private static boolean worthTelling(Memory story, VillagerEntity listener, boolean close) {
		GossipRules.Rule rule = GossipRules.rule(story);
		if (!rule.spreads() || story.hops() >= rule.reach() || rule.closeOnly() && !close) return false;
		if (story.knowledge() == MemoryKnowledge.LEGEND) return false;
		UUID them = listener.getUuid();
		// Not news to them: it's about them, they told it, or they already know.
		if (story.subject().filter(them::equals).isPresent() || story.source().filter(them::equals).isPresent()) return false;
		return MemoryStore.getAll(listener).stream()
				.noneMatch(known -> known.type().equals(story.type()) && known.subject().equals(story.subject()));
	}

	/** The story as the listener will remember it: second-hand, a hop further, fainter - or bigger. */
	private static Memory retold(Memory story, GossipRules.Rule rule, VillagerEntity teller, VillagerEntity listener, Random random) {
		MemoryImportance importance = story.importance() == MemoryImportance.INDELIBLE ? MemoryImportance.MAJOR : story.importance();
		Optional<craftingismagic.cim.memory.Emotion> emotion = story.emotion();
		EmotionIntensity intensity = story.intensity();
		if (rule.gentle()) {
			// News: only those close to whoever it's about feel it - family deeply, friends clearly.
			Optional<EmotionIntensity> closeness = story.subject().flatMap(subject -> closeness(listener, subject));
			emotion = closeness.isPresent() ? emotion : Optional.empty();
			intensity = closeness.orElse(intensity);
		} else if (emotion.isPresent()) {
			boolean bigger = rule.exaggerates() && random.nextFloat() < exaggeration(teller);
			intensity = step(intensity, bigger ? 1 : -1);
		}
		return new Memory(story.type(), story.subject(), MemoryKnowledge.HEARD, importance, emotion, intensity, story.time(),
				story.hops() + 1, Optional.of(teller.getUuid()));
	}

	private static EmotionIntensity step(EmotionIntensity intensity, int by) {
		EmotionIntensity[] all = EmotionIntensity.values();
		return all[Math.max(0, Math.min(all.length - 1, intensity.ordinal() + by))];
	}

	/** How readily a pony passes a story on to anyone at all. */
	private static float talkativeness(Entity pony) {
		if (!PersonalityAttachment.hasPersonality(pony)) return 0.4f;
		Personality personality = PersonalityAttachment.getPersonality(pony);
		if (personality.quirks().contains(Quirk.GOSSIP)) return 0.9f;
		return switch (personality.baseType()) {
			case PEPPY -> 0.7f;
			case REFINED -> 0.55f;
			case DARING -> 0.5f;
			case CRANKY -> 0.4f;
			case LAZY, BOOKISH -> 0.35f;
			case GENTLE -> 0.3f;
			case STEADFAST -> 0.25f;
		};
	}

	/** How likely a pony is to make a story bigger in the retelling. */
	private static float exaggeration(Entity pony) {
		if (!PersonalityAttachment.hasPersonality(pony)) return 0.15f;
		Personality personality = PersonalityAttachment.getPersonality(pony);
		if (personality.quirks().contains(Quirk.GOSSIP)) return 0.5f;
		return switch (personality.baseType()) {
			case PEPPY -> 0.4f;
			case DARING -> 0.35f;
			case REFINED -> 0.3f;
			case CRANKY -> 0.25f;
			default -> 0.15f;
		};
	}

	/** Family or friends. */
	private static boolean areClose(ServerWorld world, Entity a, Entity b) {
		return PonyKinship.areRelated(world, a, b)
				|| FriendGroupAttachment.hasGroup(a) && FriendGroupAttachment.getGroup(a).members().contains(b.getUuid());
	}

	/**
	 * How close {@code pony} was to {@code who}, from the pony's own family and friend lists - so it
	 * works even when {@code who} is gone: family deeply, friends clearly, otherwise not close.
	 */
	static Optional<EmotionIntensity> closeness(Entity pony, UUID who) {
		if (PonyFamilyAttachment.hasFamily(pony)) {
			PonyFamilyAttachment.PonyFamily family = PonyFamilyAttachment.getFamily(pony);
			if (family.parents().contains(who) || family.partner().contains(who)) return Optional.of(EmotionIntensity.STRONG);
		}
		if (PonyFamilyAttachment.children(pony).contains(who)) return Optional.of(EmotionIntensity.STRONG);
		if (FriendGroupAttachment.hasGroup(pony) && FriendGroupAttachment.getGroup(pony).members().contains(who)) {
			return Optional.of(EmotionIntensity.CLEAR);
		}
		return Optional.empty();
	}
}
