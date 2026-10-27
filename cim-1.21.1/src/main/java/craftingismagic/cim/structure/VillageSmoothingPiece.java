package craftingismagic.cim.structure;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Smooths a village's land a little toward its broad shape ({@link Shape}). Roads go all the way to it,
 * the ground beside roads and plots most of the way, the rest only partly; it fades out at the edge.
 * Buildings are set on the same surface by the planner, so nothing ends up in a trench.
 * <p>
 * The box sits at the bottom of the world (like the road piece's) so terrain adaptation leaves it alone.
 */
public final class VillageSmoothingPiece extends StructurePiece {
	/** Out in the open, how far each column is pulled toward the land's broad shape (0-1), and the most it's moved. */
	private static final float STRENGTH = 0.5f;
	private static final int MAX_CHANGE = 3;
	/** A road column, and the land right beside roads and buildings, may be moved this much. */
	private static final int ROAD_CHANGE = 4;
	/** Beside a road or a plot, the land's pulled fully to the broad shape, fading to the open pull over this many blocks. */
	private static final int NEAR = 5;
	/** The pull fades out over this many blocks in to the village's edge. */
	private static final int EDGE_FADE = 16;

	/** The land's broad shape: the top block's height at points {@code step} apart from the origin, {@code size} a side. */
	public record Shape(int originX, int originZ, int step, int size, float[] heights) {
		float at(int x, int z) {
			float gx = MathHelper.clamp((x - originX) / (float) step, 0f, size - 1.001f);
			float gz = MathHelper.clamp((z - originZ) / (float) step, 0f, size - 1.001f);
			int ix = (int) gx, iz = (int) gz;
			float fx = gx - ix, fz = gz - iz;
			float top = MathHelper.lerp(fx, heights[iz * size + ix], heights[iz * size + ix + 1]);
			float bottom = MathHelper.lerp(fx, heights[(iz + 1) * size + ix], heights[(iz + 1) * size + ix + 1]);
			return MathHelper.lerp(fz, top, bottom);
		}
	}

	private final Shape shape;
	private final int centerX;
	private final int centerZ;
	private final int radius;
	/** Building plots (and the plaza), each {@code x << 32 | z} - left as they are. */
	private final long[] plots;
	/** Road columns, each {@code x << 32 | z}. */
	private final long[] roads;
	/** {@link #plots} and {@link #roads}, for looking up - built on first use; chunks are laid on several threads at once. */
	private volatile Set<Long> plotCells;
	private volatile Set<Long> roadCells;

	public VillageSmoothingPiece(Shape shape, int centerX, int centerZ, int radius, long[] plots, long[] roads, BlockBox box) {
		super(PonyVillageStructure.SMOOTHING, 0, box);
		this.shape = shape;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.radius = radius;
		this.plots = plots;
		this.roads = roads;
	}

	public VillageSmoothingPiece(NbtCompound nbt) {
		super(PonyVillageStructure.SMOOTHING, nbt);
		int[] grid = nbt.getIntArray("Grid");
		int[] heights = nbt.getIntArray("Heights");
		float[] shaped = new float[heights.length];
		for (int i = 0; i < heights.length; i++) {
			shaped[i] = heights[i] / 16f;
		}
		this.shape = new Shape(grid[0], grid[1], grid[2], grid[3], shaped);
		this.centerX = nbt.getInt("CenterX");
		this.centerZ = nbt.getInt("CenterZ");
		this.radius = nbt.getInt("Radius");
		this.plots = nbt.getLongArray("Plots");
		this.roads = nbt.getLongArray("Roads");
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putIntArray("Grid", new int[] { shape.originX(), shape.originZ(), shape.step(), shape.size() });
		// Sixteenths of a block are plenty, as whole numbers.
		int[] heights = new int[shape.heights().length];
		for (int i = 0; i < heights.length; i++) {
			heights[i] = Math.round(shape.heights()[i] * 16f);
		}
		nbt.putIntArray("Heights", heights);
		nbt.putInt("CenterX", centerX);
		nbt.putInt("CenterZ", centerZ);
		nbt.putInt("Radius", radius);
		nbt.putLongArray("Plots", plots);
		nbt.putLongArray("Roads", roads);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		Set<Long> plotted = cells(true);
		Set<Long> roaded = cells(false);
		boolean changed = false;
		for (int x = chunkBox.getMinX(); x <= chunkBox.getMaxX(); x++) {
			for (int z = chunkBox.getMinZ(); z <= chunkBox.getMaxZ(); z++) {
				long cell = VillageRoadPiece.cell(x, z);
				if (plotted.contains(cell)) continue;
				float edge = edgeFade(x, z);
				if (edge <= 0f) continue;
				float pull;
				int most;
				if (roaded.contains(cell)) {
					pull = 1f;
					most = ROAD_CHANGE;
				} else {
					float near = Math.max(nearness(roaded, x, z), nearness(plotted, x, z));
					pull = Math.max(STRENGTH, near);
					most = near > STRENGTH ? ROAD_CHANGE : MAX_CHANGE;
				}
				changed |= smooth(world, x, z, pull * edge, most);
			}
		}
		if (changed) {
			// The chunk's records of where its ground is don't follow these changes by themselves - and the
			// grass, flowers and trees placed after this go by them, so they'd float over dug-down ground.
			Heightmap.populateHeightmaps(world.getChunk(chunkPos.x, chunkPos.z), EnumSet.allOf(Heightmap.Type.class));
		}
	}

	/** Full pull inside the village, fading smoothly to none at its edge. */
	private float edgeFade(int x, int z) {
		double out = Math.sqrt((double) (x - centerX) * (x - centerX) + (double) (z - centerZ) * (z - centerZ));
		float t = MathHelper.clamp((float) (radius - out) / EDGE_FADE, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** How near the nearest of {@code cells} is: 1 right beside one, smoothly down to 0 {@link #NEAR} blocks off. */
	private static float nearness(Set<Long> cells, int x, int z) {
		int nearest = NEAR + 1;
		for (int dx = -NEAR; dx <= NEAR; dx++) {
			for (int dz = -NEAR; dz <= NEAR; dz++) {
				int out = Math.max(Math.abs(dx), Math.abs(dz));
				if (out < nearest && cells.contains(VillageRoadPiece.cell(x + dx, z + dz))) {
					nearest = out;
				}
			}
		}
		float t = MathHelper.clamp((nearest - 1) / (float) NEAR, 0f, 1f);
		return 1f - t * t * (3f - 2f * t);
	}

	/**
	 * A column pulled toward the land's broad shape by {@code pull}, moved {@code most} at most: dug down, or
	 * built up in dirt, and grassed over where it's soil. Whether it changed.
	 */
	private boolean smooth(StructureWorldAccess world, int x, int z, float pull, int most) {
		int top = VillageRoadPiece.surface(world, x, z);
		if (world.getBlockState(new BlockPos(x, top, z)).getFluidState().isIn(FluidTags.WATER)) {
			return false;
		}
		int eased = top + MathHelper.clamp(Math.round((shape.at(x, z) - top) * pull), -most, most);
		if (eased == top) {
			return false;
		}
		VillageRoadPiece.clearPlants(world, x, top, z);
		if (eased < top) {
			for (int y = top; y > eased; y--) {
				world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 2);
			}
			BlockPos surface = new BlockPos(x, eased, z);
			if (world.getBlockState(surface).isIn(BlockTags.DIRT)) {
				world.setBlockState(surface, Blocks.GRASS_BLOCK.getDefaultState(), 2);
			}
		} else {
			BlockPos buried = new BlockPos(x, top, z);
			if (world.getBlockState(buried).isOf(Blocks.GRASS_BLOCK)) {
				world.setBlockState(buried, Blocks.DIRT.getDefaultState(), 2);
			}
			for (int y = top + 1; y < eased; y++) {
				world.setBlockState(new BlockPos(x, y, z), Blocks.DIRT.getDefaultState(), 2);
			}
			world.setBlockState(new BlockPos(x, eased, z), Blocks.GRASS_BLOCK.getDefaultState(), 2);
		}
		return true;
	}

	private Set<Long> cells(boolean plotted) {
		Set<Long> set = plotted ? plotCells : roadCells;
		if (set == null) {
			long[] from = plotted ? plots : roads;
			set = new HashSet<>(from.length * 2);
			for (long cell : from) {
				set.add(cell);
			}
			if (plotted) {
				plotCells = set;
			} else {
				roadCells = set;
			}
		}
		return set;
	}
}
