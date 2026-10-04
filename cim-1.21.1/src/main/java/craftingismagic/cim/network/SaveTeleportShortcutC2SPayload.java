package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player picks an empty slot in the Teleport shortcut ring, saving their current position/dimension into it. */
public record SaveTeleportShortcutC2SPayload(int slot) implements CustomPayload {
	public static final Id<SaveTeleportShortcutC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "save_teleport_shortcut"));
	public static final PacketCodec<RegistryByteBuf, SaveTeleportShortcutC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SaveTeleportShortcutC2SPayload::slot,
			SaveTeleportShortcutC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
