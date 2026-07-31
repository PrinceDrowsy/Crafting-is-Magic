package craftingismagic.cim.species;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

public final class SpeciesAttachment {
	public static final AttachmentType<Species> SPECIES = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "species"),
			builder -> builder.persistent(Species.CODEC)
					.copyOnDeath()
					.syncWith(Species.PACKET_CODEC, AttachmentSyncPredicate.all()));

	private SpeciesAttachment() {
	}

	public static boolean hasChosenSpecies(PlayerEntity player) {
		return player.hasAttached(SPECIES);
	}

	public static Species getSpecies(PlayerEntity player) {
		return player.getAttached(SPECIES);
	}

	public static void setSpecies(PlayerEntity player, Species species) {
		player.setAttached(SPECIES, species);
	}
}
