package craftingismagic.cim.speech;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * How someone sounds when they talk, Undertale-style: a sound played per letter, at a pitch
 * (design doc: Speaking voices). Ponies speak with note block instruments; players may pick any sound
 * the game knows - a note block, a mob, a resource pack's own - by its ID.
 * <p>
 * A voice can also be <i>layered</i> and <i>melodic</i> - the princesses': extra sounds ring with each
 * blip in harmony (an octave, a fifth), and the blips walk a pentatonic scale instead of wobbling, so
 * speech comes out as a little tune that never clashes with itself.
 *
 * @param sound   a sound event ID, e.g. {@code minecraft:block.note_block.harp}
 * @param pitch   the voice's own pitch, 0.5 (deep) to 2.0 (squeaky); for a melodic voice, the scale's root
 * @param layers sounds that ring along with each blip
 * @param scale  "" for an ordinary voice (it wobbles around its pitch); otherwise the scale its blips
 *               walk from {@code pitch} - {@code major} (bright, open: day), {@code minor} (melancholy,
 *               introspective: night), {@code dorian} (noble, bittersweet: a regal night), {@code lydian}
 *               (dreamy wonder: magic). See design doc: Princess
 *               voice motifs.
 * @param drift  -1 to 1: how much its melody tends to fall (lonely, settling) or rise (hopeful, rising)
 * @param leap   the most scale steps one note moves from the last: 1 flows like a sung line (smooth,
 *               melodious), 2 skips about more (lively, sparkly)
 * @param pace   how fast they talk, relative to the reader's text speed: 0.7 is slow and deliberate (Celestia
 *               imparting wisdom), 1 normal. The pauses after commas and periods stretch with it.
 * @param motif  a signature tune their speech tends to follow, word by word - scale degrees, restarting
 *               with each sentence, lightly varied - instead of wandering (each princess has her own). Empty
 *               for none: a melodic voice then walks its scale by {@code drift} and {@code leap}.
 */
public record SpeakingVoice(Identifier sound, float pitch, List<Layer> layers, String scale, float drift, int leap, float pace,
		List<Integer> motif) {
	public static final float MIN_PITCH = 0.5f;
	public static final float MAX_PITCH = 2.0f;

	/**
	 * One more sound with each blip.
	 *
	 * @param interval its pitch relative to the blip's note - 2.0 an octave up, 1.5 a fifth - so it stays in harmony
	 * @param volume   relative to the blip's own
	 * @param delayMs  how long after the blip it rings - a hair late reads as shimmer and echo
	 * @param every    rings on every Nth blip only (1 = every blip) - a sparkle now and then
	 */
	public record Layer(Identifier sound, float interval, float volume, int delayMs, int every) {
		public static final Codec<Layer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("sound").forGetter(Layer::sound),
				Codec.FLOAT.optionalFieldOf("interval", 1.0f).forGetter(Layer::interval),
				Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(Layer::volume),
				Codec.INT.optionalFieldOf("delay_ms", 0).forGetter(Layer::delayMs),
				Codec.INT.optionalFieldOf("every", 1).forGetter(Layer::every)
		).apply(instance, Layer::new));

		public static final PacketCodec<RegistryByteBuf, Layer> PACKET_CODEC = PacketCodec.tuple(
				Identifier.PACKET_CODEC, Layer::sound,
				PacketCodecs.FLOAT, Layer::interval,
				PacketCodecs.FLOAT, Layer::volume,
				PacketCodecs.VAR_INT.cast(), Layer::delayMs,
				PacketCodecs.VAR_INT.cast(), Layer::every,
				Layer::new);

		public Layer {
			every = Math.max(1, every);
			delayMs = Math.max(0, delayMs);
		}
	}

	public static final Codec<SpeakingVoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("sound").forGetter(SpeakingVoice::sound),
			Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(SpeakingVoice::pitch),
			Layer.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(SpeakingVoice::layers),
			Codec.STRING.optionalFieldOf("scale", "").forGetter(SpeakingVoice::scale),
			Codec.FLOAT.optionalFieldOf("drift", 0f).forGetter(SpeakingVoice::drift),
			Codec.INT.optionalFieldOf("leap", 2).forGetter(SpeakingVoice::leap),
			Codec.FLOAT.optionalFieldOf("pace", 1f).forGetter(SpeakingVoice::pace),
			Codec.INT.listOf().optionalFieldOf("motif", List.of()).forGetter(SpeakingVoice::motif)
	).apply(instance, SpeakingVoice::new));

	private static final PacketCodec<io.netty.buffer.ByteBuf, List<Integer>> MOTIF = PacketCodecs.INTEGER.collect(PacketCodecs.toList());

	private static final PacketCodec<RegistryByteBuf, List<Layer>> LAYERS = Layer.PACKET_CODEC.collect(PacketCodecs.toList());

	/** Written out by hand - more fields than {@code PacketCodec.tuple} takes. */
	public static final PacketCodec<RegistryByteBuf, SpeakingVoice> PACKET_CODEC = PacketCodec.of(
			(voice, buf) -> {
				Identifier.PACKET_CODEC.encode(buf, voice.sound());
				buf.writeFloat(voice.pitch());
				LAYERS.encode(buf, voice.layers());
				buf.writeString(voice.scale());
				buf.writeFloat(voice.drift());
				buf.writeVarInt(voice.leap());
				buf.writeFloat(voice.pace());
				MOTIF.encode(buf, voice.motif());
			},
			buf -> new SpeakingVoice(Identifier.PACKET_CODEC.decode(buf), buf.readFloat(), LAYERS.decode(buf), buf.readString(),
					buf.readFloat(), buf.readVarInt(), buf.readFloat(), MOTIF.decode(buf)));

	/** A plain voice: one sound at one pitch. */
	public SpeakingVoice(Identifier sound, float pitch) {
		this(sound, pitch, List.of(), "", 0f, 2, 1f, List.of());
	}

	public SpeakingVoice {
		pitch = Math.clamp(pitch, MIN_PITCH, MAX_PITCH);
		layers = List.copyOf(layers);
		drift = Math.clamp(drift, -1f, 1f);
		leap = Math.clamp(leap, 1, 3);
		pace = Math.clamp(pace, 0.4f, 1.6f);
		motif = List.copyOf(motif);
	}

	/** Whether its blips walk a scale (a melody) rather than wobbling around one pitch. */
	public boolean melodic() {
		return !scale.isEmpty();
	}

	public SpeakingVoice withPitch(float newPitch) {
		return new SpeakingVoice(sound, newPitch, layers, scale, drift, leap, pace, motif);
	}
}
