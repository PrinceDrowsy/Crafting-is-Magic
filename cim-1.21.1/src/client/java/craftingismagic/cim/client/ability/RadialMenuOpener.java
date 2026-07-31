package craftingismagic.cim.client.ability;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Opens {@link RadialMenuScreen} on a fresh press of the open-menu key. */
public final class RadialMenuOpener {
	private RadialMenuOpener() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && client.currentScreen == null
					&& RadialMenuKeyBinding.OPEN_RADIAL_MENU.isPressed()) {
				client.setScreen(new RadialMenuScreen());
			}
		});
	}
}
