package craftingismagic.cim.species;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public enum Species {
	EARTH_PONY,
	UNICORN,
	PEGASUS,
	BAT_PONY,
	CHANGELING,
	KIRIN;

	public static final Codec<Species> CODEC = Codec.STRING.xmap(Species::valueOf, Species::name);
	public static final PacketCodec<RegistryByteBuf, Species> PACKET_CODEC =
			PacketCodecs.STRING.xmap(Species::valueOf, Species::name).cast();

	public String translationKey() {
		return "species.cim." + name().toLowerCase();
	}
}
