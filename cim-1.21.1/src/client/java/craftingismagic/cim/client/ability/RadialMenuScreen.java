package craftingismagic.cim.client.ability;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import craftingismagic.cim.client.accessibility.CimText;

/**
 * A generic radial wheel of {@link Ability}-shaped wedges: opens while some key is held, closes
 * and hands off whatever's hovered when it's released. Modeled closely on vanilla's own F3+F4
 * gamemode-switcher wheel ({@code GameModeSelectionScreen}) — same slot/selection sprites, and the
 * same trick of polling the raw key state directly each frame (rather than the KeyBinding's own
 * tracked state) to detect release while this screen has input focus.
 * <p>
 * The main ability radial menu ({@link RadialMenuOpener}) uses the single-arg constructor - closes
 * on {@link RadialMenuKeyBinding#OPEN_RADIAL_MENU}'s release and *selects* (not activates - see
 * {@link SelectedAbility}) whatever's hovered. A ring built for some other purpose (e.g. the
 * Teleport shortcut menu) can supply its own release key and choice handler via the full
 * constructor instead - selecting a shortcut slot fires an action immediately rather than just
 * changing what the main activation key would later do.
 */
public class RadialMenuScreen extends Screen {
	private static final Identifier SLOT_TEXTURE = Identifier.ofVanilla("gamemode_switcher/slot");
	private static final Identifier SELECTION_TEXTURE = Identifier.ofVanilla("gamemode_switcher/selection");
	private static final int SLOT_SIZE = 26;
	private static final int ICON_OFFSET = 5;
	private static final int RING_RADIUS = 60;
	private static final int DEAD_ZONE_RADIUS = 20;

	private final List<Ability> abilities;
	private final BooleanSupplier keyReleased;
	private final Consumer<Ability> onChoose;
	private int hoveredIndex = -1;

	public RadialMenuScreen(List<Ability> abilities) {
		this(abilities, RadialMenuScreen::isOpenRadialMenuKeyReleased, SelectedAbility::set);
	}

	public RadialMenuScreen(List<Ability> abilities, BooleanSupplier keyReleased, Consumer<Ability> onChoose) {
		super(Text.empty());
		this.abilities = abilities;
		this.keyReleased = keyReleased;
		this.onChoose = onChoose;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (keyReleased.getAsBoolean()) {
			if (hoveredIndex != -1) {
				onChoose.accept(abilities.get(hoveredIndex));
			}
			client.setScreen(null);
			return;
		}

		super.render(context, mouseX, mouseY, delta);

		int centerX = width / 2;
		int centerY = height / 2;
		int count = abilities.size();

		double dx = mouseX - centerX;
		double dy = mouseY - centerY;
		double dist = Math.sqrt(dx * dx + dy * dy);

		if (dist >= DEAD_ZONE_RADIUS) {
			double angle = Math.atan2(dy, dx) + Math.PI / 2;
			if (angle < 0) {
				angle += Math.PI * 2;
			}
			double sectorSize = (Math.PI * 2) / count;
			hoveredIndex = (int) ((angle + sectorSize / 2) / sectorSize) % count;
		} else {
			hoveredIndex = -1;
		}

		for (int i = 0; i < count; i++) {
			double slotAngle = (Math.PI * 2 / count) * i - Math.PI / 2;
			int x = (int) (centerX + Math.cos(slotAngle) * RING_RADIUS - SLOT_SIZE / 2.0);
			int y = (int) (centerY + Math.sin(slotAngle) * RING_RADIUS - SLOT_SIZE / 2.0);

			context.drawGuiTexture(SLOT_TEXTURE, x, y, SLOT_SIZE, SLOT_SIZE);
			if (i == hoveredIndex) {
				context.drawGuiTexture(SELECTION_TEXTURE, x, y, SLOT_SIZE, SLOT_SIZE);
			}
			context.drawItem(abilities.get(i).icon(), x + ICON_OFFSET, y + ICON_OFFSET);
		}

		if (hoveredIndex != -1) {
			Text name = abilities.get(hoveredIndex).displayName();
			context.drawCenteredTextWithShadow(textRenderer, CimText.style(name), centerX, centerY - 4, 0xFFFFFF);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	private static boolean isOpenRadialMenuKeyReleased() {
		InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(RadialMenuKeyBinding.OPEN_RADIAL_MENU);
		return !InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), boundKey.getCode());
	}
}
