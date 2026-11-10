package craftingismagic.cim.memory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
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

/**
 * How each kind of memory travels as gossip (design doc: The memory system, Gossip). Loaded from
 * {@code data/<ns>/gossip/<path>.json}, where the file's ID is the memory type it describes -
 * {@code data/cim/gossip/loss.json} is the rule for {@code cim:loss}. Every field is optional:
 * <pre>{@code
 * {
 *   "spreads": true,       // false: never gossiped about (a confided secret, say)
 *   "reach": 2,            // how many retellings it survives; default by importance
 *   "close_only": false,   // true: told only to the teller's family and friends
 *   "gentle": false,       // news, not gossip: never exaggerated, told even by the discreet, and only
 *                          // those close to whoever it's about feel anything when they hear it
 *   "exaggerates": true    // blabbers may make it bigger in the retelling
 * }
 * }</pre>
 * Kinds with no file follow the defaults: anything notable or more spreads, a little further the more
 * it matters.
 */
public final class GossipRules {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/gossip");

	/**
	 * @param reach how many retellings it survives (a witness is 0, so reach 2 means a friend of a friend
	 *              still hears it)
	 */
	public record Rule(boolean spreads, int reach, boolean closeOnly, boolean gentle, boolean exaggerates) {
	}

	private static volatile Map<Identifier, JsonObject> rules = Map.of();

	private GossipRules() {
	}

	/** The rule for a memory - its kind's file, with defaults filled in from how much it matters. */
	public static Rule rule(Memory memory) {
		JsonObject json = rules.get(memory.type());
		int defaultReach = switch (memory.importance()) {
			case TRIVIAL -> 0;
			case NOTABLE -> 1;
			case MAJOR -> 2;
			case INDELIBLE -> 3;
		};
		if (json == null) {
			return new Rule(defaultReach > 0, defaultReach, false, false, true);
		}
		boolean gentle = JsonHelper.getBoolean(json, "gentle", false);
		return new Rule(JsonHelper.getBoolean(json, "spreads", true), JsonHelper.getInt(json, "reach", Math.max(1, defaultReach)),
				JsonHelper.getBoolean(json, "close_only", false), gentle, JsonHelper.getBoolean(json, "exaggerates", !gentle));
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "gossip");
			}

			@Override
			public void reload(ResourceManager manager) {
				Map<Identifier, JsonObject> loaded = new HashMap<>();
				for (Map.Entry<Identifier, Resource> entry : manager.findResources("gossip", path -> path.getPath().endsWith(".json")).entrySet()) {
					String path = entry.getKey().getPath();
					Identifier type = Identifier.of(entry.getKey().getNamespace(), path.substring("gossip/".length(), path.length() - ".json".length()));
					try (BufferedReader reader = entry.getValue().getReader()) {
						loaded.put(type, JsonParser.parseReader(reader).getAsJsonObject());
					} catch (IOException | RuntimeException e) {
						LOGGER.error("data/{}/{}: couldn't read it: {}", entry.getKey().getNamespace(), path, e.getMessage());
					}
				}
				rules = Map.copyOf(loaded);
				LOGGER.info("Loaded {} gossip rule(s)", loaded.size());
			}
		});
	}
}
