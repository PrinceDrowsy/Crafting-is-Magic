package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import craftingismagic.cim.Cim;

/**
 * Tells the selecting player the current selection's combined bounding box (min and max block
 * corners, inclusive) so the client can outline it - sent only to that player, since the
 * selection highlight is a personal aiming aid, not a public effect like the held-group aura.
 */
public record LevitationSelectionBoxS2CPayload(BlockPos min, BlockPos max) implements CustomPayload {
	public static final Id<LevitationSelectionBoxS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "levitation_selection_box"));
	public static final PacketCodec<RegistryByteBuf, LevitationSelectionBoxS2CPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC.cast(), LevitationSelectionBoxS2CPayload::min,
			BlockPos.PACKET_CODEC.cast(), LevitationSelectionBoxS2CPayload::max,
			LevitationSelectionBoxS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
