package craftingismagic.cim.client.pegasus;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;

import craftingismagic.cim.network.PegasusChargeReleaseC2SPayload;
import craftingismagic.cim.network.PegasusDashC2SPayload;

/**
 * Sends a dash request on a double-tap of the forward key while actively
 * flying. A single fresh press doesn't work as a trigger here: forward is
 * normally held down continuously while flying forward, so a plain
 * press-edge almost never fires again once you're already moving. Double-
 * tap (two press-edges within a short window) works regardless of whether
 * the key was already held going into the first tap.
 *
 * <p>If the player is already fast enough, that same double-tap starts a
 * Sonic Rainboom charge server-side instead of an instant dash - releasing
 * forward before the charge completes cancels it, so this also reports
 * the release edge unconditionally (the server just no-ops it if nothing
 * is charging).
 */
public final class PegasusDashKeyHandler {
	private static final long DOUBLE_TAP_WINDOW_TICKS = 7;

	private static boolean wasForwardPressed = false;
	private static long lastPressTick = Long.MIN_VALUE;
	private static long tickCounter = 0;

	private PegasusDashKeyHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tickCounter++;

			ClientPlayerEntity player = client.player;
			if (player == null) {
				wasForwardPressed = false;
				return;
			}

			boolean forwardPressed = client.options.forwardKey.isPressed();
			if (forwardPressed && !wasForwardPressed) {
				if (tickCounter - lastPressTick <= DOUBLE_TAP_WINDOW_TICKS) {
					if (PegasusFlightVisuals.isInActiveFlight(player)) {
						ClientPlayNetworking.send(new PegasusDashC2SPayload());
					}
					lastPressTick = Long.MIN_VALUE;
				} else {
					lastPressTick = tickCounter;
				}
			} else if (!forwardPressed && wasForwardPressed && PegasusFlightVisuals.isInActiveFlight(player)) {
				ClientPlayNetworking.send(new PegasusChargeReleaseC2SPayload());
			}
			wasForwardPressed = forwardPressed;
		});
	}
}
