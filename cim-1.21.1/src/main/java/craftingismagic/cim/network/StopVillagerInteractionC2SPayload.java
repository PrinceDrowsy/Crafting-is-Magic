package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Sent when the villager Interact menu closes without handing off to real trading (button press
 * that ends the conversation, or Escape) - releases the villager to wander again. Not sent when
 * handing off to "Offer Trade": vanilla's own trade-session lifecycle owns the customer field from
 * that point on.
 */
public record StopVillagerInteractionC2SPayload(int villagerEntityId) implements CustomPayload {
	public static final Id<StopVillagerInteractionC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "stop_villager_interaction"));
	public static final PacketCodec<RegistryByteBuf, StopVillagerInteractionC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), StopVillagerInteractionC2SPayload::villagerEntityId, StopVillagerInteractionC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
