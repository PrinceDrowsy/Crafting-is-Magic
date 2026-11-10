package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * Per-player relationship state with an entity - the three {@link Feeling}s, gift-cooldown tracking,
 * and {@link RelationshipType} - typed to {@code Entity} rather than {@code VillagerEntity} so
 * {@code RoguePonyEntity} can reuse the exact same relationship-type tracking (specifically
 * {@code ENEMY}/{@code FRENEMIES}) despite not being a villager. Fabric attachments are
 * one-value-per-entity, so the per-player nature lives in the attachment's own value type - a map
 * keyed by player UUID.
 * <p>
 * Server-only - deliberately never synced. It used to sync the whole map to every tracking player
 * on every change, which cost bandwidth and leaked one player's relationship data (and, with the
 * memory system coming, their secrets) onto other players' clients. Screens that need it get what
 * they need in their own open payloads instead.
 */
public final class FriendshipAttachment {
	/**
	 * A polite ceiling, not the limiter: gifts already taper within a day (see {@link FriendshipPace}),
	 * so this is only where a pony says "you've already given me so much today!".
	 */
	private static final int MAX_GIFTS_PER_DAY = 6;

	public static final AttachmentType<Map<UUID, RelationshipData>> AFFECTION = AttachmentRegistry.create(
			// The id predates the three feelings - kept as "affection" so existing saves still load.
			Identifier.of(Cim.MOD_ID, "affection"),
			builder -> builder.persistent(Codec.unboundedMap(Uuids.STRING_CODEC, RelationshipData.CODEC))
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath());

	private FriendshipAttachment() {
	}

	public static RelationshipData get(Entity entity, UUID player) {
		if (!entity.hasAttached(AFFECTION)) {
			return RelationshipData.EMPTY;
		}
		return entity.getAttached(AFFECTION).getOrDefault(player, RelationshipData.EMPTY);
	}

	public static int getFeeling(Entity entity, UUID player, Feeling feeling) {
		return get(entity, player).feeling(feeling);
	}

	public static FeelingBand getBand(Entity entity, UUID player, Feeling feeling) {
		return FeelingBand.of(getFeeling(entity, player, feeling));
	}

	public static void addFeeling(Entity entity, UUID player, Feeling feeling, int delta) {
		RelationshipData data = get(entity, player);
		set(entity, player, data.withFeeling(feeling, data.feeling(feeling) + delta));
	}

	/** Sets a feeling to an absolute value rather than a delta - only meant for debug tools; normal
	 * gameplay always goes through {@link #addFeeling}. */
	public static void setFeeling(Entity entity, UUID player, Feeling feeling, int value) {
		set(entity, player, get(entity, player).withFeeling(feeling, value));
	}

	public static RelationshipType getRelationshipType(Entity entity, UUID player) {
		return get(entity, player).relationshipType();
	}

	/** Not yet set by any real gameplay trigger for a villager - see {@link RelationshipType}'s own
	 * docs. {@code RoguePonyEntity} is the one caller that DOES set this for real, outside the
	 * Creative-only debug editor. */
	public static void setRelationshipType(Entity entity, UUID player, RelationshipType relationshipType) {
		RelationshipData data = get(entity, player);
		set(entity, player, new RelationshipData(data.fondness(), data.respect(), data.trust(),
				data.lastGiftDay(), data.giftsGivenThatDay(), relationshipType, data.lastTalked(), data.sharedMoments(),
				data.habitDay(), data.habitsToday(), data.daysVisited()));
	}

	/**
	 * Counts one everyday habit (a chat's warmth, a gift) on {@code day} and returns how many came
	 * before it that day - what {@link FriendshipPace} tapers by.
	 */
	public static int takeHabit(Entity entity, UUID player, long day) {
		RelationshipData data = get(entity, player);
		int before = data.habitDay() == day ? data.habitsToday() : 0;
		set(entity, player, new RelationshipData(data.fondness(), data.respect(), data.trust(), data.lastGiftDay(), data.giftsGivenThatDay(),
				data.relationshipType(), data.lastTalked(), data.sharedMoments(), day, before + 1, data.daysVisited()));
		return before;
	}

	/** One more different day the player came to see this pony; returns the new count. */
	public static int addVisitDay(Entity entity, UUID player) {
		RelationshipData data = get(entity, player);
		set(entity, player, new RelationshipData(data.fondness(), data.respect(), data.trust(), data.lastGiftDay(), data.giftsGivenThatDay(),
				data.relationshipType(), data.lastTalked(), data.sharedMoments(), data.habitDay(), data.habitsToday(), data.daysVisited() + 1));
		return data.daysVisited() + 1;
	}

	/** When this pony last talked with the player (world time in ticks), or -1 if they never have. */
	public static long getLastTalked(Entity entity, UUID player) {
		return get(entity, player).lastTalked();
	}

	public static void recordTalk(Entity entity, UUID player, long worldTime) {
		RelationshipData data = get(entity, player);
		set(entity, player, new RelationshipData(data.fondness(), data.respect(), data.trust(),
				data.lastGiftDay(), data.giftsGivenThatDay(), data.relationshipType(), worldTime, data.sharedMoments(),
				data.habitDay(), data.habitsToday(), data.daysVisited()));
	}

	/**
	 * The player's shared history with this pony - conversations had, gifts given - that a verse reads
	 * as the caster's own love for them ("my love", design doc: Verse). It's what the player has invested,
	 * never the pony's feelings.
	 */
	public static int getSharedMoments(Entity entity, UUID player) {
		return get(entity, player).sharedMoments();
	}

	public static void addSharedMoments(Entity entity, UUID player, int moments) {
		RelationshipData data = get(entity, player);
		set(entity, player, new RelationshipData(data.fondness(), data.respect(), data.trust(),
				data.lastGiftDay(), data.giftsGivenThatDay(), data.relationshipType(), data.lastTalked(), data.sharedMoments() + moments,
				data.habitDay(), data.habitsToday(), data.daysVisited()));
	}

	public static boolean canGiftToday(Entity entity, UUID player, long currentDay) {
		RelationshipData data = get(entity, player);
		return data.lastGiftDay() != currentDay || data.giftsGivenThatDay() < MAX_GIFTS_PER_DAY;
	}

	public static void recordGift(Entity entity, UUID player, long currentDay) {
		RelationshipData data = get(entity, player);
		int giftsToday = data.lastGiftDay() == currentDay ? data.giftsGivenThatDay() + 1 : 1;
		set(entity, player, new RelationshipData(data.fondness(), data.respect(), data.trust(),
				currentDay, giftsToday, data.relationshipType(), data.lastTalked(), data.sharedMoments(),
				data.habitDay(), data.habitsToday(), data.daysVisited()));
	}

	private static void set(Entity entity, UUID player, RelationshipData data) {
		Map<UUID, RelationshipData> relationships = entity.hasAttached(AFFECTION)
				? new HashMap<>(entity.getAttached(AFFECTION))
				: new HashMap<>();
		relationships.put(player, data);
		entity.setAttached(AFFECTION, relationships);
	}

	/**
	 * @param lastTalked    world time of their last conversation, -1 for never (greetings read it)
	 * @param sharedMoments the player's shared history with this pony: +1 a conversation, +3 a gift
	 * @param habitDay      the day {@code habitsToday} counts (see {@link FriendshipPace})
	 * @param habitsToday   everyday habits already counted that day - later ones taper
	 * @param daysVisited   how many different days the player has come to see them (consistency builds Trust)
	 */
	public record RelationshipData(int fondness, int respect, int trust, long lastGiftDay, int giftsGivenThatDay,
			RelationshipType relationshipType, long lastTalked, int sharedMoments, long habitDay, int habitsToday, int daysVisited) {
		public static final RelationshipData EMPTY = new RelationshipData(0, 0, 0, -1, 0, RelationshipType.ACQUAINTANCE, -1, 0, -1, 0, 0);

		/**
		 * Migrates old saves: before the three feelings, this stored a single {@code affection} value.
		 * A record with no {@code fondness} key but an {@code affection} key loads that affection as
		 * Fondness - the closest of the three to what affection meant - and Respect and Trust start at
		 * 0. New saves only ever write {@code fondness}. Every field is {@code optionalFieldOf} so a
		 * missing key never fails the whole record's decode (see [[project_fabric_attachment_gotchas]]).
		 */
		public static final Codec<RelationshipData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("fondness").forGetter(data -> Optional.of(data.fondness())),
				Codec.INT.optionalFieldOf("affection").forGetter(data -> Optional.empty()),
				Codec.INT.optionalFieldOf("respect", 0).forGetter(RelationshipData::respect),
				Codec.INT.optionalFieldOf("trust", 0).forGetter(RelationshipData::trust),
				Codec.LONG.optionalFieldOf("last_gift_day", -1L).forGetter(RelationshipData::lastGiftDay),
				Codec.INT.optionalFieldOf("gifts_given_that_day", 0).forGetter(RelationshipData::giftsGivenThatDay),
				RelationshipType.CODEC.optionalFieldOf("relationship_type", RelationshipType.ACQUAINTANCE).forGetter(RelationshipData::relationshipType),
				Codec.LONG.optionalFieldOf("last_talked", -1L).forGetter(RelationshipData::lastTalked),
				Codec.INT.optionalFieldOf("shared_moments", 0).forGetter(RelationshipData::sharedMoments),
				Codec.LONG.optionalFieldOf("habit_day", -1L).forGetter(RelationshipData::habitDay),
				Codec.INT.optionalFieldOf("habits_today", 0).forGetter(RelationshipData::habitsToday),
				Codec.INT.optionalFieldOf("days_visited", 0).forGetter(RelationshipData::daysVisited)
		).apply(instance, (fondness, legacyAffection, respect, trust, lastGiftDay, giftsGivenThatDay, relationshipType, lastTalked, sharedMoments,
				habitDay, habitsToday, daysVisited) ->
				new RelationshipData(fondness.orElse(legacyAffection.orElse(0)), respect, trust, lastGiftDay, giftsGivenThatDay, relationshipType,
						lastTalked, sharedMoments, habitDay, habitsToday, daysVisited)));

		public int feeling(Feeling feeling) {
			return switch (feeling) {
				case FONDNESS -> fondness;
				case RESPECT -> respect;
				case TRUST -> trust;
			};
		}

		public RelationshipData withFeeling(Feeling feeling, int value) {
			return switch (feeling) {
				case FONDNESS -> new RelationshipData(value, respect, trust, lastGiftDay, giftsGivenThatDay, relationshipType, lastTalked, sharedMoments,
						habitDay, habitsToday, daysVisited);
				case RESPECT -> new RelationshipData(fondness, value, trust, lastGiftDay, giftsGivenThatDay, relationshipType, lastTalked, sharedMoments,
						habitDay, habitsToday, daysVisited);
				case TRUST -> new RelationshipData(fondness, respect, value, lastGiftDay, giftsGivenThatDay, relationshipType, lastTalked, sharedMoments,
						habitDay, habitsToday, daysVisited);
			};
		}
	}
}
