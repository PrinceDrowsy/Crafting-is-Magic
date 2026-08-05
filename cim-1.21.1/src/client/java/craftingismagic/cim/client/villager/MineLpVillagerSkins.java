package craftingismagic.cim.client.villager;

import java.util.Map;

import com.minelittlepony.mson.api.Mson;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;

/**
 * Registers {@link CimVillagerPonyRenderer} for {@code EntityType.VILLAGER} directly through Mson's
 * {@code EntityRendererRegistry} - only ever loaded/registered when MineLP is actually installed
 * (see {@code CimClient}'s {@code FabricLoader.isModLoaded} guard).
 * <p>
 * Mson's registry keys renderers by a plain {@code EntityType -> factory} map (no predicate/priority
 * system in this MineLP version), applied to the real {@code EntityRenderDispatcher} the moment a
 * registration call arrives *if* the dispatcher has already published itself, or queued and applied
 * later otherwise. MineLP registers its own default villager renderer once, on the client's first
 * tick (see {@code MineLittlePony.onTick}, guarded by a one-shot flag) - not at mod-init time. A
 * single registration attempt at mod-init time, or even on our own first tick, consistently lost
 * that race (confirmed via logging: our factory was simply never invoked) because the dispatcher
 * doesn't finish publishing until partway through the first several dozen client ticks in this dev
 * environment (empirically tick ~39, not tick 1) - well after MineLP's own single attempt. Retrying
 * every tick for a few seconds sidesteps needing to know that exact window: whichever attempt lands
 * after the dispatcher is ready sticks, and since this keeps re-registering afterward too, our call
 * is always the last write for {@code EntityType.VILLAGER}, guaranteed to beat MineLP's one-shot
 * registration regardless of relative timing. See the villager-skins plan for the full trace.
 * <p>
 * Five of the six skins are named show characters pulled from MineLP's official
 * <a href="https://github.com/MineLittlePony/Community-Skin-Pack">Community Skin Pack</a>
 * (free-to-use/free-to-distribute fan content, per that repo's own credits file), bundled under
 * this mod's own {@code cim} namespace - real trigger-pixel-encoded race data, so Unicorn/Pegasus
 * villagers actually render with a horn/wings, not just a different color. Kirin has no matching
 * skin anywhere in that pack (not even the show's own Autumn Blaze), so it falls back to one of
 * MineLP's bundled default player skins - same wingless/hornless earth-pony body shape as every
 * species got before this class existed, just for that one case.
 */
public final class MineLpVillagerSkins {
	/** A few seconds' worth of ticks - comfortably past the observed ~39-tick dispatcher-ready point. */
	private static final int RETRY_TICKS = 100;

	static final Map<Species, Identifier> SKIN_BY_SPECIES = Map.of(
			Species.EARTH_PONY, cimSkin("applejack"),
			Species.UNICORN, cimSkin("twilight_sparkle"),
			Species.PEGASUS, cimSkin("rainbow_dash"),
			Species.BAT_PONY, cimSkin("echo"),
			Species.CHANGELING, cimSkin("changeling"),
			Species.KIRIN, minelpDefaultSkin("makena_pony"));

	private MineLpVillagerSkins() {
	}

	public static void register() {
		int[] ticksRemaining = {RETRY_TICKS};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticksRemaining[0]-- <= 0) {
				return;
			}
			Mson.getInstance().getEntityRendererRegistry().registerEntityRenderer(EntityType.VILLAGER, CimVillagerPonyRenderer::new);
		});
	}

	private static Identifier cimSkin(String name) {
		return Identifier.of(Cim.MOD_ID, "textures/entity/pony/" + name + ".png");
	}

	private static Identifier minelpDefaultSkin(String name) {
		return Identifier.of("minelittlepony", "textures/entity/player/wide/" + name + ".png");
	}
}
