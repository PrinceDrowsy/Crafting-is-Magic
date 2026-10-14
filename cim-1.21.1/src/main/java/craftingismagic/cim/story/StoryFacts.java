package craftingismagic.cim.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * Story facts - named flags that record what has happened (README: How threads work; story facts
 * as flags), at three scopes:
 * <ul>
 * <li><b>World</b> - saved with the world, shared by everyone (e.g. {@code cim:world/crystal_empire_restored});
 * see {@link WorldFactsState}.</li>
 * <li><b>Personal</b> - one player's own story (e.g. {@code cim:player/met_luna}); an attachment on the
 * player that survives death.</li>
 * <li><b>Relationship</b> - between one player and one pony (e.g. {@code cim:comforted_after_loss}); an
 * attachment on the pony, keyed by player, that survives curing.</li>
 * </ul>
 * Each fact remembers the world time it was set, for "last summer"-style lines. IDs are namespaced so
 * datapacks and other mods can add their own. Never synced: what a client needs to know about the
 * story arrives in the payloads that need it.
 */
public final class StoryFacts {
	private static final Codec<Map<Identifier, Long>> FACT_MAP_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.LONG);

	public static final AttachmentType<Map<Identifier, Long>> PERSONAL_FACTS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "personal_facts"),
			builder -> builder.persistent(FACT_MAP_CODEC).copyOnDeath());

	public static final AttachmentType<Map<UUID, Map<Identifier, Long>>> RELATIONSHIP_FACTS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "relationship_facts"),
			builder -> builder.persistent(Codec.unboundedMap(Uuids.STRING_CODEC, FACT_MAP_CODEC)).copyOnDeath());

	private StoryFacts() {
	}

	// ---- World ----

	public static boolean hasWorld(MinecraftServer server, Identifier fact) {
		return WorldFactsState.get(server).has(fact);
	}

	public static void setWorld(MinecraftServer server, Identifier fact) {
		WorldFactsState.get(server).set(fact, server.getOverworld().getTime());
	}

	public static boolean clearWorld(MinecraftServer server, Identifier fact) {
		return WorldFactsState.get(server).clear(fact);
	}

	public static Map<Identifier, Long> worldFacts(MinecraftServer server) {
		return WorldFactsState.get(server).all();
	}

	// ---- Personal ----

	public static boolean hasPersonal(Entity player, Identifier fact) {
		return personalFacts(player).containsKey(fact);
	}

	public static void setPersonal(Entity player, Identifier fact) {
		Map<Identifier, Long> facts = new HashMap<>(personalFacts(player));
		facts.put(fact, player.getWorld().getTime());
		player.setAttached(PERSONAL_FACTS, Map.copyOf(facts));
	}

	public static boolean clearPersonal(Entity player, Identifier fact) {
		Map<Identifier, Long> facts = new HashMap<>(personalFacts(player));
		boolean removed = facts.remove(fact) != null;
		if (removed) {
			player.setAttached(PERSONAL_FACTS, Map.copyOf(facts));
		}
		return removed;
	}

	public static Map<Identifier, Long> personalFacts(Entity player) {
		return player.hasAttached(PERSONAL_FACTS) ? player.getAttached(PERSONAL_FACTS) : Map.of();
	}

	// ---- Relationship ----

	public static boolean hasRelationship(Entity pony, UUID player, Identifier fact) {
		return relationshipFacts(pony, player).containsKey(fact);
	}

	public static void setRelationship(Entity pony, UUID player, Identifier fact) {
		Map<Identifier, Long> facts = new HashMap<>(relationshipFacts(pony, player));
		facts.put(fact, pony.getWorld().getTime());
		putRelationshipFacts(pony, player, facts);
	}

	public static boolean clearRelationship(Entity pony, UUID player, Identifier fact) {
		Map<Identifier, Long> facts = new HashMap<>(relationshipFacts(pony, player));
		boolean removed = facts.remove(fact) != null;
		if (removed) {
			putRelationshipFacts(pony, player, facts);
		}
		return removed;
	}

	public static Map<Identifier, Long> relationshipFacts(Entity pony, UUID player) {
		if (!pony.hasAttached(RELATIONSHIP_FACTS)) {
			return Map.of();
		}
		return pony.getAttached(RELATIONSHIP_FACTS).getOrDefault(player, Map.of());
	}

	private static void putRelationshipFacts(Entity pony, UUID player, Map<Identifier, Long> facts) {
		Map<UUID, Map<Identifier, Long>> all = pony.hasAttached(RELATIONSHIP_FACTS)
				? new HashMap<>(pony.getAttached(RELATIONSHIP_FACTS))
				: new HashMap<>();
		if (facts.isEmpty()) {
			all.remove(player);
		} else {
			all.put(player, Map.copyOf(facts));
		}
		pony.setAttached(RELATIONSHIP_FACTS, Map.copyOf(all));
	}
}
