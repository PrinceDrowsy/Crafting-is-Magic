package craftingismagic.cim.client.gui;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

import craftingismagic.cim.client.accessibility.CimText;
import craftingismagic.cim.network.DialogueBeatS2CPayload;
import craftingismagic.cim.network.DialogueChooseC2SPayload;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;

/**
 * The visual-novel-style conversation screen (README: "Idle Chatter, in more detail") - a dialogue
 * box plus a live gaze-tracking render of the pony standing in for a character portrait. It shows
 * whatever {@code .tale} conversation the server is running, one beat at a time
 * ({@link DialogueBeatS2CPayload}); Idle Chatter is one such conversation.
 * <p>
 * The portrait is a real live render via {@code InventoryScreen.drawEntity} (confirmed via
 * {@code javap}: {@code drawEntity(DrawContext, int x1, int y1, int x2, int y2, int size, float
 * yOffset, float mouseX, float mouseY, LivingEntity)} - the float right after {@code size} is a
 * Y-offset multiplier added to the entity's own height, not a mouse coordinate; vanilla's own call
 * site uses {@code 0.0625f}). It's generic over any {@code LivingEntity} and resolves through the
 * same renderer map {@code CimVillagerPonyRenderer} is registered into, so the portrait shows up
 * genetics-colored and life-stage-sized like the villager actually is, not a plain vanilla model.
 * Gaze tracking is entirely internal to that method - it recomputes head/eye rotation from the
 * {@code mouseX}/{@code mouseY} passed in every single frame, so this screen just has to pass the
 * real cursor position through each {@link #render} call.
 * <p>
 * Three states, not two: after a reply is picked the buttons disappear entirely until the server's
 * next beat arrives via {@link #showBeat} (it's a real round trip), rather than leaving the buttons
 * clickable and risking a double-send. Replies that are only a tone sit side by side, as Idle
 * Chatter always has; replies with their own words stack, one per row.
 */
public class DialogueScreen extends Screen {
	private static final int PORTRAIT_Y1 = 24;
	private static final int PORTRAIT_HEIGHT = 90;
	private static final int PORTRAIT_WIDTH = 160;
	private static final int PORTRAIT_ENTITY_SIZE = 40;
	private static final float PORTRAIT_Y_OFFSET = 0.0625f;
	private static final int TEXT_MAX_WIDTH = 260;
	private static final int BUTTON_WIDTH = 80;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 6;

	private enum State { AWAITING_REPLY, WAITING_FOR_SERVER, DONE }

	private final int entityId;
	private String bodyText;
	private List<String> optionTones = List.of();
	private List<String> optionTexts = List.of();
	private State state;

	public DialogueScreen(DialogueBeatS2CPayload beat) {
		super(Text.translatable("screen.cim.idle_chatter.title", beat.partnerName()));
		this.entityId = beat.entityId();
		apply(beat);
	}

	public int entityId() {
		return entityId;
	}

	@Override
	protected void init() {
		rebuildButtons();
	}

	/** Called by the client network receiver when the server sends the next beat. */
	public void showBeat(DialogueBeatS2CPayload beat) {
		apply(beat);
		rebuildButtons();
	}

	private void apply(DialogueBeatS2CPayload beat) {
		this.bodyText = String.join("\n\n", beat.lines());
		this.optionTones = beat.optionTones();
		this.optionTexts = beat.optionTexts();
		this.state = beat.ended() || beat.optionTexts().isEmpty() ? State.DONE : State.AWAITING_REPLY;
	}

	private void rebuildButtons() {
		this.clearChildren();
		int y = this.height - 34;
		if (state == State.AWAITING_REPLY) {
			boolean tonesOnly = optionTexts.stream().allMatch(String::isEmpty);
			if (tonesOnly) {
				int count = optionTexts.size();
				int x = (this.width - (count * BUTTON_WIDTH + (count - 1) * BUTTON_SPACING)) / 2;
				for (int i = 0; i < count; i++) {
					addOption(i, x, y, BUTTON_WIDTH);
					x += BUTTON_WIDTH + BUTTON_SPACING;
				}
			} else {
				int x = (this.width - TEXT_MAX_WIDTH) / 2;
				for (int i = optionTexts.size() - 1; i >= 0; i--) {
					addOption(i, x, y, TEXT_MAX_WIDTH);
					y -= BUTTON_HEIGHT + 2;
				}
			}
		} else if (state == State.DONE) {
			int x = (this.width - BUTTON_WIDTH) / 2;
			this.addDrawableChild(ButtonWidget.builder(CimText.style(Text.translatable("screen.cim.idle_chatter.close")), button -> this.close())
					.dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	private void addOption(int index, int x, int y, int width) {
		this.addDrawableChild(ButtonWidget.builder(CimText.style(label(index)), button -> {
			state = State.WAITING_FOR_SERVER;
			ClientPlayNetworking.send(new DialogueChooseC2SPayload(entityId, index));
			rebuildButtons();
		}).dimensions(x, y, width, BUTTON_HEIGHT).build());
	}

	/** The reply's own words, or its tone ("Friendly") when it has none. */
	private Text label(int index) {
		String text = optionTexts.get(index);
		if (!text.isEmpty()) {
			return Text.literal(text);
		}
		String tone = optionTones.get(index);
		return tone.isEmpty() ? Text.literal("...") : Text.translatable("screen.cim.idle_chatter." + tone);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		CimText.drawCentered(context, this.textRenderer, this.getTitle(), this.width / 2, 12, 0xFFFFFF);

		int centerX = this.width / 2;
		int portraitX1 = centerX - PORTRAIT_WIDTH / 2;
		int portraitX2 = centerX + PORTRAIT_WIDTH / 2;
		int portraitY2 = PORTRAIT_Y1 + PORTRAIT_HEIGHT;

		if (this.client != null && this.client.world != null
				&& this.client.world.getEntityById(entityId) instanceof LivingEntity livingEntity) {
			InventoryScreen.drawEntity(context, portraitX1, PORTRAIT_Y1, portraitX2, portraitY2,
					PORTRAIT_ENTITY_SIZE, PORTRAIT_Y_OFFSET, (float) mouseX, (float) mouseY, livingEntity);
		}

		CimText.drawWrapped(context, this.textRenderer, Text.literal(bodyText),
				centerX - TEXT_MAX_WIDTH / 2, portraitY2 + 12, TEXT_MAX_WIDTH, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		ClientPlayNetworking.send(new StopVillagerInteractionC2SPayload(entityId));
		super.close();
	}
}
