package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;

/**
 * Sent by the client once the player picks a species on the
 * species-selection screen.
 */
public record SelectSpeciesC2SPayload(Species species) implements CustomPayload {
	public static final Id<SelectSpeciesC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "select_species"));
	public static final PacketCodec<RegistryByteBuf, SelectSpeciesC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING.xmap(Species::valueOf, Species::name).cast(),
			SelectSpeciesC2SPayload::species,
			SelectSpeciesC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
