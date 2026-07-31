package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent on a fresh tap of the forward key while actively flying, to trigger a Pegasus dash. */
public record PegasusDashC2SPayload() implements CustomPayload {
	public static final Id<PegasusDashC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "pegasus_dash"));
	public static final PacketCodec<RegistryByteBuf, PegasusDashC2SPayload> CODEC =
			PacketCodec.unit(new PegasusDashC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
