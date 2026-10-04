package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent to the caster only when a Teleport charge stops, for any reason (completed, fizzled, or cancelled) - clears the HUD bar. */
public record TeleportChargeEndS2CPayload() implements CustomPayload {
	public static final Id<TeleportChargeEndS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "teleport_charge_end"));
	public static final PacketCodec<RegistryByteBuf, TeleportChargeEndS2CPayload> CODEC =
			PacketCodec.unit(new TeleportChargeEndS2CPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
