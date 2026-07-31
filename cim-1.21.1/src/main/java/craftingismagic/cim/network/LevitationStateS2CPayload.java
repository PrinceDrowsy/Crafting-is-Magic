package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.levitation.LevitationState;

/** Tells the client what it's currently doing with Levitation - nothing, selecting, or holding. */
public record LevitationStateS2CPayload(LevitationState state) implements CustomPayload {
	public static final Id<LevitationStateS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "levitation_state"));
	public static final PacketCodec<RegistryByteBuf, LevitationStateS2CPayload> CODEC = PacketCodec.tuple(
			LevitationState.PACKET_CODEC, LevitationStateS2CPayload::state, LevitationStateS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
