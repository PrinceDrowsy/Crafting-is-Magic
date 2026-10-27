package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;

/**
 * Assigns each freshly-loaded villager a real {@link LifeStage} and, unless it's a Changeling (see
 * Village Changelings in the README - excluded from families, the hive fills that role instead), a
 * place in a real family: either it joins an existing nearby one as a child (inheriting genetics via
 * {@link PonyGenetics#combine}) or it becomes an unattached Adult, optionally pairing up with
 * another nearby unpaired Adult as a co-parent for the next child that comes along.
 * <p>
 * There is no "this batch of villagers belongs to the same village" signal anywhere in vanilla or
 * Fabric API - villages generate
 * piece-by-piece as chunks generate, not as one event. Every decision here is made per-villager, at
 * the exact moment its own {@code ENTITY_LOAD} fires, by querying whichever other villagers already
 * happen to be loaded nearby - there is no batching or deferral.
 * <p>
 * Genetics assignment lives here (not {@link VillagerSpeciesHandler}) because which method
 * generates it - {@link PonyGenetics#randomize} or {@link PonyGenetics#combine} - depends entirely
 * on the family outcome decided in this same pass.
 */
public final class VillagerFamilyHandler {
	/** Most villagers roll straight to Adult - this is the adult-majority bias. */
	private static final float CHILD_ROLE_CHANCE = 0.25f;
	/** Independent of the child-role roll - a pony with no family of its own, whether or not it
	 * goes on to become a parent to someone else later. */
	private static final float ORPHAN_CHANCE = 0.10f;
	private static final double FAMILY_SEARCH_RADIUS = 28.0;
	private static final int MAX_CHILDREN_PER_PARENT = 2;
	/** Growth doesn't need fine precision (stages last many minutes) - checked every 5 seconds. Each
	 * check rewrites the growing villager's family attachment, which syncs to trackers, so a longer
	 * interval also means fewer packets. */
	private static final int GROWTH_CHECK_INTERVAL = 100;

	private static final LifeStage[] CHILD_LIFE_STAGES = {LifeStage.BABY, LifeStage.CHILD, LifeStage.TEENAGER};

	/**
	 * Only the villagers currently in a growing stage (Child or Teenager) - so the growth tick never
	 * has to walk every entity in every world. Kept current by {@code ENTITY_LOAD}/{@code
	 * ENTITY_UNLOAD} and by {@link #onFamilyChanged}, which {@link PonyFamilyAttachment#setFamily}
	 * calls on every family write. Entities don't override {@code equals}, so this is an identity set.
	 */
	private static final Set<VillagerEntity> growingVillagers = new HashSet<>();

	private VillagerFamilyHandler() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof VillagerEntity villager)) {
				return;
			}
			if (!PonyFamilyAttachment.hasFamily(villager)) {
				assignFamily(villager, world);
			} else if (!PersonalityAttachment.hasPersonality(villager)) {
				// A freshly-bred baby has a family but no personality yet (assignBredChild ran before
				// its species existed) - as does a villager saved before Personality existed at all,
				// which is why the breeding-age reset below is gated on BABY rather than applied to
				// everyone here. That reset exists because vanilla's breed task overwrites the baby's
				// age with its own fixed -24000 right after createChild, so this is the first point
				// the configured Baby duration can actually stick.
				Species species = SpeciesAttachment.hasChosenSpecies(villager) ? SpeciesAttachment.getSpecies(villager) : Species.EARTH_PONY;
				assignPersonality(villager, species, villager.getRandom());
				if (PonyFamilyAttachment.getFamily(villager).lifeStage() == LifeStage.BABY && villager.isBaby()) {
					villager.setBreedingAge(-LifeStageConfig.get().babyDurationTicks());
				}
			}
			onFamilyChanged(villager);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity villager) {
				growingVillagers.remove(villager);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(VillagerFamilyHandler::tickGrowth);
	}

	/** How many villagers the growth tick is currently tracking - shown in the debug overlay. */
	public static int growingCount() {
		return growingVillagers.size();
	}

	/**
	 * Moves a villager straight to its next life stage - the debug {@code grow} command's lever, so
	 * growth can be tested without waiting out a stage. A Baby is grown through vanilla's own path
	 * (breeding age to 0, which fires {@code onGrowUp} and lets {@code VillagerGrowthMixin} make it a
	 * Child); Child and Teenager advance directly. Returns the new stage, or null if there's nowhere
	 * left to grow (an Adult, or a villager with no family yet).
	 */
	public static LifeStage advanceLifeStage(VillagerEntity villager) {
		if (!PonyFamilyAttachment.hasFamily(villager)) {
			return null;
		}
		PonyFamily family = PonyFamilyAttachment.getFamily(villager);
		switch (family.lifeStage()) {
			case BABY -> villager.setBreedingAge(0);
			case CHILD -> PonyFamilyAttachment.setFamily(villager,
					new PonyFamily(LifeStage.TEENAGER, 0, family.parents(), family.partner(), family.orphan()));
			case TEENAGER -> PonyFamilyAttachment.setFamily(villager,
					new PonyFamily(LifeStage.ADULT, 0, family.parents(), family.partner(), family.orphan()));
			default -> {
				return null;
			}
		}
		return PonyFamilyAttachment.getFamily(villager).lifeStage();
	}

	/** Keeps {@link #growingVillagers} in step with a villager's current life stage - called from
	 * {@link PonyFamilyAttachment#setFamily} so every family write, wherever it happens, is seen. */
	static void onFamilyChanged(Entity entity) {
		if (!(entity instanceof VillagerEntity villager) || villager.getWorld().isClient()) {
			return;
		}
		LifeStage stage = PonyFamilyAttachment.hasFamily(villager) ? PonyFamilyAttachment.getFamily(villager).lifeStage() : null;
		if (stage == LifeStage.CHILD || stage == LifeStage.TEENAGER) {
			growingVillagers.add(villager);
		} else {
			growingVillagers.remove(villager);
		}
	}

	private static void assignFamily(VillagerEntity villager, ServerWorld world) {
		Random random = villager.getRandom();
		Species species = SpeciesAttachment.hasChosenSpecies(villager) ? SpeciesAttachment.getSpecies(villager) : Species.EARTH_PONY;
		assignPersonality(villager, species, random);

		if (species == Species.CHANGELING) {
			assignChangeling(villager, random);
			return;
		}

		if (random.nextFloat() < ORPHAN_CHANCE) {
			assignAdult(villager, world, random, true);
			return;
		}

		if (random.nextFloat() < CHILD_ROLE_CHANCE && tryJoinFamily(villager, world, random)) {
			return;
		}

		assignAdult(villager, world, random, false);
	}

	/**
	 * Species/profession-weighted per the README's Personality assignment-bias rules, except
	 * Changelings (fully uniform - their outward persona is a performed mask, not innate). Profession
	 * is usually still {@code NONE} at this exact moment ({@code ENTITY_LOAD}, before vanilla assigns
	 * a job site) - the profession half of the bias is therefore best-effort only, a known limitation
	 * rather than something this pass chases down further.
	 */
	private static void assignPersonality(VillagerEntity villager, Species species, Random random) {
		PersonalityAttachment.Personality personality = species == Species.CHANGELING
				? PersonalityAttachment.randomize(random)
				: PersonalityAttachment.randomizeBiased(random, species, villager.getVillagerData().getProfession());
		PersonalityAttachment.setPersonality(villager, personality);
	}

	/** Changelings still get life-stage variety, just never attach to a family - their persona is a
	 * performed mask, and the hive fills the "family" role thematically instead (see README). */
	private static void assignChangeling(VillagerEntity villager, Random random) {
		LifeStage lifeStage = random.nextFloat() < CHILD_ROLE_CHANCE ? randomChildLifeStage(random) : LifeStage.ADULT;
		setGenetics(villager, PonyGenetics.randomize(random), random);
		PonyFamilyAttachment.setFamily(villager, new PonyFamily(lifeStage, 0, List.of(), List.of(), false));
	}

	private static boolean tryJoinFamily(VillagerEntity villager, ServerWorld world, Random random) {
		VillagerEntity parent = findOpenParent(villager, world);
		if (parent == null) {
			return false;
		}

		List<UUID> parentUuids = new ArrayList<>();
		parentUuids.add(parent.getUuid());
		PonyGenetics parentGenetics = PonyGeneticsAttachment.getGenetics(parent);
		PonyGenetics secondParentGenetics = parentGenetics;

		PonyFamily parentFamily = PonyFamilyAttachment.getFamily(parent);
		if (!parentFamily.partner().isEmpty()) {
			UUID partnerUuid = parentFamily.partner().get(0);
			if (world.getEntity(partnerUuid) instanceof VillagerEntity partner && PonyGeneticsAttachment.hasGenetics(partner)) {
				parentUuids.add(partnerUuid);
				secondParentGenetics = PonyGeneticsAttachment.getGenetics(partner);
			}
		}

		PonyGenetics combined = PonyGenetics.combine(List.of(parentGenetics, secondParentGenetics), random);
		setGenetics(villager, combined, random);

		LifeStage lifeStage = randomChildLifeStage(random);
		PonyFamilyAttachment.setFamily(villager, new PonyFamily(lifeStage, 0, parentUuids, List.of(), false));
		if (lifeStage == LifeStage.BABY) {
			villager.setBreedingAge(-LifeStageConfig.get().babyDurationTicks());
		}
		return true;
	}

	/**
	 * A baby born from vanilla villager breeding has two real, known parents - unlike a freshly
	 * generated villager, which only gets a family through {@link #assignFamily}'s proximity
	 * guesswork (and before this, a bred baby went through that same guesswork too: usually rolled
	 * straight to Adult despite being a vanilla baby, with a random nearby "parent"). Called from
	 * {@code VillagerBreedingMixin} before the baby spawns, so its own {@code ENTITY_LOAD} sees a
	 * family already present and only fills in personality. Species is still rolled independently
	 * by {@link VillagerSpeciesHandler} for now - how species passes down a bloodline is an open
	 * design question (README: Pony Genetics), deliberately not decided here.
	 */
	public static void assignBredChild(VillagerEntity child, VillagerEntity parent, PassiveEntity partner) {
		Random random = child.getRandom();
		List<UUID> parentUuids = new ArrayList<>();
		parentUuids.add(parent.getUuid());
		PonyGenetics parentGenetics = PonyGeneticsAttachment.hasGenetics(parent)
				? PonyGeneticsAttachment.getGenetics(parent) : PonyGenetics.randomize(random);
		PonyGenetics secondParentGenetics = parentGenetics;

		if (partner instanceof VillagerEntity otherParent) {
			parentUuids.add(otherParent.getUuid());
			if (PonyGeneticsAttachment.hasGenetics(otherParent)) {
				secondParentGenetics = PonyGeneticsAttachment.getGenetics(otherParent);
			}
		}

		PonyGeneticsAttachment.setGenetics(child, PonyGenetics.combine(List.of(parentGenetics, secondParentGenetics), random));
		PonyFamilyAttachment.setFamily(child, new PonyFamily(LifeStage.BABY, 0, parentUuids, List.of(), false));
	}

	private static void assignAdult(VillagerEntity villager, ServerWorld world, Random random, boolean orphan) {
		setGenetics(villager, PonyGenetics.randomize(random), random);

		VillagerEntity partner = orphan ? null : findOpenPartner(villager, world);
		List<UUID> partnerList = partner != null ? List.of(partner.getUuid()) : List.of();
		PonyFamilyAttachment.setFamily(villager, new PonyFamily(LifeStage.ADULT, 0, List.of(), partnerList, orphan));

		if (partner != null) {
			PonyFamily partnerFamily = PonyFamilyAttachment.getFamily(partner);
			PonyFamilyAttachment.setFamily(partner, new PonyFamily(partnerFamily.lifeStage(), partnerFamily.growthTicks(),
					partnerFamily.parents(), List.of(villager.getUuid()), partnerFamily.orphan()));
		}
	}

	private static VillagerEntity findOpenParent(VillagerEntity self, ServerWorld world) {
		Box searchBox = new Box(self.getBlockPos()).expand(FAMILY_SEARCH_RADIUS);
		List<VillagerEntity> nearbyAdults = world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox,
				other -> other != self && PonyFamilyAttachment.hasFamily(other)
						&& PonyFamilyAttachment.getFamily(other).lifeStage() == LifeStage.ADULT);

		VillagerEntity singleFallback = null;
		for (VillagerEntity candidate : nearbyAdults) {
			if (countChildren(candidate, world, searchBox) >= MAX_CHILDREN_PER_PARENT) {
				continue;
			}
			if (!PonyFamilyAttachment.getFamily(candidate).partner().isEmpty()) {
				return candidate;
			}
			if (singleFallback == null) {
				singleFallback = candidate;
			}
		}
		return singleFallback;
	}

	private static VillagerEntity findOpenPartner(VillagerEntity self, ServerWorld world) {
		Box searchBox = new Box(self.getBlockPos()).expand(FAMILY_SEARCH_RADIUS);
		List<VillagerEntity> candidates = world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox,
				other -> other != self && PonyFamilyAttachment.hasFamily(other)
						&& PonyFamilyAttachment.getFamily(other).lifeStage() == LifeStage.ADULT
						&& PonyFamilyAttachment.getFamily(other).partner().isEmpty()
						&& !PonyKinship.areRelated(world, self, other));
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	private static int countChildren(VillagerEntity parent, ServerWorld world, Box searchBox) {
		UUID parentUuid = parent.getUuid();
		return world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox,
				other -> PonyFamilyAttachment.hasFamily(other) && PonyFamilyAttachment.getFamily(other).parents().contains(parentUuid)
		).size();
	}

	private static LifeStage randomChildLifeStage(Random random) {
		return CHILD_LIFE_STAGES[random.nextInt(CHILD_LIFE_STAGES.length)];
	}

	private static void setGenetics(VillagerEntity villager, PonyGenetics genetics, Random random) {
		genetics = PonyGenetics.applyNameColorBias(genetics, random, villager.getName().getString());
		PonyGeneticsAttachment.setGenetics(villager, genetics);
	}

	private static void tickGrowth(MinecraftServer server) {
		if (server.getTicks() % GROWTH_CHECK_INTERVAL != 0) {
			return;
		}
		LifeStageConfig config = LifeStageConfig.get();
		// Copied first: setFamily below calls back into onFamilyChanged, which may remove a villager
		// that just reached Adult from the live set mid-iteration.
		for (VillagerEntity villager : List.copyOf(growingVillagers)) {
			if (villager.isRemoved() || !PonyFamilyAttachment.hasFamily(villager)) {
				growingVillagers.remove(villager);
				continue;
			}
			PonyFamily family = PonyFamilyAttachment.getFamily(villager);
			if (family.lifeStage() != LifeStage.CHILD && family.lifeStage() != LifeStage.TEENAGER) {
				growingVillagers.remove(villager);
				continue;
			}

			int growthTicks = family.growthTicks() + GROWTH_CHECK_INTERVAL;
			int threshold = family.lifeStage() == LifeStage.CHILD ? config.childDurationTicks() : config.teenagerDurationTicks();
			if (growthTicks >= threshold) {
				LifeStage next = family.lifeStage() == LifeStage.CHILD ? LifeStage.TEENAGER : LifeStage.ADULT;
				PonyFamilyAttachment.setFamily(villager, new PonyFamily(next, 0, family.parents(), family.partner(), family.orphan()));
			} else {
				PonyFamilyAttachment.setFamily(villager,
						new PonyFamily(family.lifeStage(), growthTicks, family.parents(), family.partner(), family.orphan()));
			}
		}
	}
}
