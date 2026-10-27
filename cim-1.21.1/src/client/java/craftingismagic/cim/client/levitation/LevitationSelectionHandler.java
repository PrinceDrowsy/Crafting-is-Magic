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
 *
 * <p>Confirms whenever a selection is going on and the key isn't held - not only on the tick it's let
 * go - once per selection. The server's word that a selection started can arrive after a quick tap is
 * already over (it's slow while a world is still loading), and waiting for a release that already
 * happened left the selection stuck until a second press.
 */
public final class LevitationSelectionHandler {
	private static boolean confirmSent = false;

	private LevitationSelectionHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!LevitationClientState.isSelecting()) {
				confirmSent = false;
				return;
			}
			boolean isSelected = SelectedAbility.isSelected(AbilityRegistry.LEVITATION);
			boolean holdingActivate = isSelected && AbilityActivationKeyBinding.ACTIVATE_ABILITY.isPressed();
			if (!holdingActivate && !confirmSent) {
				confirmSent = true;
				ClientPlayNetworking.send(new ConfirmLevitationSelectionC2SPayload());
			}
		});
	}
}
