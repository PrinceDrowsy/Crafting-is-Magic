package craftingismagic.cim.client.species;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Draws a jump-charge bar in the same spot vanilla draws the mount jump
 * bar (and XP bar), reusing the vanilla sprites, while an Earth Pony is
 * charging a jump. Only shown once the charge has crossed the hold
 * buffer, so a quick tap never flashes it.
 */
public final class EarthPonyJumpBarHud {
	private static final Identifier BACKGROUND_SPRITE = Identifier.ofVanilla("hud/jump_bar_background");
	private static final Identifier PROGRESS_SPRITE = Identifier.ofVanilla("hud/jump_bar_progress");
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;

	private EarthPonyJumpBarHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register(EarthPonyJumpBarHud::render);
	}

	private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
		if (!EarthPonyJumpHandler.isCharging()) {
			return;
		}

		int x = context.getScaledWindowWidth() / 2 - 91;
		int y = context.getScaledWindowHeight() - 32 + 3;

		context.drawGuiTexture(BACKGROUND_SPRITE, x, y, BAR_WIDTH, BAR_HEIGHT);

		int filled = (int) (EarthPonyJumpHandler.getChargeFraction() * (BAR_WIDTH + 1));
		if (filled > 0) {
			context.drawGuiTexture(PROGRESS_SPRITE, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
		}
	}
}
