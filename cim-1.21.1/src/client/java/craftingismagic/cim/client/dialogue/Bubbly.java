package craftingismagic.cim.client.dialogue;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The cute-and-bubbly look shared by the mod's storytelling screens (docs/implementation-plan.md:
 * Dialogue screen rework) - rounded panels with soft vertical gradients, pills, and twinkling
 * sparkles, all drawn in code so there are no textures to keep in sync. Colors come from the Mane Six
 * rainbow ({@link VoiceStyle}).
 */
public final class Bubbly {
	/** Dark, slightly plum ink for text on pastel. */
	public static final int INK = 0xFF3A2E4A;
	/** The log panel: deep plum, so white and pastel text stay easy to read. */
	public static final int PANEL_TOP = 0xE62E2342;
	public static final int PANEL_BOTTOM = 0xE61D1830;
	/** Twilight lavender, for borders and accents. */
	public static final int ACCENT = 0xFFCC8CF2;
	/** Ponies' name tags until coat colors reach the client: a soft peach. */
	public static final int PONY_TAG = 0xFFFFD9C2;
	/** Tone replies: almost-white with a lavender blush. */
	public static final int TONE_BUBBLE = 0xFFFBF3FF;

	private Bubbly() {
	}

	/**
	 * A rounded rectangle filled with a top-to-bottom gradient. Only the corner rows are drawn one at a
	 * time; everything between is a single gradient quad - drawing every row separately cost about a
	 * thousand draw calls a frame and lagged the whole machine.
	 */
	public static void panel(DrawContext context, int x, int y, int width, int height, int radius, int top, int bottom) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int r = Math.min(radius, Math.min(width, height) / 2);
		for (int row = 0; row < r; row++) {
			int inset = inset(row, height, r);
			context.fill(x + inset, y + row, x + width - inset, y + row + 1, colorAt(top, bottom, row, height));
			int bottomRow = height - 1 - row;
			context.fill(x + inset, y + bottomRow, x + width - inset, y + bottomRow + 1, colorAt(top, bottom, bottomRow, height));
		}
		if (height - 2 * r > 0) {
			context.fillGradient(x, y + r, x + width, y + height - r, colorAt(top, bottom, r, height), colorAt(top, bottom, height - 1 - r, height));
		}
	}

	private static int colorAt(int top, int bottom, int row, int height) {
		return lerp(top, bottom, height <= 1 ? 0f : row / (float) (height - 1));
	}

	/** A rounded panel with a one-pixel border. */
	public static void framed(DrawContext context, int x, int y, int width, int height, int radius, int border, int top, int bottom) {
		panel(context, x, y, width, height, radius, border, border);
		panel(context, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), top, bottom);
	}

	/** A pill of {@code color} sized to {@code text}, drawn at {@code scale}; returns its width. */
	public static int pill(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, float scale, int color, int textColor) {
		int textWidth = Math.round(textRenderer.getWidth(text) * scale);
		int height = Math.round(textRenderer.fontHeight * scale) + 3;
		int width = textWidth + 8;
		panel(context, x, y, width, height, height / 2, lighten(color, 0.15f), color);
		context.getMatrices().push();
		context.getMatrices().translate(x + 4, y + 2, 0);
		context.getMatrices().scale(scale, scale, 1f);
		context.drawText(textRenderer, text, 0, 0, textColor, false);
		context.getMatrices().pop();
		return width;
	}

	public static int pillHeight(TextRenderer textRenderer, float scale) {
		return Math.round(textRenderer.fontHeight * scale) + 3;
	}

	/** A small four-point sparkle that twinkles over time; {@code phase} keeps neighbors out of step. */
	public static void sparkle(DrawContext context, int x, int y, float time, float phase) {
		float twinkle = (MathHelper.sin(time * 0.12f + phase) + 1f) / 2f;
		int alpha = Math.round(80 + twinkle * 175);
		int color = (alpha << 24) | 0xFFFFFF;
		int arm = twinkle > 0.6f ? 2 : 1;
		context.fill(x - arm, y, x + arm + 1, y + 1, color);
		context.fill(x, y - arm, x + 1, y + arm + 1, color);
	}

	/** How far row {@code row} of a rounded shape is pulled in at the corners. */
	private static int inset(int row, int height, int radius) {
		int fromEdge = row < radius ? radius - row : row >= height - radius ? row - (height - radius - 1) : 0;
		if (fromEdge <= 0) {
			return 0;
		}
		double dy = fromEdge - 0.5;
		return (int) Math.round(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)));
	}

	public static int lerp(int from, int to, float t) {
		int a = Math.round(MathHelper.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
		int r = Math.round(MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(MathHelper.lerp(t, from & 0xFF, to & 0xFF));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Mixes {@code color} toward white by {@code amount}, keeping its alpha. */
	public static int lighten(int color, float amount) {
		return lerp(color, (color & 0xFF000000) | 0xFFFFFF, amount);
	}

	/** Mixes {@code color} toward black by {@code amount}, keeping its alpha. */
	public static int darken(int color, float amount) {
		return lerp(color, color & 0xFF000000, amount);
	}

	/** Opaque version of an RGB color. */
	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/** Dark ink on light colors, white on dark ones - whichever reads better. */
	public static int inkFor(int background) {
		int r = (background >> 16) & 0xFF;
		int g = (background >> 8) & 0xFF;
		int b = background & 0xFF;
		return (0.299 * r + 0.587 * g + 0.114 * b) > 150 ? INK : 0xFFFFFFFF;
	}
}
