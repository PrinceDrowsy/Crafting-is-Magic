package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The shared quirk pool (design doc: Personality system, Quirks) - 2-3 rolled per pony. {@code
 * SWEET_TOOTH}/{@code COLLECTOR}/{@code SENTIMENTAL}/{@code GRUDGE_HOLDER} are the 4 with a
 * described mechanical hook (Give Gift/grudge-decay effects - not built by this pass, just the
 * enum + assignment); the rest are flavor-only today, some (NIGHT_OWL/EARLY_RISER/GOSSIP) as
 * intentional hooks for future systems that don't exist yet (day/night schedule, Ask a Rumor).
 */
public enum Quirk {
	SWEET_TOOTH,
	COLLECTOR,
	SENTIMENTAL,
	GRUDGE_HOLDER,
	SUPERSTITIOUS,
	CLUMSY,
	SECRETLY_ARTISTIC,
	COMPETITIVE_STREAK,
	NEAT_FREAK,
	HOMEBODY,
	WANDERLUST,
	NIGHT_OWL,
	EARLY_RISER,
	GOSSIP;

	public static final Codec<Quirk> CODEC = Codec.STRING.xmap(Quirk::valueOf, Quirk::name);
	public static final PacketCodec<ByteBuf, Quirk> PACKET_CODEC =
			PacketCodecs.STRING.xmap(Quirk::valueOf, Quirk::name).cast();
}
