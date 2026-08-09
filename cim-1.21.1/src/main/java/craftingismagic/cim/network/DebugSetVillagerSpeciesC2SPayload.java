package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;

/** Sent when the player cycles the Species field in the villager debug editor (Creative-only). */
public record DebugSetVillagerSpeciesC2SPayload(int villagerEntityId, Species species) implements CustomPayload {
	public static final Id<DebugSetVillagerSpeciesC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_species"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerSpeciesC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerSpeciesC2SPayload::villagerEntityId,
			Species.PACKET_CODEC, DebugSetVillagerSpeciesC2SPayload::species,
			DebugSetVillagerSpeciesC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
