package craftingismagic.cim.levitation;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.BlockPos;

/** One block within a (possibly multi-block) held/thrown Levitation group. */
record LevitationBlock(BlockPos originalPos, BlockState state, DisplayEntity.BlockDisplayEntity displayEntity) {
}
