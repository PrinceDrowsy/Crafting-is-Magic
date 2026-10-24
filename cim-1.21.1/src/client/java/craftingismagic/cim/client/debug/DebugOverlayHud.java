package craftingismagic.cim.client.debug;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;

import craftingismagic.cim.client.stamina.ClientStaminaState;
import craftingismagic.cim.network.DebugOverlayToggleC2SPayload;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;

/**
 * Client side of the debug HUD overlay (docs/implementation-plan.md: Debug and testing tools) -
 * toggled with a keybind (default F7), like F3 but for the mod's own internals. Shows the server's
 * readouts (sent once a second while toggled on, see {@code DebugOverlay}) plus the client's own
 * view of the same systems, so the two can be compared directly - e.g. the Stamina the server holds
 * versus what this client was last sent and is displaying. The server re-checks permission, so a
 * player without it just sees the client-side lines.
 */
public final class DebugOverlayHud {
	public static final KeyBinding TOGGLE = new KeyBinding(
			"key.cim.toggle_debug_overlay", GLFW.GLFW_KEY_F7, "key.categories.misc");

	private static final int MARGIN = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int BACKGROUND_COLOR = 0x90000000;

	private static boolean visible;
	private static List<String> serverLines = List.of();

	private DebugOverlayHud() {
	}

	public static void register() {
		KeyBindingHelper.registerKeyBinding(TOGGLE);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE.wasPressed()) {
				visible = !visible;
				if (!visible) {
					serverLines = List.of();
				}
				if (client.getNetworkHandler() != null) {
					ClientPlayNetworking.send(new DebugOverlayToggleC2SPayload(visible));
				}
			}
		});
		HudRenderCallback.EVENT.register(DebugOverlayHud::render);
	}

	public static void setServerLines(List<String> lines) {
		serverLines = lines;
	}

	public static void reset() {
		visible = false;
		serverLines = List.of();
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!visible || client.options.hudHidden || client.player == null || client.getDebugHud().shouldShowDebugHud()) {
			return;
		}

		List<String> lines = new ArrayList<>();
		lines.add("Crafting is Magic - debug");
		Stamina stamina = ClientStaminaState.get();
		lines.add(String.format("Stamina (client): %.2f / %.1f%s", stamina.current(), stamina.max(),
				stamina.awaitingSleep() ? "  EXHAUSTED" : ""));
		lines.addAll(serverLines);

		int width = 0; // widest line, for the background box
		for (String line : lines) {
			width = Math.max(width, client.textRenderer.getWidth(line));
		}
		// Top-left: the top-right is where vanilla draws status-effect icons (Exhaustion among them),
		// and the overlay already steps aside whenever F3's own top-left text is showing.
		int x = MARGIN * 2;
		int y = MARGIN;
		context.fill(x - MARGIN, y - 2, x + width + MARGIN, y + lines.size() * LINE_HEIGHT, BACKGROUND_COLOR);
		for (String line : lines) {
			context.drawText(client.textRenderer, line, x, y, TEXT_COLOR, false);
			y += LINE_HEIGHT;
		}
	}
}
