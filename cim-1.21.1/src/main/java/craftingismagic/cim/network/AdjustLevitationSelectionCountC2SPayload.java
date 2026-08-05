package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.levitation.LevitationSelectionAxis;

/**
 * Sent on scroll while holding the activation key during block selection, to grow/shrink it.
 * {@code axis} reflects whether Shift (horizontal) or Alt (vertical) was also held at scroll
 * time, biasing which directions the selection prefers to grow into - see {@link
 * LevitationSelectionAxis}.
 */
public record AdjustLevitationSelectionCountC2SPayload(float delta, LevitationSelectionAxis axis) implements CustomPayload {
	public static final Id<AdjustLevitationSelectionCountC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "adjust_levitation_selection_count"));
	public static final PacketCodec<RegistryByteBuf, AdjustLevitationSelectionCountC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT.cast(), AdjustLevitationSelectionCountC2SPayload::delta,
			LevitationSelectionAxis.PACKET_CODEC, AdjustLevitationSelectionCountC2SPayload::axis,
			AdjustLevitationSelectionCountC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
