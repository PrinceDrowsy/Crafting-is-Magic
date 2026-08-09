package craftingismagic.cim.network;

import net.minecraft.item.Item;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player cycles the Favorite Gift field in the villager debug editor (Creative-only). */
public record DebugSetVillagerFavoriteGiftC2SPayload(int villagerEntityId, Item favoriteGift) implements CustomPayload {
	public static final Id<DebugSetVillagerFavoriteGiftC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_favorite_gift"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerFavoriteGiftC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerFavoriteGiftC2SPayload::villagerEntityId,
			PacketCodecs.registryValue(RegistryKeys.ITEM), DebugSetVillagerFavoriteGiftC2SPayload::favoriteGift,
			DebugSetVillagerFavoriteGiftC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
