package craftingismagic.cim.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.structure.VillageAdditions;

/**
 * Buildings every village has exactly one of ({@code "once": true} in a village addition - the Schoolhouse):
 * until the village has it, it's tried first at every spot its pool fills; once it's there, never again.
 */
@Mixin(targets = "net.minecraft.structure.pool.StructurePoolBasedGenerator$StructurePoolGenerator")
public abstract class VillageOnceMixin {
	@Shadow
	@Final
	private List<? super PoolStructurePiece> children;

	@Redirect(method = "generatePiece", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/structure/pool/StructurePool;getElementIndicesInRandomOrder(Lnet/minecraft/util/math/random/Random;)Ljava/util/List;"))
	private List<StructurePoolElement> cim$onceFirst(StructurePool pool, Random random) {
		List<StructurePoolElement> elements = pool.getElementIndicesInRandomOrder(random);
		for (StructurePoolElement once : VillageAdditions.once()) {
			if (!elements.removeIf(element -> element == once)) continue;
			if (!placed(once)) elements.add(0, once);
		}
		return elements;
	}

	private boolean placed(StructurePoolElement element) {
		for (Object child : children) {
			if (child instanceof PoolStructurePiece piece && piece.getPoolElement() == element) return true;
		}
		return false;
	}
}
