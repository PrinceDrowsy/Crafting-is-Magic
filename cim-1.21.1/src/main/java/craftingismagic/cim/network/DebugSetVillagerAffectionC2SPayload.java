package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Sent when the player edits the Affection field in the villager debug editor (Creative-only) -
 * sets the requesting player's own affection with this villager to an absolute value (not a
 * delta), unlike the normal {@code FriendshipAttachment#addAffection} gameplay path.
 */
public record DebugSetVillagerAffectionC2SPayload(int villagerEntityId, int affection) implements CustomPayload {
	public static final Id<DebugSetVillagerAffectionC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_affection"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerAffectionC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerAffectionC2SPayload::villagerEntityId,
			PacketCodecs.INTEGER.cast(), DebugSetVillagerAffectionC2SPayload::affection,
			DebugSetVillagerAffectionC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
