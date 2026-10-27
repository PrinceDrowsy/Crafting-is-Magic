package craftingismagic.cim.client.accessibility;

import java.util.List;

import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import craftingismagic.cim.Cim;

/**
 * The one place the mod's screens go through to show text, so the reading settings in
 * {@link AccessibilityConfig} apply everywhere at once: {@link #style} sets the chosen font on any text
 * (buttons and labels included), and reading text - titles, dialogue, lore - is wrapped, measured and
 * drawn here at the chosen size, line spacing and letter spacing, over a backdrop if one is wanted.
 * <p>
 * The Villager Debug Editor and the debug overlay are developer tools and deliberately stay as they are.
 */
public final class CimText {
	/** Equestria Reader (a TrueType conversion of OpenDyslexic, renamed as its license requires) and Atkinson
	 * Hyperlegible - see tools/fonts/README.md. Each falls back to Minecraft's font for any glyph it lacks. */
	private static final Identifier EQUESTRIA_READER = Identifier.of(Cim.MOD_ID, "equestria_reader");
	private static final Identifier HYPERLEGIBLE = Identifier.of(Cim.MOD_ID, "hyperlegible");

	private CimText() {
	}

	public static MutableText style(Text text) {
		return inFont(text, AccessibilityConfig.get().readingFont);
	}

	/** Text in a given reading font, whatever's chosen - for showing each font as itself. */
	public static MutableText inFont(Text text, AccessibilityConfig.ReadingFont readingFont) {
		MutableText copy = text.copy();
		Identifier font = switch (readingFont) {
			case EQUESTRIA_READER -> EQUESTRIA_READER;
			case ATKINSON_HYPERLEGIBLE -> HYPERLEGIBLE;
			case MINECRAFT -> null;
		};
		if (font != null) {
			copy.styled(style -> style.withFont(font));
		}
		return copy;
	}

	public static float scale() {
		return AccessibilityConfig.get().clampedTextScale();
	}

	private static float letterSpacing() {
		return Math.max(0f, Math.min(AccessibilityConfig.MAX_LETTER_SPACING, AccessibilityConfig.get().letterSpacing));
	}

	private static float lineSpacing() {
		return Math.max(1f, Math.min(AccessibilityConfig.MAX_LINE_SPACING, AccessibilityConfig.get().lineSpacing));
	}

	/** Height of one line of reading text, spacing included - for laying out what comes below it. */
	public static int lineHeight(TextRenderer textRenderer) {
		return Math.round(textRenderer.fontHeight * lineSpacing() * scale());
	}

	/** Reading text wrapped to {@code width} screen pixels at the current size and letter spacing. */
	public static List<OrderedText> wrap(TextRenderer textRenderer, Text text, int width) {
		int unscaled = Math.max(1, Math.round(width / scale()));
		float spacing = letterSpacing();
		if (spacing <= 0f) {
			return textRenderer.wrapLines(style(text), unscaled);
		}
		TextHandler spaced = new TextHandler((codePoint, style) -> textRenderer.getWidth(OrderedText.styled(codePoint, style)) + spacing);
		return Language.getInstance().reorder(spaced.wrapLines(style(text), unscaled, Style.EMPTY));
	}

	/** How wide a line of reading text is on screen, at the current size and letter spacing. */
	public static int width(TextRenderer textRenderer, OrderedText line) {
		return Math.round(unscaledWidth(textRenderer, line) * scale());
	}

	private static float unscaledWidth(TextRenderer textRenderer, OrderedText line) {
		float spacing = letterSpacing();
		if (spacing <= 0f) {
			return textRenderer.getWidth(line);
		}
		float[] width = { 0f };
		line.accept((index, style, codePoint) -> {
			width[0] += textRenderer.getWidth(OrderedText.styled(codePoint, style)) + spacing;
			return true;
		});
		return width[0];
	}

	/**
	 * Draws wrapped reading text with its top left at (x, y). {@code backdrop}: whether it sits over
	 * whatever's behind the screen (and so gets the chosen backdrop) rather than a panel of its own.
	 */
	public static void drawLines(DrawContext context, TextRenderer textRenderer, List<OrderedText> lines, int x, int y, int color, boolean shadow,
			boolean backdrop) {
		float scale = scale();
		float step = textRenderer.fontHeight * lineSpacing();
		float backdropAlpha = AccessibilityConfig.get().textBackdrop;
		if (backdrop && backdropAlpha > 0f && !lines.isEmpty()) {
			float widest = 0f;
			for (OrderedText line : lines) {
				widest = Math.max(widest, unscaledWidth(textRenderer, line));
			}
			int alpha = Math.round(Math.min(backdropAlpha, 1f) * 255f);
			context.fill(x - 3, y - 2, x + Math.round(widest * scale) + 3, y + Math.round((lines.size() - 1) * step * scale + textRenderer.fontHeight * scale) + 2,
					alpha << 24);
		}
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0);
		context.getMatrices().scale(scale, scale, 1f);
		for (int i = 0; i < lines.size(); i++) {
			drawLine(context, textRenderer, lines.get(i), Math.round(i * step), color, shadow);
		}
		context.getMatrices().pop();
	}

	private static void drawLine(DrawContext context, TextRenderer textRenderer, OrderedText line, int y, int color, boolean shadow) {
		float spacing = letterSpacing();
		if (spacing <= 0f) {
			context.drawText(textRenderer, line, 0, y, color, shadow);
			return;
		}
		// Letter by letter, each nudged on by the extra spacing - Minecraft's fonts have no spacing of their own.
		float[] x = { 0f };
		line.accept((index, style, codePoint) -> {
			OrderedText letter = OrderedText.styled(codePoint, style);
			context.drawText(textRenderer, letter, Math.round(x[0]), y, color, shadow);
			x[0] += textRenderer.getWidth(letter) + spacing;
			return true;
		});
	}

	/** A single line of reading text (a title), centered on {@code centerX}, over the chosen backdrop. */
	public static void drawCentered(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
		OrderedText line = style(text).asOrderedText();
		drawLines(context, textRenderer, List.of(line), centerX - width(textRenderer, line) / 2, y, color, true, true);
	}
}
