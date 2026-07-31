package craftingismagic.cim.pegasus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Dash for active (elytra-glide) flight: double-tapping forward adds a
 * burst of speed along the player's full look direction (so diving into a
 * dash sends you along the dive angle, not flattened to horizontal) on
 * top of whatever velocity they already have - a single additive impulse,
 * the same way vanilla's own firework-elytra boost works, rather than
 * locking velocity to a fixed value for a duration and then resetting it.
 * There's no explicit taper afterward either: normal elytra drag bleeds
 * the extra speed off naturally. A cooldown stops the tap-edge trigger
 * from chaining boosts every tick it's held.
 *
 * <p>Deliberately uncapped: chaining dashes on top of an already-fast dive
 * can stack into a huge burst of speed, which is what makes the player
 * eligible to start charging a Sonic Rainboom - see
 * {@link PegasusRainboomCharge}, which intercepts the same double-tap
 * trigger once the player is already fast enough, so a plain dash no
 * longer auto-triggers the Rainboom itself.
 */
public final class PegasusDashManager {
	private static final double DASH_BOOST_SPEED = 0.6;
	private static final int DASH_COOLDOWN_TICKS = 20;

	private static final Map<UUID, Integer> cooldownTicksRemaining = new HashMap<>();

	private PegasusDashManager() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID id = player.getUuid();
			Integer cooldown = cooldownTicksRemaining.get(id);
			if (cooldown == null) {
				continue;
			}

			if (cooldown <= 1) {
				cooldownTicksRemaining.remove(id);
			} else {
				cooldownTicksRemaining.put(id, cooldown - 1);
			}
		}
	}

	public static void tryDash(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (!PegasusFlightManager.isActivelyFlying(player) || cooldownTicksRemaining.getOrDefault(id, 0) > 0) {
			return;
		}

		Vec3d boost = player.getRotationVec(1.0F).multiply(DASH_BOOST_SPEED);
		Vec3d boosted = player.getVelocity().add(boost);

		player.setVelocity(boosted);
		PegasusVelocitySync.push(player);
		cooldownTicksRemaining.put(id, DASH_COOLDOWN_TICKS);
	}

	public static void cancel(ServerPlayerEntity player) {
		cooldownTicksRemaining.remove(player.getUuid());
	}
}
