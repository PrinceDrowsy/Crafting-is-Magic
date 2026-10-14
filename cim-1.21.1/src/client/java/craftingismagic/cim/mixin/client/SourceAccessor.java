package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sound.Source;

/** The OpenAL source id behind a playing sound, so a low-pass filter can be attached to it. */
@Mixin(Source.class)
public interface SourceAccessor {
	@Accessor("pointer")
	int cim$getPointer();
}
