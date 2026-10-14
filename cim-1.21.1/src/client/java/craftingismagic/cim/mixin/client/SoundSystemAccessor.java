package craftingismagic.cim.mixin.client;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;

/** The sounds playing right now and the audio sources behind them (see {@code Muffler}). */
@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {
	@Accessor("sources")
	Map<SoundInstance, Channel.SourceManager> cim$getSources();
}
