package craftingismagic.cim.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import craftingismagic.cim.client.accessibility.CimText;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;
import craftingismagic.cim.network.VillagerGiveGiftC2SPayload;
import craftingismagic.cim.network.VillagerIdleChatterC2SPayload;
import craftingismagic.cim.network.VillagerOfferTradeC2SPayload;

/**
 * A villager's Interact menu, opened by right-clicking them (see {@code VillagerInteractionHandler}
 * on the server) instead of vanilla's trade screen opening directly - "Offer Trade" below reaches
 * vanilla trading through this menu instead of bypassing it. Idle Chatter, Give Gift, and Offer
 * Trade are wired up; Ask a Rumor/Offer Help/Ask Help render disabled, same "full shell, partial
 * wiring" shape as the Journal menu, since they depend on systems (quests, profession-gated favors)
 * that don't exist yet.
 * <p>
 * The server freezes the villager in place the moment this screen opens, so it doesn't wander off
 * mid-conversation. {@link #close()} releases that hold whenever the player intentionally leaves
 * (button press or Escape) - except Offer Trade and Idle Chatter, which hand off to vanilla's own
 * trade-session lifecycle / {@link IdleChatterScreen} respectively instead of releasing it.
 * Deliberately overrides {@code close()}, not {@code removed()}: {@code removed()} also fires on
 * routine screen re-initialization (e.g. a window resize/focus event), which was silently releasing
 * the freeze seconds into what still looked like an open menu.
 */
public class VillagerMenuScreen extends Screen {
	private static final int BUTTON_WIDTH = 160;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 4;
	private static final int BUTTON_COUNT = 6;
	private static final int TITLE_Y = 14;
	private static final int AFFECTION_Y = 30;

	private final int villagerEntityId;
	private final int affection;
	private final boolean canTrade;
	private boolean handingOffToTrade;
	private boolean handingOffToChat;

	public VillagerMenuScreen(int villagerEntityId, int affection, boolean canTrade, String villagerName) {
		super(Text.translatable("screen.cim.villager_menu.title", villagerName));
		this.villagerEntityId = villagerEntityId;
		this.affection = affection;
		this.canTrade = canTrade;
	}

	@Override
	protected void init() {
		int x = (this.width - BUTTON_WIDTH) / 2;
		int y = (this.height - BUTTON_COUNT * (BUTTON_HEIGHT + BUTTON_SPACING)) / 2;

		boolean canGift = !this.client.player.getMainHandStack().isEmpty();

		y = addButton(x, y, "screen.cim.villager_menu.idle_chatter", true, button -> {
			handingOffToChat = true;
			ClientPlayNetworking.send(new VillagerIdleChatterC2SPayload(villagerEntityId));
			this.close();
		});
		y = addButton(x, y, "screen.cim.villager_menu.ask_a_rumor", false, button -> {
		});
		y = addButton(x, y, "screen.cim.villager_menu.give_gift", canGift, button -> {
			ClientPlayNetworking.send(new VillagerGiveGiftC2SPayload(villagerEntityId));
			this.close();
		});
		y = addButton(x, y, "screen.cim.villager_menu.offer_trade", canTrade, button -> {
			handingOffToTrade = true;
			ClientPlayNetworking.send(new VillagerOfferTradeC2SPayload(villagerEntityId));
			this.close();
		});
		y = addButton(x, y, "screen.cim.villager_menu.offer_help", false, button -> {
		});
		addButton(x, y, "screen.cim.villager_menu.ask_help", false, button -> {
		});
	}

	private int addButton(int x, int y, String translationKey, boolean enabled, ButtonWidget.PressAction onPress) {
		ButtonWidget button = ButtonWidget.builder(CimText.style(Text.translatable(translationKey)), onPress)
				.dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.active = enabled;
		this.addDrawableChild(button);
		return y + BUTTON_HEIGHT + BUTTON_SPACING;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		CimText.drawCentered(context, this.textRenderer, this.getTitle(), this.width / 2, TITLE_Y, 0xFFFFFF);
		// Below the title by its actual (possibly scaled) height, so a larger text size never overlaps.
		int affectionY = Math.max(AFFECTION_Y, TITLE_Y + CimText.lineHeight(this.textRenderer) + 6);
		CimText.drawCentered(context, this.textRenderer,
				Text.translatable("screen.cim.villager_menu.affection", affection), this.width / 2, affectionY, 0xAAAAAA);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (!handingOffToTrade && !handingOffToChat) {
			ClientPlayNetworking.send(new StopVillagerInteractionC2SPayload(villagerEntityId));
		}
		super.close();
	}
}
