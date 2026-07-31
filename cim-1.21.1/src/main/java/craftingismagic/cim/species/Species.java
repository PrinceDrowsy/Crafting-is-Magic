package craftingismagic.cim.species;

import com.mojang.serialization.Codec;

public enum Species {
	EARTH_PONY,
	UNICORN,
	PEGASUS,
	BAT_PONY,
	CHANGELING,
	KIRIN;

	public static final Codec<Species> CODEC = Codec.STRING.xmap(Species::valueOf, Species::name);

	public String translationKey() {
		return "species.cim." + name().toLowerCase();
	}
}
