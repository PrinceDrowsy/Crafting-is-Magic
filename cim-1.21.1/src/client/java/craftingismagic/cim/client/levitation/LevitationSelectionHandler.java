package craftingismagic.cim.client.levitation;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.AbilityRegistry;
import craftingismagic.cim.client.ability.SelectedAbility;
import craftingismagic.cim.network.ConfirmLevitationSelectionC2SPayload;

/**
 * Confirms a Levitation block selection when the activation key is
 * released while selecting. This needs its own tick-based poll (like the
 * Earth Pony charge jump) because it cares about the key being *released*
 * mid-hold, not just a fresh press - the generic {@code
 * AbilityActivationHandler} only fires once on press.
 */
public final class LevitationSelectionHandler {
	private static boolean wasHoldingActivate = false;

	private LevitationSelectionHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean isSelected = SelectedAbility.isSelected(AbilityRegistry.LEVITATION);
			boolean holdingActivate = isSelected && AbilityActivationKeyBinding.ACTIVATE_ABILITY.isPressed();
			boolean justReleased = wasHoldingActivate && !holdingActivate;
			wasHoldingActivate = holdingActivate;

			if (justReleased && LevitationClientState.isSelecting()) {
				ClientPlayNetworking.send(new ConfirmLevitationSelectionC2SPayload());
			}
		});
	}
}
