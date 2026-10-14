package craftingismagic.cim.memory;

import com.mojang.serialization.Codec;

/** How strongly a memory's {@link Emotion} is felt - the same three words dialogue uses
 * ({@code mood(grief, strong)} in docs/narrative-reference.md). */
public enum EmotionIntensity {
	FAINT,
	CLEAR,
	STRONG;

	public static final Codec<EmotionIntensity> CODEC = Codec.STRING.xmap(EmotionIntensity::valueOf, EmotionIntensity::name);
}
