package craftingismagic.cim.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * The roads of a pony village (PonyVillageStructure): every road column, laid as a dirt path on the ground
 * wherever it runs - planks where it crosses water.
 * <p>
 * Its box is the road's area at the very bottom of the world, not at the ground: the village's terrain
 * adaptation shapes the land round every piece's box, and a box over the whole road would flatten the town.
 */
public final class VillageRoadPiece extends StructurePiece {
	/** Road columns, each {@code x << 32 | z}. */
	private final long[] cells;

	public VillageRoadPiece(long[] cells, BlockBox box) {
		super(PonyVillageStructure.ROAD, 0, box);
		this.cells = cells;
	}

	public VillageRoadPiece(NbtCompound nbt) {
		super(PonyVillageStructure.ROAD, nbt);
		this.cells = nbt.getLongArray("Cells");
	}

	public static long cell(int x, int z) {
		return (long) x << 32 | (z & 0xFFFFFFFFL);
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putLongArray("Cells", cells);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		for (long cell : cells) {
			int x = (int) (cell >> 32);
			int z = (int) cell;
			if (x < chunkBox.getMinX() || x > chunkBox.getMaxX() || z < chunkBox.getMinZ() || z > chunkBox.getMaxZ()) continue;
			BlockPos ground = new BlockPos(x, surface(world, x, z), z);
			BlockState state = world.getBlockState(ground);
			if (state.getFluidState().isIn(FluidTags.WATER)) {
				world.setBlockState(ground, Blocks.OAK_PLANKS.getDefaultState(), 2);
			} else if (state.isIn(BlockTags.DIRT) || state.isOf(Blocks.SAND) || state.isOf(Blocks.GRAVEL)) {
				world.setBlockState(ground, Blocks.DIRT_PATH.getDefaultState(), 2);
				clearPlants(world, x, ground.getY(), z);
			}
		}
	}

	/** Clears plants on top of a column. Neighbouring chunks can plant into this one before it's built. */
	static void clearPlants(StructureWorldAccess world, int x, int top, int z) {
		BlockPos.Mutable at = new BlockPos.Mutable(x, top + 1, z);
		for (int i = 0; i < 3; i++) {
			BlockState state = world.getBlockState(at);
			if (state.isAir() || !state.isReplaceable() || !state.getFluidState().isEmpty()) {
				return;
			}
			world.setBlockState(at, Blocks.AIR.getDefaultState(), 2);
			at.move(0, 1, 0);
		}
	}

	/** How far above the world-generation surface the real top may be - the land may have been built up since. */
	private static final int SURFACE_SLACK = 8;

	/**
	 * The column's current top block (solid or water). The worldgen heightmap doesn't follow changes made by
	 * village pieces, so this scans down from a little above it.
	 */
	static int surface(StructureWorldAccess world, int x, int z) {
		BlockPos.Mutable at = new BlockPos.Mutable(x, world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) + SURFACE_SLACK, z);
		int bottom = world.getBottomY();
		while (at.getY() > bottom) {
			BlockState state = world.getBlockState(at);
			if (!state.getFluidState().isEmpty() || (!state.isAir() && !state.isReplaceable())) {
				return at.getY();
			}
			at.move(0, -1, 0);
		}
		return bottom;
	}
}
