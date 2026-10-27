package craftingismagic.cim.client.accessibility;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * A scrollable block of reading text that honors {@link AccessibilityConfig}'s reading settings (through
 * {@link CimText}) - a drop-in for vanilla's {@code ScrollableTextWidget}, which can't. Bigger or more
 * spaced-out text wraps into more lines and scrolls instead of overflowing. Screens re-create their
 * widgets on resize, so wrapping is computed once here.
 */
public class ScaledTextWidget extends ScrollableWidget {
	private final TextRenderer textRenderer;
	private final Text text;
	private final List<OrderedText> lines;

	public ScaledTextWidget(int x, int y, int width, int height, Text text, TextRenderer textRenderer) {
		super(x, y, width, height, text);
		this.textRenderer = textRenderer;
		this.text = text;
		this.lines = CimText.wrap(textRenderer, text, width - this.getPaddingDoubled());
	}

	@Override
	protected int getContentsHeight() {
		return lines.size() * CimText.lineHeight(textRenderer);
	}

	@Override
	protected double getDeltaYPerScroll() {
		return CimText.lineHeight(textRenderer);
	}

	/** Like vanilla's scrollable text, the frame only appears once there's something to scroll. */
	@Override
	protected void drawBox(DrawContext context) {
		if (this.overflows()) {
			super.drawBox(context);
		}
	}

	@Override
	protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
		CimText.drawLines(context, textRenderer, lines, this.getX() + this.getPadding(), this.getY() + this.getPadding(), 0xFFFFFFFF, false, true);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, text);
	}
}
