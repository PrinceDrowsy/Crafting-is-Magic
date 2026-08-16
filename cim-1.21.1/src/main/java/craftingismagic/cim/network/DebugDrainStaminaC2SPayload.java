package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Creative-only test lever for the Stamina system - drains a fixed chunk of Stamina on press,
 * purely so drain/regen/growth can be exercised in-game before any real ability spends Stamina.
 * Temporary/test-only, not a real feature - remove or repurpose once a real ability (Herding, etc.)
 * becomes the actual caller of {@code StaminaManager.drain(...)}. */
public record DebugDrainStaminaC2SPayload() implements CustomPayload {
	public static final Id<DebugDrainStaminaC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "debug_drain_stamina"));
	public static final PacketCodec<RegistryByteBuf, DebugDrainStaminaC2SPayload> CODEC =
			PacketCodec.unit(new DebugDrainStaminaC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
