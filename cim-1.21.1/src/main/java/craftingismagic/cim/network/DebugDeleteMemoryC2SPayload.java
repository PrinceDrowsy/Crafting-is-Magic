package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The villager debug editor's Memories tab deleting one memory, by its position in the list the
 * client was last sent (see {@link DebugMemoriesS2CPayload}). */
public record DebugDeleteMemoryC2SPayload(int villagerEntityId, int index) implements CustomPayload {
	public static final Id<DebugDeleteMemoryC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "debug_delete_memory"));
	public static final PacketCodec<RegistryByteBuf, DebugDeleteMemoryC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugDeleteMemoryC2SPayload::villagerEntityId,
			PacketCodecs.INTEGER.cast(), DebugDeleteMemoryC2SPayload::index,
			DebugDeleteMemoryC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
