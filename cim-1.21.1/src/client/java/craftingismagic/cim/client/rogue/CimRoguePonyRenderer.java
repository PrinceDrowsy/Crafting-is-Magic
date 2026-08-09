package craftingismagic.cim.client.rogue;

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
import com.minelittlepony.mson.api.Mson;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

import craftingismagic.cim.client.villager.MineLpVillagerSkins;
import craftingismagic.cim.client.villager.PonyGeneticsSkins;
import craftingismagic.cim.entity.CimEntities;
import craftingismagic.cim.rogue.RoguePonyEntity;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.PonyBodyType;
import craftingismagic.cim.villager.PonyGeneticsAttachment;
import craftingismagic.cim.villager.PonyTailLength;
import craftingismagic.cim.villager.PonyTailShape;

/**
 * Renders {@link RoguePonyEntity} the same genetics-driven way {@code CimVillagerPonyRenderer}
 * renders villagers - mirrors that class closely (same {@code Species}-to-{@code Race} mapping,
 * same {@code PonyGeneticsSkins}-backed texture, same body-type-to-{@code SizePreset} mapping),
 * confirmed via this session's own research that MineLP's rendering pipeline
 * ({@code PonyRenderer<T extends MobEntity, ...>}) isn't villager-specific. Deliberately simpler
 * than the villager renderer in one way: no {@code PonyFamilyAttachment}/life-stage handling at
 * all - a Rogue Pony has no life stage concept and is always treated as a body-type-mapped adult.
 */
public class CimRoguePonyRenderer extends PonyRenderer<RoguePonyEntity, ClientPonyModel<RoguePonyEntity>> {
	private static final Map<Species, Race> RACE_BY_SPECIES = Map.of(
			Species.EARTH_PONY, Race.EARTH,
			Species.UNICORN, Race.UNICORN,
			Species.PEGASUS, Race.PEGASUS,
			Species.BAT_PONY, Race.BATPONY,
			Species.CHANGELING, Race.CHANGELING,
			Species.KIRIN, Race.KIRIN);

	private static final Map<Race, Models<RoguePonyEntity, ClientPonyModel<RoguePonyEntity>>> MODELS_BY_RACE = new EnumMap<>(Race.class);

	/** Same retry-tick dance as {@code MineLpVillagerSkins#register} (see its doc comment for the
	 * full trace of why a single mod-init-time registration attempt loses the race against
	 * MineLP's own dispatcher-readiness timing) - applied to {@link CimEntities#ROGUE_PONY} instead
	 * of {@code EntityType.VILLAGER}. */
	public static void register() {
		int[] ticksRemaining = {100};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticksRemaining[0]-- <= 0) {
				return;
			}
			Mson.getInstance().getEntityRendererRegistry().registerEntityRenderer(CimEntities.ROGUE_PONY, CimRoguePonyRenderer::new);
		});
	}

	public CimRoguePonyRenderer(EntityRendererFactory.Context context) {
		super(context, ModelType.<RoguePonyEntity, ClientPonyModel<RoguePonyEntity>>getPlayerModel(Race.EARTH).getKey(false),
				CimRoguePonyRenderer::resolveTexture);
		this.manager.setModelsLookup(CimRoguePonyRenderer::resolveModels);
	}

	private static Models<RoguePonyEntity, ClientPonyModel<RoguePonyEntity>> resolveModels(RoguePonyEntity pony) {
		Race race = RACE_BY_SPECIES.getOrDefault(speciesFor(pony), Race.EARTH);
		return MODELS_BY_RACE.computeIfAbsent(race,
				r -> ModelType.<RoguePonyEntity, ClientPonyModel<RoguePonyEntity>>getPlayerModel(r).create(false));
	}

	private static Identifier resolveTexture(RoguePonyEntity pony) {
		if (PonyGeneticsAttachment.hasGenetics(pony)) {
			return PonyGeneticsSkins.resolveTexture(pony);
		}
		// Brief pre-assignment window only (genetics is rolled in RoguePonyEntity#initialize) -
		// cheap insurance, not a real second code path to maintain long-term.
		return MineLpVillagerSkins.SKIN_BY_SPECIES.get(speciesFor(pony));
	}

	private static Species speciesFor(RoguePonyEntity pony) {
		return SpeciesAttachment.hasChosenSpecies(pony) ? SpeciesAttachment.getSpecies(pony) : Species.EARTH_PONY;
	}

	@Override
	public Pony getEntityPony(RoguePonyEntity pony) {
		Identifier texture = resolveTexture(pony);
		PonyData data = buildPonyData(pony);
		return new Pony(texture, () -> Optional.of(data));
	}

	private static PonyData buildPonyData(RoguePonyEntity pony) {
		Race race = RACE_BY_SPECIES.getOrDefault(speciesFor(pony), Race.EARTH);
		SizePreset size = PonyGeneticsAttachment.hasGenetics(pony)
				? mapBodyType(PonyGeneticsAttachment.getGenetics(pony).bodyType())
				: SizePreset.NORMAL;
		TailLength tailLength = PonyGeneticsAttachment.hasGenetics(pony)
				? mapTailLength(PonyGeneticsAttachment.getGenetics(pony).tailLength())
				: TailLength.FULL;
		TailShape tailShape = PonyGeneticsAttachment.hasGenetics(pony)
				? mapTailShape(PonyGeneticsAttachment.getGenetics(pony).tailShape())
				: TailShape.STRAIGHT;
		return new PonyData(race, tailLength, tailShape, stableGenderFor(pony), size,
				PonyData.DEFAULT_MAGIC_COLOR, false, 0, Wearable.EMPTY_FLAGS);
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

	/** Same technical stand-in as {@code CimVillagerPonyRenderer} - no gender system exists yet. */
	private static Gender stableGenderFor(RoguePonyEntity pony) {
		return (pony.getUuid().hashCode() & 1) == 0 ? Gender.MARE : Gender.STALLION;
	}
}
