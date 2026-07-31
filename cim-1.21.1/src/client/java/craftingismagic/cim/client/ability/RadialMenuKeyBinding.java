package craftingismagic.cim.client.ability;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

/** Opens the radial ability menu while held. Default key: V. */
public final class RadialMenuKeyBinding {
	public static final KeyBinding OPEN_RADIAL_MENU = new KeyBinding(
			"key.cim.open_radial_menu", GLFW.GLFW_KEY_V, "key.categories.movement");

	private RadialMenuKeyBinding() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_RADIAL_MENU);
	}
}
