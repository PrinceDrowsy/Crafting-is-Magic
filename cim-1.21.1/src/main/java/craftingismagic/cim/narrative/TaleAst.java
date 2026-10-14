package craftingismagic.cim.narrative;

import java.util.List;
import java.util.Optional;

import net.minecraft.util.Identifier;

/**
 * What a parsed {@code .tale} file looks like (docs/narrative-reference.md). Every piece remembers
 * the line it came from, so the validator and the runtime can report problems as {@code file:line}.
 * Plain data, no behavior - parsing lives in {@link TaleParser}, checking in {@link TaleValidator}.
 */
public final class TaleAst {
	private TaleAst() {
	}

	/**
	 * @param id      the file's own ID: {@code data/cim/tales/chatter/grief.tale} is {@code cim:chatter/grief}
	 * @param source  where it was loaded from, for messages
	 */
	public record TaleFile(Identifier id, String source, List<Node> nodes, List<Thread> threads) {
	}

	/** One beat of a conversation. {@code id} is the file's id plus the node name. */
	public record Node(String name, Identifier id, int line, List<ConditionCall> when, List<Statement> body) {
	}

	/** Anything that can appear in a node's body. */
	public sealed interface Statement permits Spoken, Interjection, EffectStatement, Jump, Reply {
		int line();
	}

	/** {@code {pony}: text} - {@code speaker} keeps its braces when it's a variable. */
	public record Spoken(int line, String speaker, String text) implements Statement {
	}

	/** {@code (Kindness: strong) text} - an inner voice speaking up on its own. */
	public record Interjection(int line, String voice, String tier, String text) implements Statement {
	}

	/** {@code ~ trust +2}. */
	public record EffectStatement(int line, EffectCall effect) implements Statement {
	}

	/** {@code -> target}, where {@code target} is a node name, a full node ID, or {@code END}. */
	public record Jump(int line, String target) implements Statement {
	}

	/**
	 * A reply line ({@code * ...}). A tone reply ({@code * Friendly: text}) is always safe; a voice reply
	 * ({@code * [Kindness: clear | Trust] text}) is rolled and needs both a success and a failure jump.
	 *
	 * @param tone          the tone, for a tone reply
	 * @param voice         the voice, for a voice reply
	 * @param voiceTier     how strong the voice must be for the option to appear, if given
	 * @param feeling       the feeling that shapes the odds, if given (never for {@code Whisper})
	 * @param once          {@code !once}: a one-chance moment
	 * @param body          effects and the plain jump nested under a tone reply
	 * @param successTarget where a won roll goes
	 * @param failureTarget where a lost roll goes
	 */
	public record Reply(int line, Optional<String> tone, Optional<String> voice, Optional<String> voiceTier,
			Optional<String> feeling, String text, boolean once, List<Statement> body,
			Optional<String> successTarget, Optional<String> failureTarget) implements Statement {
		public boolean isVoiceReply() {
			return voice.isPresent();
		}
	}

	/**
	 * A condition: {@code mood(grief, strong)}, {@code trust >= medium}, {@code night}, {@code not
	 * fact(cim:x)}. {@code type} is namespaced ({@code cim:} is assumed when none is written).
	 *
	 * @param comparison the operator for the {@code a >= b} form ({@code args} then holds the right side)
	 */
	public record ConditionCall(int line, Identifier type, List<String> args, Optional<String> comparison, boolean negated,
			String source) {
	}

	/** An effect: {@code ~ memory cim:shared_grief, grief 3} has type {@code cim:memory} and two args. */
	public record EffectCall(int line, Identifier type, List<String> args, String source) {
	}

	/** {@code === thread <id>} - a story that unfolds over time in stages. */
	public record Thread(Identifier id, int line, List<ConditionCall> requires, List<Stage> stages) {
	}

	public record Stage(String name, int line, List<Trigger> triggers, List<EffectCall> effects, Optional<After> after) {
	}

	/** {@code on <event>[, chance N%] [-> stage]}. */
	public record Trigger(int line, EventCall event, int chancePercent, Optional<String> target) {
	}

	public record EventCall(int line, Identifier type, List<String> args, String source) {
	}

	/** {@code after N times -> stage}. */
	public record After(int line, int times, String target) {
	}
}
