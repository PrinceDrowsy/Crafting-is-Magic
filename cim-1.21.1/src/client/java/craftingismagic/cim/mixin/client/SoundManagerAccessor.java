package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;

/** Reaches the sound system so a moment can muffle what's playing (see {@code Muffler}). */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
	@Accessor("soundSystem")
	SoundSystem cim$getSoundSystem();
}
