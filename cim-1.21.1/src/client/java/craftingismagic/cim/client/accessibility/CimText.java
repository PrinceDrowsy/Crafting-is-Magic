package craftingismagic.cim.client.accessibility;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * The one place the mod's screens go through to show text, so the reading settings in
 * {@link AccessibilityConfig} apply everywhere at once:
 * <ul>
 * <li>{@link #style} applies the chosen font to any text - every mod screen's titles, labels, and
 * bodies pass through it. The font is set on the root style, which children inherit unless they set
 * their own.</li>
 * <li>{@link #drawCentered} and {@link #drawWrapped} draw at the chosen text size. Buttons keep their
 * fixed size (only their font changes); reading text - titles, dialogue, lore - scales.</li>
 * </ul>
 * The Villager Debug Editor and the debug overlay are developer tools and deliberately stay as they
 * are.
 */
public final class CimText {
	/** assets/cim/font/dyslexic.json - Equestria Reader (a TrueType conversion of OpenDyslexic, renamed
	 * as its license requires; see tools/fonts/README.md), falling back to Minecraft's font for any
	 * glyph it lacks. */
	public static final Identifier DYSLEXIC_FONT = Identifier.of(Cim.MOD_ID, "dyslexic");

	private CimText() {
	}

	public static MutableText style(Text text) {
		MutableText copy = text.copy();
		if (AccessibilityConfig.get().dyslexicFont) {
			copy.styled(style -> style.withFont(DYSLEXIC_FONT));
		}
		return copy;
	}

	public static float scale() {
		return AccessibilityConfig.get().clampedTextScale();
	}

	/** Height of one line of text at the current size - for laying out what comes below it. */
	public static int lineHeight(TextRenderer textRenderer) {
		return Math.round(textRenderer.fontHeight * scale());
	}

	public static void drawCentered(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
		float scale = scale();
		Text styled = style(text);
		context.getMatrices().push();
		context.getMatrices().translate(centerX, y, 0);
		context.getMatrices().scale(scale, scale, 1f);
		context.drawTextWithShadow(textRenderer, styled, -textRenderer.getWidth(styled) / 2, 0, color);
		context.getMatrices().pop();
	}

	/** Draws wrapped text at the current size within {@code width} screen pixels, returning the height
	 * it took up. */
	public static int drawWrapped(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int width, int color) {
		float scale = scale();
		List<OrderedText> lines = textRenderer.wrapLines(style(text), Math.max(1, Math.round(width / scale)));
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0);
		context.getMatrices().scale(scale, scale, 1f);
		int lineY = 0;
		for (OrderedText line : lines) {
			context.drawText(textRenderer, line, 0, lineY, color, false);
			lineY += textRenderer.fontHeight;
		}
		context.getMatrices().pop();
		return Math.round(lineY * scale);
	}
}
