package craftingismagic.cim.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Paint on a wall (design doc: Village identity - interiors): a flat layer lying against the face of each
 * wall it touches, showing a concrete texture, with no thickness to speak of - so a room keeps its full
 * size, and you walk right through it. Laid down by the interior generation in a color that suits the
 * residents; furniture can stand where it is.
 */
public class WallPaintBlock extends Block {
	public static final MapCodec<WallPaintBlock> CODEC = createCodec(WallPaintBlock::new);
	public static final EnumProperty<DyeColor> COLOR = EnumProperty.of("color", DyeColor.class);
	public static final BooleanProperty NORTH = Properties.NORTH;
	public static final BooleanProperty EAST = Properties.EAST;
	public static final BooleanProperty SOUTH = Properties.SOUTH;
	public static final BooleanProperty WEST = Properties.WEST;

	private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 0.5);
	private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0, 0, 15.5, 16, 16, 16);
	private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0, 0, 0, 0.5, 16, 16);
	private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(15.5, 0, 0, 16, 16, 16);

	public WallPaintBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(COLOR, DyeColor.WHITE)
				.with(NORTH, false).with(EAST, false).with(SOUTH, false).with(WEST, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(COLOR, NORTH, EAST, SOUTH, WEST);
	}

	/** The property for the face lying against the wall on {@code side}. */
	public static BooleanProperty face(Direction side) {
		return switch (side) {
			case NORTH -> NORTH;
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			default -> WEST;
		};
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		VoxelShape shape = VoxelShapes.empty();
		if (state.get(NORTH)) shape = VoxelShapes.union(shape, NORTH_SHAPE);
		if (state.get(SOUTH)) shape = VoxelShapes.union(shape, SOUTH_SHAPE);
		if (state.get(EAST)) shape = VoxelShapes.union(shape, EAST_SHAPE);
		if (state.get(WEST)) shape = VoxelShapes.union(shape, WEST_SHAPE);
		return shape;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}
}
