package craftingismagic.cim.memory;

import com.mojang.serialization.Codec;

/**
 * How the holder knows what they remember (design doc: The memory system). Gossip turns one into the
 * next: a pony who witnessed something tells friends, who hold it as heard; retold over generations
 * it becomes legend - and may drift.
 */
public enum MemoryKnowledge {
	/** They were there. */
	WITNESSED,
	/** Gossip and reputation - possibly exaggerated or wrong. */
	HEARD,
	/** Old and retold, possibly distorted. */
	LEGEND;

	public static final Codec<MemoryKnowledge> CODEC = Codec.STRING.xmap(MemoryKnowledge::valueOf, MemoryKnowledge::name);
}
