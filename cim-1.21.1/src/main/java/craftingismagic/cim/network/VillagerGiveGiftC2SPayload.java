package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player picks "Give Gift" from a villager's Interact menu, gifting whatever's in their main hand. */
public record VillagerGiveGiftC2SPayload(int villagerEntityId) implements CustomPayload {
	public static final Id<VillagerGiveGiftC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "villager_give_gift"));
	public static final PacketCodec<RegistryByteBuf, VillagerGiveGiftC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), VillagerGiveGiftC2SPayload::villagerEntityId, VillagerGiveGiftC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
