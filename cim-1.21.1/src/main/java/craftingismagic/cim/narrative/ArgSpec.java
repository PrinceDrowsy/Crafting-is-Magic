package craftingismagic.cim.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.util.Identifier;

/**
 * What a condition, effect, or event accepts, written down as data instead of code - so the game
 * checks {@code .tale} files with it and the {@code .tale} editor can read the very same rules
 * (exported with {@code /cim debug tale export}). Both sides share one definition, so they can't
 * drift apart.
 * <p>
 * Arguments are separated by commas; each argument is one or more words separated by spaces, each
 * word matching a {@link Token}. A spec can offer several {@link Form}s (e.g. {@code thread start <id>}
 * or {@code thread advance <id> <stage>}); any one fitting is enough.
 */
public final class ArgSpec implements TaleVocabulary.ArgumentCheck {
	/** What one word may be. */
	public enum Kind {
		/** One of {@link Token#values()} (case-insensitive). */
		WORD,
		/** Exactly one of {@link Token#values()} - a keyword like {@code set}. */
		LITERAL,
		/** A namespaced ID; {@code {variables}} allowed. */
		ID,
		/** An ID whose path starts with one of {@link Token#values()} (fact scopes). */
		SCOPED_ID,
		/** An ID or a {@code #tag}. */
		ID_OR_TAG,
		/** {@code +N} or {@code -N}. */
		SIGNED_NUMBER,
		/** Any single lowercase word. */
		NAME
	}

	/**
	 * @param values for WORD / LITERAL / SCOPED_ID - looked up when needed, so registries that fill in
	 *               later (voices from another mod) are included
	 * @param label  what to call it in messages and the editor ("emotion", "fact")
	 */
	public record Token(Kind kind, Supplier<List<String>> values, boolean optional, String label) {
		public List<String> valueList() {
			return values == null ? List.of() : values.get();
		}
	}

	/** One comma-separated argument: a run of words. */
	public record Arg(List<Token> tokens, boolean optional) {
	}

	/** One accepted shape. With {@code repeatLast}, the last argument may repeat (lists of biomes). */
	public record Form(List<Arg> args, boolean repeatLast) {
	}

	private final List<Form> forms;
	private final boolean comparison;
	private final String usage;

	private ArgSpec(List<Form> forms, boolean comparison, String usage) {
		this.forms = forms;
		this.comparison = comparison;
		this.usage = usage;
	}

	public List<Form> forms() {
		return forms;
	}

	/** Written as a comparison - {@code trust >= medium} - with the right side as its one argument. */
	public boolean comparison() {
		return comparison;
	}

	/** How it's written, for messages and the editor: {@code mood(<emotion>[, <tier>])}. */
	public String usage() {
		return usage;
	}

	// ---- Building ----

	public static Token word(String label, Supplier<List<String>> values) {
		return new Token(Kind.WORD, values, false, label);
	}

	public static Token literal(String... words) {
		List<String> values = List.of(words);
		return new Token(Kind.LITERAL, () -> values, false, String.join("|", words));
	}

	public static Token id(String label) {
		return new Token(Kind.ID, null, false, label);
	}

	public static Token scopedId(String label, String... scopes) {
		List<String> values = List.of(scopes);
		return new Token(Kind.SCOPED_ID, () -> values, false, label);
	}

	public static Token idOrTag(String label) {
		return new Token(Kind.ID_OR_TAG, null, false, label);
	}

	public static Token signedNumber(String label) {
		return new Token(Kind.SIGNED_NUMBER, null, false, label);
	}

	public static Token name(String label) {
		return new Token(Kind.NAME, null, false, label);
	}

	public static Token optional(Token token) {
		return new Token(token.kind(), token.values(), true, token.label());
	}

	public static Arg arg(Token... tokens) {
		return new Arg(List.of(tokens), false);
	}

	public static Arg optionalArg(Token... tokens) {
		return new Arg(List.of(tokens), true);
	}

	/** Takes nothing: {@code night}. */
	public static ArgSpec none(String usage) {
		return new ArgSpec(List.of(new Form(List.of(), false)), false, usage);
	}

	public static ArgSpec of(String usage, Arg... args) {
		return new ArgSpec(List.of(new Form(List.of(args), false)), false, usage);
	}

	/** The last argument may repeat: {@code biome(<biome or #tag>, ...)}. */
	public static ArgSpec repeating(String usage, Arg... args) {
		return new ArgSpec(List.of(new Form(List.of(args), true)), false, usage);
	}

	/** Several accepted shapes. */
	public static ArgSpec anyOf(String usage, Form... forms) {
		return new ArgSpec(List.of(forms), false, usage);
	}

	public static Form form(Arg... args) {
		return new Form(List.of(args), false);
	}

	/** {@code trust >= medium}: compared against one of {@code values}. */
	public static ArgSpec compared(String usage, Token right) {
		return new ArgSpec(List.of(new Form(List.of(arg(right)), false)), true, usage);
	}

	// ---- Checking (the game's side; the editor mirrors this exactly) ----

	@Override
	public List<String> check(List<String> args, Optional<String> comparisonOperator) {
		if (comparison && comparisonOperator.isEmpty()) {
			return List.of("write it as a comparison: " + usage);
		}
		if (!comparison && comparisonOperator.isPresent()) {
			return List.of("can't be compared with " + comparisonOperator.get() + " - write it as " + usage);
		}
		List<String> firstProblems = null;
		for (Form form : forms) {
			List<String> problems = checkForm(form, args);
			if (problems.isEmpty()) {
				return List.of();
			}
			if (firstProblems == null) {
				firstProblems = problems;
			}
		}
		if (forms.size() > 1) {
			return List.of("write it as " + usage);
		}
		return firstProblems == null ? List.of() : firstProblems;
	}

	private static List<String> checkForm(Form form, List<String> args) {
		List<Arg> specs = form.args();
		int required = (int) specs.stream().filter(arg -> !arg.optional()).count();
		boolean repeats = form.repeatLast() && !specs.isEmpty();
		if (args.size() < required || (!repeats && args.size() > specs.size())) {
			return List.of(countMessage(required, specs.size(), repeats));
		}
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < args.size(); i++) {
			Arg spec = specs.get(Math.min(i, specs.size() - 1));
			problems.addAll(checkArg(spec, args.get(i)));
		}
		return problems;
	}

	private static String countMessage(int required, int total, boolean repeats) {
		if (total == 0) {
			return "takes no arguments";
		}
		if (repeats) {
			return "takes at least " + required + " argument(s)";
		}
		return required == total ? "takes " + total + " argument(s)" : "takes " + required + " to " + total + " arguments";
	}

	private static List<String> checkArg(Arg spec, String text) {
		String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
		List<Token> tokens = spec.tokens();
		int required = (int) tokens.stream().filter(token -> !token.optional()).count();
		if (words.length < required || words.length > tokens.size()) {
			String shape = String.join(" ", tokens.stream().map(token -> token.optional() ? "[" + token.label() + "]" : "<" + token.label() + ">").toList());
			return List.of("\"" + text.trim() + "\" should be " + shape);
		}
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < words.length; i++) {
			checkToken(tokens.get(i), words[i]).ifPresent(problems::add);
		}
		return problems;
	}

	private static Optional<String> checkToken(Token token, String word) {
		String lower = word.toLowerCase(Locale.ROOT);
		return switch (token.kind()) {
			case WORD, LITERAL -> token.valueList().contains(lower) ? Optional.empty()
					: Optional.of("\"" + word + "\" isn't a " + token.label() + " - one of: " + String.join(", ", token.valueList()));
			case ID -> validId(word) ? Optional.empty() : Optional.of(badId(word));
			case ID_OR_TAG -> validId(word.startsWith("#") ? word.substring(1) : word) ? Optional.empty() : Optional.of(badId(word));
			case SCOPED_ID -> {
				if (!validId(word)) {
					yield Optional.of(badId(word));
				}
				String path = withoutVariables(word).contains(":") ? withoutVariables(word).substring(withoutVariables(word).indexOf(':') + 1)
						: withoutVariables(word);
				yield token.valueList().stream().anyMatch(path::startsWith) ? Optional.empty()
						: Optional.of("a " + token.label() + "'s path starts with " + String.join(", ", token.valueList()) + " - that's where it's kept");
			}
			case SIGNED_NUMBER -> word.matches("[+-]\\d+") ? Optional.empty() : Optional.of("write the change as +N or -N, not \"" + word + "\"");
			case NAME -> lower.matches("[a-z0-9_]+") ? Optional.empty() : Optional.of("\"" + word + "\" should be a single lowercase word");
		};
	}

	/** IDs may contain {@code {variables}}, filled in at run time. */
	static String withoutVariables(String text) {
		return text.trim().replaceAll("\\{[A-Za-z0-9_.]+\\}", "x");
	}

	private static boolean validId(String text) {
		String filled = withoutVariables(text);
		return !filled.isEmpty() && Identifier.tryParse(filled.contains(":") ? filled : "cim:" + filled) != null;
	}

	private static String badId(String text) {
		return "\"" + text.trim() + "\" isn't a valid ID - lowercase letters, numbers, \"_\", \"/\", \".\", and one \":\"";
	}
}
