package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Mirrors MineLP's {@code TailShape} (STRAIGHT/BUMPY/SWIRLY/SPIKY) - see {@link PonyTailLength}
 * for why this is a separate mod-owned enum rather than using MineLP's type directly.
 */
public enum PonyTailShape {
	STRAIGHT,
	BUMPY,
	SWIRLY,
	SPIKY;

	public static final Codec<PonyTailShape> CODEC = Codec.STRING.xmap(PonyTailShape::valueOf, PonyTailShape::name);
	public static final PacketCodec<ByteBuf, PonyTailShape> PACKET_CODEC =
			PacketCodecs.STRING.xmap(PonyTailShape::valueOf, PonyTailShape::name).cast();
}
