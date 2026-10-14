package craftingismagic.cim.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.Identifier;

import craftingismagic.cim.narrative.TaleAst.After;
import craftingismagic.cim.narrative.TaleAst.ConditionCall;
import craftingismagic.cim.narrative.TaleAst.EffectCall;
import craftingismagic.cim.narrative.TaleAst.EffectStatement;
import craftingismagic.cim.narrative.TaleAst.EventCall;
import craftingismagic.cim.narrative.TaleAst.Interjection;
import craftingismagic.cim.narrative.TaleAst.Jump;
import craftingismagic.cim.narrative.TaleAst.Node;
import craftingismagic.cim.narrative.TaleAst.Reply;
import craftingismagic.cim.narrative.TaleAst.Spoken;
import craftingismagic.cim.narrative.TaleAst.Stage;
import craftingismagic.cim.narrative.TaleAst.Statement;
import craftingismagic.cim.narrative.TaleAst.TaleFile;
import craftingismagic.cim.narrative.TaleAst.Trigger;
import craftingismagic.cim.narrative.TaleAst.WeightBonus;

/**
 * Turns the text of a {@code .tale} file into a {@link TaleFile} (docs/narrative-reference.md). It
 * reads one line at a time; indentation decides what belongs to a reply or a thread stage. It never
 * throws on bad input - every problem is recorded with its line and parsing carries on, so one
 * mistake doesn't hide the rest. Whether names and references actually exist is the
 * {@link TaleValidator}'s job; this only checks that each line is shaped right.
 */
public final class TaleParser {
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");
	private static final Pattern SPEAKER_LINE = Pattern.compile("^(\\{[A-Za-z0-9_.]+\\}|[A-Za-z][A-Za-z0-9_' -]*):\\s*(.*)$");
	private static final Pattern INTERJECTION = Pattern.compile("^\\(\\s*([A-Za-z]+)\\s*(?::\\s*([A-Za-z]+)\\s*)?\\)\\s*(.*)$");
	private static final Pattern TONE_REPLY = Pattern.compile("^([A-Za-z]+):\\s*(.*)$");
	private static final Pattern COMPARISON = Pattern.compile("^([A-Za-z0-9_.:]+)\\s*(>=|<=|==|!=|>|<)\\s*(\\S+)$");
	private static final Pattern CALL = Pattern.compile("^([A-Za-z0-9_.:]+)\\s*\\((.*)\\)$");
	private static final Pattern BARE = Pattern.compile("^[A-Za-z0-9_.:]+$");
	private static final Pattern CHANCE = Pattern.compile("^chance\\s+(\\d+)\\s*%$");
	private static final Pattern AFTER = Pattern.compile("^after\\s+(\\d+)\\s+times?\\s*->\\s*(\\S+)$");
	private static final Pattern BRANCH = Pattern.compile("^(success|failure)\\s*->\\s*(\\S+)$");
	private static final Pattern WEIGHT_BASE = Pattern.compile("^(\\d+)$");
	private static final Pattern WEIGHT_BONUS = Pattern.compile("^\\+(\\d+)\\s+if\\s+(.+)$");
	/** A trailing {@code if ...} on a jump or effect. */
	private static final Pattern IF_SUFFIX = Pattern.compile("^(.*?)\\s+if\\s+(.+)$");

	private final Identifier fileId;
	private final String source;
	private final List<TaleProblem> problems;

	private final List<Node> nodes = new ArrayList<>();
	private final List<TaleAst.Thread> threads = new ArrayList<>();

	// Node being built
	private String nodeName;
	private int nodeLine;
	private List<String> nodeStart;
	private int nodeWeight;
	private List<WeightBonus> nodeBonuses;
	private List<ConditionCall> nodeWhen;
	private List<Statement> nodeBody;
	// Reply being built (inside a node)
	private ReplyBuilder reply;
	// Thread being built
	private Identifier threadId;
	private int threadLine;
	private List<ConditionCall> threadRequires;
	private List<Stage> threadStages;
	private StageBuilder stage;

	private TaleParser(Identifier fileId, String source, List<TaleProblem> problems) {
		this.fileId = fileId;
		this.source = source;
		this.problems = problems;
	}

	/** Parses one file's text. Problems are appended to {@code problems}. */
	public static TaleFile parse(Identifier fileId, String source, String text, List<TaleProblem> problems) {
		TaleParser parser = new TaleParser(fileId, source, problems);
		String[] lines = text.split("\\r?\\n", -1);
		for (int i = 0; i < lines.length; i++) {
			parser.line(i + 1, lines[i]);
		}
		parser.finishNode();
		parser.finishThread();
		return new TaleFile(fileId, source, List.copyOf(parser.nodes), List.copyOf(parser.threads));
	}

	private void line(int lineNumber, String raw) {
		String withoutComment = stripComment(raw);
		String content = withoutComment.trim();
		if (content.isEmpty()) {
			return;
		}
		boolean indented = Character.isWhitespace(withoutComment.charAt(0));

		if (content.startsWith("===")) {
			header(lineNumber, content.substring(3).trim());
		} else if (nodeName != null) {
			nodeLine(lineNumber, content, indented);
		} else if (threadId != null) {
			threadLine(lineNumber, content, indented);
		} else {
			error(lineNumber, "This line is outside any node. Start one with \"=== name\".");
		}
	}

	// ---- Headers ----

	private void header(int lineNumber, String rest) {
		finishNode();
		finishThread();
		if (rest.startsWith("thread ") || rest.equals("thread")) {
			String idText = rest.substring("thread".length()).trim();
			Identifier id = Identifier.tryParse(idText.contains(":") ? idText : fileId.getNamespace() + ":" + idText);
			if (idText.isEmpty() || id == null) {
				error(lineNumber, "A thread needs a valid ID, like \"=== thread cim:crystal/hints_of_the_north\".");
				id = Identifier.of(fileId.getNamespace(), "invalid_thread_" + lineNumber);
			}
			threadId = id;
			threadLine = lineNumber;
			threadRequires = new ArrayList<>();
			threadStages = new ArrayList<>();
			return;
		}
		if (!NAME.matcher(rest).matches()) {
			error(lineNumber, "Node names use lowercase letters, numbers, and underscores - got \"" + rest + "\".");
		}
		nodeName = rest;
		nodeLine = lineNumber;
		nodeStart = new ArrayList<>();
		nodeWeight = 1;
		nodeBonuses = new ArrayList<>();
		nodeWhen = new ArrayList<>();
		nodeBody = new ArrayList<>();
	}

	// ---- Conversation nodes ----

	private void nodeLine(int lineNumber, String content, boolean indented) {
		if (content.startsWith("when:") || content.startsWith("start:") || content.startsWith("weight:")) {
			int colon = content.indexOf(':');
			if (!nodeBody.isEmpty()) {
				error(lineNumber, "\"" + content.substring(0, colon + 1) + "\" has to come right after the node's \"===\" line.");
			}
			String rest = content.substring(colon + 1).trim();
			switch (content.substring(0, colon)) {
				case "when" -> nodeWhen.addAll(conditions(lineNumber, rest));
				case "start" -> startLine(lineNumber, rest);
				default -> weightLine(lineNumber, rest);
			}
			return;
		}
		if (content.startsWith("*")) {
			finishReply();
			reply = replyHeader(lineNumber, content.substring(1).trim());
			return;
		}
		if (indented && reply != null) {
			replyChild(lineNumber, content);
			return;
		}
		if (content.startsWith("or:")) {
			alternative(lineNumber, content.substring("or:".length()).trim());
			return;
		}
		finishReply();
		Statement statement = statement(lineNumber, content);
		if (statement != null) {
			nodeBody.add(statement);
		}
	}

	private void startLine(int lineNumber, String rest) {
		for (String part : splitTopLevel(rest)) {
			String context = part.trim();
			if (!NAME.matcher(context).matches()) {
				error(lineNumber, "\"start:\" takes lowercase names like \"chatter\" - got \"" + context + "\".");
			} else {
				nodeStart.add(context);
			}
		}
		if (nodeStart.isEmpty()) {
			error(lineNumber, "\"start:\" needs at least one name, like \"start: chatter\".");
		}
	}

	private void weightLine(int lineNumber, String rest) {
		Matcher base = WEIGHT_BASE.matcher(rest);
		Matcher bonus = WEIGHT_BONUS.matcher(rest);
		if (base.matches()) {
			nodeWeight = Integer.parseInt(base.group(1));
		} else if (bonus.matches()) {
			nodeBonuses.add(new WeightBonus(lineNumber, Integer.parseInt(bonus.group(1)), conditions(lineNumber, bonus.group(2))));
		} else {
			error(lineNumber, "Write it as \"weight: 2\" or \"weight: +3 if <conditions>\".");
		}
	}

	/** {@code or: text} under a spoken line - another way that line might come out. */
	private void alternative(int lineNumber, String text) {
		if (!nodeBody.isEmpty() && nodeBody.get(nodeBody.size() - 1) instanceof Spoken spoken) {
			List<String> alternatives = new ArrayList<>(spoken.alternatives());
			alternatives.add(text);
			nodeBody.set(nodeBody.size() - 1, new Spoken(spoken.line(), spoken.speaker(), spoken.text(), List.copyOf(alternatives)));
		} else {
			error(lineNumber, "\"or:\" goes right under a spoken line, giving another way it might come out.");
		}
	}

	private Statement statement(int lineNumber, String content) {
		if (content.startsWith("~") || content.startsWith("->")) {
			boolean isEffect = content.startsWith("~");
			String body = content.substring(isEffect ? 1 : 2).trim();
			List<ConditionCall> conditions = List.of();
			Matcher suffix = IF_SUFFIX.matcher(body);
			if (suffix.matches()) {
				body = suffix.group(1).trim();
				conditions = conditions(lineNumber, suffix.group(2));
			}
			return isEffect ? new EffectStatement(lineNumber, effect(lineNumber, body), conditions) : jump(lineNumber, body, conditions);
		}
		Matcher interjection = INTERJECTION.matcher(content);
		if (interjection.matches()) {
			String tier = interjection.group(2) == null ? "faint" : interjection.group(2);
			return new Interjection(lineNumber, interjection.group(1), tier, interjection.group(3));
		}
		Matcher spoken = SPEAKER_LINE.matcher(content);
		if (spoken.matches()) {
			return new Spoken(lineNumber, spoken.group(1).trim(), spoken.group(2), List.of());
		}
		error(lineNumber, "I don't recognize this line. Spoken lines look like \"{pony}: text\"; "
				+ "voices like \"(Kindness) text\"; replies start with \"*\"; effects with \"~\"; jumps with \"->\".");
		return null;
	}

	private Jump jump(int lineNumber, String target, List<ConditionCall> conditions) {
		if (target.isEmpty()) {
			error(lineNumber, "\"->\" needs somewhere to go - a node name, or END.");
		}
		return new Jump(lineNumber, target, conditions);
	}

	private ReplyBuilder replyHeader(int lineNumber, String content) {
		ReplyBuilder builder = new ReplyBuilder(lineNumber);
		if (content.endsWith("!once")) {
			builder.once = true;
			content = content.substring(0, content.length() - "!once".length()).trim();
		}
		if (content.startsWith("[")) {
			int close = content.indexOf(']');
			if (close < 0) {
				error(lineNumber, "This voice reply is missing its closing \"]\".");
				builder.text = content;
				return builder;
			}
			String inside = content.substring(1, close);
			builder.text = content.substring(close + 1).trim();
			String[] parts = inside.split("\\|", -1);
			if (parts.length > 2) {
				error(lineNumber, "A voice reply takes \"[Voice | Feeling]\" - only one \"|\".");
			}
			String[] voiceAndTier = parts[0].split(":", -1);
			builder.voice = Optional.of(voiceAndTier[0].trim());
			if (voiceAndTier.length > 1) {
				builder.voiceTier = Optional.of(voiceAndTier[1].trim());
			}
			if (parts.length > 1 && !parts[1].isBlank()) {
				builder.feeling = Optional.of(parts[1].trim());
			}
			if (builder.voice.get().isEmpty()) {
				error(lineNumber, "This voice reply doesn't name a voice.");
			}
			return builder;
		}
		Matcher tone = TONE_REPLY.matcher(content);
		if (tone.matches()) {
			builder.tone = Optional.of(tone.group(1));
			builder.text = tone.group(2);
		} else {
			error(lineNumber, "A reply is either a tone (\"* Friendly: text\") or a voice (\"* [Kindness | Trust] text\").");
			builder.text = content;
		}
		return builder;
	}

	private void replyChild(int lineNumber, String content) {
		Matcher branch = BRANCH.matcher(content);
		if (branch.matches()) {
			if (reply.tone.isPresent()) {
				error(lineNumber, "Only voice replies roll, so only they take success/failure. Use \"-> target\" here.");
			}
			if (branch.group(1).equals("success")) {
				reply.successTarget = Optional.of(branch.group(2));
			} else {
				reply.failureTarget = Optional.of(branch.group(2));
			}
			return;
		}
		if (content.startsWith("->") && reply.voice.isPresent()) {
			error(lineNumber, "A voice reply rolls, so it needs \"success -> target\" and \"failure -> target\" instead of a plain jump.");
			return;
		}
		if (content.startsWith("->") || content.startsWith("~")) {
			Statement statement = statement(lineNumber, content);
			if (statement != null) {
				reply.body.add(statement);
			}
			return;
		}
		error(lineNumber, "Under a reply, use \"-> target\", \"success -> target\", \"failure -> target\", or an effect (\"~\").");
	}

	private void finishReply() {
		if (reply != null) {
			nodeBody.add(reply.build());
			reply = null;
		}
	}

	private void finishNode() {
		if (nodeName == null) {
			return;
		}
		finishReply();
		Identifier id = Identifier.tryParse(fileId.getNamespace() + ":" + fileId.getPath() + "/" + nodeName);
		if (id == null) {
			id = Identifier.of(fileId.getNamespace(), fileId.getPath() + "/invalid_node_" + nodeLine);
		}
		nodes.add(new Node(nodeName, id, nodeLine, List.copyOf(nodeStart), nodeWeight, List.copyOf(nodeBonuses),
				List.copyOf(nodeWhen), List.copyOf(nodeBody)));
		nodeName = null;
	}

	// ---- Threads ----

	private void threadLine(int lineNumber, String content, boolean indented) {
		if (content.startsWith("requires:")) {
			if (!threadStages.isEmpty()) {
				error(lineNumber, "\"requires:\" has to come before the thread's first stage.");
			}
			threadRequires.addAll(conditions(lineNumber, content.substring("requires:".length())));
			return;
		}
		if (!indented && content.startsWith("stage")) {
			finishStage();
			String name = content.substring("stage".length()).trim();
			if (!NAME.matcher(name).matches()) {
				error(lineNumber, "Stage names use lowercase letters, numbers, and underscores - got \"" + name + "\".");
			}
			stage = new StageBuilder(name, lineNumber);
			return;
		}
		if (stage == null) {
			error(lineNumber, "Thread lines go inside a stage. Start one with \"stage name\".");
			return;
		}
		if (content.startsWith("on ")) {
			stage.triggers.add(trigger(lineNumber, content.substring(3).trim()));
		} else if (content.startsWith("after")) {
			Matcher after = AFTER.matcher(content);
			if (after.matches()) {
				if (stage.after.isPresent()) {
					error(lineNumber, "A stage can only have one \"after N times\".");
				}
				stage.after = Optional.of(new After(lineNumber, Integer.parseInt(after.group(1)), after.group(2)));
			} else {
				error(lineNumber, "Write it as \"after N times -> stage\".");
			}
		} else if (content.startsWith("~")) {
			stage.effects.add(effect(lineNumber, content.substring(1).trim()));
		} else {
			error(lineNumber, "Inside a stage, use \"on <event>\", \"after N times -> stage\", or an effect (\"~\").");
		}
	}

	private Trigger trigger(int lineNumber, String content) {
		Optional<String> target = Optional.empty();
		int arrow = content.indexOf("->");
		if (arrow >= 0) {
			target = Optional.of(content.substring(arrow + 2).trim());
			content = content.substring(0, arrow).trim();
		}
		List<String> parts = splitTopLevel(content);
		EventCall event = parts.isEmpty() ? null : event(lineNumber, parts.get(0));
		int chance = 100;
		for (String extra : parts.subList(Math.min(1, parts.size()), parts.size())) {
			Matcher chanceMatcher = CHANCE.matcher(extra.trim());
			if (chanceMatcher.matches()) {
				chance = Integer.parseInt(chanceMatcher.group(1));
			} else {
				error(lineNumber, "After the event, only \"chance N%\" is allowed - got \"" + extra.trim() + "\".");
			}
		}
		if (event == null) {
			error(lineNumber, "\"on\" needs an event, like \"on sleep\".");
			event = new EventCall(lineNumber, Identifier.of("cim", "missing"), List.of(), "");
		}
		return new Trigger(lineNumber, event, chance, target);
	}

	private void finishStage() {
		if (stage != null) {
			threadStages.add(stage.build());
			stage = null;
		}
	}

	private void finishThread() {
		if (threadId == null) {
			return;
		}
		finishStage();
		if (threadStages.isEmpty()) {
			error(threadLine, "This thread has no stages.");
		}
		threads.add(new TaleAst.Thread(threadId, threadLine, List.copyOf(threadRequires), List.copyOf(threadStages)));
		threadId = null;
	}

	// ---- Calls ----

	private List<ConditionCall> conditions(int lineNumber, String text) {
		List<ConditionCall> result = new ArrayList<>();
		for (String part : splitTopLevel(text)) {
			ConditionCall condition = condition(lineNumber, part.trim());
			if (condition != null) {
				result.add(condition);
			}
		}
		if (result.isEmpty()) {
			error(lineNumber, "This line expects at least one condition.");
		}
		return result;
	}

	private ConditionCall condition(int lineNumber, String text) {
		boolean negated = false;
		String body = text;
		if (body.startsWith("not ")) {
			negated = true;
			body = body.substring(4).trim();
		}
		Matcher comparison = COMPARISON.matcher(body);
		if (comparison.matches()) {
			Identifier type = typeId(lineNumber, comparison.group(1));
			return type == null ? null
					: new ConditionCall(lineNumber, type, List.of(comparison.group(3)), Optional.of(comparison.group(2)), negated, text);
		}
		Matcher call = CALL.matcher(body);
		if (call.matches()) {
			Identifier type = typeId(lineNumber, call.group(1));
			return type == null ? null : new ConditionCall(lineNumber, type, args(call.group(2)), Optional.empty(), negated, text);
		}
		if (BARE.matcher(body).matches()) {
			Identifier type = typeId(lineNumber, body);
			return type == null ? null : new ConditionCall(lineNumber, type, List.of(), Optional.empty(), negated, text);
		}
		error(lineNumber, "I can't read the condition \"" + text + "\". Conditions look like \"night\", \"mood(grief)\", or \"trust >= medium\".");
		return null;
	}

	private EffectCall effect(int lineNumber, String text) {
		int space = text.indexOf(' ');
		String name = space < 0 ? text : text.substring(0, space);
		String rest = space < 0 ? "" : text.substring(space + 1).trim();
		Identifier type = typeId(lineNumber, name);
		if (type == null) {
			type = Identifier.of("cim", "invalid");
		}
		return new EffectCall(lineNumber, type, rest.isEmpty() ? List.of() : args(rest), text);
	}

	private EventCall event(int lineNumber, String text) {
		text = text.trim();
		Matcher call = CALL.matcher(text);
		if (call.matches()) {
			Identifier type = typeId(lineNumber, call.group(1));
			return type == null ? null : new EventCall(lineNumber, type, args(call.group(2)), text);
		}
		if (BARE.matcher(text).matches()) {
			Identifier type = typeId(lineNumber, text);
			return type == null ? null : new EventCall(lineNumber, type, List.of(), text);
		}
		error(lineNumber, "I can't read the event \"" + text + "\". Events look like \"sleep\" or \"enter_biome(snowy)\".");
		return null;
	}

	/** {@code mood} becomes {@code cim:mood}; {@code othermod:thing} stays as written. */
	private Identifier typeId(int lineNumber, String name) {
		Identifier id = Identifier.tryParse(name.contains(":") ? name : "cim:" + name);
		if (id == null) {
			error(lineNumber, "\"" + name + "\" isn't a valid name - use lowercase letters, numbers, \"_\", \".\", and one \":\".");
		}
		return id;
	}

	private static List<String> args(String text) {
		List<String> result = new ArrayList<>();
		for (String part : splitTopLevel(text)) {
			result.add(part.trim());
		}
		return result;
	}

	/** Splits on commas that aren't inside parentheses. */
	private static List<String> splitTopLevel(String text) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth = Math.max(0, depth - 1);
			} else if (c == ',' && depth == 0) {
				parts.add(text.substring(start, i));
				start = i + 1;
			}
		}
		String last = text.substring(start);
		if (!last.isBlank() || !parts.isEmpty()) {
			parts.add(last);
		}
		parts.removeIf(String::isBlank);
		return parts;
	}

	private static String stripComment(String line) {
		int comment = line.indexOf("//");
		return comment < 0 ? line : line.substring(0, comment);
	}

	private void error(int lineNumber, String message) {
		problems.add(new TaleProblem(TaleProblem.Severity.ERROR, source, lineNumber, message));
	}

	private static final class ReplyBuilder {
		final int line;
		Optional<String> tone = Optional.empty();
		Optional<String> voice = Optional.empty();
		Optional<String> voiceTier = Optional.empty();
		Optional<String> feeling = Optional.empty();
		String text = "";
		boolean once;
		final List<Statement> body = new ArrayList<>();
		Optional<String> successTarget = Optional.empty();
		Optional<String> failureTarget = Optional.empty();

		ReplyBuilder(int line) {
			this.line = line;
		}

		Reply build() {
			return new Reply(line, tone, voice, voiceTier, feeling, text, once, List.copyOf(body), successTarget, failureTarget);
		}
	}

	private static final class StageBuilder {
		final String name;
		final int line;
		final List<Trigger> triggers = new ArrayList<>();
		final List<EffectCall> effects = new ArrayList<>();
		Optional<After> after = Optional.empty();

		StageBuilder(String name, int line) {
			this.name = name;
			this.line = line;
		}

		Stage build() {
			return new Stage(name, line, List.copyOf(triggers), List.copyOf(effects), after);
		}
	}
}
