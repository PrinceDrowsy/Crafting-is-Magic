package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;

/** Lets keepsakes set what an item display shows (the setter is private). */
@Mixin(DisplayEntity.ItemDisplayEntity.class)
public interface ItemDisplayAccessor {
	@Invoker("setItemStack")
	void cim$setItemStack(ItemStack stack);
}
