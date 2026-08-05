package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent to nearby players right after a Sonic Rainboom fires on a player - tells clients to start the rainbow/sparkle trail following that entity for the given duration. */
public record PegasusRainboomTrailS2CPayload(int entityId, int durationTicks) implements CustomPayload {
	public static final Id<PegasusRainboomTrailS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "rainboom_trail"));
	public static final PacketCodec<RegistryByteBuf, PegasusRainboomTrailS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, PegasusRainboomTrailS2CPayload::entityId,
			PacketCodecs.VAR_INT, PegasusRainboomTrailS2CPayload::durationTicks,
			PegasusRainboomTrailS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
