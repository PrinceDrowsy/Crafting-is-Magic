package craftingismagic.cim.villager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * One-time setup for a freshly-loaded villager: a {@link Species} (the same attachment players get
 * via species selection), a visible pony nametag, and a favorite gift item - all "assign this once
 * and never again" concerns, so they share the same {@code ENTITY_LOAD} listener rather than three
 * near-duplicate ones. Purely a data layer - visuals are handled entirely by Mine Little Pony (if
 * installed) ponifying vanilla villagers using that data, or plain vanilla villager rendering as
 * the fallback if it isn't. Nothing here touches trades.
 * <p>
 * Pony genetics and life stage/family are handled by {@link VillagerFamilyHandler} instead, in its
 * own separate {@code ENTITY_LOAD} listener registered after this one - genetics assignment there
 * depends on family resolution (combine vs. randomize), so it doesn't belong in this file's
 * independent concerns. That listener firing after this one (species/name already set) is why
 * {@code Cim} registers {@code VillagerFamilyHandler} after {@code VillagerSpeciesHandler}.
 */
public final class VillagerSpeciesHandler {
	private VillagerSpeciesHandler() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof VillagerEntity villager)) {
				return;
			}

			if (!SpeciesAttachment.hasChosenSpecies(villager)) {
				Species[] species = Species.values();
				Species chosen = species[villager.getRandom().nextInt(species.length)];
				SpeciesAttachment.setSpecies(villager, chosen);
				Cim.LOGGER.info("Villager at {} assigned species {}", villager.getBlockPos(), chosen);
			}

			if (!villager.hasCustomName()) {
				String name = VillagerNames.random(villager.getRandom());
				villager.setCustomName(Text.literal(name));
				villager.setCustomNameVisible(true);
			}

			if (!FavoriteGiftAttachment.hasFavorite(villager)) {
				Item favorite = FavoriteGiftAttachment.randomGift(villager.getRandom());
				FavoriteGiftAttachment.setFavorite(villager, favorite);
			}
		});
	}
}
