package craftingismagic.cim.client.ability;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;

import craftingismagic.cim.species.SpeciesAttachment;

/**
 * Opens {@link RadialMenuScreen} on a fresh press of the open-menu key,
 * populated with whatever abilities the player's species offers. Does
 * nothing if the player hasn't picked a species yet (shouldn't normally
 * happen, since that's forced on first join, but there's nothing sane to
 * show otherwise).
 */
public final class RadialMenuOpener {
	private RadialMenuOpener() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientPlayerEntity player = client.player;
			if (player != null && client.currentScreen == null
					&& RadialMenuKeyBinding.OPEN_RADIAL_MENU.isPressed()
					&& SpeciesAttachment.hasChosenSpecies(player)) {
				client.setScreen(new RadialMenuScreen(AbilityRegistry.abilitiesFor(SpeciesAttachment.getSpecies(player))));
			}
		});
	}
}
