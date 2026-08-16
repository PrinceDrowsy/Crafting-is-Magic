package craftingismagic.cim.client.stamina;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;

import craftingismagic.cim.network.DebugDrainStaminaC2SPayload;

/** Creative-only test lever for the Stamina system (see {@link DebugDrainStaminaC2SPayload}'s own
 * doc comment) - default key: N. Temporary/test-only, not a real feature. */
public final class DebugStaminaDrainKeyHandler {
	public static final KeyBinding DEBUG_DRAIN_STAMINA = new KeyBinding(
			"key.cim.debug_drain_stamina", GLFW.GLFW_KEY_N, "key.categories.misc");

	private DebugStaminaDrainKeyHandler() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(DEBUG_DRAIN_STAMINA);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (DEBUG_DRAIN_STAMINA.wasPressed()) {
				ClientPlayNetworking.send(new DebugDrainStaminaC2SPayload());
			}
		});
	}
}
