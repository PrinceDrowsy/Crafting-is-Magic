package craftingismagic.cim.levitation;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Which directions {@link LevitationManager#computeSelection} is allowed to grow into - plain
 * scroll grows equally on all three axes (unchanged); holding Shift while scrolling restricts
 * growth to the X axis (east/west) only, holding Alt restricts it to the Z axis (north/south)
 * only, for precisely shaping a selection along one line.
 */
public enum LevitationSelectionAxis {
	ANY,
	X,
	Z;

	public static final PacketCodec<RegistryByteBuf, LevitationSelectionAxis> PACKET_CODEC =
			PacketCodecs.STRING.xmap(LevitationSelectionAxis::valueOf, LevitationSelectionAxis::name).cast();
}
