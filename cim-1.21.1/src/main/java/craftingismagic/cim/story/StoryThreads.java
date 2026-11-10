package craftingismagic.cim.story;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.Cim;
import craftingismagic.cim.debug.DebugOverlay;
import craftingismagic.cim.narrative.TaleAst;
import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.narrative.TaleLibrary;
import craftingismagic.cim.narrative.TaleThreads;
import craftingismagic.cim.narrative.TaleThreads.Event;
import craftingismagic.cim.narrative.TaleThreads.Progress;

/**
 * Runs {@code .tale} threads for each player (design doc: How threads work): keeps where they are in
 * each one, notices the events threads listen for, and starts threads by themselves once their
 * {@code requires:} fits - unless they're {@code starts: on cue}, which only {@code ~ thread start}
 * begins. A thread starts listening from the event after the one that started it.
 * <p>
 * Events: {@code sleep}/{@code wake} from beds; {@code day}/{@code night}/{@code enter_biome} checked
 * once a second per player; {@code talk} from conversations; {@code fact} when a story fact becomes
 * true. {@code holiday} waits on the calendar. Effects can set facts that fire more events, so a chain
 * is cut off after {@link #MAX_CHAIN} hops.
 * <p>
 * Thread effects run with the player as both sides of the conversation unless a pony is involved
 * ({@code talk}, relationship facts) - so {@code ~ memory} in a stage is the player's own memory.
 */
public final class StoryThreads {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");
	private static final int CHECK_INTERVAL = 20;
	private static final int MAX_CHAIN = 8;

	public static final AttachmentType<Map<Identifier, Progress>> THREADS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "threads"),
			builder -> builder.persistent(Codec.unboundedMap(Identifier.CODEC, Progress.CODEC)).copyOnDeath());

	private static final Map<UUID, Identifier> lastBiome = new HashMap<>();
	private static final Map<UUID, Boolean> lastDay = new HashMap<>();
	private static int depth;

	private StoryThreads() {
	}

	public static void register() {
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayerEntity player) {
				fire(player, player, Event.of(cim("sleep")));
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayerEntity player) {
				fire(player, player, Event.of(cim("wake")));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(StoryThreads::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> startWhatFits(handler.getPlayer(), handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastBiome.remove(handler.getPlayer().getUuid());
			lastDay.remove(handler.getPlayer().getUuid());
		});
		DebugOverlay.addProvider((player, lines) -> {
			Map<Identifier, Progress> all = progress(player);
			long running = all.values().stream().filter(progress -> !progress.finished()).count();
			lines.add("Threads: " + running + " running, " + (all.size() - running) + " finished");
			all.entrySet().stream().filter(entry -> !entry.getValue().finished()).limit(4).forEach(entry ->
					lines.add("  " + entry.getKey() + " @ " + entry.getValue().stage() + " (" + entry.getValue().fired() + ")"));
		});
	}

	// ---- Events ----

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % CHECK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
			UUID uuid = player.getUuid();
			boolean day = player.getServerWorld().isDay();
			Boolean wasDay = lastDay.put(uuid, day);
			if (wasDay != null && wasDay != day) {
				fire(player, player, Event.of(cim(day ? "day" : "night")));
			}
			RegistryEntry<Biome> biome = player.getServerWorld().getBiome(player.getBlockPos());
			Identifier biomeId = biome.getKey().map(key -> key.getValue()).orElse(null);
			Identifier was = lastBiome.put(uuid, biomeId);
			if (was != null && biomeId != null && !was.equals(biomeId)) {
				enteredBiome(player, biome);
			}
		}
	}

	public static void enteredBiome(ServerPlayerEntity player, RegistryEntry<Biome> biome) {
		fire(player, player, new Event(cim("enter_biome"), args -> CimTaleVocabulary.biomeMatches(biome, args)));
	}

	/**
	 * A conversation with {@code pony} played {@code nodes}; {@code opened} when it just began.
	 * {@code talk(any)} fires when a conversation opens, {@code talk(<node>)} when it reaches that node.
	 */
	public static void talked(ServerPlayerEntity player, Entity pony, List<Identifier> nodes, boolean opened) {
		fire(player, pony, new Event(cim("talk"), args -> args.size() == 1
				&& (args.get(0).equals("any") ? opened : nodes.contains(id(args.get(0))))));
	}

	static void factBecameTrue(ServerPlayerEntity player, Entity speaker, Identifier fact) {
		fire(player, speaker, new Event(cim("fact"), args -> args.size() == 1 && id(args.get(0)).equals(fact)));
	}

	/** Starts any threads that now fit, then delivers {@code event} to the ones already running. */
	public static void fire(ServerPlayerEntity player, Entity speaker, Event event) {
		if (depth >= MAX_CHAIN) {
			LOGGER.warn("Thread events chained more than {} deep (last: {}) - stopping there", MAX_CHAIN, event.type());
			return;
		}
		depth++;
		try {
			TaleContext context = new TaleContext(player, speaker, player.getRandom());
			List<Identifier> running = progress(player).entrySet().stream()
					.filter(entry -> !entry.getValue().finished()).map(Map.Entry::getKey).toList();
			startWhatFits(player, speaker);
			for (Identifier id : running) {
				TaleAst.Thread thread = TaleLibrary.get().threads().get(id);
				Progress before = progress(player).get(id);
				if (thread == null || before == null) {
					continue;
				}
				Progress after = TaleThreads.fire(thread, before, event, context);
				// An effect may have moved this same thread already; that move wins.
				if (after != before && before.equals(progress(player).get(id))) {
					put(player, id, after);
				}
			}
		} finally {
			depth--;
		}
	}

	// ---- Starting and moving ----

	/** Starts every automatic thread whose {@code requires:} fits and that this player hasn't had yet. */
	private static void startWhatFits(ServerPlayerEntity player, Entity speaker) {
		Map<Identifier, Progress> mine = progress(player);
		TaleContext context = new TaleContext(player, speaker, player.getRandom());
		for (TaleAst.Thread thread : TaleLibrary.get().threads().values()) {
			if (!thread.onCue() && !mine.containsKey(thread.id()) && TaleThreads.requirementsHold(thread, context)) {
				put(player, thread.id(), TaleThreads.begin(thread));
			}
		}
	}

	/**
	 * {@code ~ thread start}: begins a thread at its first stage, if this player hasn't had it yet and
	 * (unless {@code force}) its {@code requires:} fits. Returns whether it started.
	 */
	public static boolean start(ServerPlayerEntity player, Identifier id, boolean force) {
		TaleAst.Thread thread = TaleLibrary.get().threads().get(id);
		if (thread == null || progress(player).containsKey(id)) {
			return false;
		}
		if (!force && !TaleThreads.requirementsHold(thread, new TaleContext(player, player, player.getRandom()))) {
			return false;
		}
		put(player, id, TaleThreads.begin(thread));
		return true;
	}

	/** {@code ~ thread advance}: moves to {@code stage} (or {@code END}), starting the thread if needed. */
	public static boolean advance(ServerPlayerEntity player, Identifier id, String stage) {
		TaleAst.Thread thread = TaleLibrary.get().threads().get(id);
		if (thread == null) {
			return false;
		}
		Optional<Progress> moved = TaleThreads.moveTo(thread, stage);
		moved.ifPresent(progress -> put(player, id, progress));
		return moved.isPresent();
	}

	/** Forgets a thread (or all of them), so it can start again - debug only. */
	public static int reset(ServerPlayerEntity player, Optional<Identifier> id) {
		Map<Identifier, Progress> all = new LinkedHashMap<>(progress(player));
		int before = all.size();
		if (id.isPresent()) {
			all.remove(id.get());
		} else {
			all.clear();
		}
		player.setAttached(THREADS, Map.copyOf(all));
		return before - all.size();
	}

	public static Map<Identifier, Progress> progress(Entity player) {
		return player.hasAttached(THREADS) ? player.getAttached(THREADS) : Map.of();
	}

	private static void put(Entity player, Identifier id, Progress progress) {
		Map<Identifier, Progress> all = new LinkedHashMap<>(progress(player));
		all.put(id, progress);
		player.setAttached(THREADS, Map.copyOf(all));
	}

	private static Identifier id(String text) {
		return Identifier.of(text.contains(":") ? text : "cim:" + text);
	}

	private static Identifier cim(String path) {
		return Identifier.of(Cim.MOD_ID, path);
	}
}
