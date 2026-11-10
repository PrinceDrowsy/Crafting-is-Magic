package craftingismagic.cim.verse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.network.VerseLightS2CPayload;
import craftingismagic.cim.network.VerseWatchS2CPayload;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.verse.VerseEquation.Action;
import craftingismagic.cim.verse.VerseEquation.Step;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.PonyGeneticsAttachment;

/**
 * Makes verses real (design doc: The sorcery system, Verse): a unicorn casts, pays Stamina, and the equation
 * runs - step by step, each step's actions together, a step with "when" waiting for what it senses.
 * <p>
 * The first slice's effects: <b>Move</b> (creatures, items and blocks), <b>Light</b> (real light,
 * glowing, twinkling - in a crown), <b>Sound</b> (sound colors) and <b>Shift</b> (going yonder,
 * sending away). Senses: strike, gaze, something coming near, night falling, dawn breaking, landing.
 * <p>
 * Protections are technical only (design doc: no censorship): a few verses, effects, lights and flying
 * blocks per caster at a time; waiting verses give up after a while; nothing keeps a chunk loaded; and
 * everything placed is put right when the server stops.
 */
public final class VerseRuntime {
	private static final Identifier MOVE = Identifier.of("cim", "move");
	private static final Identifier LIGHT = Identifier.of("cim", "light");
	private static final Identifier SOUND = Identifier.of("cim", "sound");
	private static final Identifier SHIFT = Identifier.of("cim", "shift");
	private static final Identifier CHANGE = Identifier.of("cim", "change");

	private static final double REACH = 6;
	private static final double SIGHT = 64;
	private static final double AROUND = 5;
	private static final double THESE = 4;
	private static final double NEAR = 5;
	/** A verse's cost, as the reader adds it up, in Stamina points - the bar holds 100 (four notches). */
	private static final float STAMINA_PER_COST = 3f;
	/** How long a "when" waits before the verse gives up: a couple of minutes; night and dawn, a day. */
	private static final int WAIT_TICKS = 2400;
	private static final int DAY_WAIT_TICKS = 24000;
	private static final int MAX_CASTINGS = 4;
	private static final int MAX_EFFECTS = 24;
	private static final int MAX_LIGHT_BLOCKS = 12;
	private static final int MAX_FLYING_BLOCKS = 8;
	/**
	 * Upkeep, in Stamina a tick: holding magic is like holding something heavy - a trickle, and no
	 * recovering while you hold it (any drain pauses regen for that tick). A verse waiting on "when" is
	 * the lightest; lights cost more the more of them there are.
	 */
	private static final float WAITING_UPKEEP = 0.005f;
	private static final float LIGHTS_UPKEEP = 0.006f;
	private static final float PER_LIGHT_UPKEEP = 0.0015f;
	private static final float LIGHT_BLOCK_UPKEEP = 0.01f;
	private static final float SPIN_UPKEEP = 0.02f;

	/** Something a spell acts on: a creature, a block, or just a place. */
	private record Spot(Entity entity, BlockPos block, Direction side, Vec3d pos) {
		static Spot of(Entity entity) {
			return new Spot(entity, null, null, entity.getPos());
		}
	}

	/** One verse in progress. */
	private static final class Casting {
		final UUID caster;
		final ServerWorld world;
		final VerseEquation equation;
		int step;
		boolean triggered;
		Identifier waiting;
		long waitUntil;
		boolean struck;
		boolean landed;
		Boolean wasNight;
		Set<UUID> nearAtStart = Set.of();
		Entity sensed;

		Casting(UUID caster, ServerWorld world, VerseEquation equation) {
			this.caster = caster;
			this.world = world;
			this.equation = equation;
		}
	}

	/** An effect that plays out over time; {@link #tick} returns false when it's done. */
	private abstract static class Running {
		final UUID caster;

		Running(UUID caster) {
			this.caster = caster;
		}

		abstract boolean tick(ServerWorld world);

		/**
		 * Stamina a tick while the caster holds this (design doc: held costs upkeep, done costs once) - 0 for
		 * a finished act (a recolor, a block already flying to its landing).
		 */
		float upkeep() {
			return 0f;
		}

		/** Puts right anything left in the world - lights removed, a flying block set down. */
		void end(ServerWorld world) {
		}
	}

	public record Result(boolean cast, String message) {
	}

	private static final List<Casting> castings = new ArrayList<>();
	private static final List<Running> running = new ArrayList<>();
	private static final Map<Running, ServerWorld> worldOf = new HashMap<>();
	private static final Map<UUID, Boolean> strikeWatchSent = new HashMap<>();

	private VerseRuntime() {
	}

	// ---- Casting ----

	/** {@code player} casts {@code reading}. Unicorns only; costs Stamina; never throws on an odd verse. */
	public static Result cast(ServerPlayerEntity player, VerseReader.Reading reading) {
		if (!SpeciesAttachment.hasChosenSpecies(player) || SpeciesAttachment.getSpecies(player) != Species.UNICORN) {
			return new Result(false, "Only a unicorn's horn can cast Verse - though anyone can write it.");
		}
		if (reading.empty()) {
			return new Result(false, "Only poetry - nothing here to cast.");
		}
		if (castings.stream().filter(c -> c.caster.equals(player.getUuid())).count() >= MAX_CASTINGS) {
			return new Result(false, "You're already holding as many verses as you can.");
		}
		float cost = reading.cost() * STAMINA_PER_COST;
		if (!StaminaManager.hasEnough(player, cost)) {
			return new Result(false, "Not enough Stamina for this verse.");
		}
		StaminaManager.drain(player, cost);
		ServerWorld world = player.getServerWorld();
		sparkleHorn(world, player);
		Casting casting = new Casting(player.getUuid(), world, reading.equation());
		castings.add(casting);
		advance(casting, player);
		return new Result(true, reading.notes().isEmpty() ? "✦ Your verse takes hold." : "✦ Your verse takes hold - unevenly (see its notes).");
	}

	/** The caster struck - their attack key - while a verse waits for "when I strike". */
	public static void strike(ServerPlayerEntity player) {
		for (Casting casting : castings) {
			if (casting.caster.equals(player.getUuid()) && Identifier.of("cim", "strike").equals(casting.waiting)) {
				casting.struck = true;
			}
		}
	}

	/** The caster left: their waiting verses end; what's already in the world plays out. */
	public static void cancel(ServerPlayerEntity player) {
		castings.removeIf(c -> c.caster.equals(player.getUuid()));
		strikeWatchSent.remove(player.getUuid());
	}

	/** The server is stopping: put everything right - lights removed, flying blocks set down. */
	public static void stopAll() {
		for (Running effect : running) {
			effect.end(worldOf.get(effect));
		}
		running.clear();
		worldOf.clear();
		castings.clear();
	}

	/** Runs steps until one waits on a trigger, or the verse is done. */
	private static void advance(Casting casting, ServerPlayerEntity player) {
		List<Step> steps = casting.equation.steps();
		while (casting.step < steps.size()) {
			Step step = steps.get(casting.step);
			if (step.trigger().isPresent() && !casting.triggered) {
				beginWait(casting, player, step.trigger().get());
				return;
			}
			casting.triggered = false;
			casting.waiting = null;
			for (Action action : step.actions()) {
				act(casting, player, action);
			}
			casting.step++;
		}
		castings.remove(casting);
	}

	private static void beginWait(Casting casting, ServerPlayerEntity player, Identifier sense) {
		casting.waiting = sense;
		boolean dayWait = sense.getPath().equals("night_falls") || sense.getPath().equals("dawn");
		casting.waitUntil = casting.world.getTime() + (dayWait ? DAY_WAIT_TICKS : WAIT_TICKS);
		casting.struck = false;
		casting.landed = false;
		casting.wasNight = isNight(casting.world);
		Set<UUID> near = new HashSet<>();
		for (Entity entity : nearby(player, NEAR)) {
			near.add(entity.getUuid());
		}
		casting.nearAtStart = near;
	}

	// ---- Each tick ----

	public static void tick(MinecraftServer server) {
		List<Casting> fired = new ArrayList<>();
		for (Iterator<Casting> it = castings.iterator(); it.hasNext();) {
			Casting casting = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(casting.caster);
			if (player == null || player.isRemoved() || player.getServerWorld() != casting.world) {
				it.remove();
				continue;
			}
			if (casting.waiting == null) continue;
			if (casting.world.getTime() > casting.waitUntil) {
				it.remove();
				player.sendMessage(Text.literal("A verse you cast stops waiting."), true);
				continue;
			}
			Entity subject = sensed(casting, player);
			if (subject != null) {
				casting.sensed = subject == player ? null : subject;
				casting.triggered = true;
				fired.add(casting);
			}
		}
		// Carried on outside the loop: advancing can finish a verse (removing it) or start effects.
		for (Casting casting : fired) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(casting.caster);
			if (player != null) advance(casting, player);
		}
		for (Iterator<Running> it = running.iterator(); it.hasNext();) {
			Running effect = it.next();
			ServerWorld world = worldOf.get(effect);
			boolean keep;
			try {
				keep = world != null && effect.tick(world);
			} catch (RuntimeException e) {
				keep = false;
			}
			if (!keep) {
				if (world != null) effect.end(world);
				worldOf.remove(effect);
				it.remove();
			}
		}
		upkeep(server);
		syncStrikeWatch(server);
	}

	/** Each caster pays for everything they're still holding; whoever runs dry lets it all go. */
	private static void upkeep(MinecraftServer server) {
		Map<UUID, Float> owed = new HashMap<>();
		for (Casting casting : castings) {
			if (casting.waiting != null) owed.merge(casting.caster, WAITING_UPKEEP, Float::sum);
		}
		for (Running effect : running) {
			float upkeep = effect.upkeep();
			if (upkeep > 0) owed.merge(effect.caster, upkeep, Float::sum);
		}
		for (Map.Entry<UUID, Float> entry : owed.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player != null && !StaminaManager.drain(player, entry.getValue())) {
				release(player);
			}
		}
	}

	/** Out of Stamina: every verse still waiting, and everything still held, slips away. */
	private static void release(ServerPlayerEntity player) {
		UUID caster = player.getUuid();
		castings.removeIf(casting -> casting.caster.equals(caster) && casting.waiting != null);
		for (Iterator<Running> it = running.iterator(); it.hasNext();) {
			Running effect = it.next();
			if (!effect.caster.equals(caster) || effect.upkeep() <= 0) continue;
			ServerWorld world = worldOf.remove(effect);
			if (world != null) effect.end(world);
			it.remove();
		}
		player.sendMessage(Text.literal("You're spent - the magic you were holding slips away."), true);
	}

	/**
	 * Whether the sense a casting waits for has happened; returns the creature it sensed (gaze, near),
	 * or the caster for senses with no subject - null while still waiting.
	 */
	private static Entity sensed(Casting casting, ServerPlayerEntity player) {
		ServerWorld world = casting.world;
		long now = world.getTime();
		switch (casting.waiting.getPath()) {
			case "strike" -> {
				return casting.struck ? player : null;
			}
			case "gaze" -> {
				if (now % 4 != 0) return null;
				Spot looked = look(player, 24);
				return looked.entity() instanceof LivingEntity living && living != player ? living : null;
			}
			case "near" -> {
				if (now % 10 != 0) return null;
				for (Entity entity : nearby(player, NEAR)) {
					if (!casting.nearAtStart.contains(entity.getUuid())) return entity;
				}
				return null;
			}
			case "night_falls", "dawn" -> {
				boolean night = isNight(world);
				boolean was = casting.wasNight;
				casting.wasNight = night;
				boolean fired = casting.waiting.getPath().equals("night_falls") ? night && !was : !night && was;
				return fired ? player : null;
			}
			case "lands" -> {
				return casting.landed ? player : null;
			}
			default -> {
				return null;
			}
		}
	}

	private static void syncStrikeWatch(MinecraftServer server) {
		Set<UUID> waiting = new HashSet<>();
		for (Casting casting : castings) {
			if (Identifier.of("cim", "strike").equals(casting.waiting)) waiting.add(casting.caster);
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean now = waiting.contains(player.getUuid());
			if (strikeWatchSent.getOrDefault(player.getUuid(), false) != now) {
				strikeWatchSent.put(player.getUuid(), now);
				ServerPlayNetworking.send(player, new VerseWatchS2CPayload(now));
			}
		}
	}

	// ---- Acting ----

	private static void act(Casting casting, ServerPlayerEntity player, Action action) {
		List<Spot> spots = resolve(action.target(), casting, player);
		if (spots.isEmpty()) {
			player.sendMessage(Text.literal(VerseReader.name(action.piece()) + " finds nothing to act on."), true);
			return;
		}
		for (int repeat = 0; repeat < action.repeats(); repeat++) {
			if (repeat == 0) {
				apply(casting, player, action, spots);
			} else {
				int delay = repeat * 14;
				start(player, new Running(player.getUuid()) {
					int wait = delay;

					@Override
					boolean tick(ServerWorld world) {
						if (--wait > 0) return true;
						ServerPlayerEntity caster = world.getServer().getPlayerManager().getPlayer(this.caster);
						if (caster != null) apply(casting, caster, action, spots);
						return false;
					}
				});
			}
		}
	}

	private static void apply(Casting casting, ServerPlayerEntity player, Action action, List<Spot> spots) {
		Identifier piece = action.piece();
		for (Spot spot : spots) {
			if (piece.equals(MOVE)) move(casting, player, action, spot);
			else if (piece.equals(LIGHT)) light(player, action, spot);
			else if (piece.equals(SOUND)) sound(player, action, spot);
			else if (piece.equals(SHIFT)) shift(player, action, spot);
			else if (piece.equals(CHANGE)) change(player, action, spot);
		}
	}

	// ---- Change ----

	/**
	 * Recolors a pony - mane and tail, coat, eyes - until undone ("as it was"), or for as long as the verse
	 * says. Village ponies and the caster themself; another player's look is theirs to choose. The colors
	 * lie over the pony's genetics, never replacing them (see {@link SpellColors}).
	 */
	private static void change(ServerPlayerEntity player, Action action, Spot spot) {
		Entity entity = spot.entity();
		if (entity == null) {
			player.sendMessage(Text.literal("Change finds no one to change."), true);
			return;
		}
		if (entity instanceof PlayerEntity && entity != player) {
			player.sendMessage(Text.literal("Another player's look is theirs to choose."), true);
			return;
		}
		if (entity != player && !PonyGeneticsAttachment.hasGenetics(entity)) {
			player.sendMessage(Text.literal("Only a pony's colors can change - for now."), true);
			return;
		}
		ServerWorld world = player.getServerWorld();
		Vec3d at = entity.getPos();
		if (action.mode().equals("restore")) {
			boolean undone = false;
			if (action.parts().isEmpty()) {
				undone = SpellColors.clear(entity, null);
			} else {
				for (String part : action.parts()) undone |= SpellColors.clear(entity, part);
			}
			if (!undone) {
				player.sendMessage(Text.literal("There's no change there to undo."), true);
				return;
			}
			world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + entity.getHeight() * 0.6, at.z, 12, 0.3, 0.4, 0.3, 0.02);
			return;
		}
		if (action.parts().isEmpty() || action.tint().isEmpty()) return; // the verse's notes say what's missing
		int rgb = Integer.parseInt(action.tint().substring(1), 16);
		long expires = action.ending().isEmpty() ? -1 : world.getTime() + duration(world, action, 1200, 1f);
		for (String part : action.parts()) {
			SpellColors.set(entity, part, rgb, expires);
		}
		if (expires >= 0) start(player, new ChangeTimer(player.getUuid(), entity, expires));
		Vector3f color = new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		world.spawnParticles(new DustParticleEffect(color, 1.2f), at.x, at.y + entity.getHeight() * 0.6, at.z, 24,
				entity.getWidth() * 0.5, entity.getHeight() * 0.35, entity.getWidth() * 0.5, 0);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.4f);
	}

	// ---- Move ----

	private static void move(Casting casting, ServerPlayerEntity player, Action action, Spot spot) {
		float s = action.strength();
		if (spot.entity() != null) {
			Entity entity = spot.entity();
			Vec3d away = horizontal(entity.getPos().subtract(player.getPos()));
			switch (action.mode()) {
				case "lift" -> entity.addVelocity(0, 0.5 + 0.35 * s, 0);
				case "push" -> entity.addVelocity(away.x * 0.9 * s, 0.25, away.z * 0.9 * s);
				case "pull" -> entity.addVelocity(-away.x * 0.9 * s, 0.2, -away.z * 0.9 * s);
				case "carry" -> entity.setVelocity(lob(entity.getPos(), destination(player, action)));
				case "spin" -> start(player, new Spin(player.getUuid(), entity, duration(casting.world, action, 40, 1f), 24f * s));
				default -> {
				}
			}
			entity.velocityModified = true;
			if (action.gentle() && entity instanceof LivingEntity living) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
			}
			if (!action.mode().equals("spin")) {
				start(player, new LandWatch(player.getUuid(), entity, casting));
			}
		} else if (spot.block() != null) {
			moveBlock(casting, player, action, spot);
		}
	}

	private static void moveBlock(Casting casting, ServerPlayerEntity player, Action action, Spot spot) {
		ServerWorld world = casting.world;
		BlockPos pos = spot.block();
		BlockState state = world.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) return;
		if (state.getHardness(world, pos) < 0 || world.getBlockEntity(pos) != null) {
			player.sendMessage(Text.literal("That block is too rooted (or too full) for Move to budge."), true);
			return;
		}
		if (running.stream().filter(r -> r instanceof BlockFlight && r.caster.equals(player.getUuid())).count() >= MAX_FLYING_BLOCKS) {
			player.sendMessage(Text.literal("You're already carrying as many blocks as you can."), true);
			return;
		}
		float s = action.strength();
		Vec3d start = Vec3d.of(pos);
		Vec3d away = horizontal(Vec3d.ofCenter(pos).subtract(player.getPos()));
		List<Vec3d> path = new ArrayList<>();
		path.add(start);
		boolean returnHome = false;
		int hold = 0;
		Vec3d end;
		switch (action.mode()) {
			case "push" -> end = start.add(away.multiply(3 + 2 * s));
			case "pull" -> {
				// Toward the caster, stopping a couple of blocks short of them.
				Vec3d toward = player.getPos().subtract(start);
				end = start.add(horizontal(toward).multiply(Math.min(Math.max(0, toward.horizontalLength() - 2), 12)));
			}
			case "carry" -> end = Vec3d.of(BlockPos.ofFloored(destination(player, action)));
			case "spin" -> {
				end = start;
				returnHome = true;
				hold = duration(world, action, 60, 1f);
			}
			default -> { // lift: up, a while held, then home
				end = start;
				returnHome = true;
				hold = duration(world, action, 60, 1f);
			}
		}
		double rise = action.mode().equals("lift") ? 1.5 + s : 1.2;
		path.add(start.add(0, rise, 0));
		if (!returnHome) {
			path.add(end.add(0, rise, 0));
			path.add(end); // and down to rest
		}
		world.removeBlock(pos, false);
		DisplayEntity.BlockDisplayEntity display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
		display.setBlockState(state);
		display.setNoGravity(true);
		display.setPosition(start.x, start.y, start.z);
		world.spawnEntity(display);
		start(player, new BlockFlight(player.getUuid(), casting, display, state, path, end, hold, returnHome,
				action.mode().equals("spin") ? 18f * s : 0f));
	}

	// ---- Light ----

	private static void light(ServerPlayerEntity player, Action action, Spot spot) {
		ServerWorld world = player.getServerWorld();
		float love = action.bindings().isEmpty() ? 0f : love(player, spot.entity());
		int ticks = duration(world, action, action.shape().equals("crown") ? 400 : 600, factor(action, "duration", love));
		boolean twinkle = action.twinkle();
		// A crown is always a ring of little lights, whichever light word made it.
		String mode = action.shape().equals("crown") && spot.entity() != null ? "lights" : action.mode();
		switch (mode) {
			case "lights" -> {
				boolean crown = action.shape().equals("crown");
				int count = Math.max(1, Math.round((crown ? 8 : 6) * factor(action, "amount", love)));
				float brightness = factor(action, "brightness", love);
				Vector3f color = color(player, action, love);
				start(player, new Sparkle(player.getUuid(), spot, crown, twinkle, count, 0.6f * brightness, color, ticks));
				glowLight(world, spot, color, crown ? 3f : 4f, 0.7f * brightness, ticks, crown, twinkle, count);
			}
			case "glow" -> {
				if (spot.entity() instanceof LivingEntity living) {
					living.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0, false, false));
					Vector3f color = color(player, action, love);
					start(player, new Sparkle(player.getUuid(), spot, false, twinkle, twinkle ? 4 : 2, 0.5f, color, ticks));
					glowLight(world, spot, color, 6f, factor(action, "brightness", love), ticks, false, twinkle, 1);
				} else {
					shine(player, action, spot, ticks, love);
				}
			}
			default -> shine(player, action, spot, ticks, love);
		}
	}

	private static void shine(ServerPlayerEntity player, Action action, Spot spot, int ticks, float love) {
		ServerWorld world = player.getServerWorld();
		long placed = running.stream().filter(r -> r instanceof Lights && r.caster.equals(player.getUuid())).count();
		if (placed >= MAX_LIGHT_BLOCKS) {
			player.sendMessage(Text.literal("You're already holding as many lights as you can."), true);
			return;
		}
		BlockPos base = spot.entity() != null ? spot.entity().getBlockPos()
				: spot.block() != null && spot.side() != null ? spot.block().offset(spot.side()) : BlockPos.ofFloored(spot.pos());
		BlockPos at = null;
		for (BlockPos candidate : List.of(base, base.up(), base.down())) {
			if (world.getBlockState(candidate).isAir()) {
				at = candidate;
				break;
			}
		}
		if (at == null) return;
		int level = MathHelper.clamp(Math.round(15 * Math.min(1f, 0.6f * factor(action, "brightness", love))), 6, 15);
		world.setBlockState(at, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, level));
		start(player, new Lights(player.getUuid(), at, ticks));
		Vec3d center = Vec3d.ofCenter(at);
		world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 6, 0.2, 0.2, 0.2, 0.01);
		// The light block lights the world for the game (crops, mobs); the Veil glow is what you see.
		glowLight(world, new Spot(null, at, null, center), color(player, action, love), 8f, factor(action, "brightness", love), ticks, false,
				action.twinkle(), 1);
	}

	/**
	 * How lit a twinkling light is right now, 0 to 1: mostly dim, with a quick bright flash on its own
	 * rhythm, so neighbors never flash together. Shared by the sparkles and the client's Veil lights, so
	 * the glow flashes with its sparkle.
	 */
	public static float twinkle(float ticks, int index) {
		float speed = 0.16f + (index * 0.37f % 1f) * 0.12f;
		float wave = (MathHelper.sin(ticks * speed + index * 2.4f) + 1f) / 2f;
		return wave * wave * wave * wave;
	}

	/** Tells clients nearby that the lights here (or on this creature) have ended - a zero-length light. */
	private static void endGlow(ServerWorld world, Spot spot) {
		Entity entity = spot.entity();
		Vec3d pos = entity != null ? entity.getPos() : spot.pos();
		VerseLightS2CPayload payload = new VerseLightS2CPayload(entity != null ? entity.getId() : -1, pos, 0, 0f, 0f, 0, false, false, 0);
		for (ServerPlayerEntity watcher : PlayerLookup.around(world, pos, 96)) {
			ServerPlayNetworking.send(watcher, payload);
		}
	}

	/** Tells every client nearby to make a real, colored Veil light here (or following this creature). */
	private static void glowLight(ServerWorld world, Spot spot, Vector3f color, float radius, float brightness, int ticks, boolean crown,
			boolean twinkle, int count) {
		Entity entity = spot.entity();
		Vec3d pos = entity != null ? entity.getPos() : spot.pos();
		int rgb = (Math.round(MathHelper.clamp(color.x(), 0f, 1f) * 255) << 16) | (Math.round(MathHelper.clamp(color.y(), 0f, 1f) * 255) << 8)
				| Math.round(MathHelper.clamp(color.z(), 0f, 1f) * 255);
		VerseLightS2CPayload payload = new VerseLightS2CPayload(entity != null ? entity.getId() : -1, pos, rgb,
				MathHelper.clamp(radius * (0.7f + 0.3f * brightness), 2f, 16f), MathHelper.clamp(brightness, 0.3f, 2.5f), ticks, crown, twinkle, count);
		for (ServerPlayerEntity watcher : PlayerLookup.around(world, pos, 96)) {
			ServerPlayNetworking.send(watcher, payload);
		}
	}

	/**
	 * The light's color: the color the verse names ("green lights"); else warming from pale to rose-gold
	 * with love, if bound; otherwise the caster's aura color.
	 */
	private static Vector3f color(ServerPlayerEntity player, Action action, float love) {
		if (!action.tint().isEmpty()) {
			int rgb = Integer.parseInt(action.tint().substring(1), 16);
			return new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		}
		boolean bound = action.bindings().stream().anyMatch(b -> b.property().equals("color"));
		if (bound) {
			return new Vector3f(MathHelper.lerp(love, 0.85f, 1.0f), MathHelper.lerp(love, 0.9f, 0.62f), MathHelper.lerp(love, 1.0f, 0.55f));
		}
		if (action.boosts().contains("color")) {
			return new Vector3f(1.0f, 0.75f, 0.45f);
		}
		return auraColor(player);
	}

	/** The caster's aura color - their Stamina color - as the default color of their magic. */
	private static Vector3f auraColor(ServerPlayerEntity player) {
		int aura = StaminaColorAttachment.getColors(player).fillColor();
		return new Vector3f(((aura >> 16) & 0xFF) / 255f, ((aura >> 8) & 0xFF) / 255f, (aura & 0xFF) / 255f);
	}

	// ---- Sound ----

	private static void sound(ServerPlayerEntity player, Action action, Spot spot) {
		ServerWorld world = player.getServerWorld();
		VerseLanguage.SoundColor color = VerseLanguage.color(action.sound().isEmpty() ? "harp" : action.sound());
		SoundEvent event = SoundEvent.of(color != null ? color.sound() : Identifier.ofVanilla("block.note_block.harp"));
		float pitch = action.pitch().equals("high") ? 1.6f : action.pitch().equals("low") ? 0.6f : 1.0f;
		float volume = action.gentle() ? 0.5f : Math.min(3f, action.strength());
		Vec3d at = spot.entity() != null ? spot.entity().getEyePos() : spot.pos();
		world.playSound(null, at.x, at.y, at.z, event, SoundCategory.PLAYERS, volume, pitch);
		world.spawnParticles(ParticleTypes.NOTE, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, pitch / 2.0);
		if (!action.ending().isEmpty()) {
			int ticks = duration(world, action, 200, 1f);
			start(player, new Running(player.getUuid()) {
				int left = ticks;

				@Override
				boolean tick(ServerWorld w) {
					if (--left % 20 == 0) {
						Vec3d where = spot.entity() != null ? spot.entity().getEyePos() : spot.pos();
						w.playSound(null, where.x, where.y, where.z, event, SoundCategory.PLAYERS, volume, pitch);
					}
					return left > 0;
				}
			});
		}
	}

	// ---- Shift ----

	private static void shift(ServerPlayerEntity player, Action action, Spot spot) {
		ServerWorld world = player.getServerWorld();
		Entity who = spot.entity() != null ? spot.entity() : null;
		if (who == null) {
			player.sendMessage(Text.literal("Shift can only carry a creature, not a block."), true);
			return;
		}
		Vec3d to;
		if (action.mode().equals("banish") || action.destination().equals("away")) {
			to = somewhereAway(world, who, 8 + 4 * action.strength());
			if (to == null) {
				player.sendMessage(Text.literal("Nowhere safe to send them."), true);
				return;
			}
		} else {
			to = destination(player, action);
			Box box = who.getBoundingBox().offset(to.subtract(who.getPos()));
			if (!world.isSpaceEmpty(who, box)) {
				player.sendMessage(Text.literal("Nowhere safe to go there."), true);
				return;
			}
		}
		Vec3d from = who.getPos();
		world.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.5, 0.3, 0.05);
		who.requestTeleport(to.x, to.y, to.z);
		world.spawnParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 30, 0.3, 0.5, 0.3, 0.05);
		world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);
	}

	private static Vec3d somewhereAway(ServerWorld world, Entity who, double distance) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = world.random.nextDouble() * Math.PI * 2;
			int x = MathHelper.floor(who.getX() + Math.cos(angle) * distance);
			int z = MathHelper.floor(who.getZ() + Math.sin(angle) * distance);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - who.getY()) > 16) continue;
			Vec3d to = new Vec3d(x + 0.5, y, z + 0.5);
			if (world.isSpaceEmpty(who, who.getBoundingBox().offset(to.subtract(who.getPos())))) return to;
		}
		return null;
	}

	// ---- Targets ----

	private static List<Spot> resolve(String target, Casting casting, ServerPlayerEntity player) {
		return switch (target) {
			case "self" -> List.of(Spot.of(player));
			case "this" -> List.of(look(player, REACH));
			case "yonder" -> List.of(look(player, SIGHT));
			case "around" -> nearby(player, AROUND).stream().map(Spot::of).toList();
			case "these" -> {
				Vec3d center = look(player, SIGHT).pos();
				yield player.getServerWorld().getOtherEntities(player, new Box(center, center).expand(THESE), VerseRuntime::affectable)
						.stream().map(Spot::of).toList();
			}
			case "sensed" -> casting.sensed != null && casting.sensed.isAlive() ? List.of(Spot.of(casting.sensed)) : List.of();
			default -> List.of();
		};
	}

	/** What the caster is looking at, within {@code range}: a creature, a block (with its face), or a point in the air. */
	private static Spot look(ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d facing = player.getRotationVec(1f);
		HitResult blockHit = player.raycast(range, 1f, false);
		double reach = blockHit.getType() == HitResult.Type.MISS ? range : eye.distanceTo(blockHit.getPos());
		Vec3d end = eye.add(facing.multiply(reach));
		EntityHitResult entityHit = ProjectileUtil.raycast(player, eye, end, player.getBoundingBox().stretch(facing.multiply(reach)).expand(1),
				entity -> !entity.isSpectator() && entity.canHit(), reach * reach);
		if (entityHit != null) {
			return Spot.of(entityHit.getEntity());
		}
		if (blockHit instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return new Spot(null, hit.getBlockPos(), hit.getSide(), hit.getPos());
		}
		return new Spot(null, null, null, end);
	}

	/** Where a carry or a "go" ends: where the caster is looking, on the near side of whatever's there. */
	private static Vec3d destination(ServerPlayerEntity player, Action action) {
		Spot spot = look(player, SIGHT);
		if (spot.block() != null && spot.side() != null) {
			Vec3d normal = Vec3d.of(spot.side().getVector());
			return spot.pos().add(normal.multiply(0.5)).add(0, spot.side() == Direction.UP ? 0 : -0.5, 0);
		}
		return spot.pos();
	}

	private static List<Entity> nearby(ServerPlayerEntity player, double radius) {
		return player.getServerWorld().getOtherEntities(player, player.getBoundingBox().expand(radius), VerseRuntime::affectable);
	}

	private static boolean affectable(Entity entity) {
		return (entity instanceof LivingEntity || entity instanceof ItemEntity) && entity.isAlive() && !entity.isSpectator();
	}

	// ---- Love, durations, helpers ----

	/**
	 * "My love": the caster's own love for {@code target}, 0 to 1 - their shared history (visits, gifts)
	 * and the warm memories they hold of them. Never the target's feelings. Other players: a middling
	 * 0.5 (nothing is measured between players yet); a creature you barely know: a little.
	 */
	static float love(ServerPlayerEntity caster, Entity target) {
		if (target == null) return 0.2f;
		if (target instanceof PlayerEntity) return 0.5f;
		int moments = FriendshipAttachment.getSharedMoments(target, caster.getUuid());
		long warm = MemoryStore.about(caster, target.getUuid()).stream()
				.filter(memory -> memory.emotion().map(e -> e == Emotion.JOY || e == Emotion.LOVE || e == Emotion.CALM).orElse(false))
				.count();
		return (float) (1 - Math.exp(-(moments + 2 * warm) / 10.0));
	}

	/** How much a property is raised: following love when bound (0.3x to 2x), a fixed lift when merely named. */
	private static float factor(Action action, String property, float love) {
		if (action.bindings().stream().anyMatch(binding -> binding.property().equals(property))) {
			return 0.3f + 1.7f * love;
		}
		return action.boosts().contains(property) ? 1.6f : 1f;
	}

	private static int duration(ServerWorld world, Action action, int fallback, float factor) {
		int ticks = switch (action.ending()) {
			case "moment" -> 60;
			case "while" -> 600;
			case "hour" -> 1000;
			case "night" -> ticksUntil(world, 23000);
			case "" -> fallback;
			default -> action.ending().endsWith("dawn") ? ticksUntil(world, 23000)
					: action.ending().endsWith("night_falls") ? ticksUntil(world, 13000) : 1200;
		};
		return Math.max(20, Math.round(ticks * factor));
	}

	private static int ticksUntil(ServerWorld world, long timeOfDay) {
		long now = Math.floorMod(world.getTimeOfDay(), 24000L);
		return (int) Math.floorMod(timeOfDay - now, 24000L);
	}

	private static boolean isNight(ServerWorld world) {
		long time = Math.floorMod(world.getTimeOfDay(), 24000L);
		return time >= 13000 && time < 23000;
	}

	private static Vec3d horizontal(Vec3d v) {
		Vec3d flat = new Vec3d(v.x, 0, v.z);
		return flat.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : flat.normalize();
	}

	/** The velocity that lobs something from {@code from} to land near {@code to}. */
	private static Vec3d lob(Vec3d from, Vec3d to) {
		Vec3d delta = to.subtract(from);
		double ticks = MathHelper.clamp(delta.horizontalLength() / 0.8, 10, 40);
		return new Vec3d(delta.x / ticks, delta.y / ticks + 0.04 * ticks, delta.z / ticks);
	}

	private static void sparkleHorn(ServerWorld world, ServerPlayerEntity player) {
		Vec3d head = player.getEyePos().add(player.getRotationVec(1f).multiply(0.3));
		world.spawnParticles(new DustParticleEffect(auraColor(player), 0.8f), head.x, head.y + 0.3, head.z, 8, 0.1, 0.1, 0.1, 0);
	}

	private static void start(ServerPlayerEntity player, Running effect) {
		if (running.stream().filter(r -> r.caster.equals(player.getUuid())).count() >= MAX_EFFECTS) {
			return;
		}
		running.add(effect);
		worldOf.put(effect, player.getServerWorld());
	}

	// ---- Running effects ----

	/**
	 * Little lights around a creature or place - or, as a crown, circling above their head. Twinkling,
	 * each flashes in and out on its own rhythm instead of holding steady.
	 */
	private static final class Sparkle extends Running {
		private final Spot spot;
		private final boolean crown;
		private final boolean twinkle;
		private final int count;
		private final float size;
		private final Vector3f color;
		private int left;
		private int age;
		private float spin;

		Sparkle(UUID caster, Spot spot, boolean crown, boolean twinkle, int count, float size, Vector3f color, int ticks) {
			super(caster);
			this.spot = spot;
			this.crown = crown;
			this.twinkle = twinkle;
			this.count = Math.min(count, 40);
			this.size = MathHelper.clamp(size, 0.3f, 2.5f);
			this.color = color;
			this.left = ticks;
		}

		@Override
		float upkeep() {
			return LIGHTS_UPKEEP + count * PER_LIGHT_UPKEEP;
		}

		@Override
		boolean tick(ServerWorld world) {
			if (--left <= 0) return false;
			Entity entity = spot.entity();
			if (entity != null && !entity.isAlive()) return false;
			age++;
			if (left % (twinkle ? 2 : 4) != 0) return true;
			Vec3d base = entity != null ? entity.getPos() : spot.pos();
			DustParticleEffect dust = new DustParticleEffect(color, size);
			spin += twinkle ? 0.175f : 0.35f; // the same turning speed, ticked twice as often
			for (int i = 0; i < count; i++) {
				float flash = 1f;
				if (twinkle) {
					// Each light's own rhythm: dark most of the time, then a quick bright flash.
					flash = twinkle(age, i);
					if (flash < 0.25f) continue;
				}
				double x;
				double y;
				double z;
				if (crown) {
					double angle = spin + i * Math.PI * 2 / count;
					double radius = 0.35 + (entity != null ? entity.getWidth() * 0.35 : 0.3);
					x = base.x + Math.cos(angle) * radius;
					z = base.z + Math.sin(angle) * radius;
					y = base.y + (entity != null ? entity.getHeight() : 1) + 0.25 + Math.sin(spin * 2 + i) * 0.04;
				} else {
					double width = entity != null ? entity.getWidth() : 1;
					double height = entity != null ? entity.getHeight() : 1;
					x = base.x + (world.random.nextDouble() - 0.5) * width * 1.6;
					y = base.y + world.random.nextDouble() * height * 1.2;
					z = base.z + (world.random.nextDouble() - 0.5) * width * 1.6;
				}
				if (twinkle) {
					world.spawnParticles(new DustParticleEffect(color, MathHelper.clamp(size * (0.6f + 0.9f * flash), 0.3f, 3f)), x, y, z, 1, 0, 0, 0, 0);
					if (flash > 0.92f) world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
				} else {
					world.spawnParticles(dust, x, y, z, 1, 0, 0, 0, 0);
				}
			}
			if (size > 1.1f && world.random.nextInt(3) == 0) {
				world.spawnParticles(ParticleTypes.END_ROD, base.x, base.y + (entity != null ? entity.getHeight() + 0.3 : 1), base.z, 1, 0.3, 0.1, 0.3, 0);
			}
			return true;
		}

		@Override
		void end(ServerWorld world) {
			endGlow(world, spot);
		}
	}

	/** A real (invisible) light block, removed when its time is up - or when the server stops. */
	private static final class Lights extends Running {
		private final BlockPos at;
		private int left;

		Lights(UUID caster, BlockPos at, int ticks) {
			super(caster);
			this.at = at;
			this.left = ticks;
		}

		@Override
		float upkeep() {
			return LIGHT_BLOCK_UPKEEP;
		}

		@Override
		boolean tick(ServerWorld world) {
			return --left > 0;
		}

		@Override
		void end(ServerWorld world) {
			if (world.getBlockState(at).isOf(Blocks.LIGHT)) {
				world.removeBlock(at, false);
			}
			endGlow(world, new Spot(null, at, null, Vec3d.ofCenter(at)));
		}
	}

	/** Lifts a timed Change when its time is up (and {@link SpellColors} catches it if the pony was away). */
	private static final class ChangeTimer extends Running {
		private final Entity entity;
		private final long expires;

		ChangeTimer(UUID caster, Entity entity, long expires) {
			super(caster);
			this.entity = entity;
			this.expires = expires;
		}

		@Override
		boolean tick(ServerWorld world) {
			if (entity.isRemoved()) return false;
			if (world.getTime() < expires) return true;
			SpellColors.prune(entity, world.getTime());
			return false;
		}
	}

	/** A creature turning in place. */
	private static final class Spin extends Running {
		private final Entity entity;
		private final float degreesPerTick;
		private int left;

		Spin(UUID caster, Entity entity, int ticks, float degreesPerTick) {
			super(caster);
			this.entity = entity;
			this.left = ticks;
			this.degreesPerTick = degreesPerTick;
		}

		@Override
		float upkeep() {
			return SPIN_UPKEEP;
		}

		@Override
		boolean tick(ServerWorld world) {
			if (--left <= 0 || !entity.isAlive()) return false;
			float yaw = entity.getYaw() + degreesPerTick;
			entity.setYaw(yaw);
			entity.setHeadYaw(yaw);
			if (entity instanceof LivingEntity living) living.setBodyYaw(yaw);
			return true;
		}
	}

	/** Watches a moved creature until it lands - for "when it lands". */
	private static final class LandWatch extends Running {
		private final Entity entity;
		private final Casting casting;
		private int age;

		LandWatch(UUID caster, Entity entity, Casting casting) {
			super(caster);
			this.entity = entity;
			this.casting = casting;
		}

		@Override
		boolean tick(ServerWorld world) {
			if (!entity.isAlive() || ++age > 200) return false;
			if (age > 4 && entity.isOnGround()) {
				casting.landed = true;
				return false;
			}
			return true;
		}
	}

	/** A block in flight: up, across, (held, spinning,) and set down - or back home. */
	private static final class BlockFlight extends Running {
		private static final double SPEED = 0.35;
		private final Casting casting;
		private final DisplayEntity.BlockDisplayEntity display;
		private final BlockState state;
		private final List<Vec3d> path;
		private final Vec3d end;
		private final boolean returnHome;
		private final float spinPerTick;
		private int hold;
		private int leg = 1;
		private Vec3d at;
		private boolean placed;

		BlockFlight(UUID caster, Casting casting, DisplayEntity.BlockDisplayEntity display, BlockState state, List<Vec3d> path, Vec3d end,
				int hold, boolean returnHome, float spinPerTick) {
			super(caster);
			this.casting = casting;
			this.display = display;
			this.state = state;
			this.path = new ArrayList<>(path);
			this.end = end;
			this.hold = hold;
			this.returnHome = returnHome;
			this.spinPerTick = spinPerTick;
			this.at = path.get(0);
			if (returnHome) this.path.add(path.get(0)); // up, (hold), and back down to where it was
		}

		@Override
		boolean tick(ServerWorld world) {
			if (display.isRemoved()) return false;
			if (spinPerTick != 0) display.setYaw(display.getYaw() + spinPerTick);
			if (leg >= path.size()) return false;
			Vec3d target = path.get(leg);
			Vec3d delta = target.subtract(at);
			if (delta.length() <= SPEED) {
				at = target;
				leg++;
				// At the top of a lift (or a spin), hold it there a while before bringing it back.
				if (returnHome && leg == path.size() - 1 && hold > 0) {
					hold--;
					leg--;
				}
			} else {
				at = at.add(delta.normalize().multiply(SPEED));
			}
			display.setPosition(at.x, at.y, at.z);
			return leg < path.size() || hold > 0;
		}

		@Override
		void end(ServerWorld world) {
			if (placed) return;
			placed = true;
			Vec3d rest = returnHome ? path.get(0) : end;
			BlockPos landing = landing(world, BlockPos.ofFloored(rest.add(0.5, 0, 0.5)));
			if (landing != null) {
				world.setBlockState(landing, state);
			} else {
				Block.dropStacks(state, world, BlockPos.ofFloored(at));
			}
			display.discard();
			casting.landed = true;
			Vec3d dust = landing != null ? Vec3d.ofCenter(landing) : at;
			world.spawnParticles(ParticleTypes.CLOUD, dust.x, dust.y, dust.z, 6, 0.3, 0.2, 0.3, 0.01);
		}

		/** Where the block comes to rest: the first open space falling down from {@code from}. */
		private static BlockPos landing(ServerWorld world, BlockPos from) {
			BlockPos pos = from;
			for (int i = 0; i < 16; i++) {
				if (!world.getBlockState(pos).isReplaceable()) {
					return null;
				}
				if (!world.getBlockState(pos.down()).isReplaceable()) {
					return pos;
				}
				pos = pos.down();
			}
			return world.getBlockState(pos).isReplaceable() ? pos : null;
		}
	}
}
