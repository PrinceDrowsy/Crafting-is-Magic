package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player presses the activation key while Teleport is selected, to start charging toward wherever they're looking. */
public record StartTeleportChargeC2SPayload() implements CustomPayload {
	public static final Id<StartTeleportChargeC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "start_teleport_charge"));
	public static final PacketCodec<RegistryByteBuf, StartTeleportChargeC2SPayload> CODEC =
			PacketCodec.unit(new StartTeleportChargeC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
