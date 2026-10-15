package craftingismagic.cim.narrative;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.util.Identifier;

/**
 * Everything a {@code .tale} file is allowed to say - the conditions, effects, thread events, and
 * variables it can name, the systems that may open a conversation ({@code start:}), and the voices,
 * tones, and feelings replies can use. The library itself knows none of these: the game registers
 * its own (see {@code CimTaleVocabulary}), and any other mod can register more with the same calls,
 * under its own namespace. That keeps the narrative library free of the rest of the mod
 * (docs/implementation-plan.md: the narrative library talks to the mod through small interfaces) and
 * open to datapacks and other mods.
 * <p>
 * Each condition and effect comes in two halves: an {@link ArgumentCheck} the validator runs at load
 * time, and the behavior the runtime runs during a conversation. Thread events only have the check
 * until threads run.
 */
public final class TaleVocabulary {
	/** Voice tiers are part of the format itself, not registered. */
	public static final List<String> TIERS = List.of("faint", "clear", "strong");

	private static final Map<Identifier, Condition> conditions = new HashMap<>();
	private static final Map<Identifier, Effect> effects = new HashMap<>();
	private static final Map<Identifier, ArgumentCheck> events = new HashMap<>();
	private static final Map<String, Variable> variables = new LinkedHashMap<>();
	private static final Set<String> startContexts = new LinkedHashSet<>();
	private static final Set<String> leadInContexts = new LinkedHashSet<>();
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
		List<String> check(List<String> args, Optional<String> comparison);
	}

	/** Whether a condition holds right now. Arguments arrive with variables already filled in. */
	@FunctionalInterface
	public interface ConditionTest {
		boolean test(TaleContext context, List<String> args, Optional<String> comparison);
	}

	/** Carries an effect out. Arguments arrive with variables already filled in. */
	@FunctionalInterface
	public interface EffectAction {
		void apply(TaleContext context, List<String> args);
	}

	/** Fills in {@code {name}}; empty when there's nothing sensible to say (the text is left as is). */
	@FunctionalInterface
	public interface Variable {
		Optional<String> resolve(TaleContext context);
	}

	/**
	 * The player's inner voices, as the runtime needs them (README: Inner voices in conversation).
	 * The game supplies one ({@link #setVoices}); until then every voice is silent, so interjections
	 * and voice replies simply don't show.
	 */
	public interface Voices {
		/** How strong {@code voice} is for this player: -1 silent, then the index into {@link #TIERS}. */
		int tier(TaleContext context, String voice);

		/** The chance, in percent, that {@code reply} succeeds right now. */
		int odds(TaleContext context, TaleAst.Node node, TaleAst.Reply reply);

		/** Rolls {@code reply} at {@code odds}; true is success. */
		boolean roll(TaleContext context, TaleAst.Node node, TaleAst.Reply reply, int odds);

		/** The player just tried {@code reply} (grow the voice, remember a {@code !once}). */
		void attempted(TaleContext context, TaleAst.Node node, TaleAst.Reply reply, boolean success);

		/** Whether a {@code !once} reply was already tried with this pony. */
		boolean alreadyTried(TaleContext context, TaleAst.Node node, TaleAst.Reply reply);
	}

	private static final Voices SILENT = new Voices() {
		public int tier(TaleContext context, String voice) {
			return -1;
		}

		public int odds(TaleContext context, TaleAst.Node node, TaleAst.Reply reply) {
			return 50;
		}

		public boolean roll(TaleContext context, TaleAst.Node node, TaleAst.Reply reply, int odds) {
			return context.random().nextInt(100) < odds;
		}

		public void attempted(TaleContext context, TaleAst.Node node, TaleAst.Reply reply, boolean success) {
		}

		public boolean alreadyTried(TaleContext context, TaleAst.Node node, TaleAst.Reply reply) {
			return false;
		}
	};

	private static Voices activeVoices = SILENT;

	public static void setVoices(Voices voices) {
		activeVoices = voices;
	}

	public static Voices voicesImpl() {
		return activeVoices;
	}

	/** {@code faint} is 0, {@code clear} 1, {@code strong} 2; -1 for anything else. */
	public static int tierIndex(String tier) {
		return TIERS.indexOf(normalize(tier));
	}

	public record Condition(ArgumentCheck check, ConditionTest test) {
	}

	public record Effect(ArgumentCheck check, EffectAction action) {
	}

	public static void registerCondition(Identifier id, ArgumentCheck check, ConditionTest test) {
		conditions.put(id, new Condition(check, test));
	}

	public static void registerEffect(Identifier id, ArgumentCheck check, EffectAction action) {
		effects.put(id, new Effect(check, action));
	}

	public static void registerEvent(Identifier id, ArgumentCheck check) {
		events.put(id, check);
	}

	/** {@code name} is written without braces, e.g. {@code pony} or {@code player.species}. */
	public static void registerVariable(String name, Variable variable) {
		variables.put(normalize(name), variable);
	}

	/** A system that opens conversations at {@code start: <name>} nodes, like {@code chatter}. */
	public static void registerStartContext(String name) {
		startContexts.add(normalize(name));
	}

	/**
	 * A speak-only start context: its nodes say their piece and run effects, nothing more - a greeting
	 * that leads in to the conversation, or a farewell that closes it. Replies and jumps in them are
	 * skipped (and the validator says so).
	 */
	public static void registerLeadInContext(String name) {
		registerStartContext(name);
		leadInContexts.add(normalize(name));
	}

	public static boolean isLeadInContext(String name) {
		return leadInContexts.contains(normalize(name));
	}

	public static Set<String> leadInContexts() {
		return Collections.unmodifiableSet(leadInContexts);
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

	public static Condition condition(Identifier id) {
		return conditions.get(id);
	}

	public static Effect effect(Identifier id) {
		return effects.get(id);
	}

	public static ArgumentCheck event(Identifier id) {
		return events.get(id);
	}

	public static Variable variable(String name) {
		return variables.get(normalize(name));
	}

	public static boolean isStartContext(String name) {
		return startContexts.contains(normalize(name));
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

	/** Every registered condition's argument check, for the editor export. */
	public static Map<Identifier, ArgumentCheck> conditionChecks() {
		Map<Identifier, ArgumentCheck> checks = new HashMap<>();
		conditions.forEach((id, condition) -> checks.put(id, condition.check()));
		return checks;
	}

	public static Map<Identifier, ArgumentCheck> effectChecks() {
		Map<Identifier, ArgumentCheck> checks = new HashMap<>();
		effects.forEach((id, effect) -> checks.put(id, effect.check()));
		return checks;
	}

	public static Map<Identifier, ArgumentCheck> eventChecks() {
		return Collections.unmodifiableMap(events);
	}

	public static Set<String> variables() {
		return Collections.unmodifiableSet(variables.keySet());
	}

	public static Set<String> startContexts() {
		return Collections.unmodifiableSet(startContexts);
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
