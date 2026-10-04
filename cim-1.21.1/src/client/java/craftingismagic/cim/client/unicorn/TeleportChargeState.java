package craftingismagic.cim.client.unicorn;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Tracks, client-side, whether the local player is currently charging a Teleport and how far along
 * (0 to 1) - computed locally from the tick the charge started plus its known duration (both sent
 * once by the server), rather than needing a continuous progress sync every tick. Unlike {@code
 * PegasusChargeVisualState}, this only ever needs to track the local player (no other player can
 * see someone else's Teleport charge), so there's no per-entity map.
 */
public final class TeleportChargeState {
	private static Long startTick = null;
	private static int durationTicks = 0;

	private TeleportChargeState() {
	}

	public static void start(int ticks) {
		startTick = currentTick();
		durationTicks = ticks;
	}

	public static void end() {
		startTick = null;
	}

	public static boolean isCharging() {
		return startTick != null;
	}

	/** @return progress from 0 to 1, or 0 if not currently charging. */
	public static float getProgress() {
		if (startTick == null) {
			return 0.0F;
		}

		long elapsed = currentTick() - startTick;
		return MathHelper.clamp((float) elapsed / durationTicks, 0.0F, 1.0F);
	}

	private static long currentTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null ? client.world.getTime() : 0L;
	}
}
