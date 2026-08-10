package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the Dispel Disguise/Return to Disguise button is clicked in the villager debug editor
 * (Creative-only) - flips a Changeling's disguise state in either direction. */
public record DebugToggleChangelingDisguiseC2SPayload(int villagerEntityId) implements CustomPayload {
	public static final Id<DebugToggleChangelingDisguiseC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_toggle_changeling_disguise"));
	public static final PacketCodec<RegistryByteBuf, DebugToggleChangelingDisguiseC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugToggleChangelingDisguiseC2SPayload::villagerEntityId,
			DebugToggleChangelingDisguiseC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
