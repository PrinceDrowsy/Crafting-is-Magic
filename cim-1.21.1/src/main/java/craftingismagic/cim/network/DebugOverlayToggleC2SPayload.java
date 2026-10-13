package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The client switched its debug overlay on or off - the server only spends effort building
 * readouts for players who are actually looking (see {@code DebugOverlay}). */
public record DebugOverlayToggleC2SPayload(boolean enabled) implements CustomPayload {
	public static final Id<DebugOverlayToggleC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "debug_overlay_toggle"));
	public static final PacketCodec<RegistryByteBuf, DebugOverlayToggleC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL.cast(), DebugOverlayToggleC2SPayload::enabled, DebugOverlayToggleC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
