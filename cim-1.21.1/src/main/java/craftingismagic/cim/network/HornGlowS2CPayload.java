package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Tells clients that a unicorn's horn has lit up (or gone out) - while they channel magic - in their
 * magic color (opaque RGB). Sent to everyone who can see the caster (see {@code HornGlow}).
 */
public record HornGlowS2CPayload(int casterId, boolean active, int color) implements CustomPayload {
	public static final Id<HornGlowS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "horn_glow"));
	public static final PacketCodec<RegistryByteBuf, HornGlowS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, HornGlowS2CPayload::casterId,
			PacketCodecs.BOOL, HornGlowS2CPayload::active,
			PacketCodecs.INTEGER, HornGlowS2CPayload::color,
			HornGlowS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
