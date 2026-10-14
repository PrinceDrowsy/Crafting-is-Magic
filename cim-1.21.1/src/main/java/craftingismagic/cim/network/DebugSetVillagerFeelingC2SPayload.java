package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.Feeling;

/**
 * Sent when the player edits a Fondness, Respect, or Trust field in the villager debug editor -
 * sets the requesting player's own value for that feeling with this villager to an absolute number
 * (not a delta), unlike the normal {@code FriendshipAttachment#addFeeling} gameplay path.
 */
public record DebugSetVillagerFeelingC2SPayload(int villagerEntityId, Feeling feeling, int value) implements CustomPayload {
	public static final Id<DebugSetVillagerFeelingC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_feeling"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerFeelingC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerFeelingC2SPayload::villagerEntityId,
			Feeling.PACKET_CODEC.cast(), DebugSetVillagerFeelingC2SPayload::feeling,
			PacketCodecs.INTEGER.cast(), DebugSetVillagerFeelingC2SPayload::value,
			DebugSetVillagerFeelingC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
