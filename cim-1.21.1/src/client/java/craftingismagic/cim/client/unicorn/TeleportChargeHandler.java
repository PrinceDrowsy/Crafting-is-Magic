package craftingismagic.cim.client.unicorn;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.AbilityRegistry;
import craftingismagic.cim.client.ability.SelectedAbility;
import craftingismagic.cim.network.CancelTeleportChargeC2SPayload;
import craftingismagic.cim.network.StartTeleportChargeC2SPayload;

/**
 * Teleport is hold-to-charge (see {@code TeleportManager}), so like Levitation's own selection
 * handler this needs its own tick-based poll of the activation key rather than the generic
 * single-press {@code AbilityActivationHandler}. A fresh press while Teleport is selected either
 * starts a charge toward wherever the player's looking, or - if Shift is held too, mirroring
 * Levitation's own Shift-modifier convention - opens the shortcut ring instead. Releasing before
 * the server says the charge completed cancels it, same as running out of Stamina mid-charge.
 */
public final class TeleportChargeHandler {
	private static boolean wasHoldingActivate = false;

	private TeleportChargeHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean isSelected = SelectedAbility.isSelected(AbilityRegistry.TELEPORT);
			boolean holdingActivate = isSelected && AbilityActivationKeyBinding.ACTIVATE_ABILITY.isPressed();
			boolean justPressed = holdingActivate && !wasHoldingActivate;
			boolean justReleased = wasHoldingActivate && !holdingActivate;
			wasHoldingActivate = holdingActivate;

			if (justPressed) {
				if (client.options.sneakKey.isPressed()) {
					client.setScreen(new TeleportShortcutMenuScreen());
				} else {
					ClientPlayNetworking.send(new StartTeleportChargeC2SPayload());
				}
			} else if (justReleased) {
				ClientPlayNetworking.send(new CancelTeleportChargeC2SPayload());
			}
		});
	}
}
