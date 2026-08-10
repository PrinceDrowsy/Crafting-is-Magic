package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.passive.VillagerEntity;

import craftingismagic.cim.villager.BatPonyScheduleHandler;

/**
 * {@code initBrain} is the only place vanilla ever assigns a villager's {@code Schedule}, and it's
 * called from every lifecycle point that matters (fresh spawn, chunk load, and grow-up via {@code
 * onGrowUp} -> {@code reinitializeBrain} -> {@code initBrain}) - hooking this one choke point covers
 * all three without a second mixin for grow-up specifically, same reasoning {@link
 * VillagerGrowthMixin} hooks {@code onGrowUp} directly for its own concern.
 */
@Mixin(VillagerEntity.class)
public abstract class BatPonyScheduleMixin {
	@Inject(method = "initBrain", at = @At("TAIL"))
	private void cim$applyNocturnalSchedule(Brain<VillagerEntity> brain, CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		BatPonyScheduleHandler.applyIfNocturnal(self, brain);
	}
}
