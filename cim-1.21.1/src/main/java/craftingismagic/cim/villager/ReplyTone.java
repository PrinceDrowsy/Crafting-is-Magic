package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** The three reply choices in an Idle Chatter conversation - see {@link PersonalityType#toneAffinity()}. */
public enum ReplyTone {
	FRIENDLY,
	CURIOUS,
	BOLD;

	public static final Codec<ReplyTone> CODEC = Codec.STRING.xmap(ReplyTone::valueOf, ReplyTone::name);
	public static final PacketCodec<ByteBuf, ReplyTone> PACKET_CODEC =
			PacketCodecs.STRING.xmap(ReplyTone::valueOf, ReplyTone::name).cast();
}
