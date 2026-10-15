package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The lines before a follow-up (like opening the trade screen) have been read - go ahead. */
public record DialogueFollowUpC2SPayload(int entityId) implements CustomPayload {
	public static final Id<DialogueFollowUpC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "dialogue_follow_up"));

	public static final PacketCodec<RegistryByteBuf, DialogueFollowUpC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT.cast(), DialogueFollowUpC2SPayload::entityId,
			DialogueFollowUpC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
