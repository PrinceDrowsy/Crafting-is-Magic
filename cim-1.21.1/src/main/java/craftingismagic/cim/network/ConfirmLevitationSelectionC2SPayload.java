package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player releases the activation key while selecting, to lock in and pick up the selection. */
public record ConfirmLevitationSelectionC2SPayload() implements CustomPayload {
	public static final Id<ConfirmLevitationSelectionC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "confirm_levitation_selection"));
	public static final PacketCodec<RegistryByteBuf, ConfirmLevitationSelectionC2SPayload> CODEC =
			PacketCodec.unit(new ConfirmLevitationSelectionC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
