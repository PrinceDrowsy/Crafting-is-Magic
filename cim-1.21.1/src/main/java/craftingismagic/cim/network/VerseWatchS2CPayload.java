package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Whether one of the player's verses is waiting for "when I strike" - so their game only reports the
 * attack key while it matters, instead of on every swing.
 */
public record VerseWatchS2CPayload(boolean strike) implements CustomPayload {
	public static final Id<VerseWatchS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "verse_watch"));
	public static final PacketCodec<RegistryByteBuf, VerseWatchS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL.cast(), VerseWatchS2CPayload::strike, VerseWatchS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
