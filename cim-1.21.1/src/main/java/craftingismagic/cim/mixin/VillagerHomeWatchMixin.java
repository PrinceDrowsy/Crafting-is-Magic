package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.passive.VillagerEntity;

import craftingismagic.cim.structure.HomeDecorator;

/**
 * Every few seconds, a pony checks whether its home has changed - a cheap comparison of its bed - so a
 * move-in is noticed and the home furnished for them (see {@link HomeDecorator}). Staggered by entity id
 * so a whole village doesn't check on the same tick.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerHomeWatchMixin {
	@Inject(method = "mobTick", at = @At("TAIL"))
	private void cim$watchHome(CallbackInfo ci) {
		VillagerEntity pony = (VillagerEntity) (Object) this;
		if ((pony.age + pony.getId()) % 100 == 0) {
			HomeDecorator.check(pony);
		}
	}
}
