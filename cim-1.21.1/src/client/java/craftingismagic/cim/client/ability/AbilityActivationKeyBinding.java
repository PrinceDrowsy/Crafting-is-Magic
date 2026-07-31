package craftingismagic.cim.client.ability;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

/**
 * Activates whichever ability is currently selected (see
 * {@link SelectedAbility}) - shared by every ability rather than each
 * getting its own dedicated key. Default key: R.
 */
public final class AbilityActivationKeyBinding {
	public static final KeyBinding ACTIVATE_ABILITY = new KeyBinding(
			"key.cim.activate_ability", GLFW.GLFW_KEY_R, "key.categories.movement");

	private AbilityActivationKeyBinding() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(ACTIVATE_ABILITY);
	}
}
