package craftingismagic.cim.species;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

public final class SpeciesAttachment {
	/**
	 * Syncs to the owning player always (so their own client can read their own species), plus to
	 * any other player already tracking that entity (so the wing-flap camera bob feature can see
	 * other Pegasi). Deliberately NOT {@link AttachmentSyncPredicate#all()}: that broadcasts to
	 * every connected client immediately, including ones that haven't started tracking the target
	 * entity yet - Fabric's own sync handler calls {@code getTarget(world).setAttached(...)} with no
	 * null check, and an untracked entity resolves to {@code world.getEntityById(id) == null} on
	 * that client, throwing an NPE. Restricting to players already tracking the entity guarantees
	 * the entity is resolvable on their end before the sync packet can even be sent.
	 */
	private static final AttachmentSyncPredicate SYNC_TO_OWNER_AND_TRACKERS = (target, player) ->
			target == player || (target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player));

	public static final AttachmentType<Species> SPECIES = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "species"),
			builder -> builder.persistent(Species.CODEC)
					.copyOnDeath()
					.syncWith(Species.PACKET_CODEC, SYNC_TO_OWNER_AND_TRACKERS));

	private SpeciesAttachment() {
	}

	public static boolean hasChosenSpecies(Entity entity) {
		return entity.hasAttached(SPECIES);
	}

	public static Species getSpecies(Entity entity) {
		return entity.getAttached(SPECIES);
	}

	public static void setSpecies(Entity entity, Species species) {
		entity.setAttached(SPECIES, species);
	}
}
