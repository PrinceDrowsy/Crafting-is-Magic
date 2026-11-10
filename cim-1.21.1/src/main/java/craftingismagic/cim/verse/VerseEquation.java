package craftingismagic.cim.verse;

import java.util.List;
import java.util.Optional;

import net.minecraft.util.Identifier;

/**
 * What a verse means, with the poetry stripped away (design doc: Verse): a language-neutral equation, the
 * same whatever words wrote it. Steps happen in order ({@code then}); each step's actions happen
 * together ({@code and}); a step can wait for a trigger ({@code when}).
 *
 * @param version the Verse version it was read with - a verse whose words go away (an update, an
 *                uninstalled add-on) is marked broken and explained, never crashing anything
 */
public record VerseEquation(int version, List<Step> steps) {
	public static final int VERSION = 1;

	/** @param trigger the sense this step waits for ({@code cim:strike}, {@code cim:dawn}...), if any */
	public record Step(Optional<Identifier> trigger, List<Action> actions) {
	}

	/**
	 * One effect.
	 *
	 * @param piece       the effect piece, e.g. {@code cim:move}
	 * @param mode        how it acts, e.g. {@code lift}, {@code carry}, {@code shine}, {@code lights}, {@code go},
	 *                    {@code recolor}, {@code restore}
	 * @param target      what it acts on: {@code self}, {@code this} (what you're looking at, within reach),
	 *                    {@code yonder} (where you're looking, in sight), {@code around}, {@code these}, or
	 *                    {@code sensed} (whoever the step's trigger sensed - the one your eyes met)
	 * @param destination for carrying and going: where to - {@code yonder} - or "" for none
	 * @param strength    1 normally; {@code gently} halves it (and does no harm), {@code greatly} doubles it
	 * @param gentle      does no harm
	 * @param repeats     {@code twice}, {@code thrice}
	 * @param sound       for Sound: the sound color's name ({@code harp}); "" for the default
	 * @param pitch       for Sound: "", {@code high} or {@code low}
	 * @param ending      how long it lasts: "" (as long as it naturally does), {@code moment}, {@code while},
	 *                    {@code hour}, {@code night}, or a sense it lasts until - {@code until:cim:dawn}
	 * @param shape       for Light: the form it takes - "" (just light), {@code crown} (a ring of lights
	 *                    above the target's head)
	 * @param twinkle     for Light: its lights flash in and out ("twinkling", "sparkling") rather than holding steady
	 * @param parts       for Change: what it recolors - {@code mane} (mane and tail), {@code coat}, {@code eyes};
	 *                    for {@code restore}, empty means everything
	 * @param tint        the color named ({@code #RRGGBB}) - what Change paints, or Light's color; "" for none
	 * @param boosts    properties raised with nothing measured - "brighter" on its own
	 * @param bindings    properties that follow something measured, live - "more as my love grows"
	 */
	public record Action(Identifier piece, String mode, String target, String destination, float strength, boolean gentle,
			int repeats, String sound, String pitch, String ending, String shape, boolean twinkle, List<String> parts,
			String tint, List<String> boosts, List<Binding> bindings) {
	}

	/**
	 * A live binding (design doc: Verse, live binding with "as"): a property of the effect follows a quantity
	 * the spell measures.
	 *
	 * @param property {@code amount}, {@code brightness}, {@code color} or {@code duration}
	 * @param quantity what's measured - {@code cim:love}: the caster's own love for the target, from their
	 *                 shared history (never the target's feelings, which aren't the caster's to read)
	 */
	public record Binding(String property, Identifier quantity) {
	}
}
