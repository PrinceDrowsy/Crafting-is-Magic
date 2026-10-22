package craftingismagic.cim.block;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * What a room marker remembers: who the room was last furnished for, and exactly which blocks that
 * furnishing placed - so the next residents' furnishing can clear it without touching the house itself.
 */
public class RoomMarkerBlockEntity extends BlockEntity {
	private long[] placed = new long[0];
	private UUID resident;

	public RoomMarkerBlockEntity(BlockPos pos, BlockState state) {
		super(CimBlocks.ROOM_MARKER_ENTITY, pos, state);
	}

	public long[] placed() {
		return placed;
	}

	public Optional<UUID> resident() {
		return Optional.ofNullable(resident);
	}

	public void furnished(UUID resident, long[] placed) {
		this.resident = resident;
		this.placed = placed;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putLongArray("placed", placed);
		if (resident != null) nbt.putUuid("resident", resident);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		placed = nbt.getLongArray("placed");
		resident = nbt.containsUuid("resident") ? nbt.getUuid("resident") : null;
	}
}
