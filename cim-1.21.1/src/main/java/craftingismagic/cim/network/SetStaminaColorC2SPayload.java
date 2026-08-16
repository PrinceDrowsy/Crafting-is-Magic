package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Sent live on every valid edit in {@code StaminaColorScreen} (no Save button, matching the
 * villager debug editor's own live-apply pattern) - carries both colors together rather than as two
 * separate payloads, so the player's stored {@code StaminaColors} is never left half-updated between
 * the fill and background edits landing.
 */
public record SetStaminaColorC2SPayload(int fillColor, int backgroundColor) implements CustomPayload {
	public static final Id<SetStaminaColorC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "set_stamina_color"));
	public static final PacketCodec<RegistryByteBuf, SetStaminaColorC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), SetStaminaColorC2SPayload::fillColor,
			PacketCodecs.INTEGER.cast(), SetStaminaColorC2SPayload::backgroundColor,
			SetStaminaColorC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
