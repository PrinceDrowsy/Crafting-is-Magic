package craftingismagic.cim.client.ability;

import java.util.List;

import net.minecraft.text.Text;

import craftingismagic.cim.species.Species;

/**
 * Player-facing "what does this species' abilities do and how do I use them" text - separate from
 * {@link AbilityRegistry}, which wires abilities into the radial menu and includes shared
 * placeholder entries that don't belong in lore-facing content. Shared by {@code SpeciesLoreScreen}
 * (the species-detail screen shown at character creation) and {@code JournalScreen}'s Abilities tab.
 */
public final class AbilityLoreRegistry {
	private AbilityLoreRegistry() {
	}

	public static List<AbilityLoreEntry> abilitiesFor(Species species) {
		return switch (species) {
			case EARTH_PONY -> List.of(new AbilityLoreEntry(
					Text.translatable("ability.cim.charged_jump.name"), Text.translatable("ability.cim.charged_jump.desc")));
			case UNICORN -> List.of(
					new AbilityLoreEntry(Text.translatable("ability.cim.levitation.name"), Text.translatable("ability.cim.levitation.desc")),
					new AbilityLoreEntry(Text.translatable("ability.cim.teleport.name"), Text.translatable("ability.cim.teleport.desc")));
			case PEGASUS -> List.of(new AbilityLoreEntry(
					Text.translatable("ability.cim.pegasus_flight.name"), Text.translatable("ability.cim.pegasus_flight.desc")));
			case BAT_PONY, CHANGELING, KIRIN -> List.of();
		};
	}

	public record AbilityLoreEntry(Text name, Text description) {
	}
}
