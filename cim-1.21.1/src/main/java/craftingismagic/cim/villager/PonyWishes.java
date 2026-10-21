package craftingismagic.cim.villager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Uuids;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.Cim;
import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;

/**
 * Small wishes, and the thoughtful gifts that answer them (README: Friendship, deepened - "a gift that
 * answers something the pony mentioned counts the most: it shows you listened, so it builds Trust").
 * <ul>
 * <li>A pony mentions in chatter that they've been missing something - {@code ~ mention_wish} - picked
 * to fit them from {@code data/<ns>/wishes/*.json}.</li>
 * <li>Only the players who <i>heard</i> them say it can answer it - that's the point.</li>
 * <li>Answering it ({@code ~ grant_wish}) is a small shared moment: a happy memory (a bonding jump) and
 * some Trust besides. A wish lapses after a week - they sorted it out themselves.</li>
 * </ul>
 * A wish file: {@code {"item": "minecraft:string", "professions": ["minecraft:fisherman"],
 * "personalities": ["gentle"], "weight": 3}} - every field but {@code item} optional.
 */
public final class PonyWishes {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/wishes");
	private static final long LASTS = 7 * 24000L;
	private static final int LISTENED_TRUST = 6;

	/** A pony's current wish, and who heard them mention it. */
	public record Wish(Identifier item, long since, List<UUID> heardBy) {
		public static final Codec<Wish> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("item").forGetter(Wish::item),
				Codec.LONG.fieldOf("since").forGetter(Wish::since),
				Uuids.CODEC.listOf().optionalFieldOf("heard_by", List.of()).forGetter(Wish::heardBy)
		).apply(instance, Wish::new));
	}

	public static final AttachmentType<Wish> WISH = AttachmentRegistry.create(Identifier.of(Cim.MOD_ID, "wish"),
			builder -> builder.persistent(Wish.CODEC));

	private record Option(Identifier item, List<Identifier> professions, List<String> personalities, int weight) {
	}

	private static volatile List<Option> options = List.of();

	private PonyWishes() {
	}

	/** The wish this pony holds now, if it hasn't lapsed. */
	public static Optional<Wish> current(Entity pony) {
		Wish wish = pony.getAttached(WISH);
		if (wish == null || pony.getWorld().getTime() - wish.since() > LASTS) return Optional.empty();
		return Optional.of(wish);
	}

	/** Whether this pony has a wish to tell this player about - one they haven't heard, or a new one to make. */
	public static boolean toShare(Entity pony, ServerPlayerEntity player) {
		Optional<Wish> wish = current(pony);
		return wish.isPresent() ? !wish.get().heardBy().contains(player.getUuid()) : !fitting(pony).isEmpty();
	}

	/** The pony mentions their wish (making one, if they have none) - and this player has now heard it. */
	public static void mention(Entity pony, ServerPlayerEntity player) {
		Wish wish = current(pony).orElse(null);
		if (wish == null) {
			Option pick = pick(pony);
			if (pick == null) return;
			wish = new Wish(pick.item(), pony.getWorld().getTime(), List.of());
		}
		if (!wish.heardBy().contains(player.getUuid())) {
			List<UUID> heard = new ArrayList<>(wish.heardBy());
			heard.add(player.getUuid());
			wish = new Wish(wish.item(), wish.since(), List.copyOf(heard));
		}
		pony.setAttached(WISH, wish);
	}

	/** What they wish for, by name - "{wish}" in a line. */
	public static Optional<String> name(Entity pony) {
		return current(pony).map(wish -> Registries.ITEM.get(wish.item()).getName().getString().toLowerCase(java.util.Locale.ROOT));
	}

	/** The gift offered answers this pony's wish - and this player heard them make it. */
	public static boolean answers(Entity pony, ServerPlayerEntity player, ItemStack gift) {
		return !gift.isEmpty() && current(pony)
				.filter(wish -> wish.heardBy().contains(player.getUuid()) && Registries.ITEM.getId(gift.getItem()).equals(wish.item()))
				.isPresent();
	}

	/**
	 * The thoughtful gift: the pony takes it, remembers you listened (a happy shared moment), trusts you
	 * a little more for it, and the wish is answered.
	 */
	public static void grant(Entity pony, ServerPlayerEntity player, ItemStack gift) {
		if (!answers(pony, player, gift)) return;
		Identifier item = current(pony).get().item();
		gift.decrement(1);
		pony.removeAttached(WISH);
		MemoryStore.add(pony, new Memory(Identifier.of("cim", "thoughtful_gift/" + item.getNamespace() + "/" + item.getPath()),
				Optional.of(player.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.NOTABLE, Optional.of(Emotion.JOY),
				EmotionIntensity.CLEAR, player.getWorld().getTime()));
		FriendshipAttachment.addFeeling(pony, player.getUuid(), Feeling.TRUST, LISTENED_TRUST);
		FriendshipAttachment.addSharedMoments(pony, player.getUuid(), 3);
	}

	// ---- Picking a wish that fits ----

	private static List<Option> fitting(Entity pony) {
		Identifier profession = pony instanceof VillagerEntity villager
				? Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()) : null;
		String personality = PersonalityAttachment.hasPersonality(pony)
				? PersonalityAttachment.getPersonality(pony).baseType().name().toLowerCase(java.util.Locale.ROOT) : null;
		return options.stream()
				.filter(option -> option.professions().isEmpty() || option.professions().contains(profession))
				.filter(option -> option.personalities().isEmpty() || option.personalities().contains(personality))
				.toList();
	}

	private static Option pick(Entity pony) {
		List<Option> fitting = fitting(pony);
		int total = fitting.stream().mapToInt(Option::weight).sum();
		if (total <= 0) return null;
		int roll = pony.getRandom().nextInt(total);
		for (Option option : fitting) {
			roll -= option.weight();
			if (roll < 0) return option;
		}
		return null;
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("cim", "wishes");
			}

			@Override
			public void reload(ResourceManager manager) {
				List<Option> loaded = new ArrayList<>();
				for (Map.Entry<Identifier, Resource> entry : manager.findResources("wishes", path -> path.getPath().endsWith(".json")).entrySet()) {
					try (BufferedReader reader = entry.getValue().getReader()) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						Identifier item = Identifier.of(JsonHelper.getString(json, "item"));
						if (Registries.ITEM.get(item) == net.minecraft.item.Items.AIR) {
							LOGGER.error("data/{}/{}: no item called {}", entry.getKey().getNamespace(), entry.getKey().getPath(), item);
							continue;
						}
						List<Identifier> professions = new ArrayList<>();
						for (JsonElement p : JsonHelper.getArray(json, "professions", new com.google.gson.JsonArray())) {
							professions.add(Identifier.of(p.getAsString()));
						}
						List<String> personalities = new ArrayList<>();
						for (JsonElement p : JsonHelper.getArray(json, "personalities", new com.google.gson.JsonArray())) {
							personalities.add(p.getAsString().toLowerCase(java.util.Locale.ROOT));
						}
						loaded.add(new Option(item, List.copyOf(professions), List.copyOf(personalities), JsonHelper.getInt(json, "weight", 1)));
					} catch (IOException | RuntimeException e) {
						LOGGER.error("data/{}/{}: couldn't read it: {}", entry.getKey().getNamespace(), entry.getKey().getPath(), e.getMessage());
					}
				}
				options = List.copyOf(loaded);
				LOGGER.info("Loaded {} wish(es)", loaded.size());
			}
		});
	}
}
