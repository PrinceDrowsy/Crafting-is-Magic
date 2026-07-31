package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Tells the client to open the species-selection screen. Sent once, when a
 * player joins without a species already recorded on their attachment data.
 */
public record OpenSpeciesSelectS2CPayload() implements CustomPayload {
	public static final Id<OpenSpeciesSelectS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "open_species_select"));
	public static final PacketCodec<RegistryByteBuf, OpenSpeciesSelectS2CPayload> CODEC =
			PacketCodec.unit(new OpenSpeciesSelectS2CPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
