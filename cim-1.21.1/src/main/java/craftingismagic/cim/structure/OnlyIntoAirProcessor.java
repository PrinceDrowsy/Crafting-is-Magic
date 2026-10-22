package craftingismagic.cim.structure;

import com.mojang.serialization.MapCodec;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

import craftingismagic.cim.Cim;
import craftingismagic.cim.block.RoomMarkerBlock;

/**
 * A room set only fills empty space: anything already there - the shell's bed, a wall it brushes,
 * a lamp the builder put in the shell - stays as it is (README: Village identity - interiors).
 */
public final class OnlyIntoAirProcessor extends StructureProcessor {
	public static final OnlyIntoAirProcessor INSTANCE = new OnlyIntoAirProcessor();
	public static final StructureProcessorType<OnlyIntoAirProcessor> TYPE = Registry.register(Registries.STRUCTURE_PROCESSOR,
			Identifier.of(Cim.MOD_ID, "only_into_air"), () -> MapCodec.unit(INSTANCE));

	private OnlyIntoAirProcessor() {
	}

	@Override
	public StructureTemplate.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
			StructureTemplate.StructureBlockInfo current, StructurePlacementData data) {
		var there = world.getBlockState(current.pos());
		// Paint counts as empty: furniture can stand against a painted wall.
		return there.isAir() || there.getBlock() instanceof RoomMarkerBlock || there.getBlock() instanceof craftingismagic.cim.block.WallPaintBlock
				? current : null;
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return TYPE;
	}

	/** Registers the type (it has to exist to be used, even though it's never written to a file). */
	public static void register() {
	}
}
