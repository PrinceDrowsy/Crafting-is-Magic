package craftingismagic.cim.verse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The words Verse hears (design doc: Verse - spells as poetic equations). Loaded from datapacks:
 * <ul>
 * <li>{@code data/<ns>/verse/words/<language>.json} - keywords and phrases by what they mean: effects
 * (by piece and mode), targets, logic, senses, durations, modifiers, "like a" for sound colors, the
 * parts Change recolors, and hues ({@code "green": "#5BC85B"}).
 * Every language's file is merged, so a verse may mix them; other mods add files of their own.</li>
 * <li>{@code data/<ns>/verse/sound_colors/<name>.json} - the sounds Sound can sing like:
 * {@code {"sound": "minecraft:block.note_block.harp", "octave": 0, "words": ["harp"]}}.</li>
 * </ul>
 * Every other word is poetry.
 */
public final class VerseLanguage {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/verse");

	/** What a word or phrase means to the spell. */
	public enum Role {
		EFFECT, TARGET, LOGIC, SENSE, DURATION, MODIFIER, LIKE, COLOR, SHAPE,
		/** Something the spell measures - "my love" - that a property can follow. */
		QUANTITY,
		/** What a quantity changes - "more", "brighter", "warmer", "longer". */
		PROPERTY,
		/** A part of a pony Change can recolor - "mane", "coat", "eyes" (and "aura", which it can't). */
		PART,
		/** A color to paint with - "green", "rose"; its value is {@code #RRGGBB}. */
		HUE
	}

	/**
	 * @param value the meaning within its role - a mode for effects ({@code lift}), a target
	 *              ({@code yonder}), a sense ID ({@code cim:dawn}), a color name ({@code harp})...
	 * @param piece for an effect, the piece it belongs to ({@code cim:move}); null otherwise
	 */
	public record Meaning(Role role, String value, Identifier piece) {
	}

	/** A sound Sound can sing like - a note block instrument, an octave offset from the harp's. */
	public record SoundColor(String name, Identifier sound, int octave) {
	}

	private record Snapshot(Map<String, Meaning> phrases, int longestPhrase, Map<String, SoundColor> colors, Map<String, String> hueNames) {
	}

	private static volatile Snapshot current = new Snapshot(Map.of(), 1, Map.of(), Map.of());

	private VerseLanguage() {
	}

	/** What {@code phrase} (lowercase, single spaces) means, or null if it's poetry. */
	public static Meaning meaning(String phrase) {
		return current.phrases().get(phrase);
	}

	/** The most words any keyword phrase has - the reader tries longest phrases first. */
	public static int longestPhrase() {
		return current.longestPhrase();
	}

	public static SoundColor color(String name) {
		return current.colors().get(name);
	}

	/** A word for a hue ({@code #RRGGBB}) - the first one the language lists for it - or the hex itself. */
	public static String hueName(String hex) {
		return current.hueNames().getOrDefault(hex, hex);
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "verse");
			}

			@Override
			public void reload(ResourceManager manager) {
				try {
					VerseLanguage.reload(manager);
				} catch (RuntimeException e) {
					LOGGER.error("Loading Verse words failed; keeping the previous ones", e);
				}
			}
		});
	}

	private static void reload(ResourceManager manager) {
		Map<String, Meaning> phrases = new HashMap<>();
		Map<String, SoundColor> colors = new HashMap<>();
		Map<String, String> hueNames = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : sorted(manager.findResources("verse/sound_colors", path -> path.getPath().endsWith(".json")))) {
			String name = entry.getKey().getPath().substring("verse/sound_colors/".length(), entry.getKey().getPath().length() - 5);
			JsonObject json = read(entry);
			if (json == null) continue;
			SoundColor color = new SoundColor(name, Identifier.of(JsonHelper.getString(json, "sound")), JsonHelper.getInt(json, "octave", 0));
			colors.put(name, color);
			for (JsonElement word : JsonHelper.getArray(json, "words", new com.google.gson.JsonArray())) {
				phrases.put(normalize(word.getAsString()), new Meaning(Role.COLOR, name, null));
			}
			phrases.putIfAbsent(normalize(name.replace('_', ' ')), new Meaning(Role.COLOR, name, null));
		}
		for (Map.Entry<Identifier, Resource> entry : sorted(manager.findResources("verse/words", path -> path.getPath().endsWith(".json")))) {
			JsonObject json = read(entry);
			if (json == null) continue;
			if (json.has("effects")) {
				for (Map.Entry<String, JsonElement> piece : json.getAsJsonObject("effects").entrySet()) {
					Identifier pieceId = Identifier.of(piece.getKey());
					piece.getValue().getAsJsonObject().entrySet().forEach(word ->
							phrases.put(normalize(word.getKey()), new Meaning(Role.EFFECT, word.getValue().getAsString(), pieceId)));
				}
			}
			table(json, "targets", Role.TARGET, phrases);
			table(json, "logic", Role.LOGIC, phrases);
			table(json, "senses", Role.SENSE, phrases);
			table(json, "durations", Role.DURATION, phrases);
			table(json, "modifiers", Role.MODIFIER, phrases);
			table(json, "like", Role.LIKE, phrases);
			table(json, "shapes", Role.SHAPE, phrases);
			table(json, "quantities", Role.QUANTITY, phrases);
			table(json, "properties", Role.PROPERTY, phrases);
			table(json, "parts", Role.PART, phrases);
			if (json.has("hues")) {
				for (Map.Entry<String, JsonElement> word : json.getAsJsonObject("hues").entrySet()) {
					String hex = word.getValue().getAsString().toUpperCase(Locale.ROOT);
					if (!hex.matches("#[0-9A-F]{6}")) {
						LOGGER.error("data/{}/{}: hue \"{}\" should be #RRGGBB, not \"{}\"", entry.getKey().getNamespace(), entry.getKey().getPath(),
								word.getKey(), hex);
						continue;
					}
					phrases.put(normalize(word.getKey()), new Meaning(Role.HUE, hex, null));
					hueNames.putIfAbsent(hex, normalize(word.getKey()));
				}
			}
		}
		int longest = phrases.keySet().stream().mapToInt(phrase -> phrase.split(" ").length).max().orElse(1);
		current = new Snapshot(Map.copyOf(phrases), longest, Map.copyOf(colors), Map.copyOf(hueNames));
		LOGGER.info("Loaded {} Verse word(s) and {} sound color(s)", phrases.size(), colors.size());
	}

	private static void table(JsonObject json, String key, Role role, Map<String, Meaning> phrases) {
		if (json.has(key)) {
			json.getAsJsonObject(key).entrySet().forEach(word ->
					phrases.put(normalize(word.getKey()), new Meaning(role, word.getValue().getAsString(), null)));
		}
	}

	private static Iterable<Map.Entry<Identifier, Resource>> sorted(Map<Identifier, Resource> found) {
		return found.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString))).toList();
	}

	private static JsonObject read(Map.Entry<Identifier, Resource> entry) {
		try (BufferedReader reader = entry.getValue().getReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			LOGGER.error("data/{}/{}: couldn't read it: {}", entry.getKey().getNamespace(), entry.getKey().getPath(), e.getMessage());
			return null;
		}
	}

	/** Lowercase, apostrophes kept, runs of space collapsed - how phrases are matched. */
	public static String normalize(String text) {
		return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
}
