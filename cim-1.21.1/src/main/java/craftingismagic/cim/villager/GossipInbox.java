package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;

import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryStore;

/**
 * News waiting for a pony who wasn't loaded when it came (README: The memory system - "unloaded ponies
 * get an inbox"): word sent to family far away, delivered the moment they load. Saved with the world,
 * like the world facts; a few letters each, and old ones dropped.
 */
public class GossipInbox extends PersistentState {
	public static final PersistentState.Type<GossipInbox> TYPE =
			new PersistentState.Type<>(GossipInbox::new, GossipInbox::fromNbt, DataFixTypes.LEVEL);

	private static final String KEY = "cim_gossip_inbox";
	private static final int PER_PONY = 8;
	/** Word older than a season isn't news any more. */
	private static final long KEEP_TICKS = 30 * 24000L;
	private static final Codec<Map<UUID, List<Memory>>> CODEC = Codec.unboundedMap(Uuids.STRING_CODEC, Memory.CODEC.listOf());

	private final Map<UUID, List<Memory>> waiting = new HashMap<>();

	public static GossipInbox get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity pony) {
				get(world.getServer()).deliver(pony, world.getTime());
			}
		});
	}

	/** Holds {@code memory} for {@code pony} until they load. */
	public void send(UUID pony, Memory memory) {
		List<Memory> letters = new ArrayList<>(waiting.getOrDefault(pony, List.of()));
		letters.add(memory);
		while (letters.size() > PER_PONY) letters.remove(0);
		waiting.put(pony, List.copyOf(letters));
		markDirty();
	}

	private void deliver(VillagerEntity pony, long now) {
		List<Memory> letters = waiting.remove(pony.getUuid());
		if (letters == null) return;
		markDirty();
		for (Memory memory : letters) {
			if (now - memory.time() <= KEEP_TICKS) MemoryStore.add(pony, memory);
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		CODEC.encodeStart(NbtOps.INSTANCE, waiting).result().ifPresent(encoded -> nbt.put("waiting", encoded));
		return nbt;
	}

	private static GossipInbox fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		GossipInbox inbox = new GossipInbox();
		if (nbt.contains("waiting")) {
			CODEC.parse(NbtOps.INSTANCE, nbt.get("waiting")).result().ifPresent(inbox.waiting::putAll);
		}
		return inbox;
	}
}
