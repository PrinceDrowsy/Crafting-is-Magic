package craftingismagic.cim.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Where a pony's - or a player's - memories live (README: The memory system; docs/implementation-plan.md
 * phase 1). One entity attachment serves both: it loads and unloads with the entity, survives death
 * and conversion ({@code copyOnDeath} - curing a zombie villager doesn't wipe what it remembered), and
 * is never synced, so one player's history with a pony never reaches another player's client.
 * <p>
 * Kept small and fast, per the README:
 * <ul>
 * <li><b>Capped</b> at {@link #NPC_CAP} memories for a pony and {@link #PLAYER_CAP} for a player.</li>
 * <li><b>Trivial memories fade</b> after {@link #TRIVIAL_LIFETIME_TICKS}, checked whenever a new memory
 * is added (no ticking).</li>
 * <li><b>When full</b>, the least important memory goes first, oldest first within the same importance.
 * {@link MemoryImportance#INDELIBLE} memories are never pushed out; if every slot is indelible, the new
 * memory is simply kept on top of the cap.</li>
 * </ul>
 * Village and world memories (legends, history) live in world-level storage, not here.
 */
public final class MemoryStore {
	public static final int NPC_CAP = 32;
	public static final int PLAYER_CAP = 64;
	/** Three in-game days. */
	public static final long TRIVIAL_LIFETIME_TICKS = 3 * 24000L;

	public static final AttachmentType<List<Memory>> MEMORIES = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "memories"),
			builder -> builder.persistent(Memory.CODEC.listOf())
					.copyOnDeath());

	private MemoryStore() {
	}

	public static List<Memory> getAll(Entity entity) {
		return entity.hasAttached(MEMORIES) ? entity.getAttached(MEMORIES) : List.of();
	}

	/** Records a memory, dropping faded trivial memories and, if still over the cap, the least
	 * important one. */
	public static void add(Entity entity, Memory memory) {
		long now = entity.getWorld().getTime();
		List<Memory> memories = new ArrayList<>(getAll(entity));
		memories.removeIf(existing -> isFaded(existing, now));
		memories.add(memory);

		int cap = entity instanceof PlayerEntity ? PLAYER_CAP : NPC_CAP;
		while (memories.size() > cap) {
			Memory leastImportant = memories.stream()
					.filter(existing -> existing.importance() != MemoryImportance.INDELIBLE)
					.min(Comparator.comparing(Memory::importance).thenComparingLong(Memory::time))
					.orElse(null);
			if (leastImportant == null) {
				break;
			}
			memories.remove(leastImportant);
		}
		entity.setAttached(MEMORIES, List.copyOf(memories));
	}

	/** Removes every memory matching the filter; returns how many were removed. */
	public static int remove(Entity entity, Predicate<Memory> filter) {
		List<Memory> memories = new ArrayList<>(getAll(entity));
		int before = memories.size();
		memories.removeIf(filter);
		if (memories.size() != before) {
			entity.setAttached(MEMORIES, List.copyOf(memories));
		}
		return before - memories.size();
	}

	public static void clear(Entity entity) {
		entity.removeAttached(MEMORIES);
	}

	/** Whether this entity holds a still-remembered memory of the given type - what dialogue's
	 * {@code remembers(...)} condition will ask. */
	public static boolean remembers(Entity entity, Identifier type) {
		long now = entity.getWorld().getTime();
		return getAll(entity).stream().anyMatch(memory -> memory.type().equals(type) && !isFaded(memory, now));
	}

	/** Every still-remembered memory this entity holds about someone. */
	public static List<Memory> about(Entity entity, UUID subject) {
		long now = entity.getWorld().getTime();
		return getAll(entity).stream().filter(memory -> memory.isAbout(subject) && !isFaded(memory, now)).toList();
	}

	private static boolean isFaded(Memory memory, long now) {
		return memory.importance() == MemoryImportance.TRIVIAL && now - memory.time() > TRIVIAL_LIFETIME_TICKS;
	}
}
