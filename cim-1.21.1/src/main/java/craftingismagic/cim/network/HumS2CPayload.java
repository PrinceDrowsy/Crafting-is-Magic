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
 * A pony nearby starts humming (README: Humming). The client plays the notes from them, in their voice,
 * with note particles - or skips it, depending on its Pony Humming setting.
 *
 * @param notes       scale degrees, {@code HumLibrary.REST} for a rest
 * @param shift       scale degrees added to every note - a pony harmonizing hums a third above
 * @param livelyOnly  only heard on the Lively setting; Soft hears just the occasional hum
 */
public record HumS2CPayload(int entityId, SpeakingVoice voice, String scale, int tempo, List<Integer> notes, int shift,
		boolean livelyOnly) implements CustomPayload {
	public static final Id<HumS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "hum"));

	private static final PacketCodec<io.netty.buffer.ByteBuf, List<Integer>> NOTES = PacketCodecs.INTEGER.collect(PacketCodecs.toList());

	/** Written out by hand - more fields than {@code PacketCodec.tuple} takes. */
	public static final PacketCodec<RegistryByteBuf, HumS2CPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.entityId());
				SpeakingVoice.PACKET_CODEC.encode(buf, payload.voice());
				buf.writeString(payload.scale());
				buf.writeVarInt(payload.tempo());
				NOTES.encode(buf, payload.notes());
				buf.writeVarInt(payload.shift());
				buf.writeBoolean(payload.livelyOnly());
			},
			buf -> new HumS2CPayload(buf.readVarInt(), SpeakingVoice.PACKET_CODEC.decode(buf), buf.readString(), buf.readVarInt(),
					NOTES.decode(buf), buf.readVarInt(), buf.readBoolean()));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
