package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent to nearby players when a Pegasus starts charging a Sonic Rainboom - which entity, and how long (in ticks) the full charge takes. */
public record PegasusChargeStartS2CPayload(int entityId, int durationTicks) implements CustomPayload {
	public static final Id<PegasusChargeStartS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "pegasus_charge_start"));
	public static final PacketCodec<RegistryByteBuf, PegasusChargeStartS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, PegasusChargeStartS2CPayload::entityId,
			PacketCodecs.VAR_INT, PegasusChargeStartS2CPayload::durationTicks,
			PegasusChargeStartS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
