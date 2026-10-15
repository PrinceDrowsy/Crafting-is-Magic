package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.speech.SpeakingVoice;

/**
 * A player near you said something in chat: their voice murmurs from where they stand. Each client
 * plays it (or not - player voices can be switched off), so the server only says who and how much.
 *
 * @param entityId the speaker
 * @param letters  how many letters they typed, which sets how long the murmur lasts
 */
public record ChatVoiceS2CPayload(int entityId, SpeakingVoice voice, int letters) implements CustomPayload {
	public static final Id<ChatVoiceS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "chat_voice"));

	public static final PacketCodec<RegistryByteBuf, ChatVoiceS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT.cast(), ChatVoiceS2CPayload::entityId,
			SpeakingVoice.PACKET_CODEC, ChatVoiceS2CPayload::voice,
			PacketCodecs.VAR_INT.cast(), ChatVoiceS2CPayload::letters,
			ChatVoiceS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
