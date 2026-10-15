package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * The player brings something up in conversation - a gift, a trade (README: Conversations replace the
 * interact menu).
 *
 * @param slot for a request that needs an item, the inventory slot of the one picked; -1 otherwise
 */
public record DialogueBringUpC2SPayload(int entityId, Identifier request, int slot) implements CustomPayload {
	public static final Id<DialogueBringUpC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "dialogue_bring_up"));

	public static final PacketCodec<RegistryByteBuf, DialogueBringUpC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT.cast(), DialogueBringUpC2SPayload::entityId,
			Identifier.PACKET_CODEC, DialogueBringUpC2SPayload::request,
			PacketCodecs.VAR_INT.cast(), DialogueBringUpC2SPayload::slot,
			DialogueBringUpC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
