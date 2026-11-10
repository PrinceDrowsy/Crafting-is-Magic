package craftingismagic.cim.verse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.util.Identifier;

import craftingismagic.cim.verse.VerseEquation.Action;
import craftingismagic.cim.verse.VerseEquation.Step;
import craftingismagic.cim.verse.VerseLanguage.Meaning;
import craftingismagic.cim.verse.VerseLanguage.Role;

/**
 * Reads a verse into its equation (design doc: Verse - spells as poetic equations). Words the language
 * knows become the spell; everything else is poetry, and anything in parentheses is always poetry.
 * Alongside the equation it returns what each word meant (for highlighting), scholarly notes on
 * anything unbalanced ("Your light has nothing to shine on"), and the cost and tier.
 * <p>
 * Never throws on bad input: an odd verse reads as an odd (or empty) spell with notes.
 */
public final class VerseReader {
	private static final Identifier MOVE = Identifier.of("cim", "move");
	private static final Identifier LIGHT = Identifier.of("cim", "light");
	private static final Identifier SOUND = Identifier.of("cim", "sound");
	private static final Identifier SHIFT = Identifier.of("cim", "shift");
	private static final Identifier CHANGE = Identifier.of("cim", "change");
	private static final Identifier LANDS = Identifier.of("cim", "lands");
	private static final Identifier GAZE = Identifier.of("cim", "gaze");
	private static final Identifier NEAR = Identifier.of("cim", "near");

	/** A stretch of the verse and what the spell heard there. {@code role} is null for poetry in parentheses. */
	public record Highlight(int start, int end, Role role) {
	}

	/** A scholar's note on part of the verse - what's unbalanced, and how to mend it. */
	public record Note(int start, int end, String message) {
	}

	/**
	 * @param cost Stamina points a cast takes, before practice makes it cheaper
	 * @param tier {@code novice}, {@code adept}, ... - the hardest piece in it
	 */
	public record Reading(String text, VerseEquation equation, List<Highlight> highlights, List<Note> notes, int cost, String tier) {
		public boolean empty() {
			return equation.steps().isEmpty();
		}
	}

	private record Token(String word, int start, int end, boolean punctuation, boolean poetry) {
		boolean endsSentence() {
			return punctuation && ".!?;".contains(word);
		}
	}

	private record Term(Meaning meaning, int start, int end, int firstToken, int lastToken) {
	}

	private VerseReader() {
	}

	public static Reading read(String text) {
		List<Token> tokens = lex(text);
		List<Object> items = terms(tokens); // Terms, and sentence-ending punctuation tokens
		return new Builder(text, tokens).build(items);
	}

	// ---- Words ----

	private static List<Token> lex(String text) {
		List<Token> tokens = new ArrayList<>();
		int depth = 0;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '(') {
				depth++;
				i++;
			} else if (c == ')') {
				depth = Math.max(0, depth - 1);
				i++;
			} else if (Character.isLetter(c)) {
				int start = i;
				while (i < text.length() && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '\'' || text.charAt(i) == '’')) {
					i++;
				}
				tokens.add(new Token(text.substring(start, i).replace('’', '\'').toLowerCase(Locale.ROOT), start, i, false, depth > 0));
			} else if (",.!?;:".indexOf(c) >= 0) {
				tokens.add(new Token(String.valueOf(c), i, i + 1, true, depth > 0));
				i++;
			} else {
				i++;
			}
		}
		return tokens;
	}

	/** Longest keyword phrases first, never across punctuation or into parentheses. */
	private static List<Object> terms(List<Token> tokens) {
		List<Object> items = new ArrayList<>();
		int i = 0;
		while (i < tokens.size()) {
			Token token = tokens.get(i);
			if (token.punctuation()) {
				if (!token.poetry()) items.add(token);
				i++;
				continue;
			}
			if (token.poetry()) {
				i++;
				continue;
			}
			Term match = null;
			for (int length = Math.min(VerseLanguage.longestPhrase(), tokens.size() - i); length >= 1 && match == null; length--) {
				StringBuilder phrase = new StringBuilder();
				boolean clean = true;
				for (int k = i; k < i + length; k++) {
					Token part = tokens.get(k);
					if (part.punctuation() || part.poetry()) {
						clean = false;
						break;
					}
					if (phrase.length() > 0) phrase.append(' ');
					phrase.append(part.word());
				}
				Meaning meaning = clean ? VerseLanguage.meaning(phrase.toString()) : null;
				if (meaning != null) {
					match = new Term(meaning, token.start(), tokens.get(i + length - 1).end(), i, i + length - 1);
				}
			}
			if (match != null) {
				items.add(match);
				i = match.lastToken() + 1;
			} else {
				i++;
			}
		}
		return items;
	}

	// ---- Building the equation ----

	/** One action while it's being read. */
	private static final class Draft {
		final Identifier piece;
		String mode;
		String target = "";
		String destination = "";
		float strength = 1f;
		boolean gentle;
		int repeats = 1;
		String sound = "";
		String pitch = "";
		String ending = "";
		String shape = "";
		boolean twinkle;
		final List<String> parts = new ArrayList<>();
		String tint = "";
		final List<String> properties = new ArrayList<>();
		Identifier quantity;
		/** Where the quantity was named - for the note when nothing says what it changes. */
		int quantityStart;
		int quantityEnd;
		final int start;
		final int end;

		Draft(Identifier piece, String mode, int start, int end) {
			this.piece = piece;
			this.mode = mode;
			this.start = start;
			this.end = end;
		}

		Action build() {
			// A property follows the quantity when one is named; on its own it's simply raised.
			List<String> boosts = quantity == null ? List.copyOf(properties) : List.of();
			List<VerseEquation.Binding> bindings = quantity == null ? List.of()
					: properties.stream().map(property -> new VerseEquation.Binding(property, quantity)).toList();
			return new Action(piece, mode, target, destination, strength, gentle, repeats, sound, pitch, ending, shape, twinkle, List.copyOf(parts), tint,
					boosts, bindings);
		}
	}

	private static final class Builder {
		private final String text;
		private final List<Token> tokens;
		private final List<Highlight> highlights = new ArrayList<>();
		private final List<Note> notes = new ArrayList<>();
		private final List<Step> steps = new ArrayList<>();
		private final List<Draft> step = new ArrayList<>();
		private Identifier trigger;
		private Draft action;
		private String lastTarget = "";
		private String pendingTarget = "";
		/** After "when" or "until", waiting for what to sense. */
		private Term awaiting;
		/** After "like a", waiting for a sound color. */
		private Term like;
		private Term previous;
		/** The effect word that started the current action - to spot "go hence", one Shift said twice. */
		private Term lastEffectTerm;
		/** A sound color named before anything sings, waiting for the next Sound. */
		private String pendingColor = "";
		/** A shape named before the light it shapes ("a crown of lights"). */
		private String pendingShape = "";
		/** "Twinkling" before the lights it describes ("in twinkling lights"). */
		private Term pendingTwinkle;
		/** Parts and a color named before the Change (or Light) they belong to - "your mane is now green". */
		private final List<String> pendingParts = new ArrayList<>();
		private String pendingHue = "";
		/** A quantity or properties named before the effect they belong to. */
		private Term pendingQuantity;
		private final List<String> pendingProperties = new ArrayList<>();
		/** Just read "twice"/"thrice" - if "greatly" follows, it was magnitude, not repetition. */
		private int pendingMagnitude;
		private boolean anyAction;

		Builder(String text, List<Token> tokens) {
			this.text = text;
			this.tokens = tokens;
			for (Token token : tokens) {
				if (token.poetry() && !token.punctuation()) highlights.add(new Highlight(token.start(), token.end(), null));
			}
		}

		Reading build(List<Object> items) {
			for (Object item : items) {
				if (item instanceof Token punctuation) {
					if (like != null) unknownSound();
					if (punctuation.endsSentence()) endSentence();
					continue;
				}
				Term term = (Term) item;
				if (like != null && term.meaning().role() != Role.COLOR) {
					unknownSound();
				}
				read(term);
				previous = term;
			}
			endSentence();
			checkUnfinished();
			VerseEquation equation = new VerseEquation(VerseEquation.VERSION, List.copyOf(steps));
			highlights.sort((a, b) -> Integer.compare(a.start(), b.start()));
			return new Reading(text, equation, List.copyOf(highlights), List.copyOf(notes), cost(equation), tier(equation));
		}

		private void read(Term term) {
			Meaning meaning = term.meaning();
			switch (meaning.role()) {
				case LOGIC -> logic(term, meaning.value());
				case SENSE -> sense(term);
				case EFFECT -> effect(term);
				case TARGET -> target(term, meaning.value());
				case DURATION -> {
					hear(term);
					if (action != null) action.ending = meaning.value();
					else note(term, "\"" + words(term) + "\" - of what? It comes before anything happens.");
				}
				case MODIFIER -> modifier(term, meaning.value());
				case LIKE -> {
					hear(term);
					like = term;
				}
				case QUANTITY -> {
					hear(term);
					if (action != null) {
						action.quantity = Identifier.of(meaning.value());
						action.quantityStart = term.start();
						action.quantityEnd = term.end();
					} else {
						pendingQuantity = term; // "let my love manifest in lights": named before the lights
					}
				}
				case PROPERTY -> {
					hear(term);
					if (action != null) {
						if (!action.properties.contains(meaning.value())) action.properties.add(meaning.value());
					} else if (!pendingProperties.contains(meaning.value())) {
						pendingProperties.add(meaning.value());
					}
				}
				case SHAPE -> {
					hear(term);
					if (action != null && action.piece.equals(LIGHT)) {
						action.shape = meaning.value();
					} else {
						pendingShape = meaning.value(); // "a crown of twinkling lights": the shape can come first
					}
				}
				case PART -> {
					hear(term);
					if (meaning.value().equals("aura")) {
						note(term, "An aura is a reflection of the soul - no ordinary verse can change it.");
					} else if (action != null && action.piece.equals(CHANGE)) {
						if (!action.parts.contains(meaning.value())) action.parts.add(meaning.value());
					} else if (!pendingParts.contains(meaning.value())) {
						pendingParts.add(meaning.value());
					}
				}
				case HUE -> {
					hear(term);
					if (action != null && (action.piece.equals(CHANGE) || action.piece.equals(LIGHT)) && action.tint.isEmpty()) {
						action.tint = meaning.value();
					} else {
						pendingHue = meaning.value(); // "green lights": the color can come first
					}
				}
				case COLOR -> {
					hear(term);
					like = null;
					if (action != null && action.piece.equals(SOUND)) {
						action.sound = meaning.value();
					} else {
						// A color with nothing singing yet waits for the next Sound; on its own it's just poetry.
						pendingColor = meaning.value();
					}
				}
			}
		}

		private void logic(Term term, String value) {
			switch (value) {
				case "when" -> {
					hear(term);
					if (!step.isEmpty()) finishStep();
					awaiting = term;
				}
				case "until" -> {
					hear(term);
					awaiting = term;
				}
				case "then" -> {
					hear(term);
					if (!anyAction) note(term, "This \"then\" follows nothing - nothing has happened before it.");
					finishStep();
				}
				default -> hear(term); // "and": at the same time, the default within a step
			}
		}

		private void sense(Term term) {
			if (awaiting == null) {
				return; // "the dawn" in passing is just poetry
			}
			hear(term);
			Identifier sense = Identifier.of(term.meaning().value());
			if (isUntil(awaiting)) {
				if (action != null) action.ending = "until:" + sense;
				else note(term, "Until " + words(term) + " - but nothing has started yet.");
			} else {
				if (sense.equals(LANDS) && !anyMove()) {
					note(term, "\"" + words(term) + "\" - nothing has been moved to land.");
				}
				trigger = sense;
			}
			awaiting = null;
		}

		private boolean isUntil(Term term) {
			return term.meaning().role() == Role.LOGIC && term.meaning().value().equals("until");
		}

		private boolean anyMove() {
			return steps.stream().flatMap(s -> s.actions().stream()).anyMatch(a -> a.piece().equals(MOVE))
					|| step.stream().anyMatch(d -> d.piece.equals(MOVE));
		}

		private void effect(Term term) {
			hear(term);
			if (awaiting != null) {
				note(awaiting, "\"" + words(awaiting) + "\" - " + (isUntil(awaiting) ? "until" : "when") + " what? Nothing here can sense that.");
				awaiting = null;
			}
			Identifier piece = term.meaning().piece();
			// "go hence", "go away", "glowing lights": one effect, said twice - the first word's meaning, the
			// second's emphasis.
			if (action != null && previous == lastEffectTerm && action.piece.equals(piece)) {
				if (term.meaning().value().equals("banish") && !action.mode.equals("banish")) action.destination = "away";
				return;
			}
			action = new Draft(piece, term.meaning().value(), term.start(), term.end());
			lastEffectTerm = term;
			if (!pendingTarget.isEmpty()) {
				action.target = pendingTarget;
				pendingTarget = "";
			}
			if (piece.equals(SOUND) && !pendingColor.isEmpty()) {
				action.sound = pendingColor;
				pendingColor = "";
			}
			if (piece.equals(LIGHT) && !pendingShape.isEmpty()) {
				action.shape = pendingShape;
				pendingShape = "";
			}
			if (piece.equals(LIGHT) && pendingTwinkle != null) {
				action.twinkle = true;
				pendingTwinkle = null;
			}
			if (piece.equals(CHANGE)) {
				action.parts.addAll(pendingParts);
				pendingParts.clear();
			}
			if ((piece.equals(CHANGE) || piece.equals(LIGHT)) && !pendingHue.isEmpty()) {
				action.tint = pendingHue;
				pendingHue = "";
			}
			if (pendingQuantity != null) {
				action.quantity = Identifier.of(pendingQuantity.meaning().value());
				action.quantityStart = pendingQuantity.start();
				action.quantityEnd = pendingQuantity.end();
				pendingQuantity = null;
			}
			action.properties.addAll(pendingProperties);
			pendingProperties.clear();
			step.add(action);
			anyAction = true;
		}

		private void target(Term term, String value) {
			hear(term);
			String target = value;
			if (value.equals("it") && trigger != null && (trigger.equals(GAZE) || trigger.equals(NEAR))) {
				// In "when mine eyes meet another, ... crown their head", they are the one the eyes met.
				target = "sensed";
			} else if (value.equals("it")) {
				if (lastTarget.isEmpty()) {
					note(term, "\"It\" - what is it? Nothing has been named yet; it will be what you're looking at.");
					target = "this";
				} else {
					target = lastTarget;
				}
			}
			if (action == null) {
				pendingTarget = target;
			} else if (action.target.isEmpty()) {
				// "go yonder", "carry yonder": a place to go, not a thing to move.
				if (target.equals("yonder") && (action.piece.equals(SHIFT) && action.mode.equals("go") || action.mode.equals("carry"))) {
					action.destination = "yonder";
					if (action.piece.equals(SHIFT)) action.target = "self";
				} else {
					action.target = target;
				}
			} else if (action.destination.isEmpty() && target.equals("yonder")) {
				action.destination = "yonder";
			} else if (!action.target.equals(target)) {
				note(term, "\"" + words(term) + "\" - a second target for the same " + name(action.piece)
						+ "; the spell will act on " + describeTarget(action.target) + ".");
			}
			if (!target.equals("yonder") || action == null || action.target.equals("yonder")) lastTarget = target;
		}

		private void modifier(Term term, String value) {
			hear(term);
			if (value.equals("twinkle")) {
				// How light behaves - flashing in and out. It can come before the light ("twinkling lights").
				if (action != null && action.piece.equals(LIGHT)) action.twinkle = true;
				else pendingTwinkle = term;
				return;
			}
			if (action == null) {
				note(term, "\"" + words(term) + "\" - but nothing has happened yet for it to change.");
				return;
			}
			switch (value) {
				case "gentle" -> {
					action.strength *= 0.5f;
					action.gentle = true;
				}
				case "great" -> {
					// The position rule: "thrice as greatly" is magnitude; "fetch water thrice" is repetition.
					if (pendingMagnitude > 0) {
						action.repeats = 1;
						action.strength *= pendingMagnitude;
					} else {
						action.strength *= 2f;
					}
				}
				case "twice", "thrice" -> action.repeats = value.equals("twice") ? 2 : 3;
				case "high", "low" -> action.pitch = value;
				default -> {
				}
			}
			pendingMagnitude = value.equals("twice") ? 2 : value.equals("thrice") ? 3 : 0;
		}

		private void unknownSound() {
			// "like a kazoo": "like a" followed by a word the language doesn't know as a sound.
			int afterLike = like.end();
			String next = "";
			for (Token token : tokens) {
				if (token.start() >= afterLike && !token.punctuation()) {
					next = token.word();
					break;
				}
			}
			if (!next.isEmpty() && action != null && action.piece.equals(SOUND)) {
				note(like, "You don't know the sound of a '" + next + "'.");
			}
			like = null;
		}

		private void endSentence() {
			if (like != null) {
				unknownSound();
			}
			if (awaiting != null) {
				note(awaiting, "\"" + words(awaiting) + "\" - " + (isUntil(awaiting) ? "until" : "when") + " what? Nothing here can sense that.");
				awaiting = null;
			}
			finishStep();
			pendingTarget = "";
			pendingParts.clear();
			pendingHue = "";
			if (pendingTwinkle != null) {
				note(pendingTwinkle, "\"" + words(pendingTwinkle) + "\" - only light can twinkle, and there's none here. Try \"twinkling lights\".");
				pendingTwinkle = null;
			}
		}

		private void finishStep() {
			if (step.isEmpty()) {
				if (trigger != null && awaiting == null) {
					// "When dawn breaks." with nothing after it.
					trigger = null;
				}
				return;
			}
			for (Draft draft : step) {
				settleTarget(draft);
				if (draft.piece.equals(CHANGE) && draft.mode.equals("recolor")) {
					if (draft.parts.isEmpty()) {
						notes.add(new Note(draft.start, draft.end, "Change what? Name it - mane, coat, or eyes."));
					}
					if (draft.tint.isEmpty()) {
						notes.add(new Note(draft.start, draft.end, "Change it to what? Name a color - green, rose, gold..."));
					}
				}
				if (draft.quantity != null && draft.properties.isEmpty()) {
					// Magic does what you wrote: love that changes nothing is left as a feeling, with a note.
					notes.add(new Note(draft.quantityStart, draft.quantityEnd, "Your " + text.substring(draft.quantityStart, draft.quantityEnd)
							.replaceFirst("(?i)^(my|mine)\\s+", "") + " is here, but what should it change? Say what it shapes - more "
							+ (draft.piece.equals(LIGHT) ? "lights" : "of it") + ", brighter, warmer, or longer - as it grows."));
				}
			}
			steps.add(new Step(Optional.ofNullable(trigger), step.stream().map(Draft::build).toList()));
			step.clear();
			trigger = null;
			action = null;
		}

		/** An effect that names nothing to act on gets the natural default - with a note when it's a guess. */
		private void settleTarget(Draft draft) {
			if (!draft.target.isEmpty()) return;
			if (draft.piece.equals(SHIFT)) {
				draft.target = "self";
				if (draft.destination.isEmpty() && !draft.mode.equals("banish")) {
					notes.add(new Note(draft.start, draft.end, "Go where? Name a place - \"yonder\" - or you'll stay put."));
				}
			} else if (draft.piece.equals(LIGHT) || draft.piece.equals(SOUND)) {
				draft.target = "self";
			} else {
				draft.target = "this";
				notes.add(new Note(draft.start, draft.end, capitalize(draft.mode) + " what? Nothing is named - it will be what you're looking at."));
			}
		}

		private void checkUnfinished() {
			if (steps.isEmpty() && !notes.isEmpty()) return;
			if (steps.isEmpty() && highlights.stream().noneMatch(h -> h.role() != null)) {
				notes.add(new Note(0, 0, "Only poetry - nothing here is a spell (yet)."));
			}
		}

		private void hear(Term term) {
			highlights.add(new Highlight(term.start(), term.end(), term.meaning().role()));
		}

		private void note(Term term, String message) {
			notes.add(new Note(term.start(), term.end(), message));
		}

		private String words(Term term) {
			return text.substring(term.start(), term.end());
		}
	}

	// ---- Cost and tier ----

	private static int cost(VerseEquation equation) {
		float total = 0;
		for (Step step : equation.steps()) {
			if (step.trigger().isPresent()) total += 1;
			for (Action action : step.actions()) {
				float base = action.piece().equals(SHIFT) ? 5 : action.piece().equals(MOVE) ? 3 : action.piece().equals(CHANGE) ? 2 * Math.max(1, action.parts().size()) : 1;
				float lasting = switch (action.ending()) {
					case "moment", "" -> 0;
					case "while" -> 1;
					case "hour" -> 2;
					case "night" -> 4;
					default -> 2; // until a sense
				};
				total += (base * (action.gentle() ? 0.75f : Math.max(1f, action.strength())) + lasting) * action.repeats();
			}
		}
		return Math.round(total);
	}

	private static String tier(VerseEquation equation) {
		boolean adept = equation.steps().stream().flatMap(s -> s.actions().stream())
				.anyMatch(a -> a.piece().equals(SHIFT) || a.strength() > 2f || a.ending().equals("night"));
		return adept ? "adept" : "novice";
	}

	// ---- Describing (the notation preview) ----

	/** The notation preview: exactly what the verse will do, in plain words (design doc: rule 1). */
	public static List<String> describe(VerseEquation equation) {
		List<String> lines = new ArrayList<>();
		int number = 1;
		for (Step step : equation.steps()) {
			StringBuilder line = new StringBuilder();
			line.append(number++).append(". ");
			step.trigger().ifPresent(sense -> line.append("When ").append(describeSense(sense)).append(": "));
			List<String> parts = new ArrayList<>();
			for (Action action : step.actions()) {
				parts.add(describe(action));
			}
			line.append(String.join(", and at the same time ", parts));
			lines.add(line.toString());
		}
		return lines;
	}

	private static String describe(Action action) {
		StringBuilder out = new StringBuilder(name(action.piece())).append(" (").append(action.mode()).append(")");
		out.append(" - ").append(describeTarget(action.target()));
		if (!action.destination().isEmpty()) {
			out.append(action.destination().equals("away") ? ", sent far away" : ", to where you're looking");
		}
		if (action.shape().equals("crown")) {
			out.append(", as a crown of light around their head");
		}
		if (action.twinkle()) {
			out.append(", twinkling in and out");
		}
		if (action.piece().equals(CHANGE)) {
			String parts = action.parts().isEmpty() ? "" : String.join(" and ", action.parts().stream()
					.map(part -> part.equals("mane") ? "mane and tail" : part).toList());
			if (action.mode().equals("restore")) {
				out.append(", back as it was").append(parts.isEmpty() ? " (every change)" : " (" + parts + ")");
			} else {
				out.append(", ").append(parts.isEmpty() ? "?" : parts).append(" to ")
						.append(action.tint().isEmpty() ? "?" : VerseLanguage.hueName(action.tint()));
				if (action.ending().isEmpty()) out.append(", until undone");
			}
		} else if (!action.tint().isEmpty()) {
			out.append(", in ").append(VerseLanguage.hueName(action.tint()));
		}
		if (action.piece().equals(SOUND)) {
			out.append(", like a ").append(action.sound().isEmpty() ? "harp" : action.sound().replace('_', ' '));
			if (!action.pitch().isEmpty()) out.append(", ").append(action.pitch());
		}
		for (String boost : action.boosts()) {
			out.append(", ").append(describeProperty(boost, action.piece()));
		}
		for (VerseEquation.Binding binding : action.bindings()) {
			out.append(", ").append(describeProperty(binding.property(), action.piece())).append(" the more ")
					.append(binding.quantity().getPath().equals("love") ? "you love them (from your shared history)" : binding.quantity().toString());
		}
		if (action.gentle()) out.append(", gently (no harm)");
		else if (action.strength() > 1f) out.append(", ").append(action.strength() >= 3f ? "thrice as " : "").append("greatly");
		if (action.repeats() > 1) out.append(", ").append(action.repeats() == 2 ? "twice" : action.repeats() + " times");
		if (!action.ending().isEmpty()) out.append(", ").append(describeEnding(action.ending()));
		return out.toString();
	}

	static String name(Identifier piece) {
		return capitalize(piece.getPath());
	}

	static String describeTarget(String target) {
		return switch (target) {
			case "self" -> "you";
			case "this" -> "what you're looking at, within reach";
			case "yonder" -> "where you're looking, as far as you can see";
			case "around" -> "everything around you";
			case "these" -> "everyone near where you're looking";
			case "sensed" -> "the one it sensed";
			default -> target;
		};
	}

	private static String describeSense(Identifier sense) {
		return switch (sense.getPath()) {
			case "strike" -> "you strike (your second ability key)";
			case "gaze" -> "your eyes meet another (your crosshair falls on a creature)";
			case "near" -> "something comes near";
			case "night_falls" -> "night falls";
			case "dawn" -> "dawn breaks";
			case "lands" -> "it lands";
			default -> sense.toString();
		};
	}

	private static String describeProperty(String property, Identifier piece) {
		return switch (property) {
			case "amount" -> piece.equals(LIGHT) ? "more lights" : "more of it";
			case "brightness" -> "brighter";
			case "color" -> "warmer in color";
			case "duration" -> "lasting longer";
			default -> property;
		};
	}

	private static String describeEnding(String ending) {
		if (ending.startsWith("until:")) {
			return "until " + describeSense(Identifier.of(ending.substring(6)));
		}
		return switch (ending) {
			case "moment" -> "for a moment";
			case "while" -> "for a while";
			case "hour" -> "for an hour";
			case "night" -> "all night";
			default -> ending;
		};
	}

	private static String capitalize(String word) {
		return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
