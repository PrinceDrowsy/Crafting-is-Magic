package craftingismagic.cim.levitation;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** What (if anything) a player is currently doing with Levitation, mirrored to their own client. */
public enum LevitationState {
	/** Not levitating anything. */
	NONE,
	/** Holding the activation key, growing a block selection with scroll. */
	SELECTING,
	/** Holding a confirmed selection, floating in front of the player. */
	HOLDING;

	public static final PacketCodec<RegistryByteBuf, LevitationState> PACKET_CODEC =
			PacketCodecs.STRING.xmap(LevitationState::valueOf, LevitationState::name).cast();
}
