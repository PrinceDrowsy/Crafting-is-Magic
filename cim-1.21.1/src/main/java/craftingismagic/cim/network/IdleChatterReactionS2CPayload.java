package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Tells the client to swap the Idle Chatter screen's text to the reaction + closing lines. */
public record IdleChatterReactionS2CPayload(String reactionLine, String closingLine) implements CustomPayload {
	public static final Id<IdleChatterReactionS2CPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "idle_chatter_reaction"));
	public static final PacketCodec<RegistryByteBuf, IdleChatterReactionS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING.cast(), IdleChatterReactionS2CPayload::reactionLine,
			PacketCodecs.STRING.cast(), IdleChatterReactionS2CPayload::closingLine,
			IdleChatterReactionS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
