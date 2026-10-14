package craftingismagic.cim.story;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.util.Identifier;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.narrative.TaleVocabulary;
import craftingismagic.cim.narrative.TaleVocabulary.ArgumentCheck;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.villager.Feeling;
import craftingismagic.cim.villager.FeelingBand;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PersonalityType;
import craftingismagic.cim.villager.ReplyTone;

/**
 * The game's own {@code .tale} words (docs/narrative-reference.md): the inner voices, reply tones,
 * feelings, and every built-in condition, effect, and thread event. Other mods register theirs the
 * same way, under their own namespace. Only argument checks for now; what each one does arrives
 * with the dialogue runtime.
 */
public final class CimTaleVocabulary {
	private static final List<String> ELEMENTS = List.of("honesty", "kindness", "laughter", "generosity", "loyalty", "magic");
	private static final List<String> SEASONS = List.of("spring", "summer", "autumn", "fall", "winter");
	private static final Pattern SIGNED_NUMBER = Pattern.compile("[+-]\\d+");
	private static final Pattern WORD = Pattern.compile("[a-z0-9_]+");

	private CimTaleVocabulary() {
	}

	public static void register() {
		for (String element : ELEMENTS) {
			TaleVocabulary.registerVoice(element, true);
		}
		TaleVocabulary.registerVoice("whisper", false);
		for (ReplyTone tone : ReplyTone.values()) {
			TaleVocabulary.registerTone(tone.name());
		}
		for (Feeling feeling : Feeling.values()) {
			TaleVocabulary.registerFeeling(feeling.name());
		}

		// Conditions
		for (Feeling feeling : Feeling.values()) {
			TaleVocabulary.registerCondition(cim(feeling.name()), CimTaleVocabulary::feelingComparison);
		}
		condition("mood", (args, cmp) -> words(args, 1, 2, List.of(names(Emotion.values()), TaleVocabulary.TIERS)));
		condition("fact", (args, cmp) -> ids(args, 1));
		condition("remembers", (args, cmp) -> ids(args, 1));
		condition("voice", (args, cmp) -> words(args, 1, 2, List.of(voices(), TaleVocabulary.TIERS)));
		condition("species", (args, cmp) -> words(args, 1, 1, List.of(names(Species.values()))));
		condition("personality", (args, cmp) -> words(args, 1, 1, List.of(names(PersonalityType.values()))));
		condition("life_stage", (args, cmp) -> words(args, 1, 1, List.of(names(LifeStage.values()))));
		condition("player.species", (args, cmp) -> words(args, 1, 1, List.of(names(Species.values()))));
		condition("player.heritage", (args, cmp) -> anyWord(args));
		condition("player.upbringing", (args, cmp) -> anyWord(args));
		condition("night", (args, cmp) -> count(args, 0, 0));
		condition("raining", (args, cmp) -> count(args, 0, 0));
		condition("holiday", (args, cmp) -> ids(args, 1));
		condition("season", (args, cmp) -> words(args, 1, 1, List.of(SEASONS)));

		// Effects - arguments are split on commas, so "fact set cim:x" arrives as one "set cim:x"
		for (Feeling feeling : Feeling.values()) {
			TaleVocabulary.registerEffect(cim(feeling.name()), (args, cmp) -> args.size() == 1 && SIGNED_NUMBER.matcher(args.get(0)).matches()
					? List.of() : List.of("write the change as +N or -N, like \"~ trust +2\""));
		}
		effect("memory", CimTaleVocabulary::memoryEffect);
		effect("fact", (args, cmp) -> subcommand(args, List.of("set", "clear"), 1));
		effect("thread", CimTaleVocabulary::threadEffect);
		effect("moment", (args, cmp) -> ids(args, 1));
		effect("leaning", (args, cmp) -> {
			String[] words = split(args);
			if (args.size() != 1 || words.length != 2 || !ELEMENTS.contains(normalize(words[0])) || !SIGNED_NUMBER.matcher(words[1]).matches()) {
				return List.of("write it as \"~ leaning <element> +N\"; elements: " + String.join(", ", ELEMENTS));
			}
			return List.of();
		});

		// Thread events
		for (String bare : List.of("sleep", "wake", "day", "night")) {
			TaleVocabulary.registerEvent(cim(bare), (args, cmp) -> count(args, 0, 0));
		}
		TaleVocabulary.registerEvent(cim("enter_biome"), (args, cmp) -> count(args, 1, 1));
		TaleVocabulary.registerEvent(cim("talk"), (args, cmp) -> count(args, 1, 1));
		TaleVocabulary.registerEvent(cim("holiday"), (args, cmp) -> ids(args, 1));
		TaleVocabulary.registerEvent(cim("fact"), (args, cmp) -> ids(args, 1));
	}

	// ---- Specific checks ----

	private static List<String> feelingComparison(List<String> args, Optional<String> comparison) {
		if (comparison.isEmpty()) {
			return List.of("compare a feeling with a band, like \"trust >= medium\"");
		}
		String band = normalize(args.get(0));
		if (Arrays.stream(FeelingBand.values()).noneMatch(value -> value.name().equalsIgnoreCase(band))) {
			return List.of("feelings are compared with bands, never numbers - bands: " + String.join(", ", names(FeelingBand.values())));
		}
		return List.of();
	}

	/** {@code ~ memory <id>[, <emotion> [intensity]]} - intensity is a tier or 1-3. */
	private static List<String> memoryEffect(List<String> args, Optional<String> comparison) {
		if (args.isEmpty() || args.size() > 2) {
			return List.of("write it as \"~ memory <id>\" or \"~ memory <id>, <emotion> <intensity>\"");
		}
		List<String> problems = new ArrayList<>(ids(args.subList(0, 1), 1));
		if (args.size() == 2) {
			String[] words = args.get(1).trim().split("\\s+");
			if (!names(Emotion.values()).contains(normalize(words[0]))) {
				problems.add("unknown emotion \"" + words[0] + "\"; emotions: " + String.join(", ", names(Emotion.values())));
			}
			if (words.length > 2 || (words.length == 2 && !TaleVocabulary.TIERS.contains(normalize(words[1])) && !words[1].matches("[1-3]"))) {
				problems.add("intensity is faint, clear, strong, or 1-3");
			}
		}
		return problems;
	}

	private static List<String> threadEffect(List<String> args, Optional<String> comparison) {
		String[] words = split(args);
		if (args.size() == 1 && words.length == 2 && words[0].equals("start")) {
			return idProblems(words[1]);
		}
		if (args.size() == 1 && words.length == 3 && words[0].equals("advance")) {
			return idProblems(words[1]);
		}
		return List.of("write it as \"~ thread start <id>\" or \"~ thread advance <id> <stage>\"");
	}

	// ---- Shared helpers ----

	private static void condition(String name, ArgumentCheck check) {
		TaleVocabulary.registerCondition(cim(name), (args, cmp) -> {
			if (cmp.isPresent()) {
				return List.of("only feelings can be compared with " + cmp.get());
			}
			return check.check(args, cmp);
		});
	}

	private static void effect(String name, ArgumentCheck check) {
		TaleVocabulary.registerEffect(cim(name), check);
	}

	private static List<String> count(List<String> args, int min, int max) {
		if (args.size() < min || args.size() > max) {
			return List.of(min == max ? "takes " + min + " argument(s)" : "takes " + min + " to " + max + " arguments");
		}
		return List.of();
	}

	/** Each argument must be one of the allowed words for its position. */
	private static List<String> words(List<String> args, int min, int max, List<List<String>> allowed) {
		List<String> problems = new ArrayList<>(count(args, min, max));
		for (int i = 0; i < Math.min(args.size(), allowed.size()); i++) {
			if (!allowed.get(i).contains(normalize(args.get(i)))) {
				problems.add("\"" + args.get(i) + "\" isn't one of: " + String.join(", ", allowed.get(i)));
			}
		}
		return problems;
	}

	private static List<String> anyWord(List<String> args) {
		List<String> problems = new ArrayList<>(count(args, 1, 1));
		if (args.size() == 1 && !WORD.matcher(normalize(args.get(0))).matches()) {
			problems.add("\"" + args.get(0) + "\" should be a single word");
		}
		return problems;
	}

	private static List<String> ids(List<String> args, int expected) {
		List<String> problems = new ArrayList<>(count(args, expected, expected));
		args.forEach(arg -> problems.addAll(idProblems(arg)));
		return problems;
	}

	/** {@code verb <id>} as one argument, like {@code set cim:world/x}. */
	private static List<String> subcommand(List<String> args, List<String> verbs, int idCount) {
		String[] words = split(args);
		if (args.size() != 1 || words.length != idCount + 1 || !verbs.contains(words[0])) {
			return List.of("starts with one of: " + String.join(", ", verbs) + ", then an ID");
		}
		return idProblems(words[1]);
	}

	/** IDs may contain variables ({@code cim:player/comforted_{pony}}), filled in at run time. */
	private static List<String> idProblems(String text) {
		String filled = text.trim().replaceAll("\\{[A-Za-z0-9_.]+\\}", "x");
		if (filled.isEmpty() || Identifier.tryParse(filled.contains(":") ? filled : "cim:" + filled) == null) {
			return List.of("\"" + text.trim() + "\" isn't a valid ID - lowercase letters, numbers, \"_\", \"/\", \".\", and one \":\"");
		}
		return List.of();
	}

	private static String[] split(List<String> args) {
		return args.isEmpty() ? new String[0] : args.get(0).trim().split("\\s+");
	}

	private static List<String> voices() {
		return List.copyOf(TaleVocabulary.voices());
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(value -> value.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
	}

	private static String normalize(String word) {
		return TaleVocabulary.normalize(word);
	}

	private static Identifier cim(String path) {
		return Identifier.of("cim", path.toLowerCase(Locale.ROOT));
	}
}
