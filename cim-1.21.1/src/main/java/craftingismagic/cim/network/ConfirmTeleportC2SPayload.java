package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Sent when the player taps the activation key with Teleport selected - a single instant blink to
 * wherever they're currently looking, so this carries no data of its own; the server re-derives
 * the player's current look direction itself rather than trusting anything client-supplied.
 */
public record ConfirmTeleportC2SPayload() implements CustomPayload {
	public static final Id<ConfirmTeleportC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "confirm_teleport"));
	public static final PacketCodec<RegistryByteBuf, ConfirmTeleportC2SPayload> CODEC =
			PacketCodec.unit(new ConfirmTeleportC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
