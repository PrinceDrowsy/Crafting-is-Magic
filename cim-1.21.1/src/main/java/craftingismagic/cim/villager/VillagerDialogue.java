package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
import craftingismagic.cim.network.DialogueBeatS2CPayload.LineData;
import craftingismagic.cim.network.DialogueBeatS2CPayload.OptionData;
import craftingismagic.cim.network.DialogueBeatS2CPayload.RequestData;
import craftingismagic.cim.speech.SpeakingVoices;
import craftingismagic.cim.voice.CimVoices;
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
	/** What happens once the current lines have been read (e.g. the trade screen opening), per player. */
	private static final Map<UUID, Runnable> followUps = new HashMap<>();
	/** Players whose beat being sent is the pony's goodbye - the screen closes after it. */
	private static final java.util.Set<UUID> closing = new java.util.HashSet<>();

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

	/**
	 * Right-clicking a pony: a greeting, then a chatter topic - a weighted pick among the openers that
	 * fit. From there the player can bring something up ({@link #bringUp}).
	 */
	public static void startChatter(ServerPlayerEntity player, int villagerEntityId) {
		VillagerEntity villager = VillagerInteractionHandler.resolveNearbyVillager(player, villagerEntityId);
		if (villager == null) {
			return;
		}
		TaleContext context = new TaleContext(player, villager, villager.getRandom());
		// The topic is picked first and sets the tone; only a greeting of that tone may open it, so a
		// joke never comes right before bad news. (Picked before start() records this talk, so
		// last_talked still reads the previous one.)
		TaleRuntime.pickOpener(CimTaleVocabulary.CHATTER, context).ifPresentOrElse(
				opener -> start(player, villager, opener,
						TaleRuntime.pickOpener(CimTaleVocabulary.GREETING, context, opener.tone()), context),
				() -> {
					LOGGER.warn("No Idle Chatter opener fits {} - are the chatter .tale files loading? (/cim debug tale validate)",
							villager.getName().getString());
					send(player, villager, new Beat(List.of(TaleRuntime.Line.spoken("", true, "...")), List.of(), true));
				});
	}

	/** Opens {@code node} with {@code pony} directly, ignoring {@code start:} and {@code when:} (debug). */
	public static void startAt(ServerPlayerEntity player, Entity pony, Node node) {
		if (pony instanceof MobEntity mob) {
			VillagerInteractionHandler.freeze(mob, player);
		}
		Random random = pony instanceof MobEntity mobPony ? mobPony.getRandom() : player.getRandom();
		start(player, pony, node, Optional.empty(), new TaleContext(player, pony, random));
	}

	private static void start(ServerPlayerEntity player, Entity pony, Node node, Optional<Node> greeting, TaleContext context) {
		Conversation conversation = TaleRuntime.start(node, context, greeting);
		FriendshipPace.visit(pony, player); // before recordTalk: it reads when you last came
		FriendshipAttachment.recordTalk(pony, player.getUuid(), player.getWorld().getTime());
		if (greeting.isPresent()) {
			// A visit (it opened with a greeting) is a moment shared - what a verse reads as "my love".
			FriendshipAttachment.addSharedMoments(pony, player.getUuid(), 1);
		}
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

	/**
	 * The player brings something up - a gift, a trade (see {@link ConversationRequests}). The pony
	 * answers with a conversation from that request's start context; {@code slot} is the inventory
	 * slot of the item picked, for a request that needs one.
	 */
	public static void bringUp(ServerPlayerEntity player, int entityId, Identifier requestId, int slot) {
		if (requestId.equals(ConversationRequests.FAREWELL)) {
			farewell(player, entityId);
			return;
		}
		Session session = sessions.get(player.getUuid());
		Optional<ConversationRequests.Request> request = ConversationRequests.get(requestId);
		if (session == null || session.entityId() != entityId || request.isEmpty()) {
			return;
		}
		Entity pony = session.conversation().context().speaker();
		if (pony.isRemoved() || pony.squaredDistanceTo(player) > VillagerInteractionHandler.MAX_INTERACT_DISTANCE_SQUARED) {
			end(player);
			return;
		}
		ItemStack item = ItemStack.EMPTY;
		if (request.get().needsItem()) {
			if (slot < 0 || slot >= player.getInventory().main.size() || player.getInventory().main.get(slot).isEmpty()) {
				return; // the item moved or ran out since the screen showed it
			}
			item = player.getInventory().main.get(slot);
		}
		Random random = pony instanceof MobEntity mob ? mob.getRandom() : player.getRandom();
		TaleContext context = new TaleContext(player, pony, random, item);
		followUps.remove(player.getUuid());
		TaleRuntime.pickOpener(request.get().startContext(), context).ifPresentOrElse(
				opener -> start(player, pony, opener, Optional.empty(), context),
				() -> {
					LOGGER.warn("Nothing answers \"start: {}\" for {} - are the request .tale files loading? (/cim debug tale validate)",
							request.get().startContext(), pony.getName().getString());
					send(player, pony, new Beat(List.of(TaleRuntime.Line.spoken("", true, "...")), List.of(), true));
				});
	}

	/**
	 * "See you later." - the pony says goodbye (a {@code start: farewell} node that fits, speech only),
	 * and the screen closes on its own once that's been read.
	 */
	private static void farewell(ServerPlayerEntity player, int entityId) {
		Session session = sessions.get(player.getUuid());
		if (session == null || session.entityId() != entityId) {
			return;
		}
		Entity pony = session.conversation().context().speaker();
		Random random = pony instanceof MobEntity mob ? mob.getRandom() : player.getRandom();
		TaleContext context = new TaleContext(player, pony, random);
		Beat goodbye = TaleRuntime.pickOpener(CimTaleVocabulary.FAREWELL, context)
				.map(node -> TaleRuntime.speakOnly(node, context).beat())
				.orElseGet(() -> new Beat(List.of(), List.of(), true));
		followUps.remove(player.getUuid());
		closing.add(player.getUuid());
		send(player, pony, goodbye);
		closing.remove(player.getUuid());
	}

	/** Runs {@code action} once the lines now being sent have been read - like opening the trade screen. */
	public static void afterLines(ServerPlayerEntity player, Runnable action) {
		followUps.put(player.getUuid(), action);
	}

	/** The screen says the lines before a follow-up have been read. */
	public static void followUp(ServerPlayerEntity player, int entityId) {
		Session session = sessions.get(player.getUuid());
		Runnable action = followUps.remove(player.getUuid());
		if (session != null && session.entityId() == entityId && action != null) {
			action.run();
		}
	}

	/** The screen closed, or the player left. */
	public static void end(ServerPlayerEntity player) {
		followUps.remove(player.getUuid());
		Session session = sessions.remove(player.getUuid());
		if (session != null) {
			// "Just now" counts from when you stopped talking, not when you started.
			Entity pony = session.conversation().context().speaker();
			FriendshipAttachment.recordTalk(pony, player.getUuid(), player.getWorld().getTime());
		}
	}

	private static void send(ServerPlayerEntity player, Entity pony, Beat beat) {
		String partnerName = pony.getName().getString();
		List<LineData> lines = beat.lines().stream().map(line -> {
			boolean spoken = line.kind() == TaleRuntime.LineKind.SPOKEN;
			boolean partner = spoken && (line.partner() || line.speaker().isEmpty());
			return new LineData(line.kind().ordinal(), line.voice(), spoken ? (partner ? partnerName : line.speaker()) : "", partner, line.text());
		}).toList();
		List<OptionData> options = beat.options().stream().map(option -> new OptionData(
				option.tone().map(t -> t.toLowerCase(Locale.ROOT)).orElse(""), option.voice().orElse(""), option.text(),
				option.odds() < 0 ? -1 : CimVoices.band(option.odds()))).toList();
		List<RequestData> requests = ConversationRequests.ALL.stream().map(request -> new RequestData(request.id(), request.needsItem())).toList();
		DialogueBeatS2CPayload.Scene scene = new DialogueBeatS2CPayload.Scene(SpeakingVoices.heard(pony), SpeakingVoices.heard(player), requests,
				closing.contains(player.getUuid()) ? "close" : followUps.containsKey(player.getUuid()) ? "action" : "");
		ServerPlayNetworking.send(player, new DialogueBeatS2CPayload(pony.getId(), pony.getName().getString(), lines, options, beat.ended(), scene));
	}
}
