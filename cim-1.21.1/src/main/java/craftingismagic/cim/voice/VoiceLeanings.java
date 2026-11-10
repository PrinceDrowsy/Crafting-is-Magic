package craftingismagic.cim.voice;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * How strongly a player leans toward each Element - their inner voices (design doc: Inner voices in
 * conversation). Points per voice on the player, saved and surviving death, never synced (what the
 * client needs arrives with each conversation beat).
 * <ul>
 * <li>Every Element starts at faint (0 points), so new players hear the obvious things from all
 * six. Whisper, the darker voice, starts silent and only speaks once it has been fed.</li>
 * <li>Tiers: faint from 0, clear from {@link #CLEAR_AT}, strong from {@link #STRONG_AT} -
 * first-pass numbers.</li>
 * <li>Using a voice reply grows that voice by {@link #USE_GROWTH}, win or lose; {@code ~ leaning}
 * grows it by hand. Whichever voice is currently strongest grows a little slower
 * ({@link #TOP_VOICE_RATE}), like an already-trained muscle, so one voice can't run away with
 * everything. Voices never shrink.</li>
 * </ul>
 */
public final class VoiceLeanings {
	public static final List<String> ELEMENTS = List.of("honesty", "kindness", "laughter", "generosity", "loyalty", "magic");
	public static final String WHISPER = "whisper";
	public static final List<String> ALL = List.of("honesty", "kindness", "laughter", "generosity", "loyalty", "magic", WHISPER);

	public static final int CLEAR_AT = 25;
	public static final int STRONG_AT = 75;
	public static final int USE_GROWTH = 3;
	public static final float TOP_VOICE_RATE = 0.6f;

	public static final AttachmentType<Map<String, Integer>> LEANINGS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "voice_leanings"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private VoiceLeanings() {
	}

	public static int points(Entity player, String voice) {
		Map<String, Integer> all = player.hasAttached(LEANINGS) ? player.getAttached(LEANINGS) : Map.of();
		return all.getOrDefault(normalize(voice), 0);
	}

	/** -1 silent, 0 faint, 1 clear, 2 strong. */
	public static int tier(Entity player, String voice) {
		String name = normalize(voice);
		if (!ALL.contains(name)) {
			return -1;
		}
		int points = points(player, name);
		if (name.equals(WHISPER) && points <= 0) {
			return -1;
		}
		return points >= STRONG_AT ? 2 : points >= CLEAR_AT ? 1 : 0;
	}

	public static String tierName(int tier) {
		return tier < 0 ? "silent" : List.of("faint", "clear", "strong").get(Math.min(tier, 2));
	}

	/** Grows {@code voice} by {@code amount}, slower if it's already the player's strongest Element. */
	public static void grow(Entity player, String voice, int amount) {
		String name = normalize(voice);
		if (!ALL.contains(name) || amount <= 0) {
			return;
		}
		int current = points(player, name);
		boolean top = ELEMENTS.contains(name) && current > 0
				&& ELEMENTS.stream().filter(other -> !other.equals(name)).allMatch(other -> points(player, other) < current);
		int gain = top ? Math.max(1, Math.round(amount * TOP_VOICE_RATE)) : amount;
		set(player, name, current + gain);
	}

	public static void set(Entity player, String voice, int points) {
		Map<String, Integer> all = new HashMap<>(player.hasAttached(LEANINGS) ? player.getAttached(LEANINGS) : Map.of());
		all.put(normalize(voice), Math.max(0, points));
		player.setAttached(LEANINGS, Map.copyOf(all));
	}

	private static String normalize(String voice) {
		return voice.trim().toLowerCase(Locale.ROOT);
	}
}
