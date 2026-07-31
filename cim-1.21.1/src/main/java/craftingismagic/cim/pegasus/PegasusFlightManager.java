package craftingismagic.cim.pegasus;

import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * Pegasus flight: toggled on/off via the Flight ability (select it in the
 * radial menu, press the activation key), same pattern as every other
 * ability rather than a double-jump gesture - a double-jump trigger was
 * tried first, but detecting it client-side turned out to be an inherent
 * race condition (walking itself causes brief single-tick "not on
 * ground" blips that a jump press can land on), giving inconsistent
 * results. Unicopia (an established MLP mod with its own Pegasus flight)
 * uses a plain ability-toggle for exactly this reason, so this mirrors
 * that instead.
 *
 * <p>Toggling on enters passive hover - a capped-speed, creative-flight-
 * style float, reusing vanilla's own creative flight movement rather than
 * reimplementing it - with a small upward hop so takeoff is visible even
 * from a standing start. Getting fast (past a speed threshold, or
 * manually via the sprint key, mirroring how sprint speeds up creative
 * flight) switches to active flight, which reuses vanilla's own elytra
 * glide physics ({@code LivingEntity#isFallFlying}) for full aerodynamic
 * movement. Slowing back down returns to passive; toggling off (or
 * landing while active, which vanilla's own elytra logic already
 * handles) ends flight.
 *
 * <p>First-pass scope: this is the core dual-mode flight only. The
 * lean/dash/dive move kit layers on top of active-mode flight as a
 * follow-up.
 */
public final class PegasusFlightManager {
	/** The passive-mode "velocity limit" - well under vanilla's own default creative fly speed (0.05), for a slow flutter rather than a normal creative-flight pace. */
	private static final float PASSIVE_FLY_SPEED = 0.02F;
	/** Speed (blocks/tick) that auto-promotes passive hover into active glide. */
	private static final double ACTIVE_TRIGGER_SPEED = 0.3;
	/**
	 * Speed active glide has to drop below before falling back to passive
	 * hover. Deliberately far below the trigger speed (not just "a bit
	 * lower"): elytra drag can shave a bit off speed almost immediately
	 * after entering active mode, before gravity's had time to build up
	 * real fall speed to compensate, and a tighter gap here was causing an
	 * instant bounce back to passive right after switching in.
	 */
	private static final double PASSIVE_RETURN_SPEED = 0.05;
	/** Forward speed given instantly when sprint triggers active flight, so there's no ramp-up delay. */
	private static final double SPRINT_LAUNCH_SPEED = ACTIVE_TRIGGER_SPEED + 0.05;
	/**
	 * Upward position nudge given on takeoff, so it's visible even from a
	 * standing start. This has to be a teleport offset rather than a
	 * velocity kick: once {@code abilities.flying} is true, vanilla's own
	 * creative-flight movement recomputes velocity from current input every
	 * tick, so a one-time {@code setVelocity} call gets silently overwritten
	 * before it ever renders.
	 */
	private static final double TAKEOFF_HOP_HEIGHT = 0.6;

	private PegasusFlightManager() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			tickPlayer(player);
		}
	}

	public static void toggleFlight(ServerPlayerEntity player) {
		if (!isPegasus(player)) {
			return;
		}

		if (player.isFallFlying()) {
			player.stopFallFlying();
			stopFlying(player);
			return;
		}

		if (player.getAbilities().flying) {
			stopFlying(player);
			return;
		}

		startPassiveFlight(player);
		player.requestTeleportOffset(0.0, TAKEOFF_HOP_HEIGHT, 0.0);
	}

	/** Whether this player is currently in active (elytra-glide) flight - used by the mixin that keeps vanilla's per-tick elytra-equipped check from cancelling it. */
	public static boolean isActivelyFlying(ServerPlayerEntity player) {
		return isPegasus(player) && player.isFallFlying();
	}

	private static void tickPlayer(ServerPlayerEntity player) {
		if (!isPegasus(player)) {
			return;
		}

		if (player.isFallFlying()) {
			if (player.getVelocity().length() < PASSIVE_RETURN_SPEED) {
				player.stopFallFlying();
				startPassiveFlight(player);
			}
			return;
		}

		PlayerAbilities abilities = player.getAbilities();
		if (!abilities.flying) {
			return;
		}

		if (player.isSprinting()) {
			Vec3d launch = player.getRotationVec(1.0F).multiply(SPRINT_LAUNCH_SPEED);
			stopFlying(player);
			player.setVelocity(launch);
			PegasusVelocitySync.push(player);
			player.startFallFlying();
			return;
		}

		if (player.getVelocity().length() > ACTIVE_TRIGGER_SPEED) {
			stopFlying(player);
			player.startFallFlying();
		}
	}

	private static boolean isPegasus(ServerPlayerEntity player) {
		return SpeciesAttachment.hasChosenSpecies(player) && SpeciesAttachment.getSpecies(player) == Species.PEGASUS;
	}

	private static void startPassiveFlight(ServerPlayerEntity player) {
		PlayerAbilities abilities = player.getAbilities();
		abilities.allowFlying = true;
		abilities.flying = true;
		abilities.setFlySpeed(PASSIVE_FLY_SPEED);
		player.sendAbilitiesUpdate();
	}

	private static void stopFlying(ServerPlayerEntity player) {
		PlayerAbilities abilities = player.getAbilities();
		if (abilities.flying || abilities.allowFlying) {
			abilities.flying = false;
			abilities.allowFlying = false;
			player.sendAbilitiesUpdate();
		}
	}
}
