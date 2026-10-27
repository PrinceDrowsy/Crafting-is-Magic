package craftingismagic.cim.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import craftingismagic.cim.client.accessibility.CimText;
import craftingismagic.cim.client.accessibility.ReadingSetupScreen;
import craftingismagic.cim.species.Species;

/**
 * Shown to a player on first join, before they have a species recorded.
 * Not closable except by picking a species — there is no "no species" state.
 */
public class SpeciesSelectScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 4;

	public SpeciesSelectScreen() {
		super(Text.translatable("screen.cim.species_select.title"));
	}

	@Override
	protected void init() {
		ReadingSetupScreen.offer(this);
		Species[] species = Species.values();
		int totalHeight = species.length * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
		int startY = (this.height - totalHeight) / 2;
		int x = (this.width - BUTTON_WIDTH) / 2;

		for (int i = 0; i < species.length; i++) {
			Species choice = species[i];
			int y = startY + i * (BUTTON_HEIGHT + BUTTON_SPACING);
			this.addDrawableChild(ButtonWidget.builder(CimText.style(Text.translatable(choice.translationKey())), button -> this.selectSpecies(choice))
					.dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build());
		}
	}

	private void selectSpecies(Species species) {
		this.client.setScreen(new SpeciesLoreScreen(species));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
