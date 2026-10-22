package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;

import craftingismagic.cim.block.PaintedBed;

/** Beds keep the wall paint behind them ({@link PaintedBed}), saved and sent to clients with the bed. */
@Mixin(BedBlockEntity.class)
public abstract class BedPaintMixin extends BlockEntity implements PaintedBed {
	@Unique
	private DyeColor cim$paintColor;
	@Unique
	private int cim$paintFaces;

	private BedPaintMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void cim$paint(DyeColor color, int faces) {
		cim$paintColor = faces == 0 ? null : color;
		cim$paintFaces = color == null ? 0 : faces;
		markDirty();
		if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
	}

	@Override
	public DyeColor cim$paintColor() {
		return cim$paintColor;
	}

	@Override
	public int cim$paintFaces() {
		return cim$paintFaces;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (cim$paintColor != null) {
			nbt.putString("cim:paint", cim$paintColor.getName());
			nbt.putInt("cim:paint_faces", cim$paintFaces);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		cim$paintColor = nbt.contains("cim:paint") ? DyeColor.byName(nbt.getString("cim:paint"), null) : null;
		cim$paintFaces = cim$paintColor == null ? 0 : nbt.getInt("cim:paint_faces");
	}

	@Override
	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	// Vanilla sends beds to clients with nothing in them; the paint goes along.
	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		NbtCompound nbt = new NbtCompound();
		writeNbt(nbt, registries);
		return nbt;
	}
}
