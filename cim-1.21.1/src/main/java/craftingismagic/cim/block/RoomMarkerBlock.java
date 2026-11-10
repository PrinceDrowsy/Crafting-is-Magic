package craftingismagic.cim.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Marks an empty room in a house shell, and what kind of room it is (design doc: Village identity -
 * house exteriors and interiors generate separately). When ponies move in, the room is furnished
 * with a room set that suits them ({@code HomeDecorator}). Invisible and walk-through, like a light
 * block - it only shows while you hold a room marker, so builders can see and break them.
 */
public class RoomMarkerBlock extends BlockWithEntity {
	public static final MapCodec<RoomMarkerBlock> CODEC = createCodec(settings -> new RoomMarkerBlock(RoomType.LIVING_ROOM, settings));
	private static final VoxelShape SHOWN = net.minecraft.block.Block.createCuboidShape(4, 0, 4, 12, 8, 12);

	private final RoomType room;

	public RoomMarkerBlock(RoomType room, Settings settings) {
		super(settings);
		this.room = room;
	}

	public RoomType room() {
		return room;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		for (RoomMarkerBlock marker : CimBlocks.ROOM_MARKERS) {
			if (context.isHolding(marker.asItem())) return SHOWN;
		}
		return VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RoomMarkerBlockEntity(pos, state);
	}
}
