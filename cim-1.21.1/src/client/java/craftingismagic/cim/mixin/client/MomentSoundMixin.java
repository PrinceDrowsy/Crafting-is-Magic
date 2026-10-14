package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;

import craftingismagic.cim.client.mood.MomentDirector;

/**
 * Lets a scene-setting moment hush the world (README: The mood system - "muffling"): every sound's
 * volume passes through here, so a moment can dip world sounds smoothly while music, voices, and the
 * interface stay untouched. A no-op whenever no moment is playing.
 */
@Mixin(SoundSystem.class)
public abstract class MomentSoundMixin {
	@Inject(method = "getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F", at = @At("RETURN"), cancellable = true)
	private void cim$hushForMoment(float volume, SoundCategory category, CallbackInfoReturnable<Float> cir) {
		float factor = MomentDirector.soundFactor(category);
		if (factor < 1f) {
			cir.setReturnValue(cir.getReturnValueF() * factor);
		}
	}
}
