package craftingismagic.cim.narrative;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.util.Identifier;

import craftingismagic.cim.narrative.TaleAst.ConditionCall;
import craftingismagic.cim.narrative.TaleAst.EffectCall;
import craftingismagic.cim.narrative.TaleAst.EffectStatement;
import craftingismagic.cim.narrative.TaleAst.Interjection;
import craftingismagic.cim.narrative.TaleAst.Jump;
import craftingismagic.cim.narrative.TaleAst.Node;
import craftingismagic.cim.narrative.TaleAst.Reply;
import craftingismagic.cim.narrative.TaleAst.Stage;
import craftingismagic.cim.narrative.TaleAst.Statement;
import craftingismagic.cim.narrative.TaleAst.TaleFile;
import craftingismagic.cim.narrative.TaleAst.Trigger;

/**
 * Checks parsed {@code .tale} files against each other and against the {@link TaleVocabulary}: jumps
 * that land nowhere, voice replies missing a branch, unknown voices/tiers/feelings/tones, conditions
 * and effects nobody registered, and pieces nothing reaches (docs/narrative-reference.md: What the
 * validator will catch). Works on every loaded file at once, because a jump may cross files.
 */
public final class TaleValidator {
	public static final String END = "END";

	private final List<TaleFile> files;
	private final List<TaleProblem> problems;
	private final Map<Identifier, Node> nodesById = new HashMap<>();
	private final Map<Identifier, TaleAst.Thread> threadsById = new HashMap<>();
	private final Set<Identifier> jumpedTo = new HashSet<>();
	/** "threadId stage" pairs moved to by {@code ~ thread advance} anywhere. */
	private final Set<String> advancedTo = new HashSet<>();

	private TaleValidator(List<TaleFile> files, List<TaleProblem> problems) {
		this.files = files;
		this.problems = problems;
	}

	/** Validates all files together. Problems are appended to {@code problems}. */
	public static void validate(List<TaleFile> files, List<TaleProblem> problems) {
		new TaleValidator(files, problems).run();
	}

	/** Resolves a jump target written in {@code file}: a local node name, or a full node ID. */
	public static Identifier resolveTarget(TaleFile file, String target) {
		if (target.contains(":")) {
			return Identifier.tryParse(target);
		}
		return Identifier.tryParse(file.id().getNamespace() + ":" + file.id().getPath() + "/" + target);
	}

	private void run() {
		for (TaleFile file : files) {
			for (Node node : file.nodes()) {
				Node previous = nodesById.putIfAbsent(node.id(), node);
				if (previous != null) {
					error(file, node.line(), "There's already a node called \"" + node.name() + "\" in this file (line " + previous.line() + ").");
				}
			}
			for (TaleAst.Thread thread : file.threads()) {
				if (threadsById.putIfAbsent(thread.id(), thread) != null) {
					error(file, thread.line(), "Another file already defines the thread " + thread.id() + ".");
				}
			}
		}
		for (TaleFile file : files) {
			for (Node node : file.nodes()) {
				checkNode(file, node);
			}
			for (TaleAst.Thread thread : file.threads()) {
				checkThread(file, thread);
			}
		}
		for (TaleFile file : files) {
			warnUnreached(file);
		}
	}

	// ---- Nodes ----

	private void checkNode(TaleFile file, Node node) {
		node.when().forEach(condition -> checkCondition(file, condition));
		for (Statement statement : node.body()) {
			checkStatement(file, statement);
		}
		for (int i = 0; i < node.body().size() - 1; i++) {
			if (node.body().get(i) instanceof Jump jump) {
				warning(file, node.body().get(i + 1).line(), "Nothing after the jump on line " + jump.line() + " will ever play.");
				break;
			}
		}
	}

	private void checkStatement(TaleFile file, Statement statement) {
		switch (statement) {
			case Jump jump -> checkJump(file, jump.line(), jump.target());
			case EffectStatement effect -> checkEffect(file, effect.effect());
			case Interjection interjection -> {
				checkVoice(file, interjection.line(), interjection.voice());
				checkTier(file, interjection.line(), interjection.tier());
			}
			case Reply reply -> checkReply(file, reply);
			default -> {
			}
		}
	}

	private void checkReply(TaleFile file, Reply reply) {
		reply.tone().ifPresent(tone -> {
			if (!TaleVocabulary.isTone(tone)) {
				error(file, reply.line(), "Unknown tone \"" + tone + "\". Tones: " + String.join(", ", TaleVocabulary.tones()) + ".");
			}
		});
		for (Statement statement : reply.body()) {
			checkStatement(file, statement);
		}
		if (reply.isVoiceReply()) {
			String voice = reply.voice().get();
			checkVoice(file, reply.line(), voice);
			reply.voiceTier().ifPresent(tier -> checkTier(file, reply.line(), tier));
			if (reply.feeling().isPresent()) {
				String feeling = reply.feeling().get();
				if (!TaleVocabulary.voiceTakesFeeling(voice)) {
					error(file, reply.line(), "A " + voice + " reply has no feeling - it doesn't rely on the pony's warmth toward you.");
				} else if (!TaleVocabulary.isFeeling(feeling)) {
					error(file, reply.line(), "Unknown feeling \"" + feeling + "\". Feelings: " + String.join(", ", TaleVocabulary.feelings()) + ".");
				}
			}
			if (reply.successTarget().isEmpty()) {
				error(file, reply.line(), "This voice reply needs a \"success -> target\" line under it.");
			}
			if (reply.failureTarget().isEmpty()) {
				error(file, reply.line(), "This voice reply needs a \"failure -> target\" line under it. Failure moves the story forward.");
			}
			reply.successTarget().ifPresent(target -> checkJump(file, reply.line(), target));
			reply.failureTarget().ifPresent(target -> checkJump(file, reply.line(), target));
		} else if (reply.body().stream().noneMatch(statement -> statement instanceof Jump)) {
			warning(file, reply.line(), "This reply has no \"-> target\", so choosing it ends the conversation. Add \"-> END\" if that's intended.");
		}
	}

	private void checkJump(TaleFile file, int line, String target) {
		if (target.equals(END)) {
			return;
		}
		Identifier id = resolveTarget(file, target);
		if (id == null || !nodesById.containsKey(id)) {
			error(file, line, "There's no node \"" + target + "\" to jump to." + (target.equalsIgnoreCase(END) ? " (END is written in capitals.)" : ""));
			return;
		}
		jumpedTo.add(id);
	}

	private void checkVoice(TaleFile file, int line, String voice) {
		if (!TaleVocabulary.isVoice(voice)) {
			error(file, line, "Unknown voice \"" + voice + "\". Voices: " + String.join(", ", TaleVocabulary.voices()) + ".");
		}
	}

	private void checkTier(TaleFile file, int line, String tier) {
		if (!TaleVocabulary.TIERS.contains(TaleVocabulary.normalize(tier))) {
			error(file, line, "Unknown tier \"" + tier + "\". Tiers: " + String.join(", ", TaleVocabulary.TIERS) + ".");
		}
	}

	// ---- Calls ----

	private void checkCondition(TaleFile file, ConditionCall condition) {
		TaleVocabulary.ArgumentCheck check = TaleVocabulary.condition(condition.type());
		if (check == null) {
			error(file, condition.line(), "No mod or datapack has registered a condition called " + condition.type() + ".");
			return;
		}
		report(file, condition.line(), "\"" + condition.source() + "\"", check.check(condition.args(), condition.comparison()));
	}

	private void checkEffect(TaleFile file, EffectCall effect) {
		TaleVocabulary.ArgumentCheck check = TaleVocabulary.effect(effect.type());
		if (check == null) {
			error(file, effect.line(), "No mod or datapack has registered an effect called " + effect.type() + ".");
			return;
		}
		report(file, effect.line(), "\"~ " + effect.source() + "\"", check.check(effect.args(), Optional.empty()));
		if (effect.type().equals(Identifier.of("cim", "thread")) && !effect.args().isEmpty()) {
			String[] words = effect.args().get(0).trim().split("\\s+");
			if (words.length == 3 && words[0].equals("advance")) {
				advancedTo.add(words[1] + " " + words[2]);
			}
		}
	}

	private void report(TaleFile file, int line, String what, List<String> messages) {
		for (String message : messages) {
			error(file, line, what + ": " + message);
		}
	}

	// ---- Threads ----

	private void checkThread(TaleFile file, TaleAst.Thread thread) {
		thread.requires().forEach(condition -> checkCondition(file, condition));
		Set<String> stageNames = new HashSet<>();
		for (Stage stage : thread.stages()) {
			if (!stageNames.add(stage.name())) {
				error(file, stage.line(), "This thread already has a stage called \"" + stage.name() + "\".");
			}
		}
		for (Stage stage : thread.stages()) {
			if (stage.triggers().isEmpty()) {
				warning(file, stage.line(), "Stage \"" + stage.name() + "\" has no \"on\" lines, so nothing in it will ever happen on its own.");
			}
			for (Trigger trigger : stage.triggers()) {
				checkEvent(file, trigger);
				if (trigger.chancePercent() < 1 || trigger.chancePercent() > 100) {
					error(file, trigger.line(), "Chance has to be between 1% and 100%.");
				}
				trigger.target().ifPresent(target -> checkStage(file, thread, stageNames, trigger.line(), target));
			}
			stage.effects().forEach(effect -> checkEffect(file, effect));
			stage.after().ifPresent(after -> {
				if (after.times() < 1) {
					error(file, after.line(), "\"after\" needs at least 1 time.");
				}
				checkStage(file, thread, stageNames, after.line(), after.target());
			});
		}
	}

	private void checkEvent(TaleFile file, Trigger trigger) {
		TaleVocabulary.ArgumentCheck check = TaleVocabulary.event(trigger.event().type());
		if (check == null) {
			error(file, trigger.line(), "No mod or datapack has registered an event called " + trigger.event().type() + ".");
			return;
		}
		report(file, trigger.line(), "\"on " + trigger.event().source() + "\"", check.check(trigger.event().args(), Optional.empty()));
	}

	private void checkStage(TaleFile file, TaleAst.Thread thread, Set<String> stageNames, int line, String target) {
		if (!stageNames.contains(target)) {
			error(file, line, "Thread " + thread.id() + " has no stage \"" + target + "\".");
		}
	}

	// ---- Reachability ----

	private void warnUnreached(TaleFile file) {
		for (Node node : file.nodes()) {
			// A node with "when:" is presumably an entry point the game picks by its conditions.
			if (node.when().isEmpty() && !jumpedTo.contains(node.id()) && file.nodes().get(0) != node) {
				warning(file, node.line(), "Nothing jumps to \"" + node.name() + "\" and it has no \"when:\", so it may never play.");
			}
		}
		for (TaleAst.Thread thread : file.threads()) {
			Set<String> reached = new HashSet<>();
			for (Stage stage : thread.stages()) {
				stage.triggers().forEach(trigger -> trigger.target().ifPresent(reached::add));
				stage.after().ifPresent(after -> reached.add(after.target()));
			}
			for (int i = 1; i < thread.stages().size(); i++) {
				Stage stage = thread.stages().get(i);
				if (!reached.contains(stage.name()) && !advancedTo.contains(thread.id() + " " + stage.name())) {
					warning(file, stage.line(), "Nothing moves thread " + thread.id() + " to stage \"" + stage.name() + "\".");
				}
			}
		}
	}

	private void error(TaleFile file, int line, String message) {
		problems.add(new TaleProblem(TaleProblem.Severity.ERROR, file.source(), line, message));
	}

	private void warning(TaleFile file, int line, String message) {
		problems.add(new TaleProblem(TaleProblem.Severity.WARNING, file.source(), line, message));
	}
}
