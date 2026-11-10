package craftingismagic.cim.memory;

import com.mojang.serialization.Codec;

/**
 * The feeling a memory carries (design doc: The mood system - mood is a query over emotional
 * memories). Matches the emotions dialogue can check in design/narrative-reference.md.
 */
public enum Emotion {
	JOY,
	GRIEF,
	FEAR,
	ANGER,
	LOVE,
	CALM;

	public static final Codec<Emotion> CODEC = Codec.STRING.xmap(Emotion::valueOf, Emotion::name);
}
