package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.story.PlayerNews;

/**
 * When a pony dies, those close to them grieve (README: The mood system - "a close friend dies";
 * decided 2026-10-14 who counts as close):
 * <ul>
 * <li><b>Ponies:</b> their family (parents, partner, children) grieve strongly; their friend group
 * clearly.</li>
 * <li><b>Players:</b> anyone with high or devoted Fondness or Trust toward them, or strong Shared
 * Memories with them, grieves strongly - and that is the first big scene-setting moment,
 * {@link #GRIEF_MOMENT}. But only once they know (decided 2026-10-14): seeing it happen means grief
 * at once; otherwise it waits as {@link PlayerNews} until a pony who was close to them tells you.
 * (Player family waits on family systems for players.)</li>
 * </ul>
 * Grief is a memory ({@link #LOSS}, about the one who died), so it fades the way the mood system
 * fades every feeling. Ponies follow a simplified version of the same rule until gossip exists: the
 * loaded family and friend group - close, and nearby - know right away; nobody farther away grieves
 * news they couldn't have heard.
 */
public final class VillagerGriefHandler {
	public static final Identifier LOSS = Identifier.of("cim", "loss");
	public static final Identifier GRIEF_MOMENT = Identifier.of("cim", "grief");
	/** Close enough to have seen it happen rather than heard about it. */
	private static final double WITNESS_DISTANCE_SQUARED = 32.0 * 32.0;
	/** Shared Memories that make a pony close even without high feelings. */
	private static final int SHARED_MEMORIES_FOR_CLOSE = 3;

	private VillagerGriefHandler() {
	}

	public static void register() {
		// Older worlds: a foal links itself to its parents as it loads (see PonyFamilyAttachment#CHILDREN).
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity) {
				PonyFamilyAttachment.linkToParents(entity);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof VillagerEntity pony && entity.getWorld() instanceof ServerWorld world) {
				grieve(pony, world);
			}
		});
	}

	private static void grieve(VillagerEntity lost, ServerWorld world) {
		Map<UUID, EmotionIntensity> mourners = new HashMap<>();
		if (PonyFamilyAttachment.hasFamily(lost)) {
			PonyFamilyAttachment.PonyFamily family = PonyFamilyAttachment.getFamily(lost);
			family.parents().forEach(uuid -> mourners.put(uuid, EmotionIntensity.STRONG));
			family.partner().forEach(uuid -> mourners.put(uuid, EmotionIntensity.STRONG));
		}
		// Their foals, from the parent's own list - no searching the world.
		PonyFamilyAttachment.children(lost).forEach(uuid -> mourners.put(uuid, EmotionIntensity.STRONG));
		if (FriendGroupAttachment.hasGroup(lost)) {
			for (UUID friend : FriendGroupAttachment.getGroup(lost).members()) {
				mourners.putIfAbsent(friend, EmotionIntensity.CLEAR);
			}
		}
		mourners.remove(lost.getUuid());
		mourners.forEach((uuid, intensity) -> {
			if (world.getEntity(uuid) instanceof VillagerEntity mourner && mourner.isAlive()) {
				remember(mourner, lost, intensity, world);
			}
		});

		// You only feel what you know: seeing it means grief now; otherwise it waits as news until a
		// pony who was close to them - or, if nobody else was, any pony - tells you (or a letter does).
		List<UUID> messengers = List.copyOf(mourners.keySet());
		for (ServerPlayerEntity player : List.copyOf(world.getServer().getPlayerManager().getPlayerList())) {
			if (!closeTo(lost, player)) {
				continue;
			}
			if (saw(player, lost)) {
				PlayerNews.feel(player, LOSS, lost.getUuid(), lost.getName().getString(), MemoryKnowledge.WITNESSED, Optional.of(Emotion.GRIEF), EmotionIntensity.STRONG,
						Optional.of(GRIEF_MOMENT));
			} else {
				PlayerNews.hold(player, new PlayerNews.News(LOSS, lost.getUuid(), lost.getName().getString(), Optional.of(Emotion.GRIEF),
						EmotionIntensity.STRONG, Optional.of(GRIEF_MOMENT), messengers, messengers.isEmpty(), world.getTime()));
			}
		}
	}

	/** Close by, in the same world, with a clear line of sight. */
	private static boolean saw(ServerPlayerEntity player, VillagerEntity lost) {
		return player.getWorld() == lost.getWorld() && player.squaredDistanceTo(lost) <= WITNESS_DISTANCE_SQUARED && player.canSee(lost);
	}

	/** High or devoted Fondness or Trust, or strong Shared Memories. */
	private static boolean closeTo(VillagerEntity lost, ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (FriendshipAttachment.getBand(lost, uuid, Feeling.FONDNESS).ordinal() >= FeelingBand.HIGH.ordinal()
				|| FriendshipAttachment.getBand(lost, uuid, Feeling.TRUST).ordinal() >= FeelingBand.HIGH.ordinal()) {
			return true;
		}
		List<Memory> shared = MemoryStore.about(lost, uuid);
		return shared.stream().anyMatch(memory -> memory.importance().compareTo(MemoryImportance.MAJOR) >= 0)
				|| shared.size() >= SHARED_MEMORIES_FOR_CLOSE;
	}

	private static void remember(Entity mourner, VillagerEntity lost, EmotionIntensity intensity, ServerWorld world) {
		MemoryKnowledge knowledge = mourner.squaredDistanceTo(lost) <= WITNESS_DISTANCE_SQUARED && mourner.getWorld() == world
				? MemoryKnowledge.WITNESSED : MemoryKnowledge.HEARD;
		MemoryStore.add(mourner, new Memory(LOSS, Optional.of(lost.getUuid()), knowledge, MemoryImportance.MAJOR,
				Optional.of(Emotion.GRIEF), intensity, world.getTime()));
	}
}
