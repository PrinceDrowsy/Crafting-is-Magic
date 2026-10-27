package craftingismagic.cim.client.gui;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import craftingismagic.cim.client.ability.AbilityLoreRegistry;
import craftingismagic.cim.client.accessibility.CimText;
import craftingismagic.cim.client.accessibility.ReadingSetupScreen;
import craftingismagic.cim.client.accessibility.ScaledTextWidget;
import craftingismagic.cim.client.ability.AbilityLoreRegistry.AbilityLoreEntry;
import craftingismagic.cim.network.SelectSpeciesC2SPayload;
import craftingismagic.cim.species.Species;

/**
 * Shown after picking a species on {@link SpeciesSelectScreen} - a chance to read a bit of
 * show-accurate lore and see what the species can actually do in the mod before committing. The
 * server doesn't learn the choice until "Continue" is pressed, so "Back" can freely return to the
 * species list to keep browsing without side effects. Body text lives in one scrollable panel
 * rather than a fixed stack of widgets, since the ability list's length varies a lot by species
 * (three abilities for Unicorn vs. none yet for Bat Pony/Changeling/Kirin) and shouldn't be able to
 * run off the bottom of the screen.
 */
public class SpeciesLoreScreen extends Screen {
	private static final int CONTENT_WIDTH = 280;
	private static final int TITLE_Y = 14;
	private static final int BODY_Y = 32;
	private static final int BODY_BOTTOM_MARGIN = 44;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 8;

	private final Species species;

	public SpeciesLoreScreen(Species species) {
		super(Text.translatable(species.translationKey()));
		this.species = species;
	}

	@Override
	protected void init() {
		ReadingSetupScreen.offer(this);
		int x = (this.width - CONTENT_WIDTH) / 2;
		// Below the title by its actual (possibly scaled) height, so a larger text size never overlaps.
		int bodyY = Math.max(BODY_Y, TITLE_Y + CimText.lineHeight(this.textRenderer) + 8);
		int bodyHeight = this.height - bodyY - BODY_BOTTOM_MARGIN;

		this.addDrawableChild(
				new ScaledTextWidget(x, bodyY, CONTENT_WIDTH, bodyHeight, buildBody(species), this.textRenderer));

		int buttonY = this.height - BUTTON_HEIGHT - 16;
		int buttonsX = (this.width - BUTTON_WIDTH * 2 - BUTTON_SPACING) / 2;
		this.addDrawableChild(ButtonWidget.builder(CimText.style(Text.translatable("screen.cim.species_detail.back")),
						button -> this.client.setScreen(new SpeciesSelectScreen()))
				.dimensions(buttonsX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		this.addDrawableChild(ButtonWidget.builder(CimText.style(Text.translatable("screen.cim.species_detail.continue")),
						button -> this.confirm())
				.dimensions(buttonsX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		CimText.drawCentered(context, this.textRenderer, this.getTitle(), this.width / 2, TITLE_Y, 0xFFFFFF);
	}

	private void confirm() {
		ClientPlayNetworking.send(new SelectSpeciesC2SPayload(species));
		this.close();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static Text buildBody(Species species) {
		MutableText body = Text.translatable(species.translationKey() + ".lore").copy()
				.append(Text.literal("\n\n"))
				// Bold, never underline or italics - those make words harder to read for many dyslexic readers.
				.append(Text.translatable("screen.cim.species_detail.abilities_header").formatted(Formatting.BOLD));

		List<AbilityLoreEntry> abilities = AbilityLoreRegistry.abilitiesFor(species);
		if (abilities.isEmpty()) {
			body.append(Text.literal("\n"))
					.append(Text.translatable("screen.cim.species_detail.no_abilities").formatted(Formatting.GRAY));
		} else {
			for (AbilityLoreEntry entry : abilities) {
				body.append(Text.literal("\n"))
						.append(entry.name().copy().formatted(Formatting.YELLOW))
						.append(Text.literal("\n"))
						.append(entry.description());
			}
		}
		return body;
	}
}
