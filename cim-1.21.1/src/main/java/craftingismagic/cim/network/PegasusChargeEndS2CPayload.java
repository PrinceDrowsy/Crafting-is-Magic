package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent to nearby players when a Pegasus's Sonic Rainboom charge ends, whether cancelled early or completed - tells clients to remove the charge visual for that entity. */
public record PegasusChargeEndS2CPayload(int entityId) implements CustomPayload {
	public static final Id<PegasusChargeEndS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "pegasus_charge_end"));
	public static final PacketCodec<RegistryByteBuf, PegasusChargeEndS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, PegasusChargeEndS2CPayload::entityId,
			PegasusChargeEndS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
