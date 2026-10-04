package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player picks a filled slot in the Teleport shortcut ring, starting a charge toward it - skips the line-of-sight raycast entirely. */
public record UseTeleportShortcutC2SPayload(int slot) implements CustomPayload {
	public static final Id<UseTeleportShortcutC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "use_teleport_shortcut"));
	public static final PacketCodec<RegistryByteBuf, UseTeleportShortcutC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, UseTeleportShortcutC2SPayload::slot,
			UseTeleportShortcutC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
