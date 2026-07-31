package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player selects Levitation from the radial menu. */
public record StartLevitationC2SPayload() implements CustomPayload {
	public static final Id<StartLevitationC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "start_levitation"));
	public static final PacketCodec<RegistryByteBuf, StartLevitationC2SPayload> CODEC =
			PacketCodec.unit(new StartLevitationC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
