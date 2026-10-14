package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Debug editor: hold a pony in place, have it follow you, or give it back its routine. */
public record DebugSetPonyMovementC2SPayload(int entityId, int mode) implements CustomPayload {
	public static final Id<DebugSetPonyMovementC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_pony_movement"));
	public static final PacketCodec<RegistryByteBuf, DebugSetPonyMovementC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetPonyMovementC2SPayload::entityId,
			PacketCodecs.VAR_INT.cast(), DebugSetPonyMovementC2SPayload::mode,
			DebugSetPonyMovementC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
