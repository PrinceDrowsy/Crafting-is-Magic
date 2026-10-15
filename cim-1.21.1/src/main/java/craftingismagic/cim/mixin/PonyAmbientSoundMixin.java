package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;

/**
 * Ponies don't honk. Their ambient sound is a soft horse breath (assets/minecraft/sounds.json), and
 * it's occasional: at least {@link #MIN_DELAY_TICKS} between them instead of vanilla's 4 seconds, so a
 * village sounds lived-in rather than noisy.
 */
@Mixin(MobEntity.class)
public abstract class PonyAmbientSoundMixin {
	private static final int MIN_DELAY_TICKS = 600;

	@Inject(method = "getMinAmbientSoundDelay", at = @At("HEAD"), cancellable = true)
	private void cim$softerVillages(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof VillagerEntity) {
			cir.setReturnValue(MIN_DELAY_TICKS);
		}
	}
}
