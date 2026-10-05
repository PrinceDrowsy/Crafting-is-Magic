package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * A villager's friend circle (see Friend groups in the README) - a decentralized peer list, not a
 * central group-id/registry, matching how the rest of this codebase always models NPC-to-NPC links
 * (see {@link PonyFamilyAttachment}'s own doc comment on why villages have no "this batch belongs
 * together" signal to hang a group id off of). {@code members} is every OTHER villager in this
 * pony's circle; the invariant is symmetric - if A is friends with B, A's list contains B and B's
 * list contains A (maintained by {@link VillagerFriendGroupHandler}, not enforced here).
 */
public final class FriendGroupAttachment {
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<FriendGroup> FRIEND_GROUP = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "friend_group"),
			builder -> builder.persistent(FriendGroup.CODEC)
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath()
					.syncWith(FriendGroup.PACKET_CODEC, SYNC_TO_TRACKERS));

	private FriendGroupAttachment() {
	}

	public static boolean hasGroup(Entity entity) {
		return entity.hasAttached(FRIEND_GROUP);
	}

	public static FriendGroup getGroup(Entity entity) {
		return entity.getAttached(FRIEND_GROUP);
	}

	public static void setGroup(Entity entity, FriendGroup group) {
		entity.setAttached(FRIEND_GROUP, group);
	}

	public record FriendGroup(List<UUID> members) {
		public static final Codec<FriendGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Uuids.CODEC.listOf().fieldOf("members").forGetter(FriendGroup::members)
		).apply(instance, FriendGroup::new));

		public static final PacketCodec<ByteBuf, FriendGroup> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.collection(ArrayList::new, Uuids.PACKET_CODEC), FriendGroup::members,
				FriendGroup::new);
	}
}
