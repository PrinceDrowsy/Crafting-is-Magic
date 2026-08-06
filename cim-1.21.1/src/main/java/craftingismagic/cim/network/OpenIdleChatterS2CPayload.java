package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Opens the client's Idle Chatter conversation screen with the picked topic's opening line. */
public record OpenIdleChatterS2CPayload(int villagerEntityId, String villagerName, String openingLine)
		implements CustomPayload {
	public static final Id<OpenIdleChatterS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "open_idle_chatter"));
	public static final PacketCodec<RegistryByteBuf, OpenIdleChatterS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), OpenIdleChatterS2CPayload::villagerEntityId,
			PacketCodecs.STRING.cast(), OpenIdleChatterS2CPayload::villagerName,
			PacketCodecs.STRING.cast(), OpenIdleChatterS2CPayload::openingLine,
			OpenIdleChatterS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
