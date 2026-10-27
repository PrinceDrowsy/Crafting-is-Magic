package craftingismagic.cim.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.Cim;
import craftingismagic.cim.client.accessibility.CimText;
import craftingismagic.cim.client.stamina.StaminaNotchBarRenderer;
import craftingismagic.cim.network.SetStaminaColorC2SPayload;
import craftingismagic.cim.stamina.StaminaColorAttachment.StaminaColors;

/**
 * Color picker for the Stamina bar's fill and background colors - the fill being the player's magic
 * color too (docs/magic-aura.md: one color per caster) - opened via a dedicated key binding (not
 * server-triggered, unlike most other screens in this mod - a player's own color preference needs no
 * server-side state to open).
 *
 * <p>A color wheel (hue round it, saturation out from the middle) and a brightness slider beside it
 * edit whichever color's swatch is picked; each color's hex field stays for typing an exact value, and
 * the two keep each other in step. Every change applies live and updates the preview bar below,
 * matching {@code VillagerDebugEditorScreen}'s own no-Save-button pattern - reuses {@link
 * StaminaNotchBarRenderer} so the preview is pixel-identical to the real HUD bar. Changes reach the
 * server at most once a tick, however fast the wheel is dragged.
 *
 * <p>The wheel is one texture made once and drawn in one call (darkened for the brightness), and the
 * slider one gradient - never drawn row by row (per-row fills lagged the whole PC before).
 */
public class StaminaColorScreen extends Screen {
	private static final int FIELD_WIDTH = 70;
	private static final int FIELD_HEIGHT = 16;
	private static final int LABEL_WIDTH = 90;
	private static final int SWATCH_SIZE = 16;
	private static final int ROW_HEIGHT = 24;
	private static final int PREVIEW_WIDTH = 182;
	private static final int PREVIEW_HEIGHT = 5;
	private static final int OPAQUE_ALPHA = 0xFF000000;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;

	private static final int WHEEL_SIZE = 100;
	private static final int SLIDER_WIDTH = 10;
	private static final int GAP = 8;
	/** The wheel's texture is made at this size and drawn smoothed down to {@link #WHEEL_SIZE}. */
	private static final int WHEEL_TEXTURE_SIZE = 256;
	private static final Identifier WHEEL_TEXTURE = Identifier.of(Cim.MOD_ID, "dynamic/color_wheel");

	private enum Target {
		FILL, BACKGROUND
	}

	private enum Dragging {
		NONE, WHEEL, SLIDER
	}

	private int fillColor;
	private int backgroundColor;
	private Target target = Target.FILL;
	/** The edited color as hue, saturation and brightness (0-1) - kept, so hue survives going grey or black. */
	private float hue;
	private float saturation;
	private float brightness;
	private Dragging dragging = Dragging.NONE;
	private boolean unsent;
	/** True while the wheel writes into a hex field, so the field doesn't write back into the wheel. */
	private boolean syncing;

	private TextFieldWidget fillField;
	private TextFieldWidget backgroundField;
	private int wheelX;
	private int wheelY;
	private int sliderX;
	private int rowsX;
	private int fillRowY;
	private int backgroundRowY;
	private int previewY;

	public StaminaColorScreen(StaminaColors initialColors) {
		super(Text.translatable("screen.cim.stamina_color.title"));
		this.fillColor = initialColors.fillColor();
		this.backgroundColor = initialColors.backgroundColor();
		pickUp(fillColor);
	}

	@Override
	protected void init() {
		int rowsWidth = LABEL_WIDTH + SWATCH_SIZE + 4 + FIELD_WIDTH;
		int totalWidth = WHEEL_SIZE + GAP + SLIDER_WIDTH + GAP * 2 + rowsWidth;
		int top = this.height / 2 - 60;
		wheelX = this.width / 2 - totalWidth / 2;
		wheelY = top;
		sliderX = wheelX + WHEEL_SIZE + GAP;
		rowsX = sliderX + SLIDER_WIDTH + GAP * 2;

		fillRowY = top + WHEEL_SIZE / 2 - ROW_HEIGHT;
		fillField = hexField(fillRowY, fillColor, Target.FILL);
		backgroundRowY = fillRowY + ROW_HEIGHT;
		backgroundField = hexField(backgroundRowY, backgroundColor, Target.BACKGROUND);

		previewY = top + WHEEL_SIZE + 14;

		this.addDrawableChild(ButtonWidget.builder(CimText.style(Text.translatable("screen.cim.stamina_color.close")), b -> this.close())
				.dimensions(this.width / 2 - BUTTON_WIDTH / 2, previewY + PREVIEW_HEIGHT + 14, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	private TextFieldWidget hexField(int y, int color, Target of) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, rowsX + LABEL_WIDTH + SWATCH_SIZE + 4, y, FIELD_WIDTH, FIELD_HEIGHT, Text.empty());
		field.setMaxLength(6);
		field.setTextPredicate(value -> value.matches("[0-9a-fA-F]*"));
		field.setText(toHex(color));
		field.setChangedListener(value -> {
			if (value.length() != 6) {
				return;
			}
			int typed = OPAQUE_ALPHA | Integer.parseInt(value, 16);
			if (of == Target.FILL) {
				fillColor = typed;
			} else {
				backgroundColor = typed;
			}
			unsent = true;
			if (!syncing && of == target) {
				pickUp(typed);
			}
		});
		this.addDrawableChild(field);
		return field;
	}

	private static String toHex(int color) {
		return String.format("%06X", color & 0xFFFFFF);
	}

	/** The wheel and slider take up a color (on picking its swatch, or typing it). */
	private void pickUp(int color) {
		float[] hsb = java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
		// Grey has no hue and black no saturation either: keep what the wheel had.
		if (hsb[1] > 0f && hsb[2] > 0f) {
			hue = hsb[0];
		}
		if (hsb[2] > 0f) {
			saturation = hsb[1];
		}
		brightness = hsb[2];
	}

	/** The wheel or slider moved: the picked color, and its hex field, follow. */
	private void apply() {
		int color = OPAQUE_ALPHA | MathHelper.hsvToRgb(hue, saturation, brightness);
		syncing = true;
		if (target == Target.FILL) {
			fillColor = color;
			fillField.setText(toHex(color));
		} else {
			backgroundColor = color;
			backgroundField.setText(toHex(color));
		}
		syncing = false;
		unsent = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (unsent) {
			unsent = false;
			ClientPlayNetworking.send(new SetStaminaColorC2SPayload(fillColor, backgroundColor));
		}
	}

	@Override
	public void removed() {
		tick();
		super.removed();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (overSwatch(mouseX, mouseY, fillRowY)) {
				target = Target.FILL;
				pickUp(fillColor);
				return true;
			}
			if (overSwatch(mouseX, mouseY, backgroundRowY)) {
				target = Target.BACKGROUND;
				pickUp(backgroundColor);
				return true;
			}
			double dx = mouseX - (wheelX + WHEEL_SIZE / 2.0);
			double dy = mouseY - (wheelY + WHEEL_SIZE / 2.0);
			if (dx * dx + dy * dy <= (WHEEL_SIZE / 2.0) * (WHEEL_SIZE / 2.0)) {
				dragging = Dragging.WHEEL;
				drag(mouseX, mouseY);
				return true;
			}
			if (mouseX >= sliderX && mouseX < sliderX + SLIDER_WIDTH && mouseY >= wheelY && mouseY < wheelY + WHEEL_SIZE) {
				dragging = Dragging.SLIDER;
				drag(mouseX, mouseY);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != Dragging.NONE) {
			drag(mouseX, mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging != Dragging.NONE) {
			dragging = Dragging.NONE;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void drag(double mouseX, double mouseY) {
		if (dragging == Dragging.WHEEL) {
			double dx = mouseX - (wheelX + WHEEL_SIZE / 2.0);
			double dy = mouseY - (wheelY + WHEEL_SIZE / 2.0);
			hue = wheelHue(dx, dy);
			saturation = (float) Math.min(Math.sqrt(dx * dx + dy * dy) / (WHEEL_SIZE / 2.0), 1.0);
			// Picking a hue off a black color would show nothing: bring the light up.
			if (brightness < 0.05f) {
				brightness = 1f;
			}
		} else {
			brightness = (float) MathHelper.clamp(1.0 - (mouseY - wheelY) / WHEEL_SIZE, 0.0, 1.0);
		}
		apply();
	}

	/** Hue round the wheel: red at the top, going clockwise. */
	private static float wheelHue(double dx, double dy) {
		double angle = Math.atan2(dx, -dy) / (Math.PI * 2);
		return (float) (angle < 0 ? angle + 1 : angle);
	}

	private boolean overSwatch(double mouseX, double mouseY, int rowY) {
		int x = rowsX + LABEL_WIDTH;
		return mouseX >= x && mouseX < x + SWATCH_SIZE && mouseY >= rowY && mouseY < rowY + SWATCH_SIZE;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, CimText.style(this.getTitle()), this.width / 2, wheelY - 20, 0xFFFFFF);

		drawWheel(context);
		drawSlider(context);

		context.drawTextWithShadow(this.textRenderer, CimText.style(Text.translatable("screen.cim.stamina_color.fill")), rowsX, fillRowY + 4, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, CimText.style(Text.translatable("screen.cim.stamina_color.background")), rowsX, backgroundRowY + 4, 0xAAAAAA);
		drawSwatch(context, fillRowY, fillColor, target == Target.FILL);
		drawSwatch(context, backgroundRowY, backgroundColor, target == Target.BACKGROUND);

		StaminaNotchBarRenderer.draw(context, this.width / 2 - PREVIEW_WIDTH / 2, previewY, PREVIEW_WIDTH, PREVIEW_HEIGHT,
				150f, 200f, fillColor, backgroundColor);
	}

	private void drawWheel(DrawContext context) {
		makeWheel();
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(brightness, brightness, brightness, 1f);
		context.drawTexture(WHEEL_TEXTURE, wheelX, wheelY, WHEEL_SIZE, WHEEL_SIZE, 0f, 0f, WHEEL_TEXTURE_SIZE, WHEEL_TEXTURE_SIZE,
				WHEEL_TEXTURE_SIZE, WHEEL_TEXTURE_SIZE);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.disableBlend();

		// The marker: where the color sits on the wheel - a small ring, dark on light colors and light on dark.
		double angle = hue * Math.PI * 2;
		int x = wheelX + WHEEL_SIZE / 2 + (int) Math.round(Math.sin(angle) * saturation * WHEEL_SIZE / 2.0);
		int y = wheelY + WHEEL_SIZE / 2 - (int) Math.round(Math.cos(angle) * saturation * WHEEL_SIZE / 2.0);
		int ring = brightness > 0.6f ? 0xFF000000 : 0xFFFFFFFF;
		outline(context, x - 3, y - 3, 7, 7, ring);
	}

	private void drawSlider(DrawContext context) {
		int bright = OPAQUE_ALPHA | MathHelper.hsvToRgb(hue, saturation, 1f);
		context.fillGradient(sliderX, wheelY, sliderX + SLIDER_WIDTH, wheelY + WHEEL_SIZE, bright, OPAQUE_ALPHA);
		outline(context, sliderX - 1, wheelY - 1, SLIDER_WIDTH + 2, WHEEL_SIZE + 2, 0xFF555555);
		int y = wheelY + Math.round((1f - brightness) * WHEEL_SIZE);
		outline(context, sliderX - 2, y - 2, SLIDER_WIDTH + 4, 5, 0xFFFFFFFF);
	}

	private void drawSwatch(DrawContext context, int rowY, int color, boolean picked) {
		int x = rowsX + LABEL_WIDTH;
		outline(context, x - 1, rowY - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, picked ? 0xFFFFFFFF : 0xFF555555);
		context.fill(x, rowY, x + SWATCH_SIZE, rowY + SWATCH_SIZE, OPAQUE_ALPHA | color);
	}

	private static void outline(DrawContext context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y + 1, x + 1, y + height - 1, color);
		context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	/** Makes the wheel's texture once: every hue round it at full brightness, white in the middle, its edge smoothed. */
	private static void makeWheel() {
		// Made again if a resource reload dropped it.
		if (MinecraftClient.getInstance().getTextureManager().getOrDefault(WHEEL_TEXTURE, null) != null) {
			return;
		}
		NativeImage image = new NativeImage(WHEEL_TEXTURE_SIZE, WHEEL_TEXTURE_SIZE, false);
		double radius = WHEEL_TEXTURE_SIZE / 2.0;
		for (int py = 0; py < WHEEL_TEXTURE_SIZE; py++) {
			for (int px = 0; px < WHEEL_TEXTURE_SIZE; px++) {
				double dx = px + 0.5 - radius;
				double dy = py + 0.5 - radius;
				double distance = Math.sqrt(dx * dx + dy * dy);
				int alpha = (int) Math.round(MathHelper.clamp(radius - distance, 0.0, 1.0) * 255);
				int rgb = MathHelper.hsvToRgb(wheelHue(dx, dy), (float) Math.min(distance / radius, 1.0), 1f);
				// NativeImage wants ABGR.
				image.setColor(px, py, alpha << 24 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF);
			}
		}
		NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
		texture.setFilter(true, false);
		MinecraftClient.getInstance().getTextureManager().registerTexture(WHEEL_TEXTURE, texture);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
