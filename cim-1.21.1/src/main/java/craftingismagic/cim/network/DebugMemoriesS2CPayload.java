package craftingismagic.cim.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** A pony's memories for the villager debug editor's Memories tab, as one readable line each - sent
 * when the editor opens and again after any change, kept separate from the editor's own open payload
 * (already at its field limit). Debug-only: memories are otherwise never sent to clients. */
public record DebugMemoriesS2CPayload(int villagerEntityId, List<String> lines) implements CustomPayload {
	public static final Id<DebugMemoriesS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "debug_memories"));
	public static final PacketCodec<RegistryByteBuf, DebugMemoriesS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugMemoriesS2CPayload::villagerEntityId,
			PacketCodecs.STRING.collect(PacketCodecs.toList()).cast(), DebugMemoriesS2CPayload::lines,
			DebugMemoriesS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
