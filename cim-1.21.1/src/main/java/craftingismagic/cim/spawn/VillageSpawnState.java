package craftingismagic.cim.spawn;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/**
 * A one-shot flag, persisted per-world, tracking whether {@link VillageSpawnHandler} has already
 * tried relocating the world spawn into a village. Only ever set once, the first time the
 * overworld loads with this mod installed - never re-run on later starts, and never undone just
 * because {@link VillageSpawnConfig#spawnInVillage} gets toggled off afterward (the same way
 * vanilla's own initial spawn-point search only ever happens once at world creation).
 */
public class VillageSpawnState extends PersistentState {
	public static final PersistentState.Type<VillageSpawnState> TYPE =
			new PersistentState.Type<>(VillageSpawnState::new, VillageSpawnState::fromNbt, DataFixTypes.LEVEL);

	private boolean relocated;

	public boolean isRelocated() {
		return relocated;
	}

	public void markRelocated() {
		relocated = true;
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putBoolean("relocated", relocated);
		return nbt;
	}

	private static VillageSpawnState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		VillageSpawnState state = new VillageSpawnState();
		state.relocated = nbt.getBoolean("relocated");
		return state;
	}
}
