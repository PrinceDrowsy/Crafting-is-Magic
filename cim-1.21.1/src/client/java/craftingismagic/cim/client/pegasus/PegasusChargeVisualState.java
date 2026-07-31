package craftingismagic.cim.client.pegasus;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Tracks, client-side, which entities are currently charging a Sonic
 * Rainboom and how far along (0 to 1) - computed locally from the tick the
 * charge started plus its known duration (both sent once by the server),
 * rather than needing a continuous progress sync every tick. Queried
 * independently by both {@link PegasusChargeEffectHandler} (to manage the
 * particle effect) and the player-stretch render mixin.
 */
public final class PegasusChargeVisualState {
	private record Charge(long startTick, int durationTicks) {
	}

	private static final Map<Integer, Charge> charges = new HashMap<>();

	private PegasusChargeVisualState() {
	}

	public static void start(int entityId, int durationTicks) {
		charges.put(entityId, new Charge(currentTick(), durationTicks));
	}

	public static void end(int entityId) {
		charges.remove(entityId);
	}

	/** @return progress from 0 to 1, or a negative value if this entity isn't currently charging. */
	public static float getProgress(int entityId) {
		Charge charge = charges.get(entityId);
		if (charge == null) {
			return -1.0F;
		}

		long elapsed = currentTick() - charge.startTick();
		return MathHelper.clamp((float) elapsed / charge.durationTicks(), 0.0F, 1.0F);
	}

	private static long currentTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null ? client.world.getTime() : 0L;
	}
}
