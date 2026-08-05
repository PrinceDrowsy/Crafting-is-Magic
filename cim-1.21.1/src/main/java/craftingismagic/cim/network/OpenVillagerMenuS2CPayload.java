package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Tells the client to open the Interact menu for a villager, with the player's current affection,
 * whether it has a profession (and thus anything to trade), and its display name all carried
 * directly.
 */
public record OpenVillagerMenuS2CPayload(int villagerEntityId, int affection, boolean canTrade, String villagerName)
		implements CustomPayload {
	public static final Id<OpenVillagerMenuS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "open_villager_menu"));
	public static final PacketCodec<RegistryByteBuf, OpenVillagerMenuS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), OpenVillagerMenuS2CPayload::villagerEntityId,
			PacketCodecs.INTEGER.cast(), OpenVillagerMenuS2CPayload::affection,
			PacketCodecs.BOOL.cast(), OpenVillagerMenuS2CPayload::canTrade,
			PacketCodecs.STRING.cast(), OpenVillagerMenuS2CPayload::villagerName,
			OpenVillagerMenuS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
