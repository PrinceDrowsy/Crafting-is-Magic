package craftingismagic.cim.client.changeling;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;

import craftingismagic.cim.network.ToggleVoiceMimicryConsentC2SPayload;

/** Flips whether a Changeling may ever capture/mimic the player's real voice - opt-out, defaults to
 * on (see {@code VoiceMimicryConsentAttachment}). Default key: comma+Shift isn't available as a
 * default binding, so this gets its own unbound-by-default key rather than fighting for a taken one. */
public final class VoiceMimicryConsentKeyHandler {
	public static final KeyBinding TOGGLE_VOICE_MIMICRY_CONSENT = new KeyBinding(
			"key.cim.toggle_voice_mimicry_consent", GLFW.GLFW_KEY_UNKNOWN, "key.categories.misc");

	private VoiceMimicryConsentKeyHandler() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(TOGGLE_VOICE_MIMICRY_CONSENT);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_VOICE_MIMICRY_CONSENT.wasPressed()) {
				ClientPlayNetworking.send(new ToggleVoiceMimicryConsentC2SPayload());
			}
		});
	}
}
