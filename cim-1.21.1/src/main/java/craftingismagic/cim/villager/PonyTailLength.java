package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Mirrors MineLP's {@code TailLength} (STUB/QUARTER/HALF/THREE_QUARTERS/FULL), same reasoning as
 * {@link PonyBodyType} - keeps {@link PonyGeneticsAttachment.PonyGenetics} free of a hard MineLP
 * dependency on the server (MineLP is a client-side, {@code modCompileOnly} rendering mod), with
 * the mapping to MineLP's actual enum living client-side in {@code CimVillagerPonyRenderer}.
 */
public enum PonyTailLength {
	STUB,
	QUARTER,
	HALF,
	THREE_QUARTERS,
	FULL;

	public static final Codec<PonyTailLength> CODEC = Codec.STRING.xmap(PonyTailLength::valueOf, PonyTailLength::name);
	public static final PacketCodec<ByteBuf, PonyTailLength> PACKET_CODEC =
			PacketCodecs.STRING.xmap(PonyTailLength::valueOf, PonyTailLength::name).cast();
}
