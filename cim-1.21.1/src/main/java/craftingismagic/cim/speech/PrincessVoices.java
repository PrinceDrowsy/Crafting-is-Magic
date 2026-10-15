package craftingismagic.cim.speech;

import java.util.List;
import java.util.Map;

import net.minecraft.util.Identifier;

import craftingismagic.cim.speech.SpeakingVoice.Layer;

/**
 * The princesses' voices (README: Speaking voices, Princess voice motifs) - melodic and layered, so
 * they sound musical and ethereal. Layers sit at consonant intervals from each note and the notes walk
 * a scale, so nothing clashes. Roots sit low (around 0.6) so the highest layer still fits under the
 * game's 2.0 pitch ceiling.
 * <p>
 * Each is built from the motif research (README): day is major, bright and rising with a full,
 * doubled sound; night is minor, falling, and alone - one voice and its own echo; love is warm with
 * sweet consonances; magic walks the Lydian scale film scores use for wonder.
 * <p>
 * PROVISIONAL instruments: tuned by ear for testing ({@code /cim debug speech princess}), to be settled
 * against canon before they're final.
 */
public final class PrincessVoices {
	private static final Identifier AMETHYST = Identifier.ofVanilla("block.amethyst_block.chime");
	/**
	 * The mod's own choir "aah" (tools/sounds/synth_choir.py), tuned to the harp note block's note so it
	 * harmonizes with every layer. Every princess has it - the sound that unites them (user, 2026-10-15) -
	 * most audible in Celestia, then Luna, then Cadance, and only a whisper in Twilight.
	 */
	private static final Identifier CHOIR = Identifier.of("cim", "voice.choir_aah");

	private static Layer choir(float volume) {
		return new Layer(CHOIR, 1.0f, volume, 15, 1);
	}

	public static final Map<String, SpeakingVoice> ALL = Map.of(
			// Day, regal and elegant, with the affect of a cello (user, 2026-10-15): deep, warm and sustained, never
			// twinkly. Note blocks can't bow, so it's built from parts: a guitar leads (an octave below the harp,
			// warm and round); a faint didgeridoo in unison is the bow's rasp and body; two soft, slightly late
			// repeats of each note make it hold the way a bowed note does instead of dying away; a bass foundation
			// for the weight of the eldest, the ruler; and a harp an octave above (the harp note block sits an
			// octave over the guitar at the same pitch), soft and a hair late - an orchestral harp shadowing the
			// cello: elegant and golden, without the twinkle. Major, slow and deliberate.
			"celestia", new SpeakingVoice(noteBlock("guitar"), 0.6f, List.of(
					new Layer(noteBlock("harp"), 1.0f, 0.35f, 25, 1),
					new Layer(noteBlock("didgeridoo"), 1.0f, 0.22f, 0, 1),
					new Layer(noteBlock("bass"), 1.0f, 0.4f, 0, 1),
					new Layer(noteBlock("guitar"), 1.0f, 0.32f, 70, 1),
					new Layer(noteBlock("guitar"), 1.0f, 0.16f, 140, 1),
					choir(0.4f)), "major", 0.25f, 1, 0.7f,
					// The sisters' leitmotif, her half (in the spirit of Lullaby for a Princess - its character,
					// never its melody): rising like a question, then sighing down and lingering. Regal, with a
					// touch of old sorrow. The "3 2 1" sigh is the figure Luna's half shares.
					List.of(0, 1, 2, 4, 3, 2, 3, 2, 1)),
			// Regal night, lonely underneath - see luna() for the sound.
			"luna", luna(
					// The sisters' leitmotif, her half: answering Celestia's from above - falling with the same
					// "3 2 1" sigh, then lifting back to the Dorian sixth, the noble note.
					List.of(5, 4, 3, 2, 1, 2, 3, 5, 4)),
			// Luna as she was before the sister leitmotif (user liked it, 2026-10-15 - kept to switch back to):
			// rises to the Dorian sixth, then falls away like night coming down.
			"luna_classic", luna(List.of(2, 4, 5, 4, 2, 1, 0, 1)),
			// Love, with strength behind it (her side of This Day Aria), and the feel of a music box at home -
			// the user's reference is "Home" (Toby Fox, Undertale): its character, never its melody. A soft bell
			// leads like a music box, her harp in unison for warmth, the bass still underneath for strength;
			// unhurried, rocking step by step like a lullaby, always circling back home.
			"cadance", new SpeakingVoice(noteBlock("bell"), 0.62f, List.of(
					new Layer(noteBlock("harp"), 1.0f, 0.55f, 0, 1),
					new Layer(noteBlock("bass"), 1.0f, 0.35f, 0, 1),
					new Layer(AMETHYST, 2.0f, 0.15f, 60, 8),
					choir(0.22f)), "major", 0.1f, 1, 0.85f,
					// A rocking, stepwise lullaby that keeps circling home (original).
					List.of(2, 1, 2, 3, 2, 1, 0, 1)),
			// Magic: Lydian, the scale of wonder; a pling with a chime an octave up and frequent sparkles,
			// skipping about a little. (User: "the most accurate" - 2026-10-15. Keep as is - only the princesses'
			// shared choir added, at a whisper.)
			"twilight", new SpeakingVoice(noteBlock("pling"), 0.6f, List.of(
					new Layer(noteBlock("chime"), 2.0f, 0.35f, 30, 1),
					new Layer(AMETHYST, 2.0f, 0.3f, 55, 4),
					choir(0.1f)), "lydian", 0.15f, 2, 1f,
					// Curious and sparkly, lingering on the Lydian raised fourth (degree 3) - the wonder note.
					List.of(0, 2, 3, 4, 3, 5, 4, 2)));

	private PrincessVoices() {
	}

	/**
	 * Luna's voice - regal night (user's pick, 2026-10-15): noble first, lonely underneath. A low bell leads,
	 * tolling like a midnight bell; a soft flute in unison (her gentleness) and a quiet bass (royal depth)
	 * under it; then the bell's own echo, twice, fading - the loneliness, as an undertone - and a rare faint
	 * star. Dorian, the noble, bittersweet minor; measured and formal in pace. Only the tune varies.
	 */
	private static SpeakingVoice luna(List<Integer> motif) {
		return new SpeakingVoice(noteBlock("bell"), 0.5f, List.of(
				new Layer(noteBlock("flute"), 1.0f, 0.45f, 0, 1),
				new Layer(noteBlock("bass"), 1.0f, 0.35f, 0, 1),
				new Layer(noteBlock("bell"), 1.0f, 0.18f, 160, 1),
				new Layer(noteBlock("bell"), 1.0f, 0.08f, 320, 1),
				new Layer(AMETHYST, 2.0f, 0.12f, 220, 9),
				choir(0.3f)), "dorian", -0.15f, 1, 0.85f, motif);
	}

	private static Identifier noteBlock(String instrument) {
		return Identifier.ofVanilla("block.note_block." + instrument);
	}
}
