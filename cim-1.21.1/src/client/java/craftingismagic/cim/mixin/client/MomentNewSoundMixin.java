package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;

import craftingismagic.cim.client.mood.MomentDirector;
import craftingismagic.cim.client.mood.Muffler;

/**
 * Muffles a sound the moment it starts during a scene-setting moment, instead of on the next
 * refresh - otherwise a short, close sound (breaking grass right beside you) rings out sharp before
 * the muffle catches it. The filter is queued right behind vanilla's own "start playing" on the sound
 * thread, so it lands within the same instant.
 */
@Mixin(SoundSystem.class)
public abstract class MomentNewSoundMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("RETURN"))
	private void cim$muffleFromTheStart(SoundInstance sound, CallbackInfo ci) {
		float highs = MomentDirector.muffleHighsFor(sound.getCategory());
		if (highs >= 1f) {
			return;
		}
		Channel.SourceManager source = ((SoundSystemAccessor) this).cim$getSources().get(sound);
		if (source != null) {
			Muffler.apply(source, highs);
		}
	}
}
