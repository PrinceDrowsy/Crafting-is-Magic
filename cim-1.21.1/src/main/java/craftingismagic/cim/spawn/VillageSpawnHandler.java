package craftingismagic.cim.spawn;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import craftingismagic.cim.Cim;
import craftingismagic.cim.structure.VillageBuildingPiece;

/**
 * Relocates a fresh world's spawn point into the nearest village (requested default, with
 * {@link VillageSpawnConfig#spawnInVillage} to turn it off), in two steps:
 * <ol>
 * <li>{@link #relocate}, at the end of vanilla's own {@code MinecraftServer.setupSpawn} (see {@code
 * VillageSpawnSetupMixin}) - after vanilla has picked its default (so nothing overwrites ours, the
 * reason an earlier {@code ServerWorldEvents.LOAD} version failed) but before vanilla prepares the
 * spawn area, so the world-creation progress screen generates the area round the village itself.</li>
 * <li>{@link #faceTheDoors}, on {@code SERVER_STARTED}, once that area exists: moves spawn out onto the
 * plaza in front of the centerpiece. It only reads chunks already generated - an earlier version
 * generated the plaza's chunk itself at that point, which stalled a new world at "100%" for a minute
 * or more with the server thread blocked.</li>
 * </ol>
 * Only the overworld is searched, and only once ever per world (see {@link VillageSpawnState});
 * {@code ServerWorld#locateStructure} is a one-time cost at world creation. If no village turns up,
 * the flag is still set, so a village-less seed doesn't re-search forever.
 */
public final class VillageSpawnHandler {
	/** Matches vanilla's own typical village search radius (in chunks) - villages are common
	 * enough not to need anything larger. */
	private static final int SEARCH_RADIUS_CHUNKS = 100;
	/** How far out from the centerpiece's front the spawn point goes: past vanilla's ten-block scatter, and
	 * short of the plaza's ring road (42 out in a village, so a 48-wide town hall leaves it 34 out). */
	private static final int PLAZA_GAP = 10;

	/** Where {@link #relocate} found a village this server session, for {@link #faceTheDoors}; null if none. */
	private static BlockPos located;

	private VillageSpawnHandler() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> faceTheDoors(server.getOverworld()));
	}

	/** Step 1: moves a brand-new world's spawn to the nearest village, before its spawn area is generated. */
	public static void relocate(ServerWorld world) {
		located = null;
		if (!VillageSpawnConfig.get().spawnInVillage) {
			return;
		}
		VillageSpawnState state = world.getPersistentStateManager().getOrCreate(VillageSpawnState.TYPE, "cim_village_spawn");
		if (state.isRelocated()) {
			return;
		}
		state.markRelocated();

		BlockPos villagePos = world.locateStructure(StructureTags.VILLAGE, world.getSpawnPos(), SEARCH_RADIUS_CHUNKS, false);
		if (villagePos == null) {
			Cim.LOGGER.info("No village found within {} chunks of spawn - keeping the vanilla spawn point", SEARCH_RADIUS_CHUNKS);
			return;
		}
		located = villagePos;
		world.setSpawnPos(villagePos, 0f);
		Cim.LOGGER.info("Relocated world spawn to a village at {}", villagePos);
	}

	/**
	 * Step 2. A pony village is located at its centerpiece - the town hall, in a village - and vanilla
	 * scatters players up to ten blocks round the spawn point, onto whatever's highest: its roof. So spawn
	 * goes out on the plaza instead, in front of the centerpiece's doors (its templates face north before
	 * rotation), far enough out that the scatter stays clear of it. Never generates anything: if the
	 * village's start chunk isn't there yet, or it isn't a pony village, the located point is kept.
	 */
	private static void faceTheDoors(ServerWorld world) {
		BlockPos village = located;
		located = null;
		if (village == null) {
			return;
		}
		Chunk chunk = world.getChunk(village.getX() >> 4, village.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, false);
		if (chunk == null) {
			return;
		}
		for (StructureStart start : chunk.getStructureStarts().values()) {
			if (!start.hasChildren() || !(start.getChildren().getFirst() instanceof VillageBuildingPiece centerpiece)) continue;
			BlockBox box = centerpiece.getBoundingBox();
			Direction front = centerpiece.rotation().rotate(Direction.NORTH);
			int reach = (front.getAxis() == Direction.Axis.X ? box.getBlockCountX() : box.getBlockCountZ()) / 2 + PLAZA_GAP;
			BlockPos spot = box.getCenter().offset(front, reach);
			// The ground's height if that chunk is already there; otherwise vanilla finds the surface when placing the player.
			if (world.getChunkManager().isChunkLoaded(spot.getX() >> 4, spot.getZ() >> 4)) {
				spot = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spot);
			}
			world.setSpawnPos(spot, 0f);
			Cim.LOGGER.info("Moved world spawn onto the plaza at {}", spot);
			return;
		}
	}
}
