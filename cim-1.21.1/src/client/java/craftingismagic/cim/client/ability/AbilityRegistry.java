package craftingismagic.cim.client.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import craftingismagic.cim.species.Species;

/**
 * The abilities the radial menu offers, per species. Species without any
 * real ability content yet fall back to a shared placeholder set, just to
 * prove out the menu with more than one wedge — real per-species lists
 * replace these as abilities get built.
 */
public final class AbilityRegistry {
	private static final Ability CHARGED_JUMP = new Ability(Text.literal("Charged Jump"), new ItemStack(Items.FEATHER),
			client -> client.player.sendMessage(Text.literal("Charged Jump selected"), true));

	private static final List<Ability> PLACEHOLDER_ABILITIES = List.of(
			new Ability(Text.literal("Test Ability A"), new ItemStack(Items.DIAMOND),
					client -> client.player.sendMessage(Text.literal("Test Ability A selected"), true)),
			new Ability(Text.literal("Test Ability B"), new ItemStack(Items.EMERALD),
					client -> client.player.sendMessage(Text.literal("Test Ability B selected"), true)),
			new Ability(Text.literal("Test Ability C"), new ItemStack(Items.AMETHYST_SHARD),
					client -> client.player.sendMessage(Text.literal("Test Ability C selected"), true)));

	private AbilityRegistry() {
	}

	public static List<Ability> abilitiesFor(Species species) {
		List<Ability> abilities = new ArrayList<>();
		if (species == Species.EARTH_PONY) {
			abilities.add(CHARGED_JUMP);
		}
		abilities.addAll(PLACEHOLDER_ABILITIES);
		return abilities;
	}
}
