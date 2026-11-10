package craftingismagic.cim.client.villager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.minelittlepony.api.model.Models;
import com.minelittlepony.api.pony.Pony;
import com.minelittlepony.api.pony.PonyData;
import com.minelittlepony.api.pony.meta.Gender;
import com.minelittlepony.api.pony.meta.Race;
import com.minelittlepony.api.pony.meta.SizePreset;
import com.minelittlepony.api.pony.meta.TailLength;
import com.minelittlepony.api.pony.meta.TailShape;
import com.minelittlepony.api.pony.meta.Wearable;
import com.minelittlepony.client.model.ClientPonyModel;
import com.minelittlepony.client.model.ModelType;
import com.minelittlepony.client.render.entity.PonyRenderer;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.ChangelingDisguiseAttachment;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PonyBodyType;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyGeneticsAttachment;
import craftingismagic.cim.villager.PonyTailLength;
import craftingismagic.cim.villager.PonyTailShape;

/**
 * Renders species-tagged villagers with the matching {@link MineLpVillagerSkins#SKIN_BY_SPECIES}
 * texture and a race-correct body (horn, wings, etc.), built on MineLP's public {@code PonyRenderer}
 * base rather than its inaccessible package-private NPC renderer. {@code AbstractPonyRenderer}'s own
 * {@code getEntityPony} (MineLP's code) reads race straight from whatever texture
 * {@link #resolveTexture} returns for a given entity - but since we already know the species
 * directly, the body model is picked from our own {@link #RACE_BY_SPECIES} mapping rather than
 * round-tripping back through the texture's trigger pixels. See the villager-skins plan for the
 * full trace of how this was found.
 * <p>
 * {@code MODELS_BY_RACE} caches the actual model instance per race: {@code modelsLookup} is
 * consulted every frame for every visible villager (via {@code EquineRenderManager#preRender}), and
 * {@code PlayerModelKey#create} rebuilds Mson's entire JSON model tree from scratch on every call -
 * without caching, a village of any real size pegs the render thread doing that rebuild dozens of
 * times a frame, which reads as a full game freeze rather than a slowdown. Sharing one model
 * instance per race across every villager of that race and across frames is also just the normal
 * vanilla pattern (entity renderers hold one model instance per type, mutated and rendered per
 * entity in sequence) - this isn't a workaround, it's the approach {@code AbstractNpcRenderer}
 * itself would have used if it were public.
 */
public class CimVillagerPonyRenderer extends PonyRenderer<VillagerEntity, ClientPonyModel<VillagerEntity>> {
	private static final Map<Species, Race> RACE_BY_SPECIES = Map.of(
			Species.EARTH_PONY, Race.EARTH,
			Species.UNICORN, Race.UNICORN,
			Species.PEGASUS, Race.PEGASUS,
			Species.BAT_PONY, Race.BATPONY,
			Species.CHANGELING, Race.CHANGELING,
			Species.KIRIN, Race.KIRIN);

	private static final Map<Race, Models<VillagerEntity, ClientPonyModel<VillagerEntity>>> MODELS_BY_RACE = new EnumMap<>(Race.class);

	public CimVillagerPonyRenderer(EntityRendererFactory.Context context) {
		super(context, ModelType.<VillagerEntity, ClientPonyModel<VillagerEntity>>getPlayerModel(Race.EARTH).getKey(false),
				CimVillagerPonyRenderer::resolveTexture);
		this.manager.setModelsLookup(CimVillagerPonyRenderer::resolveModels);
	}

	private static Models<VillagerEntity, ClientPonyModel<VillagerEntity>> resolveModels(VillagerEntity villager) {
		Race race = RACE_BY_SPECIES.getOrDefault(speciesFor(villager), Race.EARTH);
		return MODELS_BY_RACE.computeIfAbsent(race,
				r -> ModelType.<VillagerEntity, ClientPonyModel<VillagerEntity>>getPlayerModel(r).create(false));
	}

	private static Identifier resolveTexture(VillagerEntity villager) {
		if (isTrueFormChangeling(villager)) {
			return ChangelingTrueFormSkins.resolveTexture(villager);
		}
		if (PonyGeneticsAttachment.hasGenetics(villager)) {
			return PonyGeneticsSkins.resolveTexture(villager);
		}
		// Brief pre-assignment window only (genetics is assigned on ENTITY_LOAD, same as species) -
		// cheap insurance, not a real second code path to maintain long-term.
		return MineLpVillagerSkins.SKIN_BY_SPECIES.get(speciesFor(villager));
	}

	/**
	 * Returns the DISGUISE species while a Changeling's disguise is active, the real species
	 * otherwise (including a Changeling in its true form, or any non-Changeling) - the one funnel
	 * point every render decision (model race, texture) goes through, so a disguise only ever needs
	 * changing here. See {@link ChangelingDisguiseAttachment} - it's a purely additive layer on top
	 * of {@link SpeciesAttachment}, which stays the real species regardless of disguise state.
	 */
	private static Species speciesFor(VillagerEntity villager) {
		Species trueSpecies = SpeciesAttachment.hasChosenSpecies(villager) ? SpeciesAttachment.getSpecies(villager) : Species.EARTH_PONY;
		if (trueSpecies == Species.CHANGELING && ChangelingDisguiseAttachment.hasDisguise(villager)) {
			ChangelingDisguiseAttachment.ChangelingDisguise disguise = ChangelingDisguiseAttachment.getDisguise(villager);
			if (disguise.disguised()) {
				return disguise.disguiseSpecies();
			}
		}
		return trueSpecies;
	}

	private static boolean isTrueFormChangeling(VillagerEntity villager) {
		if (!SpeciesAttachment.hasChosenSpecies(villager) || SpeciesAttachment.getSpecies(villager) != Species.CHANGELING) {
			return false;
		}
		return ChangelingDisguiseAttachment.hasDisguise(villager) && !ChangelingDisguiseAttachment.getDisguise(villager).disguised();
	}

	/**
	 * Overrides MineLP's own trigger-pixel-based {@code PonyData} parsing entirely: our genetics
	 * texture (see {@link PonyGeneticsSkins}) has no trigger-pixel encoding at all, so without this
	 * override MineLP would misdetect race/size from a texture that was never meant to carry that
	 * data that way. Race still comes from {@link #RACE_BY_SPECIES} (unchanged); body type comes from
	 * {@link PonyGeneticsAttachment}'s body-type gene, mapped to MineLP's {@code SizePreset}.
	 */
	@Override
	public Pony getEntityPony(VillagerEntity villager) {
		Identifier texture = resolveTexture(villager);
		PonyData data = buildPonyData(villager);
		return new Pony(texture, () -> Optional.of(data));
	}

	private static PonyData buildPonyData(VillagerEntity villager) {
		Race race = RACE_BY_SPECIES.getOrDefault(speciesFor(villager), Race.EARTH);
		SizePreset size = resolveSize(villager);
		TailLength tailLength = PonyGeneticsAttachment.hasGenetics(villager)
				? mapTailLength(PonyGeneticsAttachment.getGenetics(villager).tailLength())
				: TailLength.FULL;
		TailShape tailShape = PonyGeneticsAttachment.hasGenetics(villager)
				? mapTailShape(PonyGeneticsAttachment.getGenetics(villager).tailShape())
				: TailShape.STRAIGHT;
		return new PonyData(race, tailLength, tailShape, stableGenderFor(villager), size,
				PonyData.DEFAULT_MAGIC_COLOR, false, 0, Wearable.EMPTY_FLAGS);
	}

	/**
	 * Baby/Child use MineLP's Foal preset, Teenager uses Yearling (Baby gets an additional scale-down
	 * on top via {@link #setupTransforms}, since Foal alone can't tell Baby and Child apart) - only
	 * Adult reads the genetics body-type roll at all.
	 */
	private static SizePreset resolveSize(VillagerEntity villager) {
		if (PonyFamilyAttachment.hasFamily(villager)) {
			LifeStage stage = PonyFamilyAttachment.getFamily(villager).lifeStage();
			if (stage == LifeStage.BABY || stage == LifeStage.CHILD) {
				return SizePreset.FOAL;
			}
			if (stage == LifeStage.TEENAGER) {
				return SizePreset.YEARLING;
			}
		}
		return PonyGeneticsAttachment.hasGenetics(villager)
				? mapBodyType(PonyGeneticsAttachment.getGenetics(villager).bodyType())
				: SizePreset.NORMAL;
	}

	private static SizePreset mapBodyType(PonyBodyType bodyType) {
		return switch (bodyType) {
			case BULKY -> SizePreset.BULKY;
			case NORMAL -> SizePreset.NORMAL;
			case LANKY -> SizePreset.LANKY;
			case STOCKY -> SizePreset.STOCKY;
			case SQUAT -> SizePreset.SQUAT;
		};
	}

	private static TailLength mapTailLength(PonyTailLength tailLength) {
		return switch (tailLength) {
			case STUB -> TailLength.STUB;
			case QUARTER -> TailLength.QUARTER;
			case HALF -> TailLength.HALF;
			case THREE_QUARTERS -> TailLength.THREE_QUARTERS;
			case FULL -> TailLength.FULL;
		};
	}

	private static TailShape mapTailShape(PonyTailShape tailShape) {
		return switch (tailShape) {
			case STRAIGHT -> TailShape.STRAIGHT;
			case BUMPY -> TailShape.BUMPY;
			case SWIRLY -> TailShape.SWIRLY;
			case SPIKY -> TailShape.SPIKY;
		};
	}

	/**
	 * No gender system exists anywhere in this mod yet (see the design doc's Gender identity thread) -
	 * this is purely a technical stand-in since {@code PonyData} requires a non-null {@code Gender}
	 * and MineLP's body model shape/animation reads it, not a real trait: stable per-villager (so a
	 * given villager doesn't visibly flip every frame) but not persisted, synced, or otherwise
	 * exposed. Revisit once a real gender trait exists.
	 */
	private static Gender stableGenderFor(VillagerEntity villager) {
		return (villager.getUuid().hashCode() & 1) == 0 ? Gender.MARE : Gender.STALLION;
	}

	/** Baby and Child share MineLP's Foal preset (see {@link #resolveSize}) and need to read as
	 * visually distinct anyway (design doc: "Baby... scaled down further than Child") - this extra
	 * scale is what does that, layered on top of whatever Foal's own transform already applies. */
	private static final float BABY_EXTRA_SCALE = 0.65f;

	@Override
	protected void setupTransforms(VillagerEntity villager, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
		super.setupTransforms(villager, matrices, animationProgress, bodyYaw, tickDelta, scale);
		if (PonyFamilyAttachment.hasFamily(villager) && PonyFamilyAttachment.getFamily(villager).lifeStage() == LifeStage.BABY) {
			matrices.scale(BABY_EXTRA_SCALE, BABY_EXTRA_SCALE, BABY_EXTRA_SCALE);
		}
	}
}
