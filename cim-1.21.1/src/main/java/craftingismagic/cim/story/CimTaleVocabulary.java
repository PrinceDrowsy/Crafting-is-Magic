package craftingismagic.cim.story;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import craftingismagic.cim.memory.Mood;
import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.network.MomentS2CPayload;
import craftingismagic.cim.narrative.TaleVocabulary;
import craftingismagic.cim.narrative.ArgSpec;
import craftingismagic.cim.narrative.TaleVocabulary.ConditionTest;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.ConversationRequests;
import craftingismagic.cim.villager.FavoriteGiftAttachment;
import craftingismagic.cim.villager.VillagerDialogue;
import craftingismagic.cim.villager.VillagerInteractionHandler;
import craftingismagic.cim.villager.Feeling;
import craftingismagic.cim.villager.FeelingBand;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.FriendshipPace;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PersonalityAttachment;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.villager.PersonalityType;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;
import craftingismagic.cim.villager.Quirk;
import craftingismagic.cim.villager.ReplyTone;
import craftingismagic.cim.villager.VillagerGriefHandler;
import craftingismagic.cim.villager.VillagerGossip;
import craftingismagic.cim.voice.VoiceLeanings;

/**
 * The game's own {@code .tale} words (docs/narrative-reference.md): the inner voices, reply tones,
 * feelings, every built-in condition, effect, thread event, and variable, and the {@code chatter}
 * start context. Other mods register theirs the same way, under their own namespace.
 * <p>
 * Some words are accepted now but wait on later systems, and say so in the log when used:
 * and
 * {@code player.heritage}/{@code player.upbringing}/{@code holiday}/{@code season}
 * on the character creator and calendar - those conditions are false until then.
 */
public final class CimTaleVocabulary {
	public static final String CHATTER = "chatter";
	/** Said first when a conversation opens, then it carries on into the topic (README: Greetings). */
	public static final String GREETING = "greeting";
	/** The pony's goodbye when the player says "See you later." - then the conversation closes. */
	public static final String FAREWELL = "farewell";

	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");
	private static final List<String> ELEMENTS = VoiceLeanings.ELEMENTS;
	private static final List<String> SEASONS = List.of("spring", "summer", "autumn", "fall", "winter");
	private static final List<String> FAMILY = List.of("recorded", "orphan", "parent", "partner");
	private static final List<String> RELATIVES = List.of("parent", "partner");
	private static final List<String> TIMES_OF_DAY = List.of("morning", "afternoon", "evening", "night");
	private static final List<String> SINCE_TALKED = List.of("just_now", "today", "a_while", "long", "never");
	private static final long DAY = 24000L;

	private CimTaleVocabulary() {
	}

	public static void register() {
		TaleVocabulary.registerStartContext(CHATTER);
		TaleVocabulary.registerLeadInContext(GREETING);
		TaleVocabulary.registerLeadInContext(FAREWELL);
		// What the player can bring up in conversation (README: Conversations replace the interact menu).
		for (ConversationRequests.Request request : ConversationRequests.ALL) {
			TaleVocabulary.registerStartContext(request.startContext());
		}
		for (String element : ELEMENTS) {
			TaleVocabulary.registerVoice(element, true);
		}
		TaleVocabulary.registerVoice(VoiceLeanings.WHISPER, false);
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
			String name = feeling.name().toLowerCase(Locale.ROOT);
			// Feelings are compared with bands, never numbers (README: no numbers shown to players).
			TaleVocabulary.registerCondition(cim(name), ArgSpec.compared(name + " >= <band>", ArgSpec.word("band", () -> names(FeelingBand.values()))),
					(tale, args, cmp) -> compare(FriendshipAttachment.getBand(tale.speaker(), tale.player().getUuid(), feeling).ordinal(),
							cmp.orElse("=="), band(args.get(0)).ordinal()));
		}
		condition("mood", ArgSpec.of("mood(<emotion>[, <tier>])", ArgSpec.arg(EMOTION), ArgSpec.optionalArg(TIER)), CimTaleVocabulary::mood);
		// How the player is feeling - ponies notice, and soften around grief (README: The mood system).
		condition("player.mood", ArgSpec.of("player.mood(<emotion>[, <tier>])", ArgSpec.arg(EMOTION), ArgSpec.optionalArg(TIER)),
				(tale, args, cmp) -> feels(tale.player(), args));
		condition("fact", ArgSpec.of("fact(<fact>)", ArgSpec.arg(FACT)), (tale, args, cmp) -> hasFact(tale, id(args.get(0))));
		condition("remembers", ArgSpec.of("remembers(<memory>)", ArgSpec.arg(ArgSpec.id("memory"))),
				(tale, args, cmp) -> MemoryStore.remembers(tale.speaker(), id(args.get(0))));
		condition("voice", ArgSpec.of("voice(<voice>[, <tier>])", ArgSpec.arg(ArgSpec.word("voice", () -> List.copyOf(TaleVocabulary.voices()))),
				ArgSpec.optionalArg(TIER)),
				(tale, args, cmp) -> VoiceLeanings.tier(tale.player(), args.get(0))
						>= (args.size() > 1 ? TaleVocabulary.tierIndex(args.get(1)) : 0));
		condition("species", oneWord("species", "species", () -> names(Species.values())),
				(tale, args, cmp) -> species(tale.speaker()).name().equalsIgnoreCase(args.get(0)));
		condition("personality", oneWord("personality", "personality", () -> names(PersonalityType.values())),
				(tale, args, cmp) -> personality(tale.speaker()).map(p -> p.baseType().name().equalsIgnoreCase(args.get(0))).orElse(false));
		condition("quirk", oneWord("quirk", "quirk", () -> names(Quirk.values())),
				(tale, args, cmp) -> personality(tale.speaker()).map(p -> p.quirks().stream().anyMatch(q -> q.name().equalsIgnoreCase(args.get(0))))
						.orElse(false));
		// The tone that suits this pony's personality (README: Personality system, Mechanical hook).
		condition("likes_tone", oneWord("likes_tone", "tone", () -> names(ReplyTone.values())),
				(tale, args, cmp) -> personality(tale.speaker()).flatMap(p -> p.baseType().toneAffinity())
						.map(tone -> tone.name().equalsIgnoreCase(args.get(0))).orElse(false));
		condition("life_stage", oneWord("life_stage", "life stage", () -> names(LifeStage.values())),
				(tale, args, cmp) -> lifeStage(tale.speaker()).name().equalsIgnoreCase(args.get(0)));
		condition("profession", ArgSpec.of("profession(<profession>)", ArgSpec.arg(ArgSpec.id("profession"))),
				(tale, args, cmp) -> tale.speaker() instanceof VillagerEntity villager
						&& Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()).equals(minecraftId(args.get(0))));
		condition("family", oneWord("family", "family", () -> FAMILY), (tale, args, cmp) -> family(tale.speaker(), args.get(0)));
		// A relative is "present" when they're loaded in the world right now, so their name can be said.
		condition("present", oneWord("present", "relative", () -> RELATIVES),
				(tale, args, cmp) -> relativeName(tale, args.get(0)).isPresent());
		condition("player.species", oneWord("player.species", "species", () -> names(Species.values())),
				(tale, args, cmp) -> SpeciesAttachment.hasChosenSpecies(tale.player())
						&& SpeciesAttachment.getSpecies(tale.player()).name().equalsIgnoreCase(args.get(0)));
		condition("player.heritage", ArgSpec.of("player.heritage(<heritage>)", ArgSpec.arg(ArgSpec.name("heritage"))),
				(tale, args, cmp) -> waiting("player.heritage", "the character creator"));
		condition("player.upbringing", ArgSpec.of("player.upbringing(<upbringing>)", ArgSpec.arg(ArgSpec.name("upbringing"))),
				(tale, args, cmp) -> waiting("player.upbringing", "the character creator"));
		// This pony could break news the player hasn't heard yet - news(cim:loss), or any news.
		condition("news", ArgSpec.of("news[(<type>)]", ArgSpec.optionalArg(ArgSpec.id("news type"))),
				(tale, args, cmp) -> PlayerNews.tellable(tale.player(), tale.speaker(), args.isEmpty() ? Optional.empty() : Optional.of(id(args.get(0))))
						.isPresent());
		// Village gossip (README: The memory system): news about another pony this pony would tell the
		// player - gossip, or gossip(<memory type>) for one kind. Gentle news goes through news(...).
		condition("gossip", ArgSpec.of("gossip[(<type>)]", ArgSpec.optionalArg(ArgSpec.id("memory type"))),
				(tale, args, cmp) -> VillagerGossip.storyFor(tale.player(), tale.speaker(), false, optionalId(args)).isPresent());
		// They saw it themselves, rather than hearing it - "I saw..." instead of "I heard...".
		condition("gossip_firsthand", ArgSpec.none("gossip_firsthand"),
				(tale, args, cmp) -> VillagerGossip.storyFor(tale.player(), tale.speaker(), false, Optional.empty())
						.filter(story -> story.hops() == 0).isPresent());
		// Your deeds getting around: something this pony has heard about you and hasn't mentioned yet.
		condition("gossip_about_you", ArgSpec.of("gossip_about_you[(<type>)]", ArgSpec.optionalArg(ArgSpec.id("memory type"))),
				(tale, args, cmp) -> VillagerGossip.storyFor(tale.player(), tale.speaker(), true, optionalId(args)).isPresent());
		condition("night", ArgSpec.none("night"), (tale, args, cmp) -> tale.world().isNight());
		condition("time_of_day", oneWord("time_of_day", "time", () -> TIMES_OF_DAY),
				(tale, args, cmp) -> timeOfDay(tale).equalsIgnoreCase(args.get(0)));
		condition("last_talked", oneWord("last_talked", "when", () -> SINCE_TALKED),
				(tale, args, cmp) -> lastTalked(tale).equalsIgnoreCase(args.get(0)));
		// The gift being offered: gift(favorite) for the pony's favorite, or any item IDs or #tags.
		condition("gift", ArgSpec.repeating("gift(favorite | <item or #tag>, ...)", ArgSpec.arg(ArgSpec.idOrTag("item, or favorite"))),
				CimTaleVocabulary::isGift);
		// Whether the pony will take another gift today - there's a daily limit, and they can say so.
		condition("can_take_gift", ArgSpec.none("can_take_gift"),
				(tale, args, cmp) -> VillagerInteractionHandler.canTakeGift(tale.player(), tale.speaker()));
		condition("raining", ArgSpec.none("raining"), (tale, args, cmp) -> tale.world().isRaining());
		condition("thundering", ArgSpec.none("thundering"), (tale, args, cmp) -> tale.world().isThundering());
		condition("biome", ArgSpec.repeating("biome(<biome or #tag>, ...)", ArgSpec.arg(ArgSpec.idOrTag("biome"))), CimTaleVocabulary::inBiome);
		condition("holiday", ArgSpec.of("holiday(<holiday>)", ArgSpec.arg(ArgSpec.id("holiday"))),
				(tale, args, cmp) -> waiting("holiday(...)", "the calendar"));
		condition("season", oneWord("season", "season", () -> SEASONS), (tale, args, cmp) -> waiting("season(...)", "the calendar"));
	}

	private static final ArgSpec.Token EMOTION = ArgSpec.word("emotion", () -> names(Emotion.values()));
	private static final ArgSpec.Token TIER = ArgSpec.word("tier", () -> TaleVocabulary.TIERS);
	/** Fact IDs say where they live: {@code cim:world/...}, {@code cim:player/...}, {@code cim:relationship/...}. */
	private static final ArgSpec.Token FACT = ArgSpec.scopedId("fact", "world/", "player/", "relationship/");

	private static ArgSpec oneWord(String condition, String label, java.util.function.Supplier<List<String>> values) {
		return ArgSpec.of(condition + "(<" + label + ">)", ArgSpec.arg(ArgSpec.word(label, values)));
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

	/** {@code mood(grief[, strong])}: the pony feels it at least that strongly now (README: The mood system). */
	private static boolean mood(TaleContext tale, List<String> args, Optional<String> comparison) {
		return feels(tale.speaker(), args);
	}

	private static boolean feels(Entity who, List<String> args) {
		Emotion emotion = Emotion.valueOf(args.get(0).toUpperCase(Locale.ROOT));
		EmotionIntensity least = args.size() > 1 ? EmotionIntensity.valueOf(args.get(1).toUpperCase(Locale.ROOT)) : EmotionIntensity.FAINT;
		return Mood.feels(who, emotion, least);
	}

	/**
	 * The time of day as this pony lives it: morning 5-11, afternoon 11-17, evening 17-20, night 20-5.
	 * Bat ponies keep a night clock, so theirs is shifted half a day - their morning is dusk.
	 */
	static String timeOfDay(TaleContext tale) {
		long time = Math.floorMod(tale.world().getTimeOfDay(), DAY); // 0 is 6:00
		if (species(tale.speaker()) == Species.BAT_PONY) {
			time = (time + DAY / 2) % DAY;
		}
		if (time >= 23000 || time < 5000) {
			return "morning";
		}
		return time < 11000 ? "afternoon" : time < 14000 ? "evening" : "night";
	}

	/** How long since this pony and the player last talked: just_now (2 minutes), today, a_while (a week), long. */
	static String lastTalked(TaleContext tale) {
		long last = FriendshipAttachment.getLastTalked(tale.speaker(), tale.player().getUuid());
		if (last < 0) {
			return "never";
		}
		long since = tale.world().getTime() - last;
		return since < 2400 ? "just_now" : since < DAY ? "today" : since < 7 * DAY ? "a_while" : "long";
	}

	private static boolean isGift(TaleContext tale, List<String> args, Optional<String> comparison) {
		if (tale.item().isEmpty()) {
			return false;
		}
		for (String arg : args) {
			String name = arg.trim();
			if (name.equalsIgnoreCase("favorite") ? VillagerInteractionHandler.isFavoriteGift(tale.speaker(), tale.item())
					: name.startsWith("#") ? tale.item().isIn(TagKey.of(RegistryKeys.ITEM, minecraftId(name.substring(1))))
					: Registries.ITEM.getId(tale.item().getItem()).equals(minecraftId(name))) {
				return true;
			}
		}
		return false;
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
		// Warmth in conversation is an everyday habit: tapered within a day and as the feeling grows,
		// paced by personality (README: friendship pacing). The big jumps come from experiences -
		// ~ memory - not from bigger numbers here.
		for (Feeling feeling : Feeling.values()) {
			String name = feeling.name().toLowerCase(Locale.ROOT);
			TaleVocabulary.registerEffect(cim(name), ArgSpec.of("~ " + name + " +N", ArgSpec.arg(ArgSpec.signedNumber("change"))),
					(tale, args) -> FriendshipPace.habit(tale.speaker(), tale.player(), feeling,
							Integer.parseInt(args.get(0).replace("+", ""))));
		}
		TaleVocabulary.registerEffect(cim("memory"), ArgSpec.of("~ memory <memory>[, <emotion> [tier]]", ArgSpec.arg(ArgSpec.id("memory")),
				ArgSpec.optionalArg(EMOTION, ArgSpec.optional(TIER))), CimTaleVocabulary::addMemory);
		// The player learns the news this pony is breaking - now the feeling and its moment arrive.
		TaleVocabulary.registerEffect(cim("tell_news"), ArgSpec.of("~ tell_news [<type>]", ArgSpec.optionalArg(ArgSpec.id("news type"))),
				(tale, args) -> PlayerNews.tellable(tale.player(), tale.speaker(), args.isEmpty() ? Optional.empty() : Optional.of(id(args.get(0))))
						.ifPresent(news -> PlayerNews.learn(tale.player(), news)));
		// The pony tells the gossip: the player now knows it (and won't be told it again).
		TaleVocabulary.registerEffect(cim("tell_gossip"), ArgSpec.of("~ tell_gossip [<type>]", ArgSpec.optionalArg(ArgSpec.id("memory type"))),
				(tale, args) -> VillagerGossip.tellPlayer(tale.player(), tale.speaker(), false, optionalId(args)));
		// The pony mentions what they've heard about you - once.
		TaleVocabulary.registerEffect(cim("tell_gossip_about_you"),
				ArgSpec.of("~ tell_gossip_about_you [<type>]", ArgSpec.optionalArg(ArgSpec.id("memory type"))),
				(tale, args) -> VillagerGossip.tellPlayer(tale.player(), tale.speaker(), true, optionalId(args)));
		TaleVocabulary.registerEffect(cim("fact"), ArgSpec.of("~ fact set|clear <fact>", ArgSpec.arg(ArgSpec.literal("set", "clear"), FACT)),
				(tale, args) -> {
					String[] words = split(args);
					setFact(tale, id(words[1]), words[0].equals("set"));
				});
		TaleVocabulary.registerEffect(cim("thread"), ArgSpec.anyOf("~ thread start <thread> | ~ thread advance <thread> <stage>",
				ArgSpec.form(ArgSpec.arg(ArgSpec.literal("start"), ArgSpec.id("thread"))),
				ArgSpec.form(ArgSpec.arg(ArgSpec.literal("advance"), ArgSpec.id("thread"), ArgSpec.name("stage")))), (tale, args) -> {
					String[] words = split(args);
					if (words[0].equals("start")) {
						StoryThreads.start(tale.player(), id(words[1]), false);
					} else {
						StoryThreads.advance(tale.player(), id(words[1]), words[2]);
					}
				});
		TaleVocabulary.registerEffect(cim("moment"), ArgSpec.of("~ moment <moment>", ArgSpec.arg(ArgSpec.id("moment"))),
				(tale, args) -> ServerPlayNetworking.send(tale.player(), new MomentS2CPayload(id(args.get(0)))));
		// The pony takes the gift being offered (one of it) - feelings grow, more for a favorite.
		TaleVocabulary.registerEffect(cim("take_gift"), ArgSpec.none("~ take_gift"),
				(tale, args) -> VillagerInteractionHandler.takeGift(tale.player(), tale.speaker(), tale.item()));
		// The trade screen opens - once the pony's lines have been read, not over them.
		TaleVocabulary.registerEffect(cim("open_trade"), ArgSpec.none("~ open_trade"),
				(tale, args) -> VillagerDialogue.afterLines(tale.player(),
						() -> VillagerInteractionHandler.offerTrade(tale.player(), tale.speaker().getId())));
		TaleVocabulary.registerEffect(cim("leaning"), ArgSpec.of("~ leaning <element> +N",
				ArgSpec.arg(ArgSpec.word("element", () -> ELEMENTS), ArgSpec.signedNumber("change"))), (tale, args) -> {
					String[] words = split(args);
					VoiceLeanings.grow(tale.player(), words[0], Integer.parseInt(words[1].replace("+", "")));
				});
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

	// ---- Facts ----

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
			TaleVocabulary.registerEvent(cim(bare), ArgSpec.none(bare));
		}
		TaleVocabulary.registerEvent(cim("enter_biome"), ArgSpec.repeating("enter_biome(<biome or #tag>, ...)",
				ArgSpec.arg(ArgSpec.idOrTag("biome"))));
		// talk(any) when a conversation opens; talk(<node id>) when one reaches that node.
		TaleVocabulary.registerEvent(cim("talk"), ArgSpec.of("talk(any | <node>)", ArgSpec.arg(ArgSpec.id("node, or any"))));
		TaleVocabulary.registerEvent(cim("holiday"), ArgSpec.of("holiday(<holiday>)", ArgSpec.arg(ArgSpec.id("holiday"))));
		TaleVocabulary.registerEvent(cim("fact"), ArgSpec.of("fact(<fact>)", ArgSpec.arg(FACT)));
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
		// Who the player is grieving most right now, so a gentle line can say who it means.
		TaleVocabulary.registerVariable("mourning", CimTaleVocabulary::mourning);
		TaleVocabulary.registerVariable("news_subject", tale -> PlayerNews.tellable(tale.player(), tale.speaker(), Optional.empty())
				.map(PlayerNews.News::subjectName));
		// Village gossip: who the story is about, and who told this pony (when they heard it, not saw it).
		TaleVocabulary.registerVariable("gossip_subject", tale -> VillagerGossip.storyFor(tale.player(), tale.speaker(), false, Optional.empty())
				.flatMap(story -> VillagerGossip.name(tale.world().getServer(), story.subject().get())));
		TaleVocabulary.registerVariable("gossip_source", tale -> gossipSource(tale));
		// The gift being offered, by name: "a {gift}?" - in lowercase, so it reads inside a sentence.
		TaleVocabulary.registerVariable("gift", tale -> tale.item().isEmpty() ? Optional.empty()
				: Optional.of(tale.item().getName().getString().toLowerCase(Locale.ROOT)));
		TaleVocabulary.registerVariable("parent", tale -> relativeName(tale, "parent"));
		TaleVocabulary.registerVariable("partner", tale -> relativeName(tale, "partner"));
	}

	/** Who told this pony the story they'd tell - about another pony, or else about you. */
	private static Optional<String> gossipSource(TaleContext tale) {
		Optional<craftingismagic.cim.memory.Memory> story = VillagerGossip.storyFor(tale.player(), tale.speaker(), false, Optional.empty())
				.or(() -> VillagerGossip.storyFor(tale.player(), tale.speaker(), true, Optional.empty()));
		return story.flatMap(craftingismagic.cim.memory.Memory::source).flatMap(who -> VillagerGossip.name(tale.world().getServer(), who));
	}

	private static Optional<Identifier> optionalId(List<String> args) {
		return args.isEmpty() ? Optional.empty() : Optional.of(id(args.get(0)));
	}

	/** The one whose loss the player feels most strongly now, by the name they knew them by. */
	private static Optional<String> mourning(TaleContext tale) {
		long now = tale.world().getTime();
		return MemoryStore.getAll(tale.player()).stream()
				.filter(memory -> memory.type().equals(VillagerGriefHandler.LOSS) && memory.subject().isPresent())
				.filter(memory -> Mood.strength(memory, now) > 0f)
				.max(java.util.Comparator.comparingDouble(memory -> Mood.strength(memory, now)))
				// A loss from before names were kept still reads naturally: "I heard about them".
				.map(memory -> PlayerNews.rememberedName(tale.player(), memory.subject().get()).orElse("them"));
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

	// ---- Helpers ----

	/** Argument rules are declarative (ArgSpec), so the .tale editor checks with the very same ones. */
	private static void condition(String name, ArgSpec spec, ConditionTest test) {
		TaleVocabulary.registerCondition(cim(name), spec, test);
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

	private static Identifier cim(String path) {
		return Identifier.of("cim", path.toLowerCase(Locale.ROOT));
	}
}
