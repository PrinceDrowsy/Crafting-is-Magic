package craftingismagic.cim.narrative;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.Identifier;

/**
 * Everything a {@code .tale} file is allowed to say - the conditions, effects, and thread events it
 * can name, and the voices, tones, and feelings replies can use. The library itself knows none of
 * these: the game registers its own (see {@code CimTaleVocabulary}), and any other mod can register
 * more with the same calls, under its own namespace. That keeps the narrative library free of the
 * rest of the mod (docs/implementation-plan.md: the narrative library talks to the mod through small
 * interfaces) and open to datapacks and other mods.
 * <p>
 * For now each entry only knows how to check its own arguments ({@link ArgumentCheck}); running them
 * arrives with the dialogue runtime.
 */
public final class TaleVocabulary {
	/** Voice tiers are part of the format itself, not registered. */
	public static final List<String> TIERS = List.of("faint", "clear", "strong");

	private static final Map<Identifier, ArgumentCheck> conditions = new HashMap<>();
	private static final Map<Identifier, ArgumentCheck> effects = new HashMap<>();
	private static final Map<Identifier, ArgumentCheck> events = new HashMap<>();
	private static final Set<String> voices = new LinkedHashSet<>();
	private static final Set<String> tones = new LinkedHashSet<>();
	private static final Set<String> feelings = new LinkedHashSet<>();
	/** Voices that never take a feeling in replies (the darker voice). */
	private static final Set<String> feelinglessVoices = new LinkedHashSet<>();

	private TaleVocabulary() {
	}

	/**
	 * Checks one call's arguments, returning a message per problem (empty when fine).
	 * {@code comparison} is the operator for the {@code a >= b} condition form, empty otherwise.
	 */
	@FunctionalInterface
	public interface ArgumentCheck {
		List<String> check(List<String> args, java.util.Optional<String> comparison);
	}

	public static void registerCondition(Identifier id, ArgumentCheck check) {
		conditions.put(id, check);
	}

	public static void registerEffect(Identifier id, ArgumentCheck check) {
		effects.put(id, check);
	}

	public static void registerEvent(Identifier id, ArgumentCheck check) {
		events.put(id, check);
	}

	public static void registerVoice(String voice, boolean takesFeeling) {
		voices.add(normalize(voice));
		if (!takesFeeling) {
			feelinglessVoices.add(normalize(voice));
		}
	}

	public static void registerTone(String tone) {
		tones.add(normalize(tone));
	}

	public static void registerFeeling(String feeling) {
		feelings.add(normalize(feeling));
	}

	public static ArgumentCheck condition(Identifier id) {
		return conditions.get(id);
	}

	public static ArgumentCheck effect(Identifier id) {
		return effects.get(id);
	}

	public static ArgumentCheck event(Identifier id) {
		return events.get(id);
	}

	public static boolean isVoice(String voice) {
		return voices.contains(normalize(voice));
	}

	public static boolean voiceTakesFeeling(String voice) {
		return !feelinglessVoices.contains(normalize(voice));
	}

	public static boolean isTone(String tone) {
		return tones.contains(normalize(tone));
	}

	public static boolean isFeeling(String feeling) {
		return feelings.contains(normalize(feeling));
	}

	public static Set<String> voices() {
		return Collections.unmodifiableSet(voices);
	}

	public static Set<String> tones() {
		return Collections.unmodifiableSet(tones);
	}

	public static Set<String> feelings() {
		return Collections.unmodifiableSet(feelings);
	}

	/** Words in a {@code .tale} file are case-insensitive: {@code Kindness} and {@code kindness} match. */
	public static String normalize(String word) {
		return word.trim().toLowerCase(Locale.ROOT);
	}
}
