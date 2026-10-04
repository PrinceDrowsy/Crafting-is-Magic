package craftingismagic.cim.client.changeling;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import craftingismagic.cim.changeling.ChangelingInfiltratorEntity;
import craftingismagic.cim.changeling.DisguiseTargetAttachment;
import craftingismagic.cim.client.villager.ChangelingTrueFormSkins;
import craftingismagic.cim.entity.CimEntities;

/**
 * Renders {@link ChangelingInfiltratorEntity}: while disguised, wears whatever the target player
 * actually looks like right now (see {@link ChangelingDisguiseApplier} for why that's a live
 * {@code Pony} lookup rather than a copied snapshot); once revealed, falls back to the same
 * true-form look Village Changelings use ({@link ChangelingTrueFormSkins}, Race.CHANGELING).
 * Mirrors {@code CimRoguePonyRenderer}'s own shape (same retry-tick Mson registration dance, same
 * per-race model cache).
 */
public class ChangelingInfiltratorRenderer extends PonyRenderer<ChangelingInfiltratorEntity, ClientPonyModel<ChangelingInfiltratorEntity>> {
	private static final Map<Race, Models<ChangelingInfiltratorEntity, ClientPonyModel<ChangelingInfiltratorEntity>>> MODELS_BY_RACE =
			new EnumMap<>(Race.class);

	public static void register() {
		int[] ticksRemaining = {100};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticksRemaining[0]-- <= 0) {
				return;
			}
			Mson.getInstance().getEntityRendererRegistry().registerEntityRenderer(CimEntities.CHANGELING_INFILTRATOR, ChangelingInfiltratorRenderer::new);
		});
	}

	public ChangelingInfiltratorRenderer(EntityRendererFactory.Context context) {
		super(context, ModelType.<ChangelingInfiltratorEntity, ClientPonyModel<ChangelingInfiltratorEntity>>getPlayerModel(Race.CHANGELING).getKey(false),
				ChangelingInfiltratorRenderer::resolveTexture);
		this.manager.setModelsLookup(ChangelingInfiltratorRenderer::resolveModels);
	}

	private static Optional<Pony> resolveDisguisePony(ChangelingInfiltratorEntity changeling) {
		if (!DisguiseTargetAttachment.isDisguised(changeling)) {
			return Optional.empty();
		}
		UUID targetUuid = DisguiseTargetAttachment.getDisguiseTarget(changeling);
		return ChangelingDisguiseApplier.resolveDisguise(targetUuid, changeling.getWorld());
	}

	private static Models<ChangelingInfiltratorEntity, ClientPonyModel<ChangelingInfiltratorEntity>> resolveModels(ChangelingInfiltratorEntity changeling) {
		Race race = resolveDisguisePony(changeling)
				.map(pony -> pony.hasMetadata() ? pony.metadata().race() : Race.CHANGELING)
				.orElse(Race.CHANGELING);
		return MODELS_BY_RACE.computeIfAbsent(race,
				r -> ModelType.<ChangelingInfiltratorEntity, ClientPonyModel<ChangelingInfiltratorEntity>>getPlayerModel(r).create(false));
	}

	private static Identifier resolveTexture(ChangelingInfiltratorEntity changeling) {
		return resolveDisguisePony(changeling)
				.map(Pony::texture)
				.orElseGet(() -> ChangelingTrueFormSkins.resolveTexture(changeling));
	}

	@Override
	public Pony getEntityPony(ChangelingInfiltratorEntity changeling) {
		Optional<Pony> disguise = resolveDisguisePony(changeling);
		if (disguise.isPresent()) {
			return disguise.get();
		}

		Identifier texture = ChangelingTrueFormSkins.resolveTexture(changeling);
		PonyData data = new PonyData(Race.CHANGELING, TailLength.FULL, TailShape.STRAIGHT, stableGenderFor(changeling),
				SizePreset.NORMAL, PonyData.DEFAULT_MAGIC_COLOR, false, 0, Wearable.EMPTY_FLAGS);
		return new Pony(texture, () -> Optional.of(data));
	}

	/** Same technical stand-in as {@code CimVillagerPonyRenderer}/{@code CimRoguePonyRenderer} - no gender system exists yet. */
	private static Gender stableGenderFor(ChangelingInfiltratorEntity changeling) {
		return (changeling.getUuid().hashCode() & 1) == 0 ? Gender.MARE : Gender.STALLION;
	}
}
