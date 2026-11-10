package craftingismagic.cim.villager;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The 8 personality base types (design doc: Personality system). {@link #toneAffinity()} is the
 * concrete mechanical hook - the {@link ReplyTone} an Idle Chatter reply-tone pick matches for a
 * bonus, per the design doc's exact mapping (Lazy leans toward none, "doesn't care either way").
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

	/**
	 * The Elements this kind of pony cares about most, as voice names (design doc: Friendship, deepened -
	 * "examples, not final"). Voice replies in a valued Element roll better with them. Daring's
	 * "courage" and Cranky's "usefulness" aren't voices, so only their Element counts.
	 */
	public List<String> valuedElements() {
		return switch (this) {
			case STEADFAST -> List.of("loyalty", "honesty");
			case PEPPY -> List.of("laughter");
			case GENTLE -> List.of("kindness");
			case REFINED -> List.of("generosity");
			case BOOKISH -> List.of("magic");
			case DARING -> List.of("loyalty");
			case CRANKY -> List.of("honesty");
			case LAZY -> List.of();
		};
	}

	public static final Codec<PersonalityType> CODEC = Codec.STRING.xmap(PersonalityType::valueOf, PersonalityType::name);
	public static final PacketCodec<ByteBuf, PersonalityType> PACKET_CODEC =
			PacketCodecs.STRING.xmap(PersonalityType::valueOf, PersonalityType::name).cast();
}
