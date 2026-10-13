package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;

/** The owning player's current Stamina, sent by {@code StaminaSync} on meaningful changes only - see
 * that class for why this replaced the attachment's own per-write sync. */
public record StaminaSyncS2CPayload(Stamina stamina) implements CustomPayload {
	public static final Id<StaminaSyncS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "stamina_sync"));
	public static final PacketCodec<RegistryByteBuf, StaminaSyncS2CPayload> CODEC = PacketCodec.tuple(
			Stamina.PACKET_CODEC, StaminaSyncS2CPayload::stamina, StaminaSyncS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
