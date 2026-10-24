package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.LakeFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.structure.Structure;

import craftingismagic.cim.structure.PonyVillageStructure;

/**
 * No lava lakes in pony villages. Lakes are placed later in world generation than structures, so a village
 * plans its plots without seeing them, and a house built into one burns down. Every chunk a village reaches
 * is marked with a reference to it, so a lava lake that would touch one of those chunks just doesn't form.
 * Water lakes still do - they make waterfronts.
 */
@Mixin(LakeFeature.class)
public abstract class NoLavaLakesInVillagesMixin {
	/** A lake fills a 16 x 16 area from its origin, so it can touch up to four chunks. */
	private static final int[][] CIM$CORNERS = { { 0, 0 }, { 15, 0 }, { 0, 15 }, { 15, 15 } };

	@Inject(method = "generate", at = @At("HEAD"), cancellable = true)
	private void cim$noLavaInVillages(FeatureContext<LakeFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
		BlockPos origin = context.getOrigin();
		// A throwaway random: asking a lake what it's made of mustn't change how the world's own lakes come out.
		if (!context.getConfig().fluid().get(Random.create(0L), origin).isOf(Blocks.LAVA)) {
			return;
		}
		StructureWorldAccess world = context.getWorld();
		for (int[] corner : CIM$CORNERS) {
			BlockPos at = origin.add(corner[0], 0, corner[1]);
			if (!world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) {
				continue;
			}
			for (Structure structure : world.getChunk(at).getStructureReferences().keySet()) {
				if (structure instanceof PonyVillageStructure) {
					cir.setReturnValue(false);
					return;
				}
			}
		}
	}
}
