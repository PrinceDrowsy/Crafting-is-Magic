package craftingismagic.cim.story;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

/**
 * The world-scope story facts (see {@link StoryFacts}), saved with the world in the overworld's data
 * folder - the same storage {@code VillageSpawnState} uses, and shared by every dimension and player.
 * Each fact maps to the world time it was set.
 */
public class WorldFactsState extends PersistentState {
	public static final PersistentState.Type<WorldFactsState> TYPE =
			new PersistentState.Type<>(WorldFactsState::new, WorldFactsState::fromNbt, DataFixTypes.LEVEL);

	private static final String KEY = "cim_world_facts";

	private final Map<Identifier, Long> facts = new HashMap<>();

	public static WorldFactsState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	public boolean has(Identifier fact) {
		return facts.containsKey(fact);
	}

	public void set(Identifier fact, long time) {
		facts.put(fact, time);
		markDirty();
	}

	public boolean clear(Identifier fact) {
		boolean removed = facts.remove(fact) != null;
		if (removed) {
			markDirty();
		}
		return removed;
	}

	public Map<Identifier, Long> all() {
		return Map.copyOf(facts);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound factsNbt = new NbtCompound();
		facts.forEach((fact, time) -> factsNbt.putLong(fact.toString(), time));
		nbt.put("facts", factsNbt);
		return nbt;
	}

	private static WorldFactsState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		WorldFactsState state = new WorldFactsState();
		NbtCompound factsNbt = nbt.getCompound("facts");
		for (String key : factsNbt.getKeys()) {
			Identifier fact = Identifier.tryParse(key);
			if (fact != null) {
				state.facts.put(fact, factsNbt.getLong(key));
			}
		}
		return state;
	}
}
