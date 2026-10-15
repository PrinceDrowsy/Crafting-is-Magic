package craftingismagic.cim.narrative;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.util.Identifier;

/**
 * Writes everything a {@code .tale} file may say - every condition, effect, event, and variable with
 * its argument rules ({@link ArgSpec}), plus voices, tones, feelings, tiers, and start contexts - as
 * JSON for the {@code .tale} editor ({@code tools/tale-editor}). Because the editor checks files
 * against the same specs the game uses, the two agree; and whatever another mod registers shows up
 * in the editor too.
 */
public final class TaleVocabularyExport {
	public static final int FORMAT = 1;

	private TaleVocabularyExport() {
	}

	public static JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		root.add("conditions", calls(TaleVocabulary.conditionChecks()));
		root.add("effects", calls(TaleVocabulary.effectChecks()));
		root.add("events", calls(TaleVocabulary.eventChecks()));
		root.add("variables", strings(TaleVocabulary.variables()));
		root.add("voices", strings(TaleVocabulary.voices()));
		root.add("feelinglessVoices", strings(TaleVocabulary.voices().stream().filter(voice -> !TaleVocabulary.voiceTakesFeeling(voice)).toList()));
		root.add("tones", strings(TaleVocabulary.tones()));
		root.add("feelings", strings(TaleVocabulary.feelings()));
		root.add("tiers", strings(TaleVocabulary.TIERS));
		root.add("startContexts", strings(TaleVocabulary.startContexts()));
		root.add("leadInContexts", strings(TaleVocabulary.leadInContexts()));
		return root;
	}

	public static void write(Path file) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		Files.createDirectories(file.toAbsolutePath().getParent());
		try (Writer writer = Files.newBufferedWriter(file)) {
			gson.toJson(toJson(), writer);
		}
	}

	private static JsonObject calls(Map<Identifier, TaleVocabulary.ArgumentCheck> checks) {
		JsonObject calls = new JsonObject();
		new TreeMap<>(checks).forEach((id, check) -> calls.add(id.toString(), spec(check)));
		return calls;
	}

	/** An {@link ArgSpec} as data; anything else is "custom" - the editor accepts any arguments for it. */
	private static JsonObject spec(TaleVocabulary.ArgumentCheck check) {
		JsonObject spec = new JsonObject();
		if (!(check instanceof ArgSpec argSpec)) {
			spec.addProperty("custom", true);
			return spec;
		}
		spec.addProperty("usage", argSpec.usage());
		spec.addProperty("comparison", argSpec.comparison());
		JsonArray forms = new JsonArray();
		for (ArgSpec.Form form : argSpec.forms()) {
			JsonObject formJson = new JsonObject();
			formJson.addProperty("repeatLast", form.repeatLast());
			JsonArray args = new JsonArray();
			for (ArgSpec.Arg arg : form.args()) {
				JsonObject argJson = new JsonObject();
				argJson.addProperty("optional", arg.optional());
				JsonArray tokens = new JsonArray();
				for (ArgSpec.Token token : arg.tokens()) {
					JsonObject tokenJson = new JsonObject();
					tokenJson.addProperty("kind", token.kind().name().toLowerCase(java.util.Locale.ROOT));
					tokenJson.addProperty("label", token.label());
					tokenJson.addProperty("optional", token.optional());
					if (!token.valueList().isEmpty()) {
						tokenJson.add("values", strings(token.valueList()));
					}
					tokens.add(tokenJson);
				}
				argJson.add("tokens", tokens);
				args.add(argJson);
			}
			formJson.add("args", args);
			forms.add(formJson);
		}
		spec.add("forms", forms);
		return spec;
	}

	private static JsonArray strings(Iterable<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}
}
