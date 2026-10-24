package craftingismagic.cim.structure;

import java.util.stream.IntStream;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

/**
 * A pony village's ground, read once for the whole village (PonyVillagePlanner). Each terrain reading is a
 * full noise calculation (~5 ms), and asking for every spot a building might go took over 13,000 of them -
 * 75 seconds for one village, holding up world creation whenever spawn landed in one. So the ground is
 * read on a coarse grid instead, every grid point at once across the CPU's cores, and the planner's many
 * "is this plot flat and dry?" checks read between grid points. Only a building that passes gets an exact
 * reading, to stand on the real ground.
 */
final class VillageTerrain {
	/** Blocks between grid points - Minecraft's own terrain is built from 4-block cells, so this stays close. */
	private static final int STEP = 8;

	private final Structure.Context context;
	private final int originX;
	private final int originZ;
	private final int size;
	private final int[] surface;
	private final int[] floor;

	VillageTerrain(Structure.Context context, int centerX, int centerZ, int reach) {
		this.context = context;
		this.originX = centerX - reach;
		this.originZ = centerZ - reach;
		this.size = reach * 2 / STEP + 2;
		this.surface = new int[size * size];
		this.floor = new int[size * size];
		ChunkGenerator generator = context.chunkGenerator();
		// Worldgen reads terrain from many threads at once anyway, so these can run side by side.
		IntStream.range(0, size * size).parallel().forEach(i -> {
			int x = originX + (i % size) * STEP;
			int z = originZ + (i / size) * STEP;
			surface[i] = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
			floor[i] = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
		});
	}

	/** Grid points read - for the planner's log. */
	int samples() {
		return surface.length * 2;
	}

	/** About where the ground is here (the first free block above it), between the grid points round it. */
	int ground(int x, int z) {
		float gx = MathHelper.clamp((x - originX) / (float) STEP, 0f, size - 1.001f);
		float gz = MathHelper.clamp((z - originZ) / (float) STEP, 0f, size - 1.001f);
		int ix = (int) gx, iz = (int) gz;
		float fx = gx - ix, fz = gz - iz;
		float top = MathHelper.lerp(fx, surface[iz * size + ix], surface[iz * size + ix + 1]);
		float bottom = MathHelper.lerp(fx, surface[(iz + 1) * size + ix], surface[(iz + 1) * size + ix + 1]);
		return Math.round(MathHelper.lerp(fz, top, bottom));
	}

	/** Whether there's no water here - dry only if every grid point round it is dry, to be safe. */
	boolean dry(int x, int z) {
		int ix = MathHelper.clamp(Math.floorDiv(x - originX, STEP), 0, size - 2);
		int iz = MathHelper.clamp(Math.floorDiv(z - originZ, STEP), 0, size - 2);
		for (int dz = 0; dz <= 1; dz++) {
			for (int dx = 0; dx <= 1; dx++) {
				int i = (iz + dz) * size + ix + dx;
				if (floor[i] < surface[i]) return false;
			}
		}
		return true;
	}

	/** Exactly where the ground is here - one full reading, for a building that's going in. */
	int exactGround(int x, int z) {
		return context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
	}
}
