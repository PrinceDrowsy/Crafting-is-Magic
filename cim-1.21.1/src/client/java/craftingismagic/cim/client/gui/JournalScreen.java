package craftingismagic.cim.client.gui;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import craftingismagic.cim.client.ability.AbilityLoreRegistry;
import craftingismagic.cim.client.ability.AbilityLoreRegistry.AbilityLoreEntry;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * The player's slower-paced, browse-at-leisure info menu - distinct from the fast in-combat radial
 * ability menu. Tabbed: Abilities is backed by real data ({@link AbilityLoreRegistry}, the same
 * source {@link SpeciesLoreScreen} uses); Friendships and Quests have no backing systems yet, so
 * they show a placeholder until those exist. Unlike the mandatory species-selection flow, this is
 * an optional info screen the player can dismiss anytime with Esc.
 */
public class JournalScreen extends Screen {
	private static final int CONTENT_WIDTH = 280;
	private static final int TITLE_Y = 14;
	private static final int TABS_Y = 32;
	private static final int TAB_BUTTON_WIDTH = 90;
	private static final int TAB_BUTTON_HEIGHT = 20;
	private static final int TAB_SPACING = 4;
	private static final int BODY_Y = TABS_Y + TAB_BUTTON_HEIGHT + 8;
	private static final int BODY_BOTTOM_MARGIN = 16;

	public enum Tab {
		ABILITIES("screen.cim.journal.tab_abilities"),
		FRIENDSHIPS("screen.cim.journal.tab_friendships"),
		QUESTS("screen.cim.journal.tab_quests");

		private final String translationKey;

		Tab(String translationKey) {
			this.translationKey = translationKey;
		}

		Text label() {
			return Text.translatable(translationKey);
		}
	}

	private final Tab activeTab;

	public JournalScreen(Tab activeTab) {
		super(Text.translatable("screen.cim.journal.title"));
		this.activeTab = activeTab;
	}

	@Override
	protected void init() {
		int x = (this.width - CONTENT_WIDTH) / 2;

		int tabsWidth = Tab.values().length * TAB_BUTTON_WIDTH + (Tab.values().length - 1) * TAB_SPACING;
		int tabX = (this.width - tabsWidth) / 2;
		for (Tab tab : Tab.values()) {
			ButtonWidget button = ButtonWidget.builder(tab.label(), button2 -> this.client.setScreen(new JournalScreen(tab)))
					.dimensions(tabX, TABS_Y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT)
					.build();
			button.active = tab != activeTab;
			this.addDrawableChild(button);
			tabX += TAB_BUTTON_WIDTH + TAB_SPACING;
		}

		int bodyHeight = this.height - BODY_Y - BODY_BOTTOM_MARGIN;
		this.addDrawableChild(
				new ScrollableTextWidget(x, BODY_Y, CONTENT_WIDTH, bodyHeight, bodyFor(activeTab, this.client.player), this.textRenderer));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, TITLE_Y, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static Text bodyFor(Tab tab, PlayerEntity player) {
		return switch (tab) {
			case ABILITIES -> abilitiesBody(player);
			case FRIENDSHIPS -> Text.translatable("screen.cim.journal.friendships_placeholder").formatted(Formatting.ITALIC);
			case QUESTS -> Text.translatable("screen.cim.journal.quests_placeholder").formatted(Formatting.ITALIC);
		};
	}

	private static Text abilitiesBody(PlayerEntity player) {
		if (!SpeciesAttachment.hasChosenSpecies(player)) {
			return Text.translatable("screen.cim.species_detail.no_abilities").formatted(Formatting.ITALIC);
		}

		Species species = SpeciesAttachment.getSpecies(player);
		List<AbilityLoreEntry> abilities = AbilityLoreRegistry.abilitiesFor(species);
		if (abilities.isEmpty()) {
			return Text.translatable("screen.cim.species_detail.no_abilities").formatted(Formatting.ITALIC);
		}

		MutableText body = Text.empty();
		boolean first = true;
		for (AbilityLoreEntry entry : abilities) {
			if (!first) {
				body.append(Text.literal("\n\n"));
			}
			first = false;
			body.append(entry.name().copy().formatted(Formatting.YELLOW))
					.append(Text.literal("\n"))
					.append(entry.description());
		}
		return body;
	}
}
