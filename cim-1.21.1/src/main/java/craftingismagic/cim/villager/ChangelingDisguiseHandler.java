package craftingismagic.cim.villager;

import java.util.EnumMap;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.ChangelingAppearanceAttachment.ChangelingAppearance;
import craftingismagic.cim.villager.ChangelingDisguiseAttachment.ChangelingDisguise;

/**
 * Assigns every freshly-loaded Changeling a disguise (see Village changelings in the README) - a
 * species to present as, a true name, a separate disguise name, and true-form coloring - the moment
 * it loads, the same proximity-based, per-villager approach {@link VillagerFamilyHandler}/
 * {@link VillagerFriendGroupHandler} already use for everything "village-scoped" (there's still no
 * vanilla/Fabric "these villagers belong to the same village" signal to query directly).
 * <p>
 * Registered in {@code Cim.onInitialize()} immediately after {@link VillagerSpeciesHandler}, before
 * {@link VillagerFamilyHandler} - not just tidiness. {@link VillagerFamilyHandler#assignChangeling}
 * rolls this villager's (visually unused, but still-persisted) {@code PonyGenetics} via {@link
 * PonyGenetics#applyNameColorBias}, which biases coat/mane hue against whatever name is on the
 * villager <em>right now</em>. If this handler ran after Family, that bias would land against the
 * discarded {@link VillagerSpeciesHandler} name roll instead of the real disguise name - a subtle,
 * hard-to-notice color bug, not a crash. Keep this registered first.
 */
public final class ChangelingDisguiseHandler {
	private static final double DISGUISE_SEARCH_RADIUS = 28.0;
	private static final Species[] COMMON_DISGUISES = {Species.EARTH_PONY, Species.UNICORN, Species.PEGASUS};

	private ChangelingDisguiseHandler() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity villager) {
				assignDisguiseIfNeeded(villager, world);
			}
		});
	}

	/**
	 * Also called directly by {@link VillagerDebugEditorHandler} - {@code ENTITY_LOAD} only ever
	 * fires once, when a villager first loads, so manually switching an ALREADY-loaded villager's
	 * species to {@link Species#CHANGELING} via the debug editor would otherwise never get a
	 * disguise at all (a real bug, reported and fixed 2026-08-09). Safe to call unconditionally -
	 * it's a no-op for anything that isn't a Changeling still missing its disguise.
	 */
	public static void assignDisguiseIfNeeded(VillagerEntity villager, ServerWorld world) {
		if (SpeciesAttachment.hasChosenSpecies(villager)
				&& SpeciesAttachment.getSpecies(villager) == Species.CHANGELING
				&& !ChangelingDisguiseAttachment.hasDisguise(villager)) {
			assignDisguise(villager, world);
		}
	}

	private static void assignDisguise(VillagerEntity villager, ServerWorld world) {
		Random random = villager.getRandom();
		Species disguiseSpecies = pickDisguiseSpecies(villager, world);
		String trueName = ChangelingNames.random(random);
		String disguiseName = randomDisguiseName(random, trueName);

		ChangelingDisguiseAttachment.setDisguise(villager, new ChangelingDisguise(disguiseSpecies, trueName, disguiseName, true));
		ChangelingAppearanceAttachment.setAppearance(villager,
				new ChangelingAppearance(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()));

		villager.setCustomName(Text.literal(disguiseName));
		villager.setCustomNameVisible(true);
	}

	/** Discards whatever name {@link VillagerSpeciesHandler} already rolled for this villager - one
	 * cheap wasted RNG call, not worth threading Changeling-awareness into that handler's unrelated
	 * shared listener just to skip it. */
	private static String randomDisguiseName(Random random, String trueName) {
		String candidate;
		do {
			candidate = VillagerNames.random(random);
		} while (candidate.equals(trueName));
		return candidate;
	}

	private static Species pickDisguiseSpecies(VillagerEntity self, ServerWorld world) {
		Box searchBox = new Box(self.getBlockPos()).expand(DISGUISE_SEARCH_RADIUS);
		Map<Species, Integer> tally = new EnumMap<>(Species.class);
		for (VillagerEntity other : world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox, o -> o != self)) {
			Species effective = effectiveSpeciesFor(other);
			if (effective != null) {
				tally.merge(effective, 1, Integer::sum);
			}
		}

		Species mode = null;
		int best = 0;
		for (Map.Entry<Species, Integer> entry : tally.entrySet()) {
			if (entry.getValue() > best) {
				best = entry.getValue();
				mode = entry.getKey();
			}
		}

		if (mode == Species.KIRIN || mode == Species.BAT_PONY) {
			return mode;
		}
		return COMMON_DISGUISES[self.getRandom().nextInt(COMMON_DISGUISES.length)];
	}

	/** What an observer would actually SEE for this villager - another Changeling's own chosen
	 * disguise species (never its true form; a village's apparent character is the visible
	 * population, disguises included), or any other villager's real species. Returns null for an
	 * as-yet-undisguised Changeling, which should influence nobody's tally either way. */
	private static Species effectiveSpeciesFor(VillagerEntity villager) {
		if (!SpeciesAttachment.hasChosenSpecies(villager)) {
			return null;
		}
		Species trueSpecies = SpeciesAttachment.getSpecies(villager);
		if (trueSpecies != Species.CHANGELING) {
			return trueSpecies;
		}
		return ChangelingDisguiseAttachment.hasDisguise(villager)
				? ChangelingDisguiseAttachment.getDisguise(villager).disguiseSpecies()
				: null;
	}
}
