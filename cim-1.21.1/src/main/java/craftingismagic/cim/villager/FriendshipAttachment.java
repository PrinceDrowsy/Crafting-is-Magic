package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
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
 * Per-player relationship state with an entity - affection, gift-cooldown tracking, and
 * {@link RelationshipType} - typed to {@code Entity} rather than {@code VillagerEntity} so
 * {@code RoguePonyEntity} can reuse the exact same relationship-type tracking (specifically
 * {@code ENEMY}/{@code FRENEMIES}) despite not being a villager; affection/gifting stay
 * villager-flavored in practice, but nothing here stops a hostile mob from using them too. Fabric
 * attachments are one-value-per-entity, so unlike
 * {@link craftingismagic.cim.species.SpeciesAttachment} (a single value shared by everyone), the
 * per-player nature here lives in the attachment's own value type - a map keyed by player UUID,
 * same overall shape otherwise.
 * <p>
 * Server-only - deliberately never synced. It used to sync the whole map to every tracking player
 * on every change, which cost bandwidth and leaked one player's relationship data (and, with the
 * memory system coming, their secrets) onto other players' clients. Screens that need it get what
 * they need in their own open payloads instead (see {@code OpenVillagerMenuS2CPayload}).
 */
public final class FriendshipAttachment {
	private static final int MAX_GIFTS_PER_DAY = 3;

	public static final AttachmentType<Map<UUID, RelationshipData>> AFFECTION = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "affection"),
			builder -> builder.persistent(Codec.unboundedMap(Uuids.STRING_CODEC, RelationshipData.CODEC))
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath());

	private FriendshipAttachment() {
	}

	public static int getAffection(Entity entity, UUID player) {
		return getData(entity, player).affection();
	}

	public static void addAffection(Entity entity, UUID player, int delta) {
		RelationshipData data = getData(entity, player);
		setData(entity, player, new RelationshipData(data.affection() + delta, data.lastGiftDay(), data.giftsGivenThatDay(), data.relationshipType()));
	}

	/** Sets affection to an absolute value rather than a delta - only meant for the Creative-only
	 * villager debug editor ({@code VillagerDebugEditorHandler}); normal gameplay always goes
	 * through {@link #addAffection}. */
	public static void setAffection(Entity entity, UUID player, int affection) {
		RelationshipData data = getData(entity, player);
		setData(entity, player, new RelationshipData(affection, data.lastGiftDay(), data.giftsGivenThatDay(), data.relationshipType()));
	}

	public static RelationshipType getRelationshipType(Entity entity, UUID player) {
		return getData(entity, player).relationshipType();
	}

	/** Not yet set by any real gameplay trigger for a villager - see {@link RelationshipType}'s own
	 * docs. {@code RoguePonyEntity} is the one caller that DOES set this for real, outside the
	 * Creative-only debug editor. */
	public static void setRelationshipType(Entity entity, UUID player, RelationshipType relationshipType) {
		RelationshipData data = getData(entity, player);
		setData(entity, player, new RelationshipData(data.affection(), data.lastGiftDay(), data.giftsGivenThatDay(), relationshipType));
	}

	public static boolean canGiftToday(Entity entity, UUID player, long currentDay) {
		RelationshipData data = getData(entity, player);
		return data.lastGiftDay() != currentDay || data.giftsGivenThatDay() < MAX_GIFTS_PER_DAY;
	}

	public static void recordGift(Entity entity, UUID player, long currentDay) {
		RelationshipData data = getData(entity, player);
		int giftsToday = data.lastGiftDay() == currentDay ? data.giftsGivenThatDay() + 1 : 1;
		setData(entity, player, new RelationshipData(data.affection(), currentDay, giftsToday, data.relationshipType()));
	}

	private static RelationshipData getData(Entity entity, UUID player) {
		if (!entity.hasAttached(AFFECTION)) {
			return RelationshipData.EMPTY;
		}
		return entity.getAttached(AFFECTION).getOrDefault(player, RelationshipData.EMPTY);
	}

	private static void setData(Entity entity, UUID player, RelationshipData data) {
		Map<UUID, RelationshipData> relationships = entity.hasAttached(AFFECTION)
				? new HashMap<>(entity.getAttached(AFFECTION))
				: new HashMap<>();
		relationships.put(player, data);
		entity.setAttached(AFFECTION, relationships);
	}

	public record RelationshipData(int affection, long lastGiftDay, int giftsGivenThatDay, RelationshipType relationshipType) {
		public static final RelationshipData EMPTY = new RelationshipData(0, -1, 0, RelationshipType.ACQUAINTANCE);

		/** {@code relationship_type} uses {@code optionalFieldOf} with a default, not {@code
		 * fieldOf} - relationship data already saved to disk before this field existed is missing
		 * that key entirely, and a mandatory field would fail the whole record's decode, silently
		 * dropping a player's real affection/gift history (see [[project_fabric_attachment_gotchas]] -
		 * the same bug already hit once this session with the genetics tail fields). */
		public static final Codec<RelationshipData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("affection").forGetter(RelationshipData::affection),
				Codec.LONG.fieldOf("last_gift_day").forGetter(RelationshipData::lastGiftDay),
				Codec.INT.fieldOf("gifts_given_that_day").forGetter(RelationshipData::giftsGivenThatDay),
				RelationshipType.CODEC.optionalFieldOf("relationship_type", RelationshipType.ACQUAINTANCE).forGetter(RelationshipData::relationshipType)
		).apply(instance, RelationshipData::new));
	}
}
