package craftingismagic.cim.client.journal;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

/** Opens the Journal menu. Default key: J. */
public final class JournalKeyBinding {
	public static final KeyBinding OPEN_JOURNAL = new KeyBinding(
			"key.cim.open_journal", GLFW.GLFW_KEY_J, "key.categories.movement");

	private JournalKeyBinding() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(OPEN_JOURNAL);
	}
}
