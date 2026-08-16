package craftingismagic.cim.client.stamina;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

import craftingismagic.cim.client.gui.StaminaColorScreen;
import craftingismagic.cim.stamina.StaminaColorAttachment;

/** Opens {@link StaminaColorScreen} - purely client-side, no server round trip needed to open since
 * the current colors are already synced locally. Default key: comma. */
public final class StaminaColorKeyHandler {
	public static final KeyBinding OPEN_STAMINA_COLOR_SCREEN = new KeyBinding(
			"key.cim.open_stamina_color_screen", GLFW.GLFW_KEY_COMMA, "key.categories.misc");

	private StaminaColorKeyHandler() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_STAMINA_COLOR_SCREEN);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_STAMINA_COLOR_SCREEN.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					client.setScreen(new StaminaColorScreen(StaminaColorAttachment.getColors(client.player)));
				}
			}
		});
	}
}
