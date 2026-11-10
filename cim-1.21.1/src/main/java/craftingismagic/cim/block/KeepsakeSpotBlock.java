package craftingismagic.cim.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * A spot in a room set where a keepsake can appear - on a shelf, a table, a windowsill (design doc: Village
 * identity - keepsakes). Invisible and walk-through; it only shows while you hold one.
 */
public class KeepsakeSpotBlock extends Block {
	private static final VoxelShape SHOWN = Block.createCuboidShape(5, 0, 5, 11, 6, 11);

	public KeepsakeSpotBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return context.isHolding(asItem()) ? SHOWN : VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}
}
