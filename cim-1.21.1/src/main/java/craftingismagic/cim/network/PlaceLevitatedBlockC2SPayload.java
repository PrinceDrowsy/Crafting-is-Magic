package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Sent when the player presses the activation key while holding a
 * levitated group. {@code forceGentle} is true if they were holding
 * sneak (Shift+R) - guarantees a gentle placement, ignoring any leftover
 * scroll momentum that would otherwise turn it into a throw. A safety
 * valve against accidental throws.
 */
public record PlaceLevitatedBlockC2SPayload(boolean forceGentle) implements CustomPayload {
	public static final Id<PlaceLevitatedBlockC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "place_levitated_block"));
	public static final PacketCodec<RegistryByteBuf, PlaceLevitatedBlockC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL.cast(), PlaceLevitatedBlockC2SPayload::forceGentle, PlaceLevitatedBlockC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
