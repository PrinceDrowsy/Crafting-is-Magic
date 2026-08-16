package craftingismagic.cim.client.stamina;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.stamina.StaminaManager;

/**
 * Draws a fixed-width chain of Stamina notches - shared by {@link StaminaBarHud} (the real HUD bar)
 * and {@link StaminaColorScreen} (its live color preview), so the tiling math only lives in one
 * place. Notch boundaries use integer division ({@code (i * width) / totalNotches}) rather than an
 * accumulated per-notch float width - notch {@code i}'s end and notch {@code i + 1}'s start come from
 * the identical formula, so they can never leave a gap or overlap no matter how {@code totalNotches}
 * changes as max Stamina grows, guaranteeing the chain always spans exactly {@code width} pixels.
 */
public final class StaminaNotchBarRenderer {
	/** How much darker the dividers/border are than the background - just enough for the notch
	 * boundaries to stay visible against the player's own chosen background color, not a fixed
	 * black that could clash with a dark background choice. */
	private static final float DARKEN_FACTOR = 0.7f;

	private StaminaNotchBarRenderer() {
	}

	public static void draw(DrawContext context, int x, int y, int width, int height,
			float current, float max, int fillColor, int backgroundColor) {
		int totalNotches = Math.max(1, Math.round(max / StaminaManager.NOTCH_VALUE));
		float filledNotches = current / StaminaManager.NOTCH_VALUE;
		int lineColor = darken(backgroundColor, DARKEN_FACTOR);

		for (int i = 0; i < totalNotches; i++) {
			int notchStartX = x + (i * width) / totalNotches;
			int notchEndX = x + ((i + 1) * width) / totalNotches;
			float notchFill = MathHelper.clamp(filledNotches - i, 0f, 1f);

			context.fill(notchStartX, y, notchEndX, y + height, backgroundColor);
			if (notchFill > 0f) {
				int filledEndX = notchStartX + Math.round((notchEndX - notchStartX) * notchFill);
				context.fill(notchStartX, y, filledEndX, y + height, fillColor);
			}
			if (i > 0) {
				context.fill(notchStartX, y, notchStartX + 1, y + height, lineColor);
			}
		}

		context.fill(x, y, x + width, y + 1, lineColor);
		context.fill(x, y + height - 1, x + width, y + height, lineColor);
		context.fill(x, y, x + 1, y + height, lineColor);
		context.fill(x + width - 1, y, x + width, y + height, lineColor);
	}

	private static int darken(int color, float factor) {
		int alpha = (color >> 24) & 0xFF;
		int red = Math.round(((color >> 16) & 0xFF) * factor);
		int green = Math.round(((color >> 8) & 0xFF) * factor);
		int blue = Math.round((color & 0xFF) * factor);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}
}
