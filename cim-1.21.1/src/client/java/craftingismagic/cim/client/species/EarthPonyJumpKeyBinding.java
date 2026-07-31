package craftingismagic.cim.client.species;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

/**
 * A dedicated key for the Earth Pony charge jump, kept entirely separate
 * from vanilla's own jump key. Vanilla space-bar jumping is left
 * completely untouched by this mod as a result — no mixin, no
 * suppression, no tap-vs-hold ambiguity to resolve.
 */
public final class EarthPonyJumpKeyBinding {
	public static final KeyBinding CHARGE_JUMP = new KeyBinding(
			"key.cim.charge_jump", GLFW.GLFW_KEY_R, "key.categories.movement");

	private EarthPonyJumpKeyBinding() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(CHARGE_JUMP);
	}
}
