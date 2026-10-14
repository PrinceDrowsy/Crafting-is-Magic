package craftingismagic.cim.memory;

import com.mojang.serialization.Codec;

/**
 * How much a memory matters - which decides what gets forgotten (README: The memory system -
 * "trivial memories fade, important ones never do"). Ordered least to most important.
 */
public enum MemoryImportance {
	/** A passing moment - fades after a few days, and is first to go when memory is full. */
	TRIVIAL,
	/** Worth keeping - only forgotten to make room, oldest first. */
	NOTABLE,
	/** A big moment - forgotten only after every lesser memory is gone. */
	MAJOR,
	/** Never forgotten, never pushed out - the shared-history floor of a friendship. */
	INDELIBLE;

	public static final Codec<MemoryImportance> CODEC = Codec.STRING.xmap(MemoryImportance::valueOf, MemoryImportance::name);
}
