package craftingismagic.cim.story;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.narrative.TaleVocabulary;
import craftingismagic.cim.narrative.TaleVocabulary.ArgumentCheck;
import craftingismagic.cim.narrative.TaleVocabulary.ConditionTest;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.FavoriteGiftAttachment;
import craftingismagic.cim.villager.Feeling;
import craftingismagic.cim.villager.FeelingBand;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PersonalityAttachment;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.villager.PersonalityType;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;
import craftingismagic.cim.villager.Quirk;
import craftingismagic.cim.villager.ReplyTone;

/**
 * The game's own {@code .tale} words (docs/narrative-reference.md): the inner voices, reply tones,
 * feelings, every built-in condition, effect, thread event, and variable, and the {@code chatter}
 * start context. Other mods register theirs the same way, under their own namespace.
 * <p>
 * Some words are accepted now but wait on later systems, and say so in the log when used: voices
 * ({@code voice(...)}, {@code ~ leaning}) on Phase 3, {@code ~ moment} on Phase 4, and
 * {@code player.heritage}/{@code player.upbringing}/{@code holiday}/{@code season}
 * on the character creator and calendar - those conditions are false until then.
 */
public final class CimTaleVocabulary {
	public static final String CHATTER = "chatter";

	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");
	private static final List<String> ELEMENTS = List.of("honesty", "kindness", "laughter", "generosity", "loyalty", "magic");
	private static final List<String> SEASONS = List.of("spring", "summer", "autumn", "fall", "winter");
	private static final List<String> FAMILY = List.of("recorded", "orphan", "parent", "partner");
	private static final List<String> RELATIVES = List.of("parent", "partner");
	/** Where a story fact lives is the first part of its path. */
	private static final List<String> FACT_SCOPES = List.of("world/", "player/", "relationship/");
	private static final Pattern SIGNED_NUMBER = Pattern.compile("[+-]\\d+");
	private static final Pattern WORD = Pattern.compile("[a-z0-9_]+");

	private CimTaleVocabulary() {
	}

	public static void register() {
		TaleVocabulary.registerStartContext(CHATTER);
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
		registerConditions();
		registerEffects();
		registerEvents();
		registerVariables();
	}

	// ---- Conditions ----

	private static void registerConditions() {
		for (Feeling feeling : Feeling.values()) {
			TaleVocabulary.registerCondition(cim(feeling.name()), CimTaleVocabulary::feelingComparison,
					(tale, args, cmp) -> compare(FriendshipAttachment.getBand(tale.speaker(), tale.player().getUuid(), feeling).ordinal(),
							cmp.orElse("=="), band(args.get(0)).ordinal()));
		}
		condition("mood", (args, cmp) -> words(args, 1, 2, List.of(names(Emotion.values()), TaleVocabulary.TIERS)), CimTaleVocabulary::mood);
		condition("fact", (args, cmp) -> factIds(args), (tale, args, cmp) -> hasFact(tale, id(args.get(0))));
		condition("remembers", (args, cmp) -> ids(args, 1), (tale, args, cmp) -> MemoryStore.remembers(tale.speaker(), id(args.get(0))));
		condition("voice", (args, cmp) -> words(args, 1, 2, List.of(List.copyOf(TaleVocabulary.voices()), TaleVocabulary.TIERS)),
				(tale, args, cmp) -> waiting("voice(...)", "inner voices (Phase 3)"));
		condition("species", (args, cmp) -> words(args, 1, 1, List.of(names(Species.values()))),
				(tale, args, cmp) -> species(tale.speaker()).name().equalsIgnoreCase(args.get(0)));
		condition("personality", (args, cmp) -> words(args, 1, 1, List.of(names(PersonalityType.values()))),
				(tale, args, cmp) -> personality(tale.speaker()).map(p -> p.baseType().name().equalsIgnoreCase(args.get(0))).orElse(false));
		condition("quirk", (args, cmp) -> words(args, 1, 1, List.of(names(Quirk.values()))),
				(tale, args, cmp) -> personality(tale.speaker()).map(p -> p.quirks().stream().anyMatch(q -> q.name().equalsIgnoreCase(args.get(0))))
						.orElse(false));
		// The tone that suits this pony's personality (README: Personality system, Mechanical hook).
		condition("likes_tone", (args, cmp) -> words(args, 1, 1, List.of(names(ReplyTone.values()))),
				(tale, args, cmp) -> personality(tale.speaker()).flatMap(p -> p.baseType().toneAffinity())
						.map(tone -> tone.name().equalsIgnoreCase(args.get(0))).orElse(false));
		condition("life_stage", (args, cmp) -> words(args, 1, 1, List.of(names(LifeStage.values()))),
				(tale, args, cmp) -> lifeStage(tale.speaker()).name().equalsIgnoreCase(args.get(0)));
		condition("profession", (args, cmp) -> ids(args, 1), (tale, args, cmp) -> tale.speaker() instanceof VillagerEntity villager
				&& Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()).equals(minecraftId(args.get(0))));
		condition("family", (args, cmp) -> words(args, 1, 1, List.of(FAMILY)), (tale, args, cmp) -> family(tale.speaker(), args.get(0)));
		// A relative is "present" when they're loaded in the world right now, so their name can be said.
		condition("present", (args, cmp) -> words(args, 1, 1, List.of(RELATIVES)),
				(tale, args, cmp) -> relativeName(tale, args.get(0)).isPresent());
		condition("player.species", (args, cmp) -> words(args, 1, 1, List.of(names(Species.values()))),
				(tale, args, cmp) -> SpeciesAttachment.hasChosenSpecies(tale.player())
						&& SpeciesAttachment.getSpecies(tale.player()).name().equalsIgnoreCase(args.get(0)));
		condition("player.heritage", (args, cmp) -> anyWord(args), (tale, args, cmp) -> waiting("player.heritage", "the character creator"));
		condition("player.upbringing", (args, cmp) -> anyWord(args), (tale, args, cmp) -> waiting("player.upbringing", "the character creator"));
		condition("night", (args, cmp) -> count(args, 0, 0), (tale, args, cmp) -> tale.world().isNight());
		condition("raining", (args, cmp) -> count(args, 0, 0), (tale, args, cmp) -> tale.world().isRaining());
		condition("thundering", (args, cmp) -> count(args, 0, 0), (tale, args, cmp) -> tale.world().isThundering());
		condition("biome", (args, cmp) -> biomeArgs(args), CimTaleVocabulary::inBiome);
		condition("holiday", (args, cmp) -> ids(args, 1), (tale, args, cmp) -> waiting("holiday(...)", "the calendar"));
		condition("season", (args, cmp) -> words(args, 1, 1, List.of(SEASONS)), (tale, args, cmp) -> waiting("season(...)", "the calendar"));
	}

	private static List<String> feelingComparison(List<String> args, Optional<String> comparison) {
		if (comparison.isEmpty()) {
			return List.of("compare a feeling with a band, like \"trust >= medium\"");
		}
		if (Arrays.stream(FeelingBand.values()).noneMatch(value -> value.name().equalsIgnoreCase(args.get(0)))) {
			return List.of("feelings are compared with bands, never numbers - bands: " + String.join(", ", names(FeelingBand.values())));
		}
		return List.of();
	}

	private static boolean compare(int actual, String operator, int target) {
		return switch (operator) {
			case ">=" -> actual >= target;
			case "<=" -> actual <= target;
			case ">" -> actual > target;
			case "<" -> actual < target;
			case "!=" -> actual != target;
			default -> actual == target;
		};
	}

	/**
	 * First pass at the mood query: the pony holds a memory with this emotion at least this strong.
	 * Phase 4 replaces it with real mood (README: The mood system).
	 */
	private static boolean mood(TaleContext tale, List<String> args, Optional<String> comparison) {
		Emotion emotion = Emotion.valueOf(args.get(0).toUpperCase(Locale.ROOT));
		EmotionIntensity least = args.size() > 1 ? EmotionIntensity.valueOf(args.get(1).toUpperCase(Locale.ROOT)) : EmotionIntensity.FAINT;
		return MemoryStore.getAll(tale.speaker()).stream()
				.anyMatch(memory -> memory.emotion().filter(emotion::equals).isPresent() && memory.intensity().compareTo(least) >= 0);
	}

	private static boolean family(Entity pony, String kind) {
		if (!PonyFamilyAttachment.hasFamily(pony)) {
			return false;
		}
		PonyFamily family = PonyFamilyAttachment.getFamily(pony);
		return switch (kind) {
			case "orphan" -> family.orphan();
			case "parent" -> !family.parents().isEmpty();
			case "partner" -> !family.partner().isEmpty();
			default -> true;
		};
	}

	private static List<String> biomeArgs(List<String> args) {
		List<String> problems = new ArrayList<>(count(args, 1, 64));
		for (String arg : args) {
			problems.addAll(idProblems(arg.startsWith("#") ? arg.substring(1) : arg));
		}
		return problems;
	}

	/** {@code biome(minecraft:forest, #minecraft:is_ocean)} - any listed biome or biome tag where the pony stands. */
	private static boolean inBiome(TaleContext tale, List<String> args, Optional<String> comparison) {
		return biomeMatches(tale.world().getBiome(tale.speaker().getBlockPos()), args);
	}

	/** Whether {@code biome} is any of the listed biome IDs or {@code #tags}. */
	static boolean biomeMatches(RegistryEntry<Biome> biome, List<String> args) {
		for (String arg : args) {
			if (arg.startsWith("#") ? biome.isIn(TagKey.of(RegistryKeys.BIOME, minecraftId(arg.substring(1)))) : biome.matchesId(minecraftId(arg))) {
				return true;
			}
		}
		return false;
	}

	// ---- Effects ----

	private static void registerEffects() {
		for (Feeling feeling : Feeling.values()) {
			TaleVocabulary.registerEffect(cim(feeling.name()),
					(args, cmp) -> args.size() == 1 && SIGNED_NUMBER.matcher(args.get(0)).matches()
							? List.of() : List.of("write the change as +N or -N, like \"~ trust +2\""),
					(tale, args) -> FriendshipAttachment.addFeeling(tale.speaker(), tale.player().getUuid(), feeling,
							Integer.parseInt(args.get(0).replace("+", ""))));
		}
		TaleVocabulary.registerEffect(cim("memory"), CimTaleVocabulary::memoryArgs, CimTaleVocabulary::addMemory);
		TaleVocabulary.registerEffect(cim("fact"), (args, cmp) -> {
			List<String> problems = new ArrayList<>(subcommand(args, List.of("set", "clear")));
			if (problems.isEmpty()) {
				problems.addAll(factIds(List.of(split(args)[1])));
			}
			return problems;
		}, (tale, args) -> {
			String[] words = split(args);
			setFact(tale, id(words[1]), words[0].equals("set"));
		});
		TaleVocabulary.registerEffect(cim("thread"), CimTaleVocabulary::threadArgs, (tale, args) -> {
			String[] words = split(args);
			if (words[0].equals("start")) {
				StoryThreads.start(tale.player(), id(words[1]), false);
			} else {
				StoryThreads.advance(tale.player(), id(words[1]), words[2]);
			}
		});
		TaleVocabulary.registerEffect(cim("moment"), (args, cmp) -> ids(args, 1),
				(tale, args) -> waiting("~ moment " + args.get(0), "the mood system (Phase 4)"));
		TaleVocabulary.registerEffect(cim("leaning"), (args, cmp) -> {
			String[] words = split(args);
			if (args.size() != 1 || words.length != 2 || !ELEMENTS.contains(normalize(words[0])) || !SIGNED_NUMBER.matcher(words[1]).matches()) {
				return List.of("write it as \"~ leaning <element> +N\"; elements: " + String.join(", ", ELEMENTS));
			}
			return List.of();
		}, (tale, args) -> waiting("~ leaning " + args.get(0), "inner voices (Phase 3)"));
	}

	/** {@code ~ memory <id>[, <emotion> [faint|clear|strong]]}. */
	private static List<String> memoryArgs(List<String> args, Optional<String> comparison) {
		if (args.isEmpty() || args.size() > 2) {
			return List.of("write it as \"~ memory <id>\" or \"~ memory <id>, <emotion> <intensity>\"");
		}
		List<String> problems = new ArrayList<>(ids(args.subList(0, 1), 1));
		if (args.size() == 2) {
			String[] words = args.get(1).trim().split("\\s+");
			if (!names(Emotion.values()).contains(normalize(words[0]))) {
				problems.add("unknown emotion \"" + words[0] + "\"; emotions: " + String.join(", ", names(Emotion.values())));
			}
			if (words.length > 2 || (words.length == 2 && !TaleVocabulary.TIERS.contains(normalize(words[1])))) {
				problems.add("intensity is faint, clear, or strong");
			}
		}
		return problems;
	}

	/** The pony remembers it, about the player, as something they saw happen. */
	private static void addMemory(TaleContext tale, List<String> args) {
		Optional<Emotion> emotion = Optional.empty();
		EmotionIntensity intensity = EmotionIntensity.CLEAR;
		if (args.size() == 2) {
			String[] words = args.get(1).trim().split("\\s+");
			emotion = Optional.of(Emotion.valueOf(words[0].toUpperCase(Locale.ROOT)));
			if (words.length == 2) {
				intensity = EmotionIntensity.valueOf(words[1].toUpperCase(Locale.ROOT));
			}
		}
		MemoryStore.add(tale.speaker(), new Memory(id(args.get(0)), Optional.of(tale.player().getUuid()), MemoryKnowledge.WITNESSED,
				MemoryImportance.NOTABLE, emotion, intensity, tale.world().getTime()));
	}

	private static List<String> threadArgs(List<String> args, Optional<String> comparison) {
		String[] words = split(args);
		if (args.size() == 1 && words.length == 2 && words[0].equals("start")) {
			return idProblems(words[1]);
		}
		if (args.size() == 1 && words.length == 3 && words[0].equals("advance")) {
			return idProblems(words[1]);
		}
		return List.of("write it as \"~ thread start <id>\" or \"~ thread advance <id> <stage>\"");
	}

	// ---- Facts ----

	/** Fact IDs say where they live: {@code cim:world/...}, {@code cim:player/...}, {@code cim:relationship/...}. */
	private static List<String> factIds(List<String> args) {
		List<String> problems = new ArrayList<>(ids(args, 1));
		// Checked with variables stood in for, since {pony} isn't known until the conversation runs.
		if (problems.isEmpty() && FACT_SCOPES.stream().noneMatch(scope -> id(withoutVariables(args.get(0))).getPath().startsWith(scope))) {
			problems.add("a fact's path starts with world/, player/, or relationship/ - that's where it's kept");
		}
		return problems;
	}

	private static boolean hasFact(TaleContext tale, Identifier fact) {
		String path = fact.getPath();
		if (path.startsWith("world/")) {
			return StoryFacts.hasWorld(tale.world().getServer(), fact);
		}
		if (path.startsWith("player/")) {
			return StoryFacts.hasPersonal(tale.player(), fact);
		}
		return StoryFacts.hasRelationship(tale.speaker(), tale.player().getUuid(), fact);
	}

	private static void setFact(TaleContext tale, Identifier fact, boolean set) {
		String path = fact.getPath();
		UUID player = tale.player().getUuid();
		if (path.startsWith("world/")) {
			if (set) {
				StoryFacts.setWorld(tale.world().getServer(), fact);
			} else {
				StoryFacts.clearWorld(tale.world().getServer(), fact);
			}
		} else if (path.startsWith("player/")) {
			if (set) {
				StoryFacts.setPersonal(tale.player(), fact);
			} else {
				StoryFacts.clearPersonal(tale.player(), fact);
			}
		} else if (set) {
			StoryFacts.setRelationship(tale.speaker(), player, fact);
		} else {
			StoryFacts.clearRelationship(tale.speaker(), player, fact);
		}
	}

	// ---- Thread events (noticed by StoryThreads) ----

	private static void registerEvents() {
		for (String bare : List.of("sleep", "wake", "day", "night")) {
			TaleVocabulary.registerEvent(cim(bare), (args, cmp) -> count(args, 0, 0));
		}
		TaleVocabulary.registerEvent(cim("enter_biome"), (args, cmp) -> biomeArgs(args));
		// talk(any) when a conversation opens; talk(<node id>) when one reaches that node.
		TaleVocabulary.registerEvent(cim("talk"), (args, cmp) -> args.size() == 1 && args.get(0).equals("any") ? List.of() : ids(args, 1));
		TaleVocabulary.registerEvent(cim("holiday"), (args, cmp) -> ids(args, 1));
		TaleVocabulary.registerEvent(cim("fact"), (args, cmp) -> factIds(args));
	}

	// ---- Variables ----

	private static void registerVariables() {
		TaleVocabulary.registerVariable("pony", tale -> Optional.of(tale.speaker().getName().getString()));
		TaleVocabulary.registerVariable("player", tale -> Optional.of(tale.player().getName().getString()));
		TaleVocabulary.registerVariable("species", tale -> Optional.of(speciesName(species(tale.speaker()))));
		TaleVocabulary.registerVariable("player.species", tale -> SpeciesAttachment.hasChosenSpecies(tale.player())
				? Optional.of(speciesName(SpeciesAttachment.getSpecies(tale.player()))) : Optional.empty());
		TaleVocabulary.registerVariable("profession", tale -> tale.speaker() instanceof VillagerEntity villager
				? Optional.of(villager.getVillagerData().getProfession().id().replace('_', ' ')) : Optional.empty());
		// What Idle Chatter's goodbye hints at - "a surprise" when the pony has no favorite.
		TaleVocabulary.registerVariable("favorite_gift", tale -> Optional.of(FavoriteGiftAttachment.hasFavorite(tale.speaker())
				? FavoriteGiftAttachment.getFavorite(tale.speaker()).getName().getString() : "a surprise"));
		TaleVocabulary.registerVariable("parent", tale -> relativeName(tale, "parent"));
		TaleVocabulary.registerVariable("partner", tale -> relativeName(tale, "partner"));
	}

	/** The first parent's or partner's name, if they're loaded in the world right now. */
	private static Optional<String> relativeName(TaleContext tale, String relative) {
		if (!PonyFamilyAttachment.hasFamily(tale.speaker())) {
			return Optional.empty();
		}
		PonyFamily family = PonyFamilyAttachment.getFamily(tale.speaker());
		List<UUID> uuids = relative.equals("parent") ? family.parents() : family.partner();
		if (uuids.isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(tale.world().getEntity(uuids.get(0))).filter(VillagerEntity.class::isInstance)
				.map(entity -> entity.getName().getString());
	}

	// ---- Reading the pony ----

	/** Ponies that predate species default to Earth Pony, as Idle Chatter always has. */
	private static Species species(Entity pony) {
		return SpeciesAttachment.hasChosenSpecies(pony) ? SpeciesAttachment.getSpecies(pony) : Species.EARTH_PONY;
	}

	private static String speciesName(Species species) {
		return species.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private static Optional<Personality> personality(Entity pony) {
		return PersonalityAttachment.hasPersonality(pony) ? Optional.of(PersonalityAttachment.getPersonality(pony)) : Optional.empty();
	}

	private static LifeStage lifeStage(Entity pony) {
		return PonyFamilyAttachment.hasFamily(pony) ? PonyFamilyAttachment.getFamily(pony).lifeStage() : LifeStage.ADULT;
	}

	private static FeelingBand band(String name) {
		return FeelingBand.valueOf(name.toUpperCase(Locale.ROOT));
	}

	/** Logs, once per use, that a word is waiting on a later system; conditions read as false meanwhile. */
	private static boolean waiting(String what, String system) {
		LOGGER.debug("{} does nothing until {} exists", what, system);
		return false;
	}

	// ---- Argument checks ----

	private static void condition(String name, ArgumentCheck check, ConditionTest test) {
		TaleVocabulary.registerCondition(cim(name), (args, cmp) -> {
			if (cmp.isPresent()) {
				return List.of("only feelings can be compared with " + cmp.get());
			}
			return check.check(args, cmp);
		}, test);
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
	private static List<String> subcommand(List<String> args, List<String> verbs) {
		String[] words = split(args);
		if (args.size() != 1 || words.length != 2 || !verbs.contains(words[0])) {
			return List.of("starts with one of: " + String.join(", ", verbs) + ", then an ID");
		}
		return idProblems(words[1]);
	}

	/** IDs may contain variables ({@code cim:player/comforted_{pony}}), filled in at run time. */
	private static List<String> idProblems(String text) {
		String filled = withoutVariables(text);
		if (filled.isEmpty() || Identifier.tryParse(filled.contains(":") ? filled : "cim:" + filled) == null) {
			return List.of("\"" + text.trim() + "\" isn't a valid ID - lowercase letters, numbers, \"_\", \"/\", \".\", and one \":\"");
		}
		return List.of();
	}

	private static String withoutVariables(String text) {
		return text.trim().replaceAll("\\{[A-Za-z0-9_.]+\\}", "x");
	}

	private static String[] split(List<String> args) {
		return args.isEmpty() ? new String[0] : args.get(0).trim().split("\\s+");
	}

	/** Written IDs default to {@code cim:}. */
	private static Identifier id(String text) {
		String trimmed = text.trim();
		return Identifier.of(trimmed.contains(":") ? trimmed : "cim:" + trimmed);
	}

	/** Vanilla things (biomes, professions) default to {@code minecraft:}. */
	private static Identifier minecraftId(String text) {
		return Identifier.of(text.trim().contains(":") ? text.trim() : "minecraft:" + text.trim());
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
