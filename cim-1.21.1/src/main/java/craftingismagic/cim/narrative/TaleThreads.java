package craftingismagic.cim.narrative;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.narrative.TaleAst.EffectCall;
import craftingismagic.cim.narrative.TaleAst.Stage;
import craftingismagic.cim.narrative.TaleAst.Trigger;

/**
 * How threads move (design/narrative-reference.md: Threads). Pure rules - given a thread, where a
 * player is in it, and something that just happened, work out what changes. Saving progress and
 * noticing events is the mod's job.
 * <ul>
 * <li>An {@code on} line with {@code -> stage} is a transition: it moves on, nothing else.</li>
 * <li>Any other matching {@code on} line makes the stage <em>act</em>: its effects happen and its
 * count goes up; {@code after N times -> stage} moves on once the count reaches N.</li>
 * <li>Each {@code on} line is tried in order; the first whose event matches and whose chance
 * comes up wins.</li>
 * <li>Moving to {@code END} finishes the thread for good.</li>
 * </ul>
 */
public final class TaleThreads {
	public static final String END = "END";
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/narrative");

	private TaleThreads() {
	}

	/** Where one player is in one thread. {@code fired} counts how often the current stage acted. */
	public record Progress(String stage, int fired, boolean finished) {
		public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("stage").forGetter(Progress::stage),
				Codec.INT.optionalFieldOf("fired", 0).forGetter(Progress::fired),
				Codec.BOOL.optionalFieldOf("finished", false).forGetter(Progress::finished))
				.apply(instance, Progress::new));
	}

	/**
	 * Something that happened, as threads see it: an event type, and a test for whether an {@code on}
	 * line's arguments describe it (e.g. {@code enter_biome(#c:is_snowy)} against the biome entered).
	 */
	public record Event(Identifier type, Predicate<List<String>> matches) {
		public static Event of(Identifier type) {
			return new Event(type, args -> true);
		}
	}

	/** A thread just starting: at its first stage. */
	public static Progress begin(TaleAst.Thread thread) {
		return new Progress(thread.stages().get(0).name(), 0, false);
	}

	/** Moves to {@code target} (a stage name or {@code END}); empty if the thread has no such stage. */
	public static Optional<Progress> moveTo(TaleAst.Thread thread, String target) {
		if (target.equals(END)) {
			return Optional.of(new Progress(END, 0, true));
		}
		return thread.stage(target).map(stage -> new Progress(stage.name(), 0, false));
	}

	/** Whether the thread may start for this player right now. */
	public static boolean requirementsHold(TaleAst.Thread thread, TaleContext context) {
		return TaleRuntime.allHold(thread.requires(), context);
	}

	/**
	 * Delivers {@code event} to one thread. Returns the new progress, or {@code progress} itself when
	 * nothing happened.
	 */
	public static Progress fire(TaleAst.Thread thread, Progress progress, Event event, TaleContext context) {
		if (progress.finished()) {
			return progress;
		}
		Optional<Stage> current = thread.stage(progress.stage());
		if (current.isEmpty()) {
			// The file changed under a saved player; leave them where they are rather than guess.
			LOGGER.warn("Thread {} has no stage \"{}\" any more - holding there", thread.id(), progress.stage());
			return progress;
		}
		Stage stage = current.get();
		for (Trigger trigger : stage.triggers()) {
			if (!trigger.event().type().equals(event.type()) || !matches(trigger, event, context)) {
				continue;
			}
			if (trigger.chancePercent() < 100 && context.random().nextInt(100) >= trigger.chancePercent()) {
				continue;
			}
			if (trigger.target().isPresent()) {
				return move(thread, progress, trigger.target().get());
			}
			for (EffectCall effect : stage.effects()) {
				TaleRuntime.apply(effect, context);
			}
			Progress acted = new Progress(progress.stage(), progress.fired() + 1, false);
			if (stage.after().isPresent() && acted.fired() >= stage.after().get().times()) {
				return move(thread, acted, stage.after().get().target());
			}
			return acted;
		}
		return progress;
	}

	private static boolean matches(Trigger trigger, Event event, TaleContext context) {
		try {
			return event.matches().test(trigger.event().args().stream().map(arg -> TaleRuntime.fill(arg, context, true)).toList());
		} catch (RuntimeException e) {
			LOGGER.error("Thread event \"on {}\" (line {}) failed to match; skipping it", trigger.event().source(), trigger.line(), e);
			return false;
		}
	}

	private static Progress move(TaleAst.Thread thread, Progress from, String target) {
		return moveTo(thread, target).orElseGet(() -> {
			LOGGER.error("Thread {} has no stage \"{}\" to move to - staying at \"{}\"", thread.id(), target, from.stage());
			return from;
		});
	}
}
