package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.Entity;

/** Exposes {@code Entity#setFlag}, which is protected, to the fall-flying bypass mixin. */
@Mixin(Entity.class)
public interface EntitySetFlagAccessor {
	@Invoker("setFlag")
	void cim$setFlag(int index, boolean value);
}
