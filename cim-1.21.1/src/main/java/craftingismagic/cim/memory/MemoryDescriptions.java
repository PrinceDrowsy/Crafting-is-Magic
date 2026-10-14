package craftingismagic.cim.memory;

import java.util.Locale;

/** One readable line per memory for the debug tools (the command, the editor's Memories tab, the
 * overlay) - so they all describe a memory the same way. Never shown to players. */
public final class MemoryDescriptions {
	private MemoryDescriptions() {
	}

	public static String describe(Memory memory, long now) {
		String feeling = memory.emotion().map(e -> ", " + lower(memory.intensity()) + " " + lower(e)).orElse("");
		long daysAgo = Math.max(0, (now - memory.time()) / 24000L);
		return memory.type() + " (" + lower(memory.importance()) + ", " + lower(memory.knowledge()) + feeling + ", "
				+ daysAgo + (daysAgo == 1 ? " day ago)" : " days ago)");
	}

	private static String lower(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
