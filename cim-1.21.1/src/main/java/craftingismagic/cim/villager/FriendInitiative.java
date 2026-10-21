package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.narrative.TaleRuntime;
import craftingismagic.cim.speech.ChatVoices;
import craftingismagic.cim.story.CimTaleVocabulary;

/**
 * Ponies take initiative (README: Friendship, deepened - "close friends seek the player out on their
 * own"): a friend who spots the player nearby, and hasn't seen them yet today, comes over to say hello
 * - in chat, in their own voice, from a {@code start: seek} line that fits. Once a day per friend; a
 * friend who's asleep, already busy talking, or too far off doesn't come.
 * <p>
 * Light on purpose: checked every few seconds per player, only friends nearby, and nothing opens a
 * screen - the player can answer by talking to them, or carry on.
 */
public final class FriendInitiative {
	private static final int CHECK_INTERVAL = 100;
	private static final double NOTICE_RANGE = 20;
	private static final double HELLO_DISTANCE_SQUARED = 3.5 * 3.5;
	private static final long GIVE_UP_TICKS = 600;
	private static final long DAY = 24000L;

	/** A friend on their way over: to whom, which day, and since when. */
	private record Seeking(UUID player, long day, long since) {
	}

	private static final Map<UUID, Seeking> seeking = new HashMap<>();
	/** Friend and player -> the day they last came over, so it's once a day. */
	private static final Map<String, Long> cameToday = new HashMap<>();

	private FriendInitiative() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FriendInitiative::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % CHECK_INTERVAL != 0) return;
		for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
			if (!player.isAlive() || player.isSpectator()) continue;
			long now = player.getWorld().getTime();
			long today = now / DAY;
			List<VillagerEntity> friends = player.getServerWorld().getEntitiesByClass(VillagerEntity.class,
					player.getBoundingBox().expand(NOTICE_RANGE), pony -> pony.isAlive() && FriendshipBonds.isFriend(pony, player.getUuid()));
			for (VillagerEntity friend : friends) {
				Seeking on = seeking.get(friend.getUuid());
				if (on != null && on.player().equals(player.getUuid())) {
					arrive(friend, player, on, now);
				} else if (on == null && wantsToCome(friend, player, today)) {
					seeking.put(friend.getUuid(), new Seeking(player.getUuid(), today, now));
					friend.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.6f, 2));
					friend.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(player, true));
					break; // one friend at a time - a herd stampeding over at once would be a lot
				}
			}
		}
		// Tidy up: friends whose player left mid-walk, and once-a-day marks from before yesterday.
		long now = server.getOverworld().getTime();
		seeking.entrySet().removeIf(entry -> now - entry.getValue().since() > GIVE_UP_TICKS * 2);
		cameToday.values().removeIf(day -> day < now / DAY - 1);
	}

	private static boolean wantsToCome(VillagerEntity friend, ServerPlayerEntity player, long today) {
		if (friend.isSleeping() || friend.isBaby() || VillagerDialogue.isTalking(friend)) return false;
		if (FriendshipAttachment.getLastTalked(friend, player.getUuid()) / DAY >= today) return false; // already saw you today
		return !Long.valueOf(today).equals(cameToday.get(key(friend, player)));
	}

	/** Still on the way: keep walking - or, close enough, say hello; too long, and they give up. */
	private static void arrive(VillagerEntity friend, ServerPlayerEntity player, Seeking on, long now) {
		if (now - on.since() > GIVE_UP_TICKS || friend.isSleeping()) {
			seeking.remove(friend.getUuid());
			cameToday.put(key(friend, player), on.day());
			return;
		}
		if (friend.squaredDistanceTo(player) > HELLO_DISTANCE_SQUARED) {
			friend.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.6f, 2));
			return;
		}
		seeking.remove(friend.getUuid());
		cameToday.put(key(friend, player), on.day());
		friend.getLookControl().lookAt(player, 30f, 30f);
		TaleContext context = new TaleContext(player, friend, friend.getRandom());
		Optional<String> hello = TaleRuntime.pickOpener(CimTaleVocabulary.SEEK, context)
				.flatMap(node -> TaleRuntime.speakOnly(node, context).beat().lines().stream().findFirst())
				.map(TaleRuntime.Line::text);
		hello.ifPresent(text -> {
			player.sendMessage(Text.literal(friend.getName().getString() + ": ").formatted(Formatting.YELLOW)
					.append(Text.literal(text).formatted(Formatting.WHITE)), false);
			ChatVoices.murmur(friend, text);
		});
	}

	private static String key(VillagerEntity friend, ServerPlayerEntity player) {
		return friend.getUuidAsString() + "/" + player.getUuidAsString();
	}
}
