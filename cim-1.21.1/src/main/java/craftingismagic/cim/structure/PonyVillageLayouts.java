package craftingismagic.cim.structure;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
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
import net.minecraft.util.math.random.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a pony village is built from (design doc: Village identity - the ring layout), one file per village kind,
 * {@code data/<ns>/pony_village/<name>.json}:
 * <pre>{@code
 * {
 *   "centerpieces": [{"structure": "minecraft:village/plains/town_centers/plains_fountain_01", "weight": 1}],
 *   "plaza":     [...],   // the special buildings facing the plaza - each at most once per village
 *   "homes":     [...],   // the rings of houses
 *   "outskirts": [...],   // farms and pens, round the edge of town
 *   "schoolhouse": "cim:village/plains/houses/schoolhouse",  // optional: one per village, at the end of a road
 *   "town_hall": "cim:village/plains/centers/town_hall"      // optional: the heart of every village bigger than a hamlet
 * }
 * }</pre>
 * Buildings face the road through their {@code minecraft:building_entrance} jigsaw, as in vanilla villages.
 */
public final class PonyVillageLayouts {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/structures");

	public record Entry(Identifier structure, int weight) {
	}

	/** @param townHall the centerpiece of every village bigger than a hamlet; hamlets pick from {@code centerpieces} */
	public record Layout(List<Entry> centerpieces, List<Entry> plaza, List<Entry> homes, List<Entry> outskirts, Identifier schoolhouse,
			Identifier townHall) {
	}

	private static volatile Map<Identifier, Layout> layouts = Map.of();

	private PonyVillageLayouts() {
	}

	public static Layout get(Identifier id) {
		return layouts.get(id);
	}

	/** A weighted pick from {@code entries}, or null if there are none. */
	public static Entry pick(List<Entry> entries, Random random) {
		int total = 0;
		for (Entry entry : entries) total += entry.weight();
		if (total <= 0) return null;
		int roll = random.nextInt(total);
		for (Entry entry : entries) {
			roll -= entry.weight();
			if (roll < 0) return entry;
		}
		return null;
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "pony_village");
			}

			@Override
			public void reload(ResourceManager manager) {
				Map<Identifier, Layout> loaded = new HashMap<>();
				for (Map.Entry<Identifier, Resource> entry : manager.findResources("pony_village", path -> path.getPath().endsWith(".json")).entrySet()) {
					Identifier path = entry.getKey();
					Identifier id = Identifier.of(path.getNamespace(), path.getPath().substring("pony_village/".length(), path.getPath().length() - 5));
					try (BufferedReader reader = entry.getValue().getReader()) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						loaded.put(id, new Layout(entries(json, "centerpieces"), entries(json, "plaza"), entries(json, "homes"), entries(json, "outskirts"),
								json.has("schoolhouse") ? Identifier.of(JsonHelper.getString(json, "schoolhouse")) : null,
								json.has("town_hall") ? Identifier.of(JsonHelper.getString(json, "town_hall")) : null));
					} catch (Exception e) {
						LOGGER.error("data/{}/{}: couldn't read it: {}", path.getNamespace(), path.getPath(), e.getMessage());
					}
				}
				layouts = Map.copyOf(loaded);
				LOGGER.info("Loaded {} pony village layout(s)", loaded.size());
			}
		});
	}

	private static List<Entry> entries(JsonObject json, String key) {
		List<Entry> out = new ArrayList<>();
		for (JsonElement element : JsonHelper.getArray(json, key, new JsonArray())) {
			JsonObject entry = element.getAsJsonObject();
			out.add(new Entry(Identifier.of(JsonHelper.getString(entry, "structure")), Math.max(1, JsonHelper.getInt(entry, "weight", 1))));
		}
		return List.copyOf(out);
	}
}
