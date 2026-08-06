package craftingismagic.cim.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

import craftingismagic.cim.network.IdleChatterReplyC2SPayload;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;
import craftingismagic.cim.villager.ReplyTone;

/**
 * Idle Chatter's visual-novel-style conversation screen (README: "Idle Chatter, in more detail") -
 * a dialogue box plus a live gaze-tracking render of the pony standing in for a character
 * portrait, opened from {@link VillagerMenuScreen} instead of the old flat chat-message boost.
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
 * Three states, not two: after a reply tone is picked the buttons disappear entirely until the
 * server's reaction arrives via {@link #showReaction} (there's no "instant" reaction - it's a real
 * round trip), rather than leaving the tone buttons clickable and risking a double-send.
 */
public class IdleChatterScreen extends Screen {
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

	private final int villagerEntityId;
	private String bodyText;
	private State state = State.AWAITING_REPLY;

	public IdleChatterScreen(int villagerEntityId, String villagerName, String openingLine) {
		super(Text.translatable("screen.cim.idle_chatter.title", villagerName));
		this.villagerEntityId = villagerEntityId;
		this.bodyText = openingLine;
	}

	@Override
	protected void init() {
		rebuildButtons();
	}

	/** Called by the client network receiver once the server's reply-reaction round trip lands. */
	public void showReaction(String reactionLine, String closingLine) {
		this.bodyText = reactionLine + "\n\n" + closingLine;
		this.state = State.DONE;
		rebuildButtons();
	}

	private void rebuildButtons() {
		this.clearChildren();
		int y = this.height - 34;
		if (state == State.AWAITING_REPLY) {
			int totalWidth = 3 * BUTTON_WIDTH + 2 * BUTTON_SPACING;
			int x = (this.width - totalWidth) / 2;
			x = addToneButton(x, y, ReplyTone.FRIENDLY, "screen.cim.idle_chatter.friendly");
			x = addToneButton(x, y, ReplyTone.CURIOUS, "screen.cim.idle_chatter.curious");
			addToneButton(x, y, ReplyTone.BOLD, "screen.cim.idle_chatter.bold");
		} else if (state == State.DONE) {
			int x = (this.width - BUTTON_WIDTH) / 2;
			this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.cim.idle_chatter.close"), button -> this.close())
					.dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	private int addToneButton(int x, int y, ReplyTone tone, String translationKey) {
		this.addDrawableChild(ButtonWidget.builder(Text.translatable(translationKey), button -> {
			state = State.WAITING_FOR_SERVER;
			ClientPlayNetworking.send(new IdleChatterReplyC2SPayload(villagerEntityId, tone));
			rebuildButtons();
		}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		return x + BUTTON_WIDTH + BUTTON_SPACING;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, 12, 0xFFFFFF);

		int centerX = this.width / 2;
		int portraitX1 = centerX - PORTRAIT_WIDTH / 2;
		int portraitX2 = centerX + PORTRAIT_WIDTH / 2;
		int portraitY2 = PORTRAIT_Y1 + PORTRAIT_HEIGHT;

		if (this.client != null && this.client.world != null
				&& this.client.world.getEntityById(villagerEntityId) instanceof LivingEntity livingEntity) {
			InventoryScreen.drawEntity(context, portraitX1, PORTRAIT_Y1, portraitX2, portraitY2,
					PORTRAIT_ENTITY_SIZE, PORTRAIT_Y_OFFSET, (float) mouseX, (float) mouseY, livingEntity);
		}

		context.drawTextWrapped(this.textRenderer, Text.literal(bodyText),
				centerX - TEXT_MAX_WIDTH / 2, portraitY2 + 12, TEXT_MAX_WIDTH, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		ClientPlayNetworking.send(new StopVillagerInteractionC2SPayload(villagerEntityId));
		super.close();
	}
}
