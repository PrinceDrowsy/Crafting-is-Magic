package craftingismagic.cim.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import craftingismagic.cim.client.stamina.StaminaNotchBarRenderer;
import craftingismagic.cim.network.SetStaminaColorC2SPayload;
import craftingismagic.cim.stamina.StaminaColorAttachment.StaminaColors;

/**
 * Free-form hex color picker for the Stamina bar's fill and background colors, opened via a
 * dedicated key binding (not server-triggered, unlike most other screens in this mod - a player's
 * own color preference needs no server-side state to open). Every valid edit applies live (a
 * {@link SetStaminaColorC2SPayload} per change) and updates the preview bar below, matching {@code
 * VillagerDebugEditorScreen}'s own no-Save-button pattern - reuses {@link StaminaNotchBarRenderer}
 * so the preview is pixel-identical to the real HUD bar.
 */
public class StaminaColorScreen extends Screen {
	private static final int FIELD_WIDTH = 70;
	private static final int FIELD_HEIGHT = 16;
	private static final int LABEL_WIDTH = 90;
	private static final int ROW_HEIGHT = 24;
	private static final int PREVIEW_WIDTH = 182;
	private static final int PREVIEW_HEIGHT = 5;
	private static final int OPAQUE_ALPHA = 0xFF000000;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;

	private int fillColor;
	private int backgroundColor;
	private int fillRowY;
	private int backgroundRowY;
	private int previewY;

	public StaminaColorScreen(StaminaColors initialColors) {
		super(Text.translatable("screen.cim.stamina_color.title"));
		this.fillColor = initialColors.fillColor();
		this.backgroundColor = initialColors.backgroundColor();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int x = centerX - (LABEL_WIDTH + FIELD_WIDTH) / 2;
		int y = this.height / 2 - 40;

		fillRowY = y;
		TextFieldWidget fillField = new TextFieldWidget(this.textRenderer, x + LABEL_WIDTH, y, FIELD_WIDTH, FIELD_HEIGHT, Text.empty());
		fillField.setMaxLength(6);
		fillField.setTextPredicate(value -> value.matches("[0-9a-fA-F]*"));
		fillField.setText(toHex(fillColor));
		fillField.setChangedListener(value -> {
			if (value.length() == 6) {
				fillColor = OPAQUE_ALPHA | Integer.parseInt(value, 16);
				ClientPlayNetworking.send(new SetStaminaColorC2SPayload(fillColor, backgroundColor));
			}
		});
		this.addDrawableChild(fillField);

		y += ROW_HEIGHT;
		backgroundRowY = y;
		TextFieldWidget backgroundField = new TextFieldWidget(this.textRenderer, x + LABEL_WIDTH, y, FIELD_WIDTH, FIELD_HEIGHT, Text.empty());
		backgroundField.setMaxLength(6);
		backgroundField.setTextPredicate(value -> value.matches("[0-9a-fA-F]*"));
		backgroundField.setText(toHex(backgroundColor));
		backgroundField.setChangedListener(value -> {
			if (value.length() == 6) {
				backgroundColor = OPAQUE_ALPHA | Integer.parseInt(value, 16);
				ClientPlayNetworking.send(new SetStaminaColorC2SPayload(fillColor, backgroundColor));
			}
		});
		this.addDrawableChild(backgroundField);

		previewY = y + ROW_HEIGHT + 10;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.cim.stamina_color.close"), b -> this.close())
				.dimensions(centerX - BUTTON_WIDTH / 2, this.height / 2 + 60, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	private static String toHex(int color) {
		return String.format("%06X", color & 0xFFFFFF);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, this.height / 2 - 60, 0xFFFFFF);

		int centerX = this.width / 2;
		int labelX = centerX - (LABEL_WIDTH + FIELD_WIDTH) / 2;
		context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.cim.stamina_color.fill"), labelX, fillRowY + 4, 0xAAAAAA);
		context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.cim.stamina_color.background"), labelX, backgroundRowY + 4, 0xAAAAAA);

		StaminaNotchBarRenderer.draw(context, centerX - PREVIEW_WIDTH / 2, previewY, PREVIEW_WIDTH, PREVIEW_HEIGHT,
				150f, 200f, fillColor, backgroundColor);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
