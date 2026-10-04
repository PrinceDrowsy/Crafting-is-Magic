package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Sent when the player releases the activation key before a Teleport charge finishes on its own -
 * cancels the charge the same way running out of Stamina mid-charge does (no free/costless cancel;
 * whatever's already been spent stays spent). A no-op server-side if the charge already
 * completed/fizzled on its own before this arrives.
 */
public record CancelTeleportChargeC2SPayload() implements CustomPayload {
	public static final Id<CancelTeleportChargeC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "cancel_teleport_charge"));
	public static final PacketCodec<RegistryByteBuf, CancelTeleportChargeC2SPayload> CODEC =
			PacketCodec.unit(new CancelTeleportChargeC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
