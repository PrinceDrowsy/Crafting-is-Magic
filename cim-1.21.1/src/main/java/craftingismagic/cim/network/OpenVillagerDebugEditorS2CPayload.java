package craftingismagic.cim.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;
import craftingismagic.cim.villager.RelationshipType;

/**
 * Opens the client's villager debug editor (see {@code VillagerDebugEditorHandler} on the server),
 * carrying the villager's complete current state so the editor starts pre-populated instead of
 * blank. {@code identity}, {@code relationship}, and {@code family} each bundle several fields
 * into one nested record purely to stay under {@link PacketCodec#tuple}'s confirmed
 * 6-field-per-call maximum - this payload would otherwise need well over 6 top-level fields.
 */
public record OpenVillagerDebugEditorS2CPayload(
		int villagerEntityId, Identity identity, PonyGenetics genetics, Personality personality,
		Relationship relationship, Family family)
		implements CustomPayload {
	public static final Id<OpenVillagerDebugEditorS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "open_villager_debug_editor"));
	public static final PacketCodec<RegistryByteBuf, OpenVillagerDebugEditorS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), OpenVillagerDebugEditorS2CPayload::villagerEntityId,
			Identity.PACKET_CODEC, OpenVillagerDebugEditorS2CPayload::identity,
			PonyGenetics.PACKET_CODEC.cast(), OpenVillagerDebugEditorS2CPayload::genetics,
			Personality.PACKET_CODEC.cast(), OpenVillagerDebugEditorS2CPayload::personality,
			Relationship.PACKET_CODEC, OpenVillagerDebugEditorS2CPayload::relationship,
			Family.PACKET_CODEC, OpenVillagerDebugEditorS2CPayload::family,
			OpenVillagerDebugEditorS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Identity(String name, Species species, LifeStage lifeStage, int growthTicks) {
		public static final PacketCodec<RegistryByteBuf, Identity> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.STRING.cast(), Identity::name,
				Species.PACKET_CODEC, Identity::species,
				LifeStage.PACKET_CODEC.cast(), Identity::lifeStage,
				PacketCodecs.INTEGER.cast(), Identity::growthTicks,
				Identity::new);
	}

	public record Relationship(int affection, RelationshipType relationshipType, Item favoriteGift, List<String> friendGroupNames) {
		public static final PacketCodec<RegistryByteBuf, Relationship> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER.cast(), Relationship::affection,
				RelationshipType.PACKET_CODEC.cast(), Relationship::relationshipType,
				PacketCodecs.registryValue(RegistryKeys.ITEM), Relationship::favoriteGift,
				PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING.cast()), Relationship::friendGroupNames,
				Relationship::new);
	}

	/**
	 * Read-only - the villager debug editor can view a family tree but not edit its links (would
	 * need a villager-picker UI, a bigger lift deferred for now). Names are resolved server-side
	 * (a loaded-but-unlinked-elsewhere name isn't something the client could look up on its own);
	 * {@code children}/{@code siblings} come from a proximity search (no reverse-link exists from a
	 * parent to its children), so a family member who's wandered far away or is unloaded simply
	 * won't show up - a known, honest limitation, not a bug.
	 */
	public record Family(List<String> parents, String partner, List<String> children, List<String> siblings, boolean orphan) {
		public static final PacketCodec<RegistryByteBuf, Family> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING.cast()), Family::parents,
				PacketCodecs.STRING.cast(), Family::partner,
				PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING.cast()), Family::children,
				PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING.cast()), Family::siblings,
				PacketCodecs.BOOL.cast(), Family::orphan,
				Family::new);
	}
}
