package craftingismagic.cim.client.stamina;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment.StaminaColors;

/**
 * Draws the Stamina bar as a chain of discrete notches (see {@link StaminaNotchBarRenderer}) rather
 * than a smooth fill - stacked one row above {@code EarthPonyJumpBarHud}'s own bar so the two don't
 * overlap for an Earth Pony charging a jump while also spending Stamina. Always visible once the
 * player has chosen a species - unlike the jump bar (only shown while actively charging), Stamina is
 * a persistent resource worth seeing at a glance.
 */
public final class StaminaBarHud {
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	private static final int ROW_GAP = 2;

	private StaminaBarHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register(StaminaBarHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !SpeciesAttachment.hasChosenSpecies(client.player)) {
			return;
		}

		Stamina stamina = ClientStaminaState.get();
		StaminaColors colors = StaminaColorAttachment.getColors(client.player);
		int x = context.getScaledWindowWidth() / 2 - 91;
		int y = context.getScaledWindowHeight() - 32 + 3 - BAR_HEIGHT - ROW_GAP;

		StaminaNotchBarRenderer.draw(context, x, y, BAR_WIDTH, BAR_HEIGHT,
				ClientStaminaState.displayedCurrent(), stamina.max(), colors.fillColor(), colors.backgroundColor());
	}
}
