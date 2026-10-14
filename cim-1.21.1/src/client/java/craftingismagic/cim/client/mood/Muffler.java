package craftingismagic.cim.client.mood;

import java.util.Map;
import java.util.Set;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.Source;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.Cim;
import craftingismagic.cim.mixin.client.SoundManagerAccessor;
import craftingismagic.cim.mixin.client.SoundSystemAccessor;
import craftingismagic.cim.mixin.client.SourceAccessor;

/**
 * Real muffling for scene-setting moments (README: The mood system - "muffled, pulled-back sound puts
 * you inside a character", as in *Sound of Metal*): a low-pass filter on every playing world sound,
 * cutting the high frequencies so the world sounds dull and far away rather than merely quieter. Uses
 * OpenAL's effects extension (EFX); where it isn't available, moments simply don't muffle.
 * <p>
 * Filters are attached on the sound thread, through each sound's own source manager - the same way
 * vanilla changes a playing sound's volume. Music, conversation voices, and the interface are never
 * muffled.
 */
public final class Muffler {
	private static int filter = -1;
	private static boolean unavailable;

	private Muffler() {
	}

	/**
	 * Muffles every playing sound in {@code categories} by {@code amount} (0 = clear, 1 = the
	 * moment's full muffle); every other sound is cleared. {@code highs} is how much treble is left at
	 * full muffle (0-1).
	 */
	public static void apply(MinecraftClient client, Set<SoundCategory> categories, float amount, float highs) {
		if (unavailable) {
			return;
		}
		Map<SoundInstance, Channel.SourceManager> playing =
				((SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).cim$getSoundSystem()).cim$getSources();
		float gainHigh = MathHelper.lerp(MathHelper.clamp(amount, 0f, 1f), 1f, highs);
		for (Map.Entry<SoundInstance, Channel.SourceManager> entry : playing.entrySet()) {
			boolean muffle = amount > 0.001f && categories.contains(entry.getKey().getCategory());
			entry.getValue().run(source -> filter(source, muffle ? gainHigh : 1f));
		}
	}

	/** Muffles one just-started sound to this treble level (see {@code MomentNewSoundMixin}). */
	public static void apply(Channel.SourceManager source, float gainHigh) {
		if (!unavailable) {
			source.run(s -> filter(s, gainHigh));
		}
	}

	/** On the sound thread: attach the low-pass filter at this treble level, or remove it at 1. */
	private static void filter(Source source, float gainHigh) {
		if (unavailable) {
			return;
		}
		try {
			int pointer = ((SourceAccessor) source).cim$getPointer();
			if (gainHigh >= 0.999f) {
				AL10.alSourcei(pointer, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
				return;
			}
			if (filter == -1) {
				filter = EXTEfx.alGenFilters();
				EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
			}
			// A filter's settings are copied when it's attached, so one filter serves every source.
			EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1f);
			EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHigh);
			AL10.alSourcei(pointer, EXTEfx.AL_DIRECT_FILTER, filter);
		} catch (RuntimeException | UnsatisfiedLinkError e) {
			unavailable = true;
			Cim.LOGGER.warn("Audio filters aren't available here, so moments won't muffle sound", e);
		}
	}
}
