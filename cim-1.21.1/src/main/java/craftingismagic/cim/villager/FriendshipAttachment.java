package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * Per-player relationship state with a villager - affection plus gift-cooldown tracking. Fabric
 * attachments are one-value-per-entity, so unlike {@link craftingismagic.cim.species.SpeciesAttachment}
 * (a single value shared by everyone), the per-player nature here lives in the attachment's own
 * value type - a map keyed by player UUID, same overall shape otherwise.
 */
public final class FriendshipAttachment {
	private static final int MAX_GIFTS_PER_DAY = 3;

	/** Same reasoning as {@code SpeciesAttachment}'s identical predicate: avoids syncing to a
	 * player who hasn't started tracking the villager yet, which crashes Fabric's own sync handler. */
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<Map<UUID, RelationshipData>> AFFECTION = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "affection"),
			builder -> builder.persistent(Codec.unboundedMap(Uuids.STRING_CODEC, RelationshipData.CODEC))
					.syncWith(PacketCodecs.map(HashMap::new, Uuids.PACKET_CODEC, RelationshipData.PACKET_CODEC), SYNC_TO_TRACKERS));

	private FriendshipAttachment() {
	}

	public static int getAffection(VillagerEntity villager, UUID player) {
		return getData(villager, player).affection();
	}

	public static void addAffection(VillagerEntity villager, UUID player, int delta) {
		RelationshipData data = getData(villager, player);
		setData(villager, player, new RelationshipData(data.affection() + delta, data.lastGiftDay(), data.giftsGivenThatDay()));
	}

	public static boolean canGiftToday(VillagerEntity villager, UUID player, long currentDay) {
		RelationshipData data = getData(villager, player);
		return data.lastGiftDay() != currentDay || data.giftsGivenThatDay() < MAX_GIFTS_PER_DAY;
	}

	public static void recordGift(VillagerEntity villager, UUID player, long currentDay) {
		RelationshipData data = getData(villager, player);
		int giftsToday = data.lastGiftDay() == currentDay ? data.giftsGivenThatDay() + 1 : 1;
		setData(villager, player, new RelationshipData(data.affection(), currentDay, giftsToday));
	}

	private static RelationshipData getData(VillagerEntity villager, UUID player) {
		if (!villager.hasAttached(AFFECTION)) {
			return RelationshipData.EMPTY;
		}
		return villager.getAttached(AFFECTION).getOrDefault(player, RelationshipData.EMPTY);
	}

	private static void setData(VillagerEntity villager, UUID player, RelationshipData data) {
		Map<UUID, RelationshipData> relationships = villager.hasAttached(AFFECTION)
				? new HashMap<>(villager.getAttached(AFFECTION))
				: new HashMap<>();
		relationships.put(player, data);
		villager.setAttached(AFFECTION, relationships);
	}

	public record RelationshipData(int affection, long lastGiftDay, int giftsGivenThatDay) {
		public static final RelationshipData EMPTY = new RelationshipData(0, -1, 0);

		public static final Codec<RelationshipData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("affection").forGetter(RelationshipData::affection),
				Codec.LONG.fieldOf("last_gift_day").forGetter(RelationshipData::lastGiftDay),
				Codec.INT.fieldOf("gifts_given_that_day").forGetter(RelationshipData::giftsGivenThatDay)
		).apply(instance, RelationshipData::new));

		public static final PacketCodec<ByteBuf, RelationshipData> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, RelationshipData::affection,
				PacketCodecs.VAR_LONG, RelationshipData::lastGiftDay,
				PacketCodecs.INTEGER, RelationshipData::giftsGivenThatDay,
				RelationshipData::new);
	}
}
