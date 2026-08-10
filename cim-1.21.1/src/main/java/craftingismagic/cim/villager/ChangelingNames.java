package craftingismagic.cim.villager;

import java.util.List;

import net.minecraft.util.math.random.Random;

/**
 * A Changeling's TRUE name pool (see {@link ChangelingDisguiseHandler}) - deliberately distinct from
 * {@link VillagerNames}' cheerful pony pool, mirroring how the show's own named Changelings (Thorax,
 * Pharynx, Ocellus, Pupa - all four genuine, not invented) read differently from a typical pony name.
 * Rounded out with original hive/insect-flavored names in the same register. Chrysalis (the reigning
 * Changeling queen and a major named villain) is deliberately excluded - the same reasoning
 * {@link VillagerNames} already applies by never handing a Mane Six name to a background villager.
 */
public final class ChangelingNames {
	private static final List<String> NAMES = List.of(
			"Thorax", "Pharynx", "Ocellus", "Pupa",
			"Vespid", "Chiton", "Carapace", "Mandible",
			"Exuvia", "Ichor", "Nectar", "Drone",
			"Hex", "Vex", "Skitter", "Husk");

	private ChangelingNames() {
	}

	public static String random(Random random) {
		return NAMES.get(random.nextInt(NAMES.size()));
	}
}
