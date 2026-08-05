package craftingismagic.cim.client.villager;

import java.util.EnumMap;
import java.util.Map;

import com.minelittlepony.api.model.Models;
import com.minelittlepony.api.pony.meta.Race;
import com.minelittlepony.client.model.ClientPonyModel;
import com.minelittlepony.client.model.ModelType;
import com.minelittlepony.client.render.entity.PonyRenderer;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

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
		return MineLpVillagerSkins.SKIN_BY_SPECIES.get(speciesFor(villager));
	}

	private static Species speciesFor(VillagerEntity villager) {
		return SpeciesAttachment.hasChosenSpecies(villager) ? SpeciesAttachment.getSpecies(villager) : Species.EARTH_PONY;
	}
}
