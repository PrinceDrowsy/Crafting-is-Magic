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
	/** Water no deeper than this can be waded (or bridged with a plank) - a stream doesn't cut a village in two. */
	private static final int STREAM_DEPTH = 2;
	/** Blocks between points of the finer grid reachability's read on (see {@link #reachableFrom}). */
	private static final int REACH_STEP = 4;
	/**
	 * Ground counts as the village's own only if the walk there from the plaza is at most this many times
	 * the straight line, plus this many blocks - so land across a ravine, reachable only by going round its
	 * end, doesn't.
	 */
	private static final double DETOUR = 1.5;
	private static final int DETOUR_SLACK = 16;

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

	/**
	 * The ground's height (the top block's) at every grid point, softened a little - each point averaged with
	 * the eight round it - so only the land's broad shape is left: its hills and dips, without the sharp
	 * steps between. What a village's land is eased toward (VillageSmoothingPiece).
	 */
	VillageSmoothingPiece.Shape softened() {
		float[] heights = new float[size * size];
		for (int iz = 0; iz < size; iz++) {
			for (int ix = 0; ix < size; ix++) {
				float sum = 0;
				int count = 0;
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						int x = ix + dx, z = iz + dz;
						if (x < 0 || z < 0 || x >= size || z >= size) continue;
						sum += surface[z * size + x] - 1;
						count++;
					}
				}
				heights[iz * size + ix] = sum / count;
			}
		}
		return new VillageSmoothingPiece.Shape(originX, originZ, STEP, size, heights);
	}

	/** Grid points read - for the planner's log. */
	int samples() {
		return surface.length * 2;
	}

	/** About where the ground is here (the first free block above it), between the grid points round it. */
	int ground(int x, int z) {
		return Math.round(smoothGround(x, z));
	}

	/** {@link #ground}, unrounded - so slopes between grid points read smoothly (for finding a road's way). */
	float smoothGround(int x, int z) {
		float gx = MathHelper.clamp((x - originX) / (float) STEP, 0f, size - 1.001f);
		float gz = MathHelper.clamp((z - originZ) / (float) STEP, 0f, size - 1.001f);
		int ix = (int) gx, iz = (int) gz;
		float fx = gx - ix, fz = gz - iz;
		float top = MathHelper.lerp(fx, surface[iz * size + ix], surface[iz * size + ix + 1]);
		float bottom = MathHelper.lerp(fx, surface[(iz + 1) * size + ix], surface[(iz + 1) * size + ix + 1]);
		return MathHelper.lerp(fz, top, bottom);
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

	/**
	 * The ground a pony could walk to from {@code (x, z)} (the plaza), within {@code radius} of it.
	 * <p>
	 * Read on its own finer grid ({@link #REACH_STEP} blocks): on the 8-block one, a ravine with stepped
	 * sides never changes more than 8 blocks between points, so the flood walked down one side and up the
	 * other, cliffs and all. Floods out point to point across ground that climbs or drops no more than a
	 * block a block between them, and through water no deeper than a stream; a ravine, a cliff or a lake
	 * stops it. Every point's a full terrain reading, all at once across the CPU's cores.
	 */
	Reach reachableFrom(int x, int z, int radius) {
		int side = radius * 2 / REACH_STEP + 1;
		int startX = x - radius, startZ = z - radius;
		int[] heights = new int[side * side];
		ChunkGenerator generator = context.chunkGenerator();
		IntStream.range(0, side * side).parallel().forEach(i -> {
			int px = startX + (i % side) * REACH_STEP, pz = startZ + (i / side) * REACH_STEP;
			double dx = px - x, dz = pz - z;
			heights[i] = dx * dx + dz * dz > (double) radius * radius ? Integer.MIN_VALUE
					: generator.getHeight(px, pz, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
		});
		boolean[] reached = new boolean[side * side];
		// How many steps each point's walk from the plaza takes - for telling the far side of a ravine (only
		// reached the long way round) from the near.
		int[] steps = new int[side * side];
		int start = (side / 2) * side + side / 2;
		java.util.ArrayDeque<Integer> open = new java.util.ArrayDeque<>();
		reached[start] = true;
		open.add(start);
		int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		while (!open.isEmpty()) {
			int i = open.poll();
			int ix = i % side, iz = i / side;
			for (int[] step : sides) {
				int nx = ix + step[0], nz = iz + step[1];
				if (nx < 0 || nz < 0 || nx >= side || nz >= side) continue;
				int next = nz * side + nx;
				if (reached[next] || heights[next] == Integer.MIN_VALUE || Math.abs(heights[next] - heights[i]) > REACH_STEP
						|| deep(startX + nx * REACH_STEP, startZ + nz * REACH_STEP)) continue;
				reached[next] = true;
				steps[next] = steps[i] + 1;
				open.add(next);
			}
		}
		// Reached only by a long way round - more than DETOUR times as far as the straight line, give or take
		// DETOUR_SLACK - isn't really on the village's side.
		for (int i = 0; i < side * side; i++) {
			if (!reached[i]) continue;
			double straight = Math.hypot((i % side - side / 2) * REACH_STEP, (i / side - side / 2) * REACH_STEP);
			if (steps[i] * REACH_STEP > straight * DETOUR + DETOUR_SLACK) reached[i] = false;
		}
		return new Reach(startX, startZ, side, reached);
	}

	/** Whether there's water deeper than a stream here, by the nearest point of the coarse grid. */
	private boolean deep(int x, int z) {
		int ix = MathHelper.clamp(Math.round((x - originX) / (float) STEP), 0, size - 1);
		int iz = MathHelper.clamp(Math.round((z - originZ) / (float) STEP), 0, size - 1);
		return surface[iz * size + ix] - floor[iz * size + ix] > STREAM_DEPTH;
	}

	/** The ground reachable from a village's plaza ({@link #reachableFrom}), on its fine grid. */
	record Reach(int originX, int originZ, int side, boolean[] reached) {
		/** Whether a spot is on reachable ground: its nearest point is (outside the grid, it isn't). */
		boolean at(int x, int z) {
			int ix = Math.round((x - originX) / (float) REACH_STEP), iz = Math.round((z - originZ) / (float) REACH_STEP);
			return ix >= 0 && iz >= 0 && ix < side && iz < side && reached[iz * side + ix];
		}

		/** How much of the ground within {@code radius} of {@code (x, z)} is reachable (0-1). */
		double share(int x, int z, double radius) {
			int count = 0, total = 0;
			for (int iz = 0; iz < side; iz++) {
				for (int ix = 0; ix < side; ix++) {
					double dx = originX + ix * REACH_STEP - x, dz = originZ + iz * REACH_STEP - z;
					if (dx * dx + dz * dz > radius * radius) continue;
					total++;
					if (reached[iz * side + ix]) count++;
				}
			}
			return total == 0 ? 1 : count / (double) total;
		}
	}

	/** Exactly where the ground is here - one full reading, for a building that's going in. */
	int exactGround(int x, int z) {
		return context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
	}
}
