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
 * A scrollable block of reading text that honors {@link AccessibilityConfig}'s font and text size -
 * a drop-in for vanilla's {@code ScrollableTextWidget}, which can't do either. Text is wrapped to the
 * widget's width divided by the scale, then drawn scaled up, so bigger text wraps into more lines
 * and scrolls instead of overflowing. Screens re-create their widgets on resize, so wrapping is
 * computed once here.
 */
public class ScaledTextWidget extends ScrollableWidget {
	private final TextRenderer textRenderer;
	private final Text text;
	private final float scale;
	private final List<OrderedText> lines;

	public ScaledTextWidget(int x, int y, int width, int height, Text text, TextRenderer textRenderer) {
		super(x, y, width, height, text);
		this.textRenderer = textRenderer;
		this.text = text;
		this.scale = CimText.scale();
		int wrapWidth = Math.max(1, Math.round((width - this.getPaddingDoubled()) / scale));
		this.lines = textRenderer.wrapLines(CimText.style(text), wrapWidth);
	}

	@Override
	protected int getContentsHeight() {
		return Math.round(lines.size() * textRenderer.fontHeight * scale);
	}

	@Override
	protected double getDeltaYPerScroll() {
		return textRenderer.fontHeight * scale;
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
		context.getMatrices().push();
		context.getMatrices().translate(this.getX() + this.getPadding(), this.getY() + this.getPadding(), 0);
		context.getMatrices().scale(scale, scale, 1f);
		int lineY = 0;
		for (OrderedText line : lines) {
			context.drawText(textRenderer, line, 0, lineY, 0xFFFFFFFF, false);
			lineY += textRenderer.fontHeight;
		}
		context.getMatrices().pop();
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, text);
	}
}
