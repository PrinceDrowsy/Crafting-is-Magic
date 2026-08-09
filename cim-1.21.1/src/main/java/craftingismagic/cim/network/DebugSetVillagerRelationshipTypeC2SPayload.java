package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.RelationshipType;

/** Sent when the player cycles the Relationship Type field in the villager debug editor (Creative-only). */
public record DebugSetVillagerRelationshipTypeC2SPayload(int villagerEntityId, RelationshipType relationshipType)
		implements CustomPayload {
	public static final Id<DebugSetVillagerRelationshipTypeC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_relationship_type"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerRelationshipTypeC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerRelationshipTypeC2SPayload::villagerEntityId,
			RelationshipType.PACKET_CODEC.cast(), DebugSetVillagerRelationshipTypeC2SPayload::relationshipType,
			DebugSetVillagerRelationshipTypeC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
