package craftingismagic.cim.voice;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.debug.DebugOverlay;
import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.memory.Mood;
import craftingismagic.cim.narrative.TaleAst.Node;
import craftingismagic.cim.narrative.TaleAst.Reply;
import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.narrative.TaleVocabulary;
import craftingismagic.cim.story.StoryFacts;
import craftingismagic.cim.villager.Feeling;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.PersonalityAttachment;

/**
 * The game's inner voices for the narrative runtime: how strong each is ({@link VoiceLeanings}), and
 * how a voice reply rolls (design doc: Inner voices in conversation - Voice options can fail; Dice).
 * <p>
 * <b>Odds</b> start from the voice's strength (30-60%) and move with every odds shaper the design doc
 * lists: the pony valuing that Element (+15), the feeling the reply names (none -10 up to devoted
 * +22), the pony's mood (a grieving pony is more open to Kindness and less to Laughter), and what
 * they remember about the player (good memories help, bad ones hurt). Always between 5% and 95% -
 * a long shot can land, a sure thing can slip. All numbers are first-pass.
 * <p>
 * <b>The roll is seeded</b> by the player, the pony, the reply, and the state that shapes the odds -
 * so retrying right away gives the same answer, and only a real change (a stronger voice, more
 * Trust, a new memory) brings a fresh roll. That's the design doc's retry rule and its protection
 * against save-scumming in one.
 */
public final class CimVoices implements TaleVocabulary.Voices {
	public static final int MIN_ODDS = 5;
	public static final int MAX_ODDS = 95;

	/** Debug: players whose next rolls are forced (true success, false failure). */
	private static final Map<UUID, Boolean> forced = new HashMap<>();
	/** The last roll each player made, for the F7 overlay. */
	private static final Map<UUID, String> lastRoll = new HashMap<>();

	public static void register() {
		TaleVocabulary.setVoices(new CimVoices());
		DebugOverlay.addProvider((player, lines) -> {
			StringBuilder voices = new StringBuilder("Voices:");
			for (String voice : VoiceLeanings.ALL) {
				voices.append(' ').append(voice, 0, 3).append(' ').append(VoiceLeanings.points(player, voice))
						.append(VoiceLeanings.tierName(VoiceLeanings.tier(player, voice)).charAt(0));
			}
			lines.add(voices.toString());
			Optional.ofNullable(lastRoll.get(player.getUuid())).ifPresent(roll -> lines.add("Last roll: " + roll));
			Optional.ofNullable(forced.get(player.getUuid())).ifPresent(force -> lines.add("Dice forced: " + (force ? "success" : "failure")));
		});
	}

	/** Debug: force every roll for {@code player}, or {@code null} to roll fairly again. */
	public static void force(UUID player, Boolean outcome) {
		if (outcome == null) {
			forced.remove(player);
		} else {
			forced.put(player, outcome);
		}
	}

	@Override
	public int tier(TaleContext context, String voice) {
		return VoiceLeanings.tier(context.player(), voice);
	}

	@Override
	public int odds(TaleContext context, Node node, Reply reply) {
		String voice = TaleVocabulary.normalize(reply.voice().orElse(""));
		Entity pony = context.speaker();
		UUID player = context.player().getUuid();

		int odds = 30 + Math.round(Math.min(VoiceLeanings.points(context.player(), voice), 150) / 150f * 30);
		if (PersonalityAttachment.hasPersonality(pony)
				&& PersonalityAttachment.getPersonality(pony).baseType().valuedElements().contains(voice)) {
			odds += 15;
		}
		Optional<Feeling> feeling = reply.feeling().flatMap(CimVoices::feeling);
		if (feeling.isPresent() && TaleVocabulary.voiceTakesFeeling(voice)) {
			odds += switch (FriendshipAttachment.getBand(pony, player, feeling.get())) {
				case NONE -> -10;
				case LOW -> 0;
				case MEDIUM -> 8;
				case HIGH -> 15;
				case DEVOTED -> 22;
			};
		}
		odds += moodShift(voice, moodOf(pony));
		odds += memoryShift(pony, player);
		return Math.max(MIN_ODDS, Math.min(MAX_ODDS, odds));
	}

	@Override
	public boolean roll(TaleContext context, Node node, Reply reply, int odds) {
		String voice = TaleVocabulary.normalize(reply.voice().orElse(""));
		UUID player = context.player().getUuid();
		Boolean force = forced.get(player);
		int roll;
		boolean success;
		if (force != null) {
			roll = force ? 0 : 99;
			success = force;
		} else {
			roll = new Random(seed(context, node, reply, voice)).nextInt(100);
			success = roll < odds;
		}
		lastRoll.put(player, voice + " at " + odds + "%, rolled " + roll + " - " + (success ? "success" : "failure")
				+ (force != null ? " (forced)" : ""));
		return success;
	}

	@Override
	public void attempted(TaleContext context, Node node, Reply reply, boolean success) {
		VoiceLeanings.grow(context.player(), reply.voice().orElse(""), VoiceLeanings.USE_GROWTH);
		if (reply.once()) {
			StoryFacts.setRelationship(context.speaker(), context.player().getUuid(), triedFact(node, reply));
		}
	}

	@Override
	public boolean alreadyTried(TaleContext context, Node node, Reply reply) {
		return StoryFacts.hasRelationship(context.speaker(), context.player().getUuid(), triedFact(node, reply));
	}

	/** Same player, pony, reply, and odds-shaping state: same roll. Any real change: a new one. */
	private static long seed(TaleContext context, Node node, Reply reply, String voice) {
		Entity pony = context.speaker();
		UUID player = context.player().getUuid();
		int feelingValue = reply.feeling().flatMap(CimVoices::feeling).map(f -> FriendshipAttachment.getFeeling(pony, player, f)).orElse(0);
		List<Memory> memories = MemoryStore.getAll(pony);
		return Objects.hash(player, pony.getUuid(), node.id(), reply.line(), VoiceLeanings.points(context.player(), voice), feelingValue,
				memories.size(), memories.isEmpty() ? 0L : memories.get(memories.size() - 1).time());
	}

	/** A {@code !once} reply that's been tried, kept as a relationship fact so it shows in the fact list. */
	private static Identifier triedFact(Node node, Reply reply) {
		return Identifier.of("cim", "relationship/tried/" + node.id().getNamespace() + "/" + node.id().getPath() + "/" + reply.line());
	}

	/** Emotions the pony feels at least clearly right now (design doc: The mood system). */
	private static Set<Emotion> moodOf(Entity pony) {
		Set<Emotion> mood = EnumSet.noneOf(Emotion.class);
		Mood.of(pony).forEach((emotion, strength) -> {
			if (strength >= Mood.threshold(EmotionIntensity.CLEAR)) {
				mood.add(emotion);
			}
		});
		return mood;
	}

	/** The right voice at the right moment: grief opens a pony to Kindness and closes them to jokes. */
	private static int moodShift(String voice, Set<Emotion> mood) {
		boolean hurting = mood.contains(Emotion.GRIEF) || mood.contains(Emotion.FEAR);
		return switch (voice) {
			case "kindness", "generosity" -> hurting ? 10 : 0;
			case "laughter" -> (mood.contains(Emotion.JOY) ? 10 : 0) - (mood.contains(Emotion.GRIEF) || mood.contains(Emotion.ANGER) ? 10 : 0);
			case "honesty" -> mood.contains(Emotion.ANGER) ? -10 : 0;
			case "loyalty" -> mood.contains(Emotion.FEAR) ? 10 : 0;
			case "magic" -> mood.contains(Emotion.JOY) || mood.contains(Emotion.CALM) ? 5 : 0;
			case VoiceLeanings.WHISPER -> hurting ? 10 : 0;
			default -> 0;
		};
	}

	/** What the pony remembers about the player: warm memories help, painful ones hurt, within ±12. */
	private static int memoryShift(Entity pony, UUID player) {
		int shift = 0;
		for (Memory memory : MemoryStore.about(pony, player)) {
			if (memory.emotion().isEmpty()) {
				continue;
			}
			shift += switch (memory.emotion().get()) {
				case JOY, LOVE, CALM -> 4;
				case ANGER, FEAR, GRIEF -> -4;
			};
		}
		return Math.max(-12, Math.min(12, shift));
	}

	private static Optional<Feeling> feeling(String name) {
		for (Feeling feeling : Feeling.values()) {
			if (feeling.name().equalsIgnoreCase(name.trim())) {
				return Optional.of(feeling);
			}
		}
		return Optional.empty();
	}

	/** The phrase band (0 long shot ... 4 almost certain) shown for {@code odds} - no numbers on screen. */
	public static int band(int odds) {
		return odds < 25 ? 0 : odds < 45 ? 1 : odds < 60 ? 2 : odds < 80 ? 3 : 4;
	}
}
