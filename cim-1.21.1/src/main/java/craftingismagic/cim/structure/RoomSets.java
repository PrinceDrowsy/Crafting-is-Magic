package craftingismagic.cim.structure;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.random.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.block.RoomType;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.PersonalityAttachment;

/**
 * Furnished rooms, and who they suit (design doc: Village identity - interiors; docs/building-guide.md).
 * Each {@code data/<ns>/room_sets/*.json} names a built room structure and when it fits:
 * <pre>{@code
 * {
 *   "structure": "cim:rooms/bedroom/bookish_reading_nook",
 *   "room": "bedroom",                       // bedroom, kitchen, living_room, workroom, nursery
 *   "personalities": ["bookish"],            // every list optional; a set with a list only suits
 *   "professions": ["minecraft:librarian"],  //   residents who match it - a set with none suits anyone
 *   "quirks": ["collector"],
 *   "species": ["unicorn"],
 *   "foals": true,                           // optional: only for households with foals (or without: false)
 *   "walls": "light_blue",                   // optional: the wall paint color this set comes with (any dye color)
 *   "weight": 1
 * }
 * }</pre>
 * A set that suits the residents specifically is picked over a plain one, the more specifically the
 * likelier.
 */
public final class RoomSets {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/structures");

	/** @param walls the wall paint color this set comes with ("light_blue"), or null for one that suits the resident */
	public record RoomSet(Identifier structure, RoomType room, List<String> personalities, List<Identifier> professions, List<String> quirks,
			List<String> species, Boolean foals, int weight, String walls) {
		/** How many things it asks about the residents - a more specific fit counts for more. */
		int specificity() {
			return (personalities.isEmpty() ? 0 : 1) + (professions.isEmpty() ? 0 : 1) + (quirks.isEmpty() ? 0 : 1)
					+ (species.isEmpty() ? 0 : 1) + (foals == null ? 0 : 1);
		}

		boolean suits(Entity pony, boolean householdHasFoals) {
			if (foals != null && foals != householdHasFoals) return false;
			if (!personalities.isEmpty()) {
				if (!PersonalityAttachment.hasPersonality(pony)) return false;
				if (!personalities.contains(PersonalityAttachment.getPersonality(pony).baseType().name().toLowerCase(Locale.ROOT))) return false;
			}
			if (!quirks.isEmpty()) {
				if (!PersonalityAttachment.hasPersonality(pony)) return false;
				if (PersonalityAttachment.getPersonality(pony).quirks().stream()
						.noneMatch(quirk -> quirks.contains(quirk.name().toLowerCase(Locale.ROOT)))) return false;
			}
			if (!professions.isEmpty()) {
				if (!(pony instanceof VillagerEntity villager)) return false;
				if (!professions.contains(Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()))) return false;
			}
			if (!species.isEmpty()) {
				// A pony with no species recorded is an earth pony, as everywhere else.
				String theirs = SpeciesAttachment.hasChosenSpecies(pony) ? SpeciesAttachment.getSpecies(pony).name().toLowerCase(Locale.ROOT) : "earth_pony";
				if (!species.contains(theirs)) return false;
			}
			return true;
		}
	}

	private static volatile List<RoomSet> sets = List.of();

	private RoomSets() {
	}

	/** A set for this room that suits the resident and passes {@code fits} (it has to fit the room), if any. */
	public static RoomSet choose(RoomType room, Entity resident, boolean foals, Predicate<RoomSet> fits, Random random) {
		List<RoomSet> candidates = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (RoomSet set : sets) {
			if (set.room() != room || !set.suits(resident, foals) || !fits.test(set)) continue;
			int weight = set.weight() * (1 + 2 * set.specificity());
			candidates.add(set);
			weights.add(weight);
			total += weight;
		}
		if (total <= 0) return null;
		int roll = random.nextInt(total);
		for (int i = 0; i < candidates.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) return candidates.get(i);
		}
		return null;
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "room_sets");
			}

			@Override
			public void reload(ResourceManager manager) {
				List<RoomSet> loaded = new ArrayList<>();
				for (Map.Entry<Identifier, Resource> entry : manager.findResources("room_sets", path -> path.getPath().endsWith(".json")).entrySet()) {
					try (BufferedReader reader = entry.getValue().getReader()) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						loaded.add(new RoomSet(Identifier.of(JsonHelper.getString(json, "structure")), RoomType.of(JsonHelper.getString(json, "room")),
								words(json, "personalities"), ids(json, "professions"), words(json, "quirks"), words(json, "species"),
								json.has("foals") ? JsonHelper.getBoolean(json, "foals") : null, Math.max(1, JsonHelper.getInt(json, "weight", 1)),
								json.has("walls") ? JsonHelper.getString(json, "walls").toLowerCase(Locale.ROOT) : null));
					} catch (Exception e) {
						LOGGER.error("data/{}/{}: couldn't read it: {}", entry.getKey().getNamespace(), entry.getKey().getPath(), e.getMessage());
					}
				}
				sets = List.copyOf(loaded);
				LOGGER.info("Loaded {} room set(s)", loaded.size());
			}
		});
	}

	private static List<String> words(JsonObject json, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement word : JsonHelper.getArray(json, key, new JsonArray())) out.add(word.getAsString().toLowerCase(Locale.ROOT));
		return List.copyOf(out);
	}

	private static List<Identifier> ids(JsonObject json, String key) {
		List<Identifier> out = new ArrayList<>();
		for (JsonElement id : JsonHelper.getArray(json, key, new JsonArray())) out.add(Identifier.of(id.getAsString()));
		return List.copyOf(out);
	}
}
