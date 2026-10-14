package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The three feelings a pony holds toward a player, replacing the old single affection number
 * (README: Friendship, deepened - "Three feelings instead of one meter"). What separates "a co-worker
 * I like" (Respect without much Fondness) from "a work friend" (Fondness without much Trust) from a
 * true best friend (all three).
 */
public enum Feeling {
	/** Enjoying the player's company. */
	FONDNESS,
	/** Admiring how the player does things - earned by what a pony witnesses. */
	RESPECT,
	/** Willing to share what matters - secrets, history, the truth. */
	TRUST;

	public static final Codec<Feeling> CODEC = Codec.STRING.xmap(Feeling::valueOf, Feeling::name);
	public static final PacketCodec<ByteBuf, Feeling> PACKET_CODEC =
			PacketCodecs.STRING.xmap(Feeling::valueOf, Feeling::name).cast();
}
