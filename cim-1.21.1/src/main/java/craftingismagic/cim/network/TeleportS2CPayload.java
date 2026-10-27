package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.Cim;

/**
 * One end of a unicorn's teleport (docs/magic-aura.md, teleporting), for everyone near it to draw: the
 * departure, or the arrival.
 *
 * @param casterId who teleported, or -1 for none (a debug stick)
 * @param at       the middle of the caster at this end
 * @param size     the caster's size (the larger of width and height), for the bubble
 * @param color    their magic color, 0xRRGGBB
 */
public record TeleportS2CPayload(int casterId, Vec3d at, float size, int color, boolean arrival) implements CustomPayload {
	public static final Id<TeleportS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "teleport"));

	public static final PacketCodec<RegistryByteBuf, TeleportS2CPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.casterId());
				buf.writeDouble(payload.at().x);
				buf.writeDouble(payload.at().y);
				buf.writeDouble(payload.at().z);
				buf.writeFloat(payload.size());
				buf.writeInt(payload.color());
				buf.writeBoolean(payload.arrival());
			},
			buf -> new TeleportS2CPayload(buf.readVarInt(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(),
					buf.readInt(), buf.readBoolean()));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
