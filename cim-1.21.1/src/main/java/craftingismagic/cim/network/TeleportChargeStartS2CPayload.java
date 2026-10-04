package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent to the caster only when a Teleport charge starts - how many ticks the full charge takes, for the HUD bar. */
public record TeleportChargeStartS2CPayload(int durationTicks) implements CustomPayload {
	public static final Id<TeleportChargeStartS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "teleport_charge_start"));
	public static final PacketCodec<RegistryByteBuf, TeleportChargeStartS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, TeleportChargeStartS2CPayload::durationTicks,
			TeleportChargeStartS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
