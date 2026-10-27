package craftingismagic.cim.client.accessibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.client.accessibility.AccessibilityConfig.ReadingFont;

/**
 * The reading options, offered the first time one of the mod's reading screens opens rather than left in a
 * settings menu to be found (README: Accessibility): font - each button in its own font - size, line and
 * letter spacing, and a backdrop, with a sample that changes as they do. The full set, visual comfort
 * included, stays in the mod's settings.
 */
public final class ReadingSetupScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW = 24;

	/** Offered at most once a session, even if the screen that asked is re-opened before it shows. */
	private static boolean offered;

	private record Label(int x, int y, Text text) {
	}

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();

	private ReadingSetupScreen(Screen parent) {
		super(Text.translatable("screen.cim.reading_setup.title"));
		this.parent = parent;
	}

	/** Called as a reading screen opens: the first time ever, the reading options come up over it. */
	public static void offer(Screen parent) {
		if (offered || AccessibilityConfig.get().readingSetupSeen) {
			return;
		}
		offered = true;
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.setScreen(new ReadingSetupScreen(parent)));
	}

	@Override
	protected void init() {
		AccessibilityConfig config = AccessibilityConfig.get();
		int left = (this.width - WIDTH) / 2;
		int y = 36;

		ReadingFont[] fonts = ReadingFont.values();
		int fontWidth = (WIDTH - (fonts.length - 1) * 4) / fonts.length;
		for (int i = 0; i < fonts.length; i++) {
			ReadingFont font = fonts[i];
			Text name = Text.translatable("screen.cim.reading_setup.font." + font.name().toLowerCase(Locale.ROOT));
			Text label = CimText.inFont(config.readingFont == font ? Text.literal("> ").append(name).append(" <") : name, font);
			this.addDrawableChild(ButtonWidget.builder(label, button -> {
				config.readingFont = font;
				this.clearAndInit();
			}).dimensions(left + i * (fontWidth + 4), y, fontWidth, BUTTON_HEIGHT).build());
		}
		y += ROW + 4;

		stepper(left, y, "screen.cim.reading_setup.size", () -> Math.round(config.clampedTextScale() * 100) + "%",
				step -> config.textScale = MathHelper.clamp(config.clampedTextScale() + step * 0.25f, AccessibilityConfig.MIN_TEXT_SCALE, AccessibilityConfig.MAX_TEXT_SCALE));
		y += ROW;
		stepper(left, y, "screen.cim.reading_setup.line_spacing", () -> String.format(Locale.ROOT, "%.1fx", config.lineSpacing),
				step -> config.lineSpacing = MathHelper.clamp(config.lineSpacing + step * 0.25f, 1f, AccessibilityConfig.MAX_LINE_SPACING));
		y += ROW;
		stepper(left, y, "screen.cim.reading_setup.letter_spacing", () -> String.format(Locale.ROOT, "+%.1f", config.letterSpacing),
				step -> config.letterSpacing = MathHelper.clamp(config.letterSpacing + step * 0.5f, 0f, AccessibilityConfig.MAX_LETTER_SPACING));
		y += ROW;
		stepper(left, y, "screen.cim.reading_setup.backdrop", () -> Math.round(config.textBackdrop * 100) + "%",
				step -> config.textBackdrop = MathHelper.clamp(config.textBackdrop + step * 0.25f, 0f, 1f));

		this.addDrawableChild(ButtonWidget.builder(CimText.style(Text.translatable("screen.cim.reading_setup.done")), button -> this.close())
				.dimensions((this.width - 100) / 2, this.height - BUTTON_HEIGHT - 12, 100, BUTTON_HEIGHT).build());
	}

	/** A label with its current value, and buttons to step it down and up. */
	private void stepper(int left, int y, String key, Supplier<String> value, Consumer<Integer> step) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> {
			step.accept(-1);
			this.clearAndInit();
		}).dimensions(left + WIDTH - 60, y, 28, BUTTON_HEIGHT).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
			step.accept(1);
			this.clearAndInit();
		}).dimensions(left + WIDTH - 28, y, 28, BUTTON_HEIGHT).build());
		this.labels.add(new Label(left, y + 6, CimText.style(Text.translatable(key, value.get()))));
	}

	@Override
	protected void clearAndInit() {
		labels.clear();
		super.clearAndInit();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		CimText.drawCentered(context, this.textRenderer, this.getTitle(), this.width / 2, 14, 0xFFFFFF);
		for (Label label : labels) {
			context.drawTextWithShadow(this.textRenderer, label.text(), label.x(), label.y(), 0xFFFFFF);
		}
		int left = (this.width - WIDTH) / 2;
		int sampleY = 36 + ROW + 4 + ROW * 4 + 8;
		List<OrderedText> sample = CimText.wrap(this.textRenderer, Text.translatable("screen.cim.reading_setup.sample"), WIDTH);
		CimText.drawLines(context, this.textRenderer, sample, left, sampleY, 0xFFFFFF, false, true);
		int noteY = this.height - BUTTON_HEIGHT - 16 - CimText.lineHeight(this.textRenderer) * 2;
		CimText.drawLines(context, this.textRenderer, CimText.wrap(this.textRenderer, Text.translatable("screen.cim.reading_setup.note"), WIDTH),
				left, noteY, 0xAAAAAA, false, false);
	}

	@Override
	public void close() {
		AccessibilityConfig config = AccessibilityConfig.get();
		config.readingSetupSeen = true;
		config.save();
		this.client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
