package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The caster struck (their attack key) while a verse waits for "when I strike" (design doc: Verse). */
public record VerseStrikeC2SPayload() implements CustomPayload {
	public static final Id<VerseStrikeC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "verse_strike"));
	public static final PacketCodec<RegistryByteBuf, VerseStrikeC2SPayload> CODEC = PacketCodec.unit(new VerseStrikeC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
