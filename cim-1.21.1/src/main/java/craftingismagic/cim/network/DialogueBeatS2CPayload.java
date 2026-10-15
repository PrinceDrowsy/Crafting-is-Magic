package craftingismagic.cim.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.speech.SpeakingVoice;

/**
 * One beat of a conversation for the dialogue screen: the lines to show, and either the replies to
 * pick from or the end. Opens the screen if it isn't open yet.
 */
public record DialogueBeatS2CPayload(int entityId, String partnerName, List<LineData> lines, List<OptionData> options,
		boolean ended, Scene scene) implements CustomPayload {
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

	/**
	 * Something the player can bring up in conversation (README: Conversations replace the interact menu).
	 *
	 * @param id        the request, e.g. {@code cim:gift}; its words are {@code request.<namespace>.<path>}
	 * @param needsItem whether the player picks an item from their inventory first (a gift)
	 */
	public record RequestData(Identifier id, boolean needsItem) {
		public static final PacketCodec<RegistryByteBuf, RequestData> CODEC = PacketCodec.tuple(
				Identifier.PACKET_CODEC, RequestData::id,
				PacketCodecs.BOOL.cast(), RequestData::needsItem,
				RequestData::new);
	}

	/**
	 * Everything around the lines: how the pony and the player sound (README: Speaking voices), what
	 * the player can bring up, and what happens once the lines are read.
	 *
	 * @param followUp "" for nothing; {@code action} for something the screen asks the server to do once
	 *                 the lines have shown and had a moment to sink in (the trade screen opening, so
	 *                 "Sure, take a look!" is read first); {@code close} when these lines are the pony's
	 *                 goodbye, and the screen closes after them
	 */
	public record Scene(SpeakingVoice partner, SpeakingVoice you, List<RequestData> requests, String followUp) {
		public static final PacketCodec<RegistryByteBuf, Scene> CODEC = PacketCodec.tuple(
				SpeakingVoice.PACKET_CODEC, Scene::partner,
				SpeakingVoice.PACKET_CODEC, Scene::you,
				RequestData.CODEC.collect(PacketCodecs.toList()), Scene::requests,
				PacketCodecs.STRING.cast(), Scene::followUp,
				Scene::new);
	}

	public static final PacketCodec<RegistryByteBuf, DialogueBeatS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DialogueBeatS2CPayload::entityId,
			PacketCodecs.STRING.cast(), DialogueBeatS2CPayload::partnerName,
			LineData.CODEC.collect(PacketCodecs.toList()), DialogueBeatS2CPayload::lines,
			OptionData.CODEC.collect(PacketCodecs.toList()), DialogueBeatS2CPayload::options,
			PacketCodecs.BOOL.cast(), DialogueBeatS2CPayload::ended,
			Scene.CODEC, DialogueBeatS2CPayload::scene,
			DialogueBeatS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
