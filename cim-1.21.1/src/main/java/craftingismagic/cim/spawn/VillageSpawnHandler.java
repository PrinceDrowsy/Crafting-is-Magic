package craftingismagic.cim.spawn;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import craftingismagic.cim.Cim;

/**
 * Relocates a fresh world's spawn point into the nearest village (README-requested default, with
 * {@link VillageSpawnConfig#spawnInVillage} to turn it off).
 * <p>
 * Fires on {@code ServerLifecycleEvents.SERVER_STARTED}, not {@code ServerWorldEvents.LOAD} - a
 * first attempt used {@code LOAD} (it runs early during startup, so it looked safely "before any
 * player can join"), but that missed the real ordering problem: for a genuinely brand-new world,
 * vanilla's own {@code MinecraftServer.setupSpawn(...)} - its biome-search-based default spawn
 * placement - fires from inside the same {@code createWorlds()} call {@code LOAD} does, and runs
 * AFTER it. So the relocation was landing first and then getting silently overwritten by vanilla's
 * own default a moment later - invisible in testing on an *existing* world (which skips
 * {@code setupSpawn} entirely, since it already has a saved spawn point), which is exactly why it
 * looked like it worked before shipping. {@code SERVER_STARTED} fires once the entire startup
 * sequence - all dimensions, all of vanilla's own spawn setup - has finished, so there's nothing
 * left to overwrite it afterward.
 * <p>
 * Only the overworld is searched, and only once ever per world (see {@link VillageSpawnState}) -
 * {@code ServerWorld#locateStructure} does real chunk generation as needed to search, which is a
 * one-time cost worth paying at world creation, not something to repeat on every later server
 * start. If no village turns up within the search radius, the flag is still set so this doesn't
 * retry (and re-pay that cost) forever on a village-less seed - the vanilla spawn point is kept.
 */
public final class VillageSpawnHandler {
	/** Matches vanilla's own typical village search radius (in chunks) - villages are common
	 * enough not to need anything larger. */
	private static final int SEARCH_RADIUS_CHUNKS = 100;

	private VillageSpawnHandler() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!VillageSpawnConfig.get().spawnInVillage) {
				return;
			}
			ServerWorld world = server.getOverworld();

			VillageSpawnState state = world.getPersistentStateManager().getOrCreate(VillageSpawnState.TYPE, "cim_village_spawn");
			if (state.isRelocated()) {
				return;
			}
			state.markRelocated();

			BlockPos villagePos = world.locateStructure(StructureTags.VILLAGE, world.getSpawnPos(), SEARCH_RADIUS_CHUNKS, false);
			if (villagePos != null) {
				world.setSpawnPos(villagePos, 0f);
				Cim.LOGGER.info("Relocated world spawn to a village at {}", villagePos);
			} else {
				Cim.LOGGER.info("No village found within {} chunks of spawn - keeping the vanilla spawn point", SEARCH_RADIUS_CHUNKS);
			}
		});
	}
}
