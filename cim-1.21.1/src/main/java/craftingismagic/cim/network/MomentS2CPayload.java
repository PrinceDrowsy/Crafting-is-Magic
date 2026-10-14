package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Starts a scene-setting moment on this player's screen (README: The mood system - "set the scene,
 * never label the feeling"). Client-side only and seen only by this player; the client decides what
 * the moment looks and sounds like, within their comfort settings.
 */
public record MomentS2CPayload(Identifier moment) implements CustomPayload {
	public static final Id<MomentS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "moment"));
	public static final PacketCodec<RegistryByteBuf, MomentS2CPayload> CODEC =
			Identifier.PACKET_CODEC.<RegistryByteBuf>cast().xmap(MomentS2CPayload::new, MomentS2CPayload::moment);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
