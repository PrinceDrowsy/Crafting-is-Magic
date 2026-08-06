package craftingismagic.cim.villager;

import java.util.Optional;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The 8 personality base types (README: Personality system). {@link #toneAffinity()} is the
 * concrete mechanical hook - the {@link ReplyTone} an Idle Chatter reply-tone pick matches for a
 * bonus, per the README's exact mapping (Lazy leans toward none, "doesn't care either way").
 */
public enum PersonalityType {
	PEPPY(ReplyTone.FRIENDLY),
	STEADFAST(ReplyTone.FRIENDLY),
	BOOKISH(ReplyTone.CURIOUS),
	DARING(ReplyTone.BOLD),
	GENTLE(ReplyTone.FRIENDLY),
	REFINED(ReplyTone.CURIOUS),
	CRANKY(ReplyTone.BOLD),
	LAZY(null);

	private final ReplyTone toneAffinity;

	PersonalityType(ReplyTone toneAffinity) {
		this.toneAffinity = toneAffinity;
	}

	public Optional<ReplyTone> toneAffinity() {
		return Optional.ofNullable(toneAffinity);
	}

	public static final Codec<PersonalityType> CODEC = Codec.STRING.xmap(PersonalityType::valueOf, PersonalityType::name);
	public static final PacketCodec<ByteBuf, PersonalityType> PACKET_CODEC =
			PacketCodecs.STRING.xmap(PersonalityType::valueOf, PersonalityType::name).cast();
}
