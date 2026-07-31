package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the client detects the forward key was released - cancels an in-progress Sonic Rainboom charge, if any. */
public record PegasusChargeReleaseC2SPayload() implements CustomPayload {
	public static final Id<PegasusChargeReleaseC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "pegasus_charge_release"));
	public static final PacketCodec<RegistryByteBuf, PegasusChargeReleaseC2SPayload> CODEC =
			PacketCodec.unit(new PegasusChargeReleaseC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
