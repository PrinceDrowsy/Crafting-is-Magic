package craftingismagic.cim.pegasus;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.network.PegasusRainboomTrailS2CPayload;

/**
 * The rainbow/sparkle trail a player leaves behind right after a Sonic
 * Rainboom fires - lasts a bit longer than the ring itself before fading.
 * Both the visual ribbon/sparkles and the looping shimmer sound are
 * entirely client-driven (see {@code PegasusRainboomTrailEffectHandler} and
 * {@code RainboomTrailShimmerSound}) so they can track the player's own
 * position every tick rather than a periodically stale one; this class just
 * broadcasts the one-time start signal.
 */
public final class PegasusRainboomTrail {
	/** How much longer the trail lasts than the ring's own ~2s lifetime. */
	private static final int TRAIL_DURATION_TICKS = 60;

	private PegasusRainboomTrail() {
	}

	public static void start(ServerPlayerEntity player) {
		PegasusRainboomTrailS2CPayload payload = new PegasusRainboomTrailS2CPayload(player.getId(), TRAIL_DURATION_TICKS);
		for (ServerPlayerEntity nearby : PlayerLookup.around(player.getServerWorld(), player.getPos(), 128.0)) {
			ServerPlayNetworking.send(nearby, payload);
		}
	}
}
