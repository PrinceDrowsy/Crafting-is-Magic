package craftingismagic.cim.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * One beat of a conversation for the dialogue screen: the lines to show, and either the replies to
 * pick from or the end. Opens the screen if it isn't open yet.
 */
public record DialogueBeatS2CPayload(int entityId, String partnerName, List<LineData> lines, List<OptionData> options,
		boolean ended) implements CustomPayload {
	public static final Id<DialogueBeatS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "dialogue_beat"));

	/** Line kinds, matching {@code TaleRuntime.LineKind}. */
	public static final int SPOKEN = 0;
	public static final int VOICE = 1;
	public static final int SUCCESS = 2;
	public static final int FAILURE = 3;

	/**
	 * @param kind    {@link #SPOKEN}, {@link #VOICE}, {@link #SUCCESS}, or {@link #FAILURE}
	 * @param voice   the voice for every kind but spoken; "" otherwise
	 * @param speaker who says a spoken line, for its name tag; "" otherwise
	 * @param partner a spoken line by the pony you're talking to
	 * @param text    the words alone, no name
	 */
	public record LineData(int kind, String voice, String speaker, boolean partner, String text) {
		public static final PacketCodec<RegistryByteBuf, LineData> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT.cast(), LineData::kind,
				PacketCodecs.STRING.cast(), LineData::voice,
				PacketCodecs.STRING.cast(), LineData::speaker,
				PacketCodecs.BOOL.cast(), LineData::partner,
				PacketCodecs.STRING.cast(), LineData::text,
				LineData::new);
	}

	/**
	 * @param tone     a tone reply's tone ("friendly"), or ""
	 * @param voice    a voice reply's voice ("kindness"), or ""
	 * @param text     the reply's words, or "" to show the tone instead
	 * @param oddsBand for a voice reply, its odds as a phrase band (0 a long shot ... 4 almost
	 *                 certain); -1 for a tone reply. Never the number itself
	 */
	public record OptionData(String tone, String voice, String text, int oddsBand) {
		public static final PacketCodec<RegistryByteBuf, OptionData> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING.cast(), OptionData::tone,
				PacketCodecs.STRING.cast(), OptionData::voice,
				PacketCodecs.STRING.cast(), OptionData::text,
				PacketCodecs.VAR_INT.cast(), OptionData::oddsBand,
				OptionData::new);
	}

	public static final PacketCodec<RegistryByteBuf, DialogueBeatS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DialogueBeatS2CPayload::entityId,
			PacketCodecs.STRING.cast(), DialogueBeatS2CPayload::partnerName,
			LineData.CODEC.collect(PacketCodecs.toList()), DialogueBeatS2CPayload::lines,
			OptionData.CODEC.collect(PacketCodecs.toList()), DialogueBeatS2CPayload::options,
			PacketCodecs.BOOL.cast(), DialogueBeatS2CPayload::ended,
			DialogueBeatS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
