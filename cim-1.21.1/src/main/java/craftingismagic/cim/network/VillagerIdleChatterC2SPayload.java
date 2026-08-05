package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player picks "Idle Chatter" from a villager's Interact menu. */
public record VillagerIdleChatterC2SPayload(int villagerEntityId) implements CustomPayload {
	public static final Id<VillagerIdleChatterC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "villager_idle_chatter"));
	public static final PacketCodec<RegistryByteBuf, VillagerIdleChatterC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), VillagerIdleChatterC2SPayload::villagerEntityId, VillagerIdleChatterC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
