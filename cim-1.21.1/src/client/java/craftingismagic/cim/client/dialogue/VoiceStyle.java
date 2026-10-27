package craftingismagic.cim.client.dialogue;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * How each inner voice looks and sounds in a conversation: its Mane Six rainbow color, a symbol and its name, so a voice is never told apart by color alone (README: Further
 * rules - highlighting that never relies on color alone), and its own short vanilla sound, picked for
 * the pony it belongs to - played per letter as the voice talks, Undertale-style. The darker voice
 * has no name on screen and speaks in a muted, slanted hand.
 */
public enum VoiceStyle {
	HONESTY("honesty", 0xF9C468, "●", SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value(), 1.0f, 1, 1.0f),      // Applejack: country twang
	KINDNESS("kindness", 0xFFF7A8, "✿", SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 1.2f, 2, 0.8f), // Fluttershy: soft, unhurried; the flute rings, so every 2nd letter
	LAUGHTER("laughter", 0xF7A8D8, "☺", SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE.value(), 1.3f, 1, 1.0f), // Pinkie: bouncy
	GENEROSITY("generosity", 0xF5F5F5, "♦", SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.9f, 1, 1.0f),   // Rarity: clear and elegant (the amethyst chime is breathy as a blip)
	LOYALTY("loyalty", 0x9EE6FA, "⚡", SoundEvents.BLOCK_NOTE_BLOCK_GUITAR.value(), 1.2f, 1, 1.0f),     // Rainbow Dash: rock and roll
	MAGIC("magic", 0xCC8CF2, "✦", SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.0f, 1, 1.0f),          // Twilight: sparkle
	WHISPER("whisper", 0x9C7486, "…", SoundEvents.PARTICLE_SOUL_ESCAPE.value(), 0.8f, 3, 1.0f);        // the darker voice: a hiss, sparser

	public final String voice;
	public final int color;
	public final String symbol;
	private final SoundEvent sound;
	private final float pitch;
	/** Speaks every Nth letter - Whisper only now and then, a hiss under the words. */
	public final int blipEvery;
	/** How fast this voice's lines type out, relative to the Text Reveal setting - each voice talks at its own pace. */
	public final float pace;

	VoiceStyle(String voice, int color, String symbol, SoundEvent sound, float pitch, int blipEvery, float pace) {
		this.voice = voice;
		this.color = color;
		this.symbol = symbol;
		this.sound = sound;
		this.pitch = pitch;
		this.blipEvery = blipEvery;
		this.pace = pace;
	}

	public static VoiceStyle of(String voice) {
		for (VoiceStyle style : values()) {
			if (style.voice.equals(voice)) {
				return style;
			}
		}
		return MAGIC;
	}

	public boolean named() {
		return this != WHISPER;
	}

	/** One spoken blip as this voice talks, Undertale-style; {@code wobble} keeps it from sounding mechanical. */
	public void blip(MinecraftClient client, float wobble) {
		client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch * wobble, this == WHISPER ? 0.32f : 0.35f));
	}

	/** Plays this voice's sound; {@code pitchShift} raises it for a landed attempt, lowers it for a miss. */
	public void play(MinecraftClient client, float pitchShift) {
		client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch * pitchShift, 0.45f));
	}

	/** "✿ Kindness" in the voice's color - or just "…" for the darker voice. */
	public MutableText label() {
		MutableText label = Text.literal(symbol);
		if (named()) {
			label.append(" ").append(Text.translatable("voice.cim." + voice));
		}
		return label.styled(style -> style.withColor(color).withItalic(this == WHISPER));
	}

	/** {@code text} in this voice's color. */
	public MutableText colored(String text) {
		return Text.literal(text).styled(style -> style.withColor(color).withItalic(this == WHISPER));
	}

	public static MutableText gray(Text text) {
		return text.copy().formatted(Formatting.GRAY);
	}
}
