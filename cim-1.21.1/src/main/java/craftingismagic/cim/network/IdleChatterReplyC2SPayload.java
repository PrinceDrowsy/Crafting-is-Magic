package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.ReplyTone;

/** Sent when the player picks a reply tone in the Idle Chatter conversation screen. */
public record IdleChatterReplyC2SPayload(int villagerEntityId, ReplyTone tone) implements CustomPayload {
	public static final Id<IdleChatterReplyC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "idle_chatter_reply"));
	public static final PacketCodec<RegistryByteBuf, IdleChatterReplyC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), IdleChatterReplyC2SPayload::villagerEntityId,
			ReplyTone.PACKET_CODEC.cast(), IdleChatterReplyC2SPayload::tone,
			IdleChatterReplyC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
