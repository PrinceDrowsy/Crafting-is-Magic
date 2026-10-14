package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.debug.DebugOverlay;
import craftingismagic.cim.narrative.TaleAst.Node;
import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.narrative.TaleRuntime;
import craftingismagic.cim.narrative.TaleRuntime.Beat;
import craftingismagic.cim.narrative.TaleRuntime.Conversation;
import craftingismagic.cim.network.DialogueBeatS2CPayload;
import craftingismagic.cim.story.CimTaleVocabulary;
import craftingismagic.cim.story.StoryThreads;

/**
 * Runs {@code .tale} conversations between players and ponies: one at a time per player, held on the
 * server, with each beat sent to the dialogue screen. Idle Chatter is the first user - it opens at a
 * {@code start: chatter} node (data/cim/tales/chatter/idle_chatter.tale) - and {@code /cim debug
 * dialogue test} can open any node at all.
 */
public final class VillagerDialogue {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");

	private record Session(int entityId, Conversation conversation) {
	}

	private static final Map<UUID, Session> sessions = new HashMap<>();

	private VillagerDialogue() {
	}

	public static void register() {
		DebugOverlay.addProvider((player, lines) -> {
			Session session = sessions.get(player.getUuid());
			if (session != null) {
				lines.add("Dialogue: " + session.conversation().node().id() + (session.conversation().beat().ended() ? " (ended)" : ""));
			}
		});
	}

	/** Idle Chatter from the villager menu: a weighted pick among the chatter openers that fit. */
	public static void startChatter(ServerPlayerEntity player, int villagerEntityId) {
		VillagerEntity villager = VillagerInteractionHandler.resolveNearbyVillager(player, villagerEntityId);
		if (villager == null) {
			return;
		}
		TaleContext context = new TaleContext(player, villager, villager.getRandom());
		TaleRuntime.pickOpener(CimTaleVocabulary.CHATTER, context).ifPresentOrElse(
				opener -> start(player, villager, opener, context),
				() -> {
					LOGGER.warn("No Idle Chatter opener fits {} - are the chatter .tale files loading? (/cim debug tale validate)",
							villager.getName().getString());
					send(player, villager, new Beat(List.of(new TaleRuntime.Line("", true, "...")), List.of(), true));
				});
	}

	/** Opens {@code node} with {@code pony} directly, ignoring {@code start:} and {@code when:} (debug). */
	public static void startAt(ServerPlayerEntity player, Entity pony, Node node) {
		if (pony instanceof MobEntity mob) {
			VillagerInteractionHandler.freeze(mob, player);
		}
		Random random = pony instanceof MobEntity mobPony ? mobPony.getRandom() : player.getRandom();
		start(player, pony, node, new TaleContext(player, pony, random));
	}

	private static void start(ServerPlayerEntity player, Entity pony, Node node, TaleContext context) {
		Conversation conversation = TaleRuntime.start(node, context);
		sessions.put(player.getUuid(), new Session(pony.getId(), conversation));
		send(player, pony, conversation.beat());
		StoryThreads.talked(player, pony, conversation.drainVisited(), true);
	}

	public static void choose(ServerPlayerEntity player, int entityId, int option) {
		Session session = sessions.get(player.getUuid());
		if (session == null || session.entityId() != entityId) {
			return;
		}
		Entity pony = session.conversation().context().speaker();
		if (pony.isRemoved() || pony.squaredDistanceTo(player) > VillagerInteractionHandler.MAX_INTERACT_DISTANCE_SQUARED) {
			end(player);
			return;
		}
		send(player, pony, session.conversation().choose(option));
		StoryThreads.talked(player, pony, session.conversation().drainVisited(), false);
	}

	/** The screen closed, or the player left. */
	public static void end(ServerPlayerEntity player) {
		sessions.remove(player.getUuid());
	}

	private static void send(ServerPlayerEntity player, Entity pony, Beat beat) {
		List<String> lines = beat.lines().stream()
				.map(line -> line.partner() || line.speaker().isEmpty() ? line.text() : line.speaker() + ": " + line.text())
				.toList();
		List<String> tones = beat.options().stream().map(option -> option.tone().map(t -> t.toLowerCase(Locale.ROOT)).orElse("")).toList();
		List<String> texts = beat.options().stream().map(TaleRuntime.Option::text).toList();
		ServerPlayNetworking.send(player, new DialogueBeatS2CPayload(pony.getId(), pony.getName().getString(), lines, tones, texts, beat.ended()));
	}
}
