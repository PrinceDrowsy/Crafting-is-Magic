package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;

/**
 * Sent whenever any field in the villager debug editor's Genetics tab changes (Creative-only) -
 * carries the whole {@link PonyGenetics} record rather than one payload per sub-field, since the
 * client already has to hold a full working copy locally to keep the sliders/buttons in sync.
 */
public record DebugSetVillagerGeneticsC2SPayload(int villagerEntityId, PonyGenetics genetics) implements CustomPayload {
	public static final Id<DebugSetVillagerGeneticsC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_genetics"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerGeneticsC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerGeneticsC2SPayload::villagerEntityId,
			PonyGenetics.PACKET_CODEC.cast(), DebugSetVillagerGeneticsC2SPayload::genetics,
			DebugSetVillagerGeneticsC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
