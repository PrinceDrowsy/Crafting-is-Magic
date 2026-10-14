package craftingismagic.cim.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;
import craftingismagic.cim.debug.DebugOverlay;
import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.memory.Mood;
import craftingismagic.cim.network.MomentS2CPayload;

/**
 * News a player hasn't heard yet (decided 2026-10-14): you only feel what you know. A friend dying
 * out of sight - or later, a birth or a wedding - waits here until someone tells you, and only then
 * do the feeling and its moment arrive.
 * <ul>
 * <li><b>Who can tell you:</b> the ponies close to the one it happened to ({@link News#messengers});
 * if nobody but you was close to them, any pony can ({@link News#anyone}). It's told once - then
 * it's gone, and nobody repeats it.</li>
 * <li><b>The letter:</b> if it's still unheard after {@link #LETTER_AFTER} and you're away from any
 * village, a letter finds you, and that's when you learn.</li>
 * </ul>
 * Stories deliver it with {@code news(...)}, {@code {news_subject}}, and {@code ~ tell_news}.
 */
public final class PlayerNews {
	public static final long LETTER_AFTER = 3 * Mood.DAY;
	/** No pony this close means you're away from villages. */
	private static final double VILLAGE_RANGE = 64.0;
	private static final int CHECK_INTERVAL = 1200;

	/**
	 * One piece of unheard news.
	 *
	 * @param type        the memory it becomes (e.g. {@code cim:loss})
	 * @param subject     who it happened to
	 * @param subjectName their name, for {@code {news_subject}} and the letter
	 * @param moment      the scene-setting moment it plays when you learn it, if any
	 * @param messengers  ponies who may tell you
	 * @param anyone      any pony may tell you (nobody else was close)
	 * @param since       when it happened
	 */
	public record News(Identifier type, UUID subject, String subjectName, Optional<Emotion> emotion, EmotionIntensity intensity,
			Optional<Identifier> moment, List<UUID> messengers, boolean anyone, long since) {
		public static final Codec<News> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("type").forGetter(News::type),
				Uuids.CODEC.fieldOf("subject").forGetter(News::subject),
				Codec.STRING.fieldOf("subject_name").forGetter(News::subjectName),
				Emotion.CODEC.optionalFieldOf("emotion").forGetter(News::emotion),
				EmotionIntensity.CODEC.optionalFieldOf("intensity", EmotionIntensity.CLEAR).forGetter(News::intensity),
				Identifier.CODEC.optionalFieldOf("moment").forGetter(News::moment),
				Uuids.CODEC.listOf().optionalFieldOf("messengers", List.of()).forGetter(News::messengers),
				Codec.BOOL.optionalFieldOf("anyone", false).forGetter(News::anyone),
				Codec.LONG.fieldOf("since").forGetter(News::since))
				.apply(instance, News::new));

		public boolean tellableBy(Entity pony) {
			return anyone || messengers.contains(pony.getUuid());
		}
	}

	public static final AttachmentType<List<News>> PENDING = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "pending_news"),
			builder -> builder.persistent(News.CODEC.listOf()).copyOnDeath());

	/**
	 * Names of those the player has memories about, kept when the news arrives - someone who has
	 * died can't be looked up any more, but lines like {@code {mourning}} still need their name.
	 */
	public static final AttachmentType<Map<UUID, String>> NAMES = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "remembered_names"),
			builder -> builder.persistent(Codec.unboundedMap(Uuids.STRING_CODEC, Codec.STRING)).copyOnDeath());

	public static Optional<String> rememberedName(Entity player, UUID who) {
		return Optional.ofNullable(player.hasAttached(NAMES) ? player.getAttached(NAMES).get(who) : null);
	}

	private PlayerNews() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PlayerNews::tick);
		DebugOverlay.addProvider((player, lines) -> {
			List<News> pending = pending(player);
			if (!pending.isEmpty()) {
				lines.add("Unheard news: " + pending.size() + " (" + pending.get(0).type() + " - " + pending.get(0).subjectName() + ")");
			}
		});
	}

	public static List<News> pending(Entity player) {
		return player.hasAttached(PENDING) ? player.getAttached(PENDING) : List.of();
	}

	/**
	 * Whether the player already knows this - they hold the memory of it. News you already know isn't
	 * news: it's never queued, and never told or posted to you again.
	 */
	public static boolean knows(Entity player, Identifier type, UUID subject) {
		return MemoryStore.getAll(player).stream()
				.anyMatch(memory -> memory.type().equals(type) && memory.subject().filter(subject::equals).isPresent());
	}

	/** Holds {@code news} until someone tells this player - unless they already know it. */
	public static void hold(ServerPlayerEntity player, News news) {
		if (knows(player, news.type(), news.subject())) {
			return;
		}
		List<News> all = new ArrayList<>(pending(player));
		all.removeIf(old -> old.type().equals(news.type()) && old.subject().equals(news.subject()));
		all.add(news);
		player.setAttached(PENDING, List.copyOf(all));
	}

	/** The first news this pony could tell the player, if any - optionally only of {@code type}. */
	public static Optional<News> tellable(ServerPlayerEntity player, Entity pony, Optional<Identifier> type) {
		forgetKnown(player);
		return pending(player).stream()
				.filter(news -> type.isEmpty() || news.type().equals(type.get()))
				.filter(news -> news.tellableBy(pony) && !news.subject().equals(pony.getUuid()))
				.findFirst();
	}

	/** Drops pending news the player has since come to know some other way. */
	private static void forgetKnown(ServerPlayerEntity player) {
		List<News> all = pending(player);
		if (all.stream().anyMatch(news -> knows(player, news.type(), news.subject()))) {
			player.setAttached(PENDING, all.stream().filter(news -> !knows(player, news.type(), news.subject())).toList());
		}
	}

	/** The player learns {@code news} now - heard, not seen. Already known: it's just dropped. */
	public static void learn(ServerPlayerEntity player, News news) {
		List<News> all = new ArrayList<>(pending(player));
		all.remove(news);
		player.setAttached(PENDING, List.copyOf(all));
		if (knows(player, news.type(), news.subject())) {
			return;
		}
		feel(player, news.type(), news.subject(), news.subjectName(), MemoryKnowledge.HEARD, news.emotion(), news.intensity(), news.moment());
	}

	/** The feeling arrives: a memory about who it happened to (and their name), and the moment, if any. */
	public static void feel(ServerPlayerEntity player, Identifier type, UUID subject, String subjectName, MemoryKnowledge knowledge,
			Optional<Emotion> emotion, EmotionIntensity intensity, Optional<Identifier> moment) {
		Map<UUID, String> names = new HashMap<>(player.hasAttached(NAMES) ? player.getAttached(NAMES) : Map.of());
		names.put(subject, subjectName);
		player.setAttached(NAMES, Map.copyOf(names));
		MemoryStore.add(player, new Memory(type, Optional.of(subject), knowledge, MemoryImportance.MAJOR, emotion, intensity,
				player.getWorld().getTime()));
		moment.ifPresent(id -> ServerPlayNetworking.send(player, new MomentS2CPayload(id)));
	}

	public static int clear(ServerPlayerEntity player) {
		int count = pending(player).size();
		player.setAttached(PENDING, List.of());
		return count;
	}

	// ---- The letter ----

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % CHECK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
			forgetKnown(player);
			long now = player.getWorld().getTime();
			for (News news : pending(player)) {
				if (now - news.since() >= LETTER_AFTER && awayFromVillages(player)) {
					sendLetter(player, news);
					break; // one letter at a time
				}
			}
		}
	}

	private static boolean awayFromVillages(ServerPlayerEntity player) {
		return player.getServerWorld().getEntitiesByClass(VillagerEntity.class, player.getBoundingBox().expand(VILLAGE_RANGE), pony -> true)
				.isEmpty();
	}

	/** Word reaches you anyway: a letter in your pack, and the feeling with it. Placeholder words. */
	public static void sendLetter(ServerPlayerEntity player, News news) {
		if (knows(player, news.type(), news.subject())) {
			learn(player, news); // already known: just clears it, no letter
			return;
		}
		String body = news.type().equals(Identifier.of("cim", "loss"))
				? "I'm sorry to be the one to write this.\n\n" + news.subjectName() + " has passed away.\n\nCome home when you can."
				: "There's news about " + news.subjectName() + ".\n\nCome home when you can.";
		ItemStack letter = new ItemStack(Items.WRITTEN_BOOK);
		letter.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of("A letter"), "",
				0, List.of(RawFilteredPair.of(Text.literal(body))), true));
		if (!player.getInventory().insertStack(letter)) {
			player.dropItem(letter, false);
		}
		player.sendMessage(Text.translatable("message.cim.letter_arrived"), true);
		learn(player, news);
	}
}
