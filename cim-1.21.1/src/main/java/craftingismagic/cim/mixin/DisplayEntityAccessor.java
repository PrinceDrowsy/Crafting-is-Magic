package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;

/** Lets keepsakes size and turn their display (the setter is private). */
@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
	@Invoker("setTransformation")
	void cim$setTransformation(AffineTransformation transformation);
}
