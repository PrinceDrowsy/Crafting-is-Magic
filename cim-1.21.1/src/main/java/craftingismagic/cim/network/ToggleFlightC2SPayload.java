package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player selects and activates the Flight ability - toggles Pegasus flight on or off. */
public record ToggleFlightC2SPayload() implements CustomPayload {
	public static final Id<ToggleFlightC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "toggle_flight"));
	public static final PacketCodec<RegistryByteBuf, ToggleFlightC2SPayload> CODEC =
			PacketCodec.unit(new ToggleFlightC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
