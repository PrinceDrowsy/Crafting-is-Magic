package craftingismagic.cim.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** One second's worth of server-side debug readouts for the overlay, as plain text lines - kept
 * generic so each phase can add lines without a new packet type. */
public record DebugInfoS2CPayload(List<String> lines) implements CustomPayload {
	public static final Id<DebugInfoS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "debug_info"));
	public static final PacketCodec<RegistryByteBuf, DebugInfoS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING.collect(PacketCodecs.toList()).cast(), DebugInfoS2CPayload::lines, DebugInfoS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
