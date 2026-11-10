package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Adult body-proportion presets, mirroring MineLP's {@code SizePreset} minus {@code TALL} (reserved
 * for Alicorns, which never spawn naturally through this genetics system - see the design doc's Pony
 * Genetics section) and minus {@code YEARLING}/{@code FOAL}/{@code UNSET} (those come from the
 * entity's actual life-stage/age, not this heritable-ish gene).
 */
public enum PonyBodyType {
	BULKY,
	NORMAL,
	LANKY,
	STOCKY,
	SQUAT;

	public static final Codec<PonyBodyType> CODEC = Codec.STRING.xmap(PonyBodyType::valueOf, PonyBodyType::name);
	public static final PacketCodec<ByteBuf, PonyBodyType> PACKET_CODEC =
			PacketCodecs.STRING.xmap(PonyBodyType::valueOf, PonyBodyType::name).cast();
}
