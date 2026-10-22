package craftingismagic.cim.structure;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.mixin.StructurePoolAccessor;

/**
 * The mod's buildings, generated as part of villages (README: Village identity; docs/building-guide.md).
 * Each {@code data/<ns>/village_additions/*.json} adds one structure to one of vanilla's village pools:
 * <pre>{@code
 * {
 *   "pool": "minecraft:village/plains/houses",
 *   "structure": "cim:village/plains/houses/pony_cottage_1",
 *   "weight": 4,                                  // optional, default 1 (vanilla houses are 1-10)
 *   "processors": "minecraft:mossify_10_percent", // optional, default none
 *   "projection": "rigid"                         // optional: rigid, or terrain_matching for paths
 * }
 * }</pre>
 * Applied once when a world starts - village pools don't reload with {@code /reload}, so neither do
 * these. Datapacks and other mods can add their own the same way.
 */
public final class VillageAdditions {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/structures");

	private VillageAdditions() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(VillageAdditions::apply);
	}

	private static void apply(MinecraftServer server) {
		Registry<StructurePool> pools = server.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);
		Registry<StructureProcessorList> processors = server.getRegistryManager().get(RegistryKeys.PROCESSOR_LIST);
		int added = 0;
		for (Map.Entry<Identifier, Resource> entry : server.getResourceManager()
				.findResources("village_additions", path -> path.getPath().endsWith(".json")).entrySet()) {
			String where = "data/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath();
			try (BufferedReader reader = entry.getValue().getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				Identifier poolId = Identifier.of(JsonHelper.getString(json, "pool"));
				StructurePool pool = pools.get(poolId);
				if (pool == null) {
					LOGGER.error("{}: there's no village pool called {}", where, poolId);
					continue;
				}
				Identifier processorId = Identifier.of(JsonHelper.getString(json, "processors", "minecraft:empty"));
				Optional<RegistryEntry.Reference<StructureProcessorList>> processorList =
						processors.getEntry(RegistryKey.of(RegistryKeys.PROCESSOR_LIST, processorId));
				if (processorList.isEmpty()) {
					LOGGER.error("{}: there's no processor list called {}", where, processorId);
					continue;
				}
				StructurePool.Projection projection = JsonHelper.getString(json, "projection", "rigid").equals("terrain_matching")
						? StructurePool.Projection.TERRAIN_MATCHING : StructurePool.Projection.RIGID;
				StructurePoolElement element = StructurePoolElement.ofProcessedSingle(JsonHelper.getString(json, "structure"), processorList.get())
						.apply(projection);
				add(pool, element, Math.max(1, JsonHelper.getInt(json, "weight", 1)));
				added++;
			} catch (Exception e) {
				LOGGER.error("{}: couldn't add it: {}", where, e.getMessage());
			}
		}
		LOGGER.info("Added {} building(s) to village pools", added);
	}

	/** The same way vanilla holds a weighted element: listed once per weight, and counted. */
	private static void add(StructurePool pool, StructurePoolElement element, int weight) {
		StructurePoolAccessor access = (StructurePoolAccessor) pool;
		for (int i = 0; i < weight; i++) {
			access.cim$elements().add(element);
		}
		List<Pair<StructurePoolElement, Integer>> counts = new ArrayList<>(access.cim$elementCounts());
		counts.add(Pair.of(element, weight));
		access.cim$setElementCounts(counts);
	}
}
