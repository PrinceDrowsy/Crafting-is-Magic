package craftingismagic.cim.client.dialogue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.network.ChatVoiceS2CPayload;
import craftingismagic.cim.network.HumS2CPayload;
import craftingismagic.cim.speech.HumLibrary;
import craftingismagic.cim.speech.SpeakingVoice;
import net.minecraft.particle.ParticleTypes;

/**
 * Plays speaking voices (design doc: Speaking voices): one blip in the dialogue screen, or a short murmur
 * from a player who just chatted. Any sound ID works - a resource pack's own sounds too, since the
 * sound manager looks them up by ID.
 * <p>
 * A melodic voice (the princesses') walks a pentatonic scale instead of wobbling, and its layers ring
 * along with each note at their harmony interval, a few milliseconds late - shimmer and echo, never a
 * clash. Those delays are kept here and played by {@link #flush}, every frame and every tick.
 */
public final class SpeechSounds {
	/** Chat murmurs: about a blip per three letters, but never a long drone. */
	private static final int MAX_MURMUR_BLIPS = 12;
	private static final int TICKS_BETWEEN_BLIPS = 2;
	/**
	 * The scales a melodic voice can walk (design doc: Princess voice motifs), as pitch ratios from the root.
	 * The pentatonics leave out the notes that rub, so nothing sounds sour; Lydian keeps its raised
	 * fourth on purpose - that note is what film scores use for magic and wonder.
	 */
	private static final java.util.Map<String, float[]> SCALES = java.util.Map.of(
			"major", new float[] {1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f},
			"minor", new float[] {1f, 6f / 5f, 4f / 3f, 3f / 2f, 9f / 5f},
			// Dorian: a minor with a raised sixth - noble and bittersweet rather than plainly sad (Luna).
			"dorian", new float[] {1f, 9f / 8f, 6f / 5f, 4f / 3f, 3f / 2f, 5f / 3f},
			"lydian", new float[] {1f, 9f / 8f, 5f / 4f, 45f / 32f, 3f / 2f, 5f / 3f});

	/** Plays one sound - in the screen, or from where someone stands. */
	@FunctionalInterface
	private interface Out {
		void play(Identifier sound, float pitch, float volume);
	}

	private static final class Murmur {
		final int entityId;
		final SpeakingVoice voice;
		int blipsLeft;
		int ticksUntilNext;

		Murmur(int entityId, SpeakingVoice voice, int blips) {
			this.entityId = entityId;
			this.voice = voice;
			this.blipsLeft = blips;
		}
	}

	private record Pending(long dueNanos, Runnable play) {
	}

	private static final List<Murmur> murmurs = new ArrayList<>();
	private static final List<Pending> pending = new ArrayList<>();
	private static final Random random = Random.create();
	private static int melodyStep = 2;
	/** Where a voice's signature tune is up to - back to its start with every sentence. */
	private static int motifIndex;
	private static int blipIndex;

	private SpeechSounds() {
	}

	/**
	 * Where a melodic voice's sentence is: in the middle of it, at a comma (a hanging, unfinished note),
	 * or at its end (home - the note that sounds finished). Gives each line a beginning, middle and end.
	 */
	public enum Cadence {
		NONE,
		HALF,
		FULL
	}

	/** One blip of {@code voice} in the screen, {@code wobble} keeping a plain voice from sounding mechanical. */
	public static void blip(MinecraftClient client, SpeakingVoice voice, float wobble, float volume) {
		blip(client, voice, wobble, volume, Cadence.NONE);
	}

	/** One blip; for a melodic voice, {@code cadence} says where in the sentence this word falls. */
	public static void blip(MinecraftClient client, SpeakingVoice voice, float wobble, float volume, Cadence cadence) {
		playNote(voice, note(voice, wobble, cadence), volume, (sound, pitch, vol) ->
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(sound), pitch, vol)));
	}

	/** One blip, its note and its layers, sent to {@code out}. */
	private static void speak(SpeakingVoice voice, float wobble, float volume, Out out) {
		playNote(voice, note(voice, wobble, Cadence.NONE), volume, out);
	}

	/** {@code note} in {@code voice}: the sound itself, and its layers at their intervals and delays. */
	private static void playNote(SpeakingVoice voice, float note, float volume, Out out) {
		int index = blipIndex++;
		out.play(voice.sound(), note, volume);
		for (SpeakingVoice.Layer layer : voice.layers()) {
			if (index % layer.every() != 0) {
				continue;
			}
			float pitch = Math.clamp(note * layer.interval(), SpeakingVoice.MIN_PITCH, SpeakingVoice.MAX_PITCH);
			Runnable play = () -> out.play(layer.sound(), pitch, volume * layer.volume());
			if (layer.delayMs() == 0) {
				play.run();
			} else {
				pending.add(new Pending(System.nanoTime() + layer.delayMs() * 1_000_000L, play));
			}
		}
	}

	/**
	 * This blip's pitch: a melodic voice steps along its scale (a little tune), leaning up or down by its
	 * drift - a falling line settles and sounds alone, a rising one sounds hopeful. Others wobble.
	 */
	private static float note(SpeakingVoice voice, float wobble, Cadence cadence) {
		float[] scale = SCALES.get(voice.scale());
		if (scale == null) {
			return voice.pitch() * wobble;
		}
		if (cadence == Cadence.FULL) {
			// The end of a sentence comes home to the root; the next one starts again - from the middle, or
			// from the top of the voice's own tune.
			float home = voice.pitch() * scale[0];
			melodyStep = 2;
			motifIndex = 0;
			return home;
		}
		if (cadence == Cadence.HALF) {
			// A comma hangs on the third or the fifth - open, waiting for the rest.
			melodyStep = Math.min(scale.length - 1, random.nextBoolean() ? 2 : 3);
			return voice.pitch() * scale[melodyStep];
		}
		if (!voice.motif().isEmpty()) {
			// Her own tune, word by word, now and then a neighbouring note instead - recognizable, not mechanical.
			int degree = voice.motif().get(motifIndex++ % voice.motif().size());
			if (random.nextInt(6) == 0) {
				degree += random.nextBoolean() ? 1 : -1;
			}
			melodyStep = Math.clamp(degree, 0, scale.length - 1);
			return voice.pitch() * ratio(voice.scale(), Math.max(0, degree));
		}
		int move = random.nextInt(voice.leap() * 2 + 1) - voice.leap();
		if (random.nextFloat() < Math.abs(voice.drift())) {
			move += voice.drift() > 0 ? 1 : -1;
		}
		// At the end of the scale, a falling (or rising) line starts its phrase again from the other end.
		int next = melodyStep + move;
		melodyStep = next < 0 ? scale.length - 1 - random.nextInt(2) : next >= scale.length ? random.nextInt(2) : next;
		melodyStep = Math.clamp(melodyStep, 0, scale.length - 1);
		return voice.pitch() * scale[melodyStep];
	}

	/** Plays the layers whose moment has come. Called every frame by the dialogue screen, and every tick. */
	public static void flush() {
		if (pending.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		List<Pending> due = new ArrayList<>();
		pending.removeIf(entry -> entry.dueNanos() <= now && due.add(entry));
		due.forEach(entry -> entry.play().run());
	}

	/** The pitch ratio of scale degree {@code degree} (it may run past the scale into the next octave, or below). */
	private static float ratio(String scaleName, int degree) {
		float[] scale = SCALES.getOrDefault(scaleName, SCALES.get("major"));
		int octave = Math.floorDiv(degree, scale.length);
		return scale[Math.floorMod(degree, scale.length)] * (float) Math.pow(2, octave);
	}

	// ---- Humming (design doc: Humming) ----

	/** A soft breath before each phrase - a pony's, so the horse breath, high and very quiet. */
	private static final Identifier BREATH = Identifier.ofVanilla("entity.horse.breathe");
	private static final float BREATH_VOLUME = 0.35f;
	private static final int TICKS_AFTER_BREATH = 4;

	private static final class Hum {
		final int entityId;
		final HumS2CPayload tune;
		final float root;
		final float volume;
		/** For each note: where it sits in its phrase (0 first .. 1 last); -1 for rests. */
		final float[] place;
		int next;
		int ticksUntilNext;
		boolean breathed;

		Hum(HumS2CPayload tune, float root, float volume) {
			this.entityId = tune.entityId();
			this.tune = tune;
			this.root = root;
			this.volume = volume;
			this.place = phrasePlaces(tune.notes());
		}

		boolean startsPhrase(int index) {
			return place[index] == 0f;
		}

		boolean endsPhrase(int index) {
			return place[index] == 1f || place[index] >= 0 && (index + 1 >= place.length || place[index + 1] < 0);
		}
	}

	/** Phrases are the runs of notes between rests; each note's place in its run, 0 to 1 (rests -1). */
	private static float[] phrasePlaces(List<Integer> notes) {
		float[] place = new float[notes.size()];
		int i = 0;
		while (i < notes.size()) {
			if (notes.get(i) == HumLibrary.REST) {
				place[i++] = -1;
				continue;
			}
			int start = i;
			while (i < notes.size() && notes.get(i) != HumLibrary.REST) {
				i++;
			}
			int length = i - start;
			for (int k = 0; k < length; k++) {
				place[start + k] = length == 1 ? 0f : k / (float) (length - 1);
			}
		}
		return place;
	}

	/**
	 * How loud a note sits in its phrase, like a voice: a gentle start, swelling toward the middle,
	 * tapering at the end - instead of every note struck the same.
	 */
	private static float phraseShape(float place) {
		return 0.7f + 0.4f * (float) Math.sin(Math.PI * place) - (place == 1f ? 0.1f : 0f);
	}

	private static final List<Hum> hums = new ArrayList<>();

	/** A pony nearby started humming: play it from them, if this player's Pony Humming setting wants it. */
	public static void hummed(HumS2CPayload tune) {
		DialogueConfig config = DialogueConfig.get();
		DialogueConfig.Humming level = config.humming();
		if (level == DialogueConfig.Humming.OFF || !config.revealSound || tune.livelyOnly() && level != DialogueConfig.Humming.LIVELY) {
			return;
		}
		// Fit the tune into the game's pitch range: start from the pony's own pitch, then move the whole tune
		// down (or up) so its highest and lowest notes still sound - the melody keeps its shape.
		float low = Float.MAX_VALUE;
		float high = 0f;
		for (int degree : tune.notes()) {
			if (degree != HumLibrary.REST) {
				float r = ratio(tune.scale(), degree + tune.shift());
				low = Math.min(low, r);
				high = Math.max(high, r);
			}
		}
		float root = tune.voice().pitch();
		if (high > 0 && root * high > SpeakingVoice.MAX_PITCH) {
			root = SpeakingVoice.MAX_PITCH / high;
		}
		if (low < Float.MAX_VALUE && root * low < SpeakingVoice.MIN_PITCH) {
			root = SpeakingVoice.MIN_PITCH / low;
		}
		hums.add(new Hum(tune, root, level.volume));
	}

	/** The next note of each hum, on its beat, from where the pony stands now - with a note rising from them. */
	private static void tickHums(MinecraftClient client) {
		for (Iterator<Hum> it = hums.iterator(); it.hasNext();) {
			Hum hum = it.next();
			Entity pony = client.world.getEntityById(hum.entityId);
			if (pony == null || hum.next >= hum.tune.notes().size()) {
				it.remove();
				continue;
			}
			if (hum.ticksUntilNext-- > 0) {
				continue;
			}
			double x = pony.getX();
			double y = pony.getEyeY();
			double z = pony.getZ();
			int index = hum.next;
			int degree = hum.tune.notes().get(index);
			// A breath before each phrase, then the phrase a moment later - like someone humming to themselves.
			if (degree != HumLibrary.REST && hum.startsPhrase(index) && !hum.breathed) {
				hum.breathed = true;
				client.getSoundManager().play(new PositionedSoundInstance(SoundEvent.of(BREATH), SoundCategory.NEUTRAL,
						hum.volume * BREATH_VOLUME, 1.35f + random.nextFloat() * 0.1f, random, x, y, z));
				hum.ticksUntilNext = TICKS_AFTER_BREATH - 1;
				continue;
			}
			hum.next++;
			hum.breathed = false;
			// Loose timing: now and then a tick early or late; the end of a phrase held a little longer.
			int jitter = random.nextInt(4) == 0 ? random.nextInt(3) - 1 : 0;
			boolean phraseEnd = degree != HumLibrary.REST && hum.endsPhrase(index);
			hum.ticksUntilNext = Math.max(1, hum.tune.tempo() - 1 + jitter + (phraseEnd ? 2 : 0));
			if (degree == HumLibrary.REST) {
				continue;
			}
			float pitch = Math.clamp(hum.root * ratio(hum.tune.scale(), degree + hum.tune.shift()), SpeakingVoice.MIN_PITCH, SpeakingVoice.MAX_PITCH);
			float volume = hum.volume * phraseShape(hum.place[index]);
			// A hum is the voice's own sound with its layers (a princess's shimmer too), but no wobble - it's a tune.
			playNote(hum.tune.voice().withPitch(pitch), pitch, volume, (sound, p, v) -> client.getSoundManager().play(
					new PositionedSoundInstance(SoundEvent.of(sound), SoundCategory.NEUTRAL, v, p, random, x, y, z)));
			// The note particle's color follows the pitch, like a note block's.
			double color = Math.clamp((pitch - SpeakingVoice.MIN_PITCH) / (SpeakingVoice.MAX_PITCH - SpeakingVoice.MIN_PITCH), 0, 1);
			client.world.addParticle(ParticleTypes.NOTE, x + (random.nextDouble() - 0.5) * 0.3, pony.getY() + pony.getHeight() + 0.35,
					z + (random.nextDouble() - 0.5) * 0.3, color, 0, 0);
		}
	}

	/** A player nearby chatted: murmur in their voice from where they stand, if player voices are on. */
	public static void chatted(ChatVoiceS2CPayload payload) {
		// A pony speaking aloud follows the NPC voice setting; a player chatting, the player voice one.
		MinecraftClient client = MinecraftClient.getInstance();
		boolean pony = client.world != null && !(client.world.getEntityById(payload.entityId()) instanceof net.minecraft.entity.player.PlayerEntity);
		if (pony ? !DialogueConfig.get().revealSound : !DialogueConfig.get().playerVoices) {
			return;
		}
		int blips = Math.min(MAX_MURMUR_BLIPS, Math.max(1, (payload.letters() + 2) / 3));
		murmurs.add(new Murmur(payload.entityId(), payload.voice(), blips));
	}

	/** Every client tick: the next blip of each murmur, from the speaker's current spot. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			murmurs.clear();
			pending.clear();
			hums.clear();
			return;
		}
		flush();
		tickHums(client);
		for (Iterator<Murmur> it = murmurs.iterator(); it.hasNext();) {
			Murmur murmur = it.next();
			Entity speaker = client.world.getEntityById(murmur.entityId);
			if (speaker == null || murmur.blipsLeft <= 0) {
				it.remove();
				continue;
			}
			if (murmur.ticksUntilNext-- > 0) {
				continue;
			}
			float wobble = 0.93f + random.nextFloat() * 0.14f;
			double x = speaker.getX();
			double y = speaker.getEyeY();
			double z = speaker.getZ();
			speak(murmur.voice, wobble, 0.35f, (sound, pitch, volume) -> client.getSoundManager().play(
					new PositionedSoundInstance(SoundEvent.of(sound), SoundCategory.PLAYERS, volume, pitch, random, x, y, z)));
			murmur.blipsLeft--;
			// A beat of silence now and then, like the gaps between words.
			murmur.ticksUntilNext = TICKS_BETWEEN_BLIPS - 1 + (random.nextInt(4) == 0 ? 2 : 0);
		}
	}
}
