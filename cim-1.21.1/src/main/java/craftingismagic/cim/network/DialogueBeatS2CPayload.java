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
 *
 * @param lines       ready to show; lines by the conversation partner carry no name
 * @param optionTones each option's tone ("friendly"), or "" for none
 * @param optionTexts each option's text, or "" to show the tone instead
 */
public record DialogueBeatS2CPayload(int entityId, String partnerName, List<String> lines, List<String> optionTones,
		List<String> optionTexts, boolean ended) implements CustomPayload {
	public static final Id<DialogueBeatS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "dialogue_beat"));
	public static final PacketCodec<RegistryByteBuf, DialogueBeatS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DialogueBeatS2CPayload::entityId,
			PacketCodecs.STRING.cast(), DialogueBeatS2CPayload::partnerName,
			PacketCodecs.STRING.collect(PacketCodecs.toList()).cast(), DialogueBeatS2CPayload::lines,
			PacketCodecs.STRING.collect(PacketCodecs.toList()).cast(), DialogueBeatS2CPayload::optionTones,
			PacketCodecs.STRING.collect(PacketCodecs.toList()).cast(), DialogueBeatS2CPayload::optionTexts,
			PacketCodecs.BOOL.cast(), DialogueBeatS2CPayload::ended,
			DialogueBeatS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
