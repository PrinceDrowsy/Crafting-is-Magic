package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent live as the player edits the Name field in the villager debug editor (Creative-only). */
public record DebugSetVillagerNameC2SPayload(int villagerEntityId, String name) implements CustomPayload {
	public static final Id<DebugSetVillagerNameC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_name"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerNameC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerNameC2SPayload::villagerEntityId,
			PacketCodecs.STRING.cast(), DebugSetVillagerNameC2SPayload::name,
			DebugSetVillagerNameC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
