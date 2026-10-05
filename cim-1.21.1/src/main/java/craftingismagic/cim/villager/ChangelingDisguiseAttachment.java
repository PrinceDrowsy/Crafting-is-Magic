package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;

/**
 * A Changeling's disguise (see Village changelings in the README) - a purely additive layer on top
 * of {@link craftingismagic.cim.species.SpeciesAttachment}, which stays the single source of truth
 * for the REAL species ({@link Species#CHANGELING}) and is never overwritten by this. {@code
 * trueName}/{@code disguiseName} are two genuinely separate identities, not "whichever one vanilla's
 * single custom-name slot currently holds" - {@link ChangelingDisguiseHandler} keeps vanilla's own
 * custom name in sync with whichever one is currently active.
 */
public final class ChangelingDisguiseAttachment {
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<ChangelingDisguise> DISGUISE = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "changeling_disguise"),
			builder -> builder.persistent(ChangelingDisguise.CODEC)
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath()
					.syncWith(ChangelingDisguise.PACKET_CODEC, SYNC_TO_TRACKERS));

	private ChangelingDisguiseAttachment() {
	}

	public static boolean hasDisguise(Entity entity) {
		return entity.hasAttached(DISGUISE);
	}

	public static ChangelingDisguise getDisguise(Entity entity) {
		return entity.getAttached(DISGUISE);
	}

	public static void setDisguise(Entity entity, ChangelingDisguise disguise) {
		entity.setAttached(DISGUISE, disguise);
	}

	/**
	 * @param disguiseSpecies the species this Changeling presents as while disguised
	 * @param trueName        the Changeling's real, hidden name (see {@link ChangelingNames})
	 * @param disguiseName    the ordinary pony name it goes by while disguised (see {@link VillagerNames})
	 * @param disguised       whether the disguise is currently active - togglable via the Villager
	 *                        Debug Editor, not one-way
	 */
	public record ChangelingDisguise(Species disguiseSpecies, String trueName, String disguiseName, boolean disguised) {
		public static final Codec<ChangelingDisguise> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Species.CODEC.fieldOf("disguise_species").forGetter(ChangelingDisguise::disguiseSpecies),
				Codec.STRING.fieldOf("true_name").forGetter(ChangelingDisguise::trueName),
				Codec.STRING.fieldOf("disguise_name").forGetter(ChangelingDisguise::disguiseName),
				Codec.BOOL.fieldOf("disguised").forGetter(ChangelingDisguise::disguised)
		).apply(instance, ChangelingDisguise::new));

		public static final PacketCodec<RegistryByteBuf, ChangelingDisguise> PACKET_CODEC = PacketCodec.tuple(
				Species.PACKET_CODEC, ChangelingDisguise::disguiseSpecies,
				PacketCodecs.STRING, ChangelingDisguise::trueName,
				PacketCodecs.STRING, ChangelingDisguise::disguiseName,
				PacketCodecs.BOOL, ChangelingDisguise::disguised,
				ChangelingDisguise::new);
	}
}
