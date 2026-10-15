package craftingismagic.cim.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.narrative.TaleAst.ConditionCall;
import craftingismagic.cim.narrative.TaleAst.EffectCall;
import craftingismagic.cim.narrative.TaleAst.EffectStatement;
import craftingismagic.cim.narrative.TaleAst.Interjection;
import craftingismagic.cim.narrative.TaleAst.Jump;
import craftingismagic.cim.narrative.TaleAst.Node;
import craftingismagic.cim.narrative.TaleAst.Reply;
import craftingismagic.cim.narrative.TaleAst.Spoken;
import craftingismagic.cim.narrative.TaleAst.Statement;
import craftingismagic.cim.narrative.TaleAst.WeightBonus;

/**
 * Plays loaded {@code .tale} conversations. A {@link Conversation} runs a node from the top - spoken
 * lines pile up, effects happen, jumps are followed - until it reaches the node's replies (it pauses
 * and hands back a {@link Beat}) or runs out (it ends). Interjections and voice replies show only
 * when the player's voice is strong enough ({@link TaleVocabulary.Voices}); a voice reply is rolled,
 * and the conversation follows its success or failure branch.
 * <p>
 * Never throws on bad content: a broken condition counts as false, a broken effect is skipped, and a
 * jump loop is cut off after {@link #MAX_STEPS} statements - each logged, so a datapack mistake can't
 * crash or hang the server.
 */
public final class TaleRuntime {
	public static final String END = "END";
	public static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z0-9_.]+)\\}");
	/** Statements one advance may run before it's treated as an endless loop. */
	public static final int MAX_STEPS = 256;
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");

	private TaleRuntime() {
	}

	/** What a line on screen is. */
	public enum LineKind {
		/** Someone speaking. */
		SPOKEN,
		/** An inner voice speaking up ({@code voice} says which). */
		VOICE,
		/** A voice reply just landed ({@code voice} says which). */
		SUCCESS,
		/** A voice reply just didn't land. */
		FAILURE
	}

	/**
	 * One line on screen.
	 *
	 * @param partner true when the conversation partner ({@code {pony}}) says it, so screens can leave
	 *                the name off
	 * @param voice   the voice, for every kind but {@link LineKind#SPOKEN}; empty otherwise
	 */
	public record Line(LineKind kind, String speaker, boolean partner, String text, String voice) {
		public static Line spoken(String speaker, boolean partner, String text) {
			return new Line(LineKind.SPOKEN, speaker, partner, text, "");
		}
	}

	/**
	 * A reply the player can pick; {@code text} may be empty for a tone reply, in which case screens
	 * show the tone.
	 *
	 * @param odds for a voice reply, its chance in percent; -1 for a tone reply
	 */
	public record Option(Optional<String> tone, Optional<String> voice, String text, int odds) {
	}

	/** What to show now: the lines since the last choice, and either options or the end. */
	public record Beat(List<Line> lines, List<Option> options, boolean ended) {
	}

	/**
	 * Picks where a conversation opens: among nodes marked {@code start: <context>} whose {@code when:}
	 * holds, weighted by {@code weight:}. Empty when none fit.
	 */
	public static Optional<Node> pickOpener(String context, TaleContext tale) {
		return pickOpener(context, tale, null);
	}

	/**
	 * Like {@link #pickOpener(String, TaleContext)}, but only among nodes of exactly {@code tone} (empty
	 * for untoned) - how a greeting is matched to the conversation it opens. {@code null} takes any tone.
	 */
	public static Optional<Node> pickOpener(String context, TaleContext tale, Optional<String> tone) {
		List<Node> candidates = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (Node node : TaleLibrary.get().nodes().values()) {
			if (!node.start().contains(context) || (tone != null && !node.tone().equals(tone)) || !allHold(node.when(), tale)) {
				continue;
			}
			int weight = node.weight();
			for (WeightBonus bonus : node.bonuses()) {
				if (allHold(bonus.conditions(), tale)) {
					weight += bonus.amount();
				}
			}
			if (weight > 0) {
				candidates.add(node);
				weights.add(weight);
				total += weight;
			}
		}
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		int roll = tale.random().nextInt(total);
		for (int i = 0; i < candidates.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return Optional.of(candidates.get(i));
			}
		}
		return Optional.of(candidates.get(candidates.size() - 1));
	}

	public static Conversation start(Node node, TaleContext tale) {
		return new Conversation(node, tale, Optional.empty());
	}

	/** Opens at {@code node} after {@code leadIn} (a greeting, say) has said its piece. */
	public static Conversation start(Node node, TaleContext tale, Optional<Node> leadIn) {
		return new Conversation(node, tale, leadIn);
	}

	/**
	 * Plays {@code node} as speech only - its lines and effects, never its replies or jumps - and ends.
	 * For speak-only contexts like farewells: the pony's goodbye, then the conversation is over.
	 */
	public static Conversation speakOnly(Node node, TaleContext tale) {
		return new Conversation(node, tale);
	}

	/** One conversation in progress. Not thread-safe; lives on the server thread. */
	public static final class Conversation {
		private final TaleContext tale;
		private Node node;
		private final List<Reply> offered = new ArrayList<>();
		/** Nodes played since {@link #drainVisited} was last called. */
		private final List<Identifier> visited = new ArrayList<>();
		private Beat beat;

		private Conversation(Node node, TaleContext tale, Optional<Node> leadIn) {
			this.tale = tale;
			this.node = node;
			List<Line> lines = new ArrayList<>();
			leadIn.ifPresent(greeting -> leadIn(greeting, lines));
			this.beat = run(node, lines, new int[] {0});
		}

		/** Speech only: {@code node}'s lines and effects, then the end. */
		private Conversation(Node node, TaleContext tale) {
			this.tale = tale;
			this.node = node;
			List<Line> lines = new ArrayList<>();
			leadIn(node, lines);
			this.beat = new Beat(List.copyOf(lines), List.of(), true);
		}

		/** A lead-in only speaks and runs effects; its replies and jumps are skipped (the validator warns). */
		private void leadIn(Node from, List<Line> lines) {
			visited.add(from.id());
			for (Statement statement : from.body()) {
				switch (statement) {
					case Spoken spoken -> lines.add(line(spoken));
					case EffectStatement effect -> {
						if (allHold(effect.conditions(), tale)) {
							apply(effect.effect(), tale);
						}
					}
					case Interjection interjection -> {
						if (hears(interjection.voice(), interjection.tier())) {
							lines.add(new Line(LineKind.VOICE, "", false, fill(interjection.text(), tale, false),
									TaleVocabulary.normalize(interjection.voice())));
						}
					}
					default -> {
					}
				}
			}
		}

		public Beat beat() {
			return beat;
		}

		public TaleContext context() {
			return tale;
		}

		/** The node the conversation is at (where the current options came from). */
		public Node node() {
			return node;
		}

		/** The nodes played since the last call, in order - for {@code talk(<node>)} thread events. */
		public List<Identifier> drainVisited() {
			List<Identifier> nodes = List.copyOf(visited);
			visited.clear();
			return nodes;
		}

		/** Picks option {@code index} of the current beat. Out-of-range picks are ignored. */
		public Beat choose(int index) {
			if (beat.ended() || index < 0 || index >= offered.size()) {
				return beat;
			}
			Reply reply = offered.get(index);
			Node from = node;
			offered.clear();
			List<Line> lines = new ArrayList<>();
			int[] steps = {0};
			Optional<String> rolledTarget = Optional.empty();
			if (reply.isVoiceReply()) {
				TaleVocabulary.Voices voices = TaleVocabulary.voicesImpl();
				String voice = TaleVocabulary.normalize(reply.voice().get());
				boolean success;
				try {
					success = voices.roll(tale, from, reply, voices.odds(tale, from, reply));
					voices.attempted(tale, from, reply, success);
				} catch (RuntimeException e) {
					LOGGER.error("{}: line {}: rolling the voice reply failed; treating it as a failure", from.id(), reply.line(), e);
					success = false;
				}
				lines.add(new Line(success ? LineKind.SUCCESS : LineKind.FAILURE, "", false, "", voice));
				rolledTarget = success ? reply.successTarget() : reply.failureTarget();
			}
			for (Statement statement : reply.body()) {
				if (++steps[0] > MAX_STEPS) {
					return beat = loop();
				}
				if (statement instanceof EffectStatement effect && allHold(effect.conditions(), tale)) {
					apply(effect.effect(), tale);
				} else if (statement instanceof Jump jump && allHold(jump.conditions(), tale)) {
					return beat = follow(jump.target(), lines, steps);
				}
			}
			if (rolledTarget.isPresent()) {
				return beat = follow(rolledTarget.get(), lines, steps);
			}
			return beat = new Beat(List.copyOf(lines), List.of(), true);
		}

		/** Runs {@code from} from the top until it pauses or ends. */
		private Beat run(Node from, List<Line> lines, int[] steps) {
			node = from;
			visited.add(from.id());
			offered.clear();
			List<Statement> body = from.body();
			for (int i = 0; i < body.size(); i++) {
				if (++steps[0] > MAX_STEPS) {
					return loop();
				}
				Statement statement = body.get(i);
				if (!offered.isEmpty() && !(statement instanceof Reply)) {
					continue; // once replies start, the rest of the node is replies
				}
				switch (statement) {
					case Spoken spoken -> lines.add(line(spoken));
					case EffectStatement effect -> {
						if (allHold(effect.conditions(), tale)) {
							apply(effect.effect(), tale);
						}
					}
					case Jump jump -> {
						if (allHold(jump.conditions(), tale)) {
							return follow(jump.target(), lines, steps);
						}
					}
					case Interjection interjection -> {
						if (hears(interjection.voice(), interjection.tier())) {
							lines.add(new Line(LineKind.VOICE, "", false, fill(interjection.text(), tale, false),
									TaleVocabulary.normalize(interjection.voice())));
						}
					}
					case Reply reply -> {
						if (!reply.isVoiceReply() || offersVoiceReply(from, reply)) {
							offered.add(reply);
						}
					}
				}
			}
			if (offered.isEmpty()) {
				return new Beat(List.copyOf(lines), List.of(), true);
			}
			List<Option> options = new ArrayList<>();
			for (Reply reply : offered) {
				if (reply.isVoiceReply()) {
					int odds;
					try {
						odds = TaleVocabulary.voicesImpl().odds(tale, from, reply);
					} catch (RuntimeException e) {
						LOGGER.error("{}: line {}: working out the odds failed", from.id(), reply.line(), e);
						odds = 50;
					}
					options.add(new Option(Optional.empty(), Optional.of(TaleVocabulary.normalize(reply.voice().get())),
							fill(reply.text(), tale, false), odds));
				} else {
					options.add(new Option(reply.tone(), Optional.empty(), fill(reply.text(), tale, false), -1));
				}
			}
			return new Beat(List.copyOf(lines), List.copyOf(options), false);
		}

		/** Whether the player's {@code voice} is at least {@code tier}. */
		private boolean hears(String voice, String tier) {
			try {
				int needed = Math.max(0, TaleVocabulary.tierIndex(tier));
				return TaleVocabulary.voicesImpl().tier(tale, TaleVocabulary.normalize(voice)) >= needed;
			} catch (RuntimeException e) {
				LOGGER.error("Checking the {} voice failed; staying quiet", voice, e);
				return false;
			}
		}

		/** A voice reply shows when the voice is strong enough and a {@code !once} hasn't been tried. */
		private boolean offersVoiceReply(Node from, Reply reply) {
			if (!hears(reply.voice().get(), reply.voiceTier().orElse("faint"))) {
				return false;
			}
			try {
				return !reply.once() || !TaleVocabulary.voicesImpl().alreadyTried(tale, from, reply);
			} catch (RuntimeException e) {
				LOGGER.error("{}: line {}: checking a !once reply failed; hiding it", from.id(), reply.line(), e);
				return false;
			}
		}

		private Beat follow(String target, List<Line> lines, int[] steps) {
			if (target.equals(END)) {
				offered.clear();
				return new Beat(List.copyOf(lines), List.of(), true);
			}
			Identifier id = resolve(node, target);
			Node next = id == null ? null : TaleLibrary.get().nodes().get(id);
			if (next == null) {
				LOGGER.error("{}: line {}: no node \"{}\" to jump to - ending the conversation", node.id(), node.line(), target);
				offered.clear();
				return new Beat(List.copyOf(lines), List.of(), true);
			}
			return run(next, lines, steps);
		}

		private Beat loop() {
			LOGGER.error("{}: more than {} steps without a reply - probably a jump loop; ending the conversation", node.id(), MAX_STEPS);
			offered.clear();
			return new Beat(List.of(), List.of(), true);
		}

		private Line line(Spoken spoken) {
			List<String> texts = spoken.texts();
			String text = texts.size() == 1 ? texts.get(0) : texts.get(tale.random().nextInt(texts.size()));
			boolean partner = spoken.speaker().equals("{pony}");
			return Line.spoken(fill(spoken.speaker(), tale, false), partner, fill(text, tale, false));
		}
	}

	/** A jump target written in {@code from}'s file: a local node name, or a full node ID. */
	public static Identifier resolve(Node from, String target) {
		if (target.contains(":")) {
			return Identifier.tryParse(target);
		}
		String path = from.id().getPath();
		return Identifier.tryParse(from.id().getNamespace() + ":" + path.substring(0, path.lastIndexOf('/') + 1) + target);
	}

	public static boolean allHold(List<ConditionCall> conditions, TaleContext tale) {
		for (ConditionCall condition : conditions) {
			if (!holds(condition, tale)) {
				return false;
			}
		}
		return true;
	}

	public static boolean holds(ConditionCall condition, TaleContext tale) {
		TaleVocabulary.Condition type = TaleVocabulary.condition(condition.type());
		if (type == null) {
			return false;
		}
		boolean result;
		try {
			result = type.test().test(tale, fillAll(condition.args(), tale), condition.comparison());
		} catch (RuntimeException e) {
			LOGGER.error("Condition \"{}\" (line {}) failed; treating it as false", condition.source(), condition.line(), e);
			return false;
		}
		return result != condition.negated();
	}

	public static void apply(EffectCall effect, TaleContext tale) {
		TaleVocabulary.Effect type = TaleVocabulary.effect(effect.type());
		if (type == null) {
			return;
		}
		try {
			type.action().apply(tale, fillAll(effect.args(), tale));
		} catch (RuntimeException e) {
			LOGGER.error("Effect \"~ {}\" (line {}) failed; skipping it", effect.source(), effect.line(), e);
		}
	}

	private static List<String> fillAll(List<String> args, TaleContext tale) {
		return args.stream().map(arg -> fill(arg, tale, true)).toList();
	}

	/**
	 * Fills in {@code {variables}}. In arguments ({@code asId}) the value is made ID-safe, so
	 * {@code cim:player/comforted_{pony}} becomes {@code cim:player/comforted_apple_bloom}.
	 */
	public static String fill(String text, TaleContext tale, boolean asId) {
		if (text.indexOf('{') < 0) {
			return text;
		}
		Matcher matcher = VARIABLE.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			TaleVocabulary.Variable variable = TaleVocabulary.variable(matcher.group(1));
			Optional<String> value = Optional.empty();
			if (variable != null) {
				try {
					value = variable.resolve(tale);
				} catch (RuntimeException e) {
					LOGGER.error("Variable {{}} failed", matcher.group(1), e);
				}
			}
			String replacement = value.map(v -> asId ? v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_") : v).orElse(matcher.group());
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
