package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent to nearby players when a Pegasus dash breaks the sound barrier, telling clients where to play the Sonic Rainboom effect. */
public record SonicRainboomS2CPayload(double x, double y, double z) implements CustomPayload {
	public static final Id<SonicRainboomS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "sonic_rainboom"));
	public static final PacketCodec<RegistryByteBuf, SonicRainboomS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, SonicRainboomS2CPayload::x,
			PacketCodecs.DOUBLE, SonicRainboomS2CPayload::y,
			PacketCodecs.DOUBLE, SonicRainboomS2CPayload::z,
			SonicRainboomS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
