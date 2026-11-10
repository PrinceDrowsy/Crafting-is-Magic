package craftingismagic.cim.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * A chalkboard - the Teacher's job site (design doc: Progression - the Schoolhouse). A slim board in a
 * wooden frame, standing against whatever is behind it. Placeholder look for now.
 */
public class ChalkboardBlock extends HorizontalFacingBlock {
	public static final MapCodec<ChalkboardBlock> CODEC = createCodec(ChalkboardBlock::new);
	private static final VoxelShape NORTH = Block.createCuboidShape(0, 0, 13, 16, 16, 16);
	private static final VoxelShape SOUTH = Block.createCuboidShape(0, 0, 0, 16, 16, 3);
	private static final VoxelShape WEST = Block.createCuboidShape(13, 0, 0, 16, 16, 16);
	private static final VoxelShape EAST = Block.createCuboidShape(0, 0, 0, 3, 16, 16);

	public ChalkboardBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		// The board faces whoever places it.
		return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}
}
