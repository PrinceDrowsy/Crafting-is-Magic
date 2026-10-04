package craftingismagic.cim.changeling;

import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * Which real player (by UUID) a Changeling is currently shapeshifted into - shared by both the test
 * mob and a Changeling player's own Shapeshift ability, since both need the exact same "who am I
 * disguised as right now" state and the exact same visual resolution (see
 * {@link ChangelingDisguiseApplier}). Absent entirely means not currently disguised (natural/true
 * form) - not a separate boolean flag.
 * <p>
 * Synced to trackers, not just the owner (unlike {@link VoiceMimicryConsentAttachment}) - everyone
 * nearby needs to see the disguise, same reasoning as {@code PonyGeneticsAttachment}.
 */
public final class DisguiseTargetAttachment {
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<UUID> DISGUISE_TARGET = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "changeling_disguise_target"),
			builder -> builder.persistent(Uuids.CODEC).syncWith(Uuids.PACKET_CODEC.cast(), SYNC_TO_TRACKERS));

	private DisguiseTargetAttachment() {
	}

	public static boolean isDisguised(Entity entity) {
		return entity.hasAttached(DISGUISE_TARGET);
	}

	public static UUID getDisguiseTarget(Entity entity) {
		return entity.getAttached(DISGUISE_TARGET);
	}

	public static void setDisguiseTarget(Entity entity, UUID targetUuid) {
		entity.setAttached(DISGUISE_TARGET, targetUuid);
	}

	public static void clearDisguiseTarget(Entity entity) {
		entity.removeAttached(DISGUISE_TARGET);
	}
}
