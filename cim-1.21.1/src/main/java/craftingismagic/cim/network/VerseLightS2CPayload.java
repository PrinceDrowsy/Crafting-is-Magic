package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.Cim;

/**
 * A Verse light begins (README: Verse, Light): each client nearby makes it real with Veil's dynamic
 * lights - a glow that actually lights the world around it.
 *
 * @param entityId   the creature it follows, or -1 for a fixed place
 * @param pos        where it is, for a fixed place
 * @param color      0xRRGGBB
 * @param radius     how far its light reaches, in blocks
 * @param brightness how bright, 1 normal
 * @param ticks      how long it lasts
 * @param crown      a crown: several small lights circling above the creature's head
 * @param twinkle    the light flashes in and out rather than holding steady
 * @param count      for a crown, how many lights circle
 */
public record VerseLightS2CPayload(int entityId, Vec3d pos, int color, float radius, float brightness, int ticks, boolean crown,
		boolean twinkle, int count)
		implements CustomPayload {
	public static final Id<VerseLightS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "verse_light"));

	/** Written out by hand - more fields than {@code PacketCodec.tuple} takes. */
	public static final PacketCodec<RegistryByteBuf, VerseLightS2CPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.entityId());
				buf.writeDouble(payload.pos().x);
				buf.writeDouble(payload.pos().y);
				buf.writeDouble(payload.pos().z);
				buf.writeInt(payload.color());
				buf.writeFloat(payload.radius());
				buf.writeFloat(payload.brightness());
				buf.writeVarInt(payload.ticks());
				buf.writeBoolean(payload.crown());
				buf.writeBoolean(payload.twinkle());
				buf.writeVarInt(payload.count());
			},
			buf -> new VerseLightS2CPayload(buf.readVarInt(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readInt(),
					buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
