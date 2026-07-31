package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent on scroll while holding a levitated block, to move it closer/farther. */
public record AdjustLevitationDistanceC2SPayload(float delta) implements CustomPayload {
	public static final Id<AdjustLevitationDistanceC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "adjust_levitation_distance"));
	public static final PacketCodec<RegistryByteBuf, AdjustLevitationDistanceC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT.cast(), AdjustLevitationDistanceC2SPayload::delta, AdjustLevitationDistanceC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
