package craftingismagic.cim.speech;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

import craftingismagic.cim.narrative.TaleAst.ConditionCall;
import craftingismagic.cim.narrative.TaleParser;
import craftingismagic.cim.narrative.TaleProblem;
import craftingismagic.cim.narrative.TaleVocabulary;

/**
 * The tunes ponies hum as they go about their day (design doc: Humming), loaded from
 * {@code data/<namespace>/hums/<name>.json}:
 * <pre>
 * {
 *   "scale": "major",                       // major, minor or lydian - see SpeakingVoice
 *   "tempo": 5,                             // ticks per note (4 = brisk, 7 = slow)
 *   "notes": "0 2 4 - 4 5 4 2 0",           // scale degrees from the hummer's pitch; 5 is an octave up
 *                                           // in a 5-note scale, -1 a step below; "-" is a rest
 *   "weight": 1,                            // how often it's picked against the others that fit
 *   "when": "time_of_day(morning)"          // optional .tale conditions, all of which must hold
 * }
 * </pre>
 * Datapacks can add their own - including the optional pack meant for tunes from the show.
 */
public final class HumLibrary {
	public static final String FOLDER = "hums";
	/** A rest, in {@link Hum#notes()}. */
	public static final int REST = Integer.MIN_VALUE;
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/hums");

	public record Hum(Identifier id, String scale, int tempo, List<Integer> notes, int weight, List<ConditionCall> when) {
	}

	private static volatile List<Hum> hums = List.of();

	private HumLibrary() {
	}

	public static List<Hum> all() {
		return hums;
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "hums");
			}

			@Override
			public void reload(ResourceManager manager) {
				try {
					HumLibrary.reload(manager);
				} catch (RuntimeException e) {
					LOGGER.error("Loading hums failed; keeping the previous ones", e);
				}
			}
		});
	}

	private static void reload(ResourceManager manager) {
		List<Hum> loaded = new ArrayList<>();
		Map<Identifier, Resource> found = manager.findResources(FOLDER, path -> path.getPath().endsWith(".json"));
		found.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString))).forEach(entry -> {
			Identifier path = entry.getKey();
			Identifier id = Identifier.of(path.getNamespace(), path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - 5));
			String source = "data/" + path.getNamespace() + "/" + path.getPath() + " (" + entry.getValue().getPackId() + ")";
			try (BufferedReader reader = entry.getValue().getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				loaded.add(read(id, source, json));
			} catch (IOException | RuntimeException e) {
				LOGGER.error("{}: couldn't read this hum: {}", source, e.getMessage());
			}
		});
		hums = List.copyOf(loaded);
		LOGGER.info("Loaded {} hum(s)", hums.size());
	}

	private static Hum read(Identifier id, String source, JsonObject json) {
		String scale = JsonHelper.getString(json, "scale", "major");
		int tempo = Math.clamp(JsonHelper.getInt(json, "tempo", 5), 2, 20);
		List<Integer> notes = new ArrayList<>();
		for (String token : JsonHelper.getString(json, "notes").trim().split("\\s+")) {
			notes.add(token.equals("-") ? REST : Integer.parseInt(token));
		}
		if (notes.isEmpty() || notes.size() > 64) {
			throw new IllegalArgumentException("a hum needs 1 to 64 notes");
		}
		List<ConditionCall> when = List.of();
		if (json.has("when")) {
			List<TaleProblem> problems = new ArrayList<>();
			when = TaleParser.parseConditions(id, source, JsonHelper.getString(json, "when"), problems);
			problems.forEach(problem -> LOGGER.error("{}", problem));
			for (ConditionCall condition : when) {
				if (TaleVocabulary.condition(condition.type()) == null) {
					LOGGER.error("{}: no condition called {} - this hum will never be picked", source, condition.type());
				}
			}
		}
		return new Hum(id, scale, tempo, List.copyOf(notes), Math.max(1, JsonHelper.getInt(json, "weight", 1)), when);
	}
}
