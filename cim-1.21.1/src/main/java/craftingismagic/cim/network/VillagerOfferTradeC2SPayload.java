package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player picks "Offer Trade" from a villager's Interact menu, to open real vanilla trading. */
public record VillagerOfferTradeC2SPayload(int villagerEntityId) implements CustomPayload {
	public static final Id<VillagerOfferTradeC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "villager_offer_trade"));
	public static final PacketCodec<RegistryByteBuf, VillagerOfferTradeC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), VillagerOfferTradeC2SPayload::villagerEntityId, VillagerOfferTradeC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
