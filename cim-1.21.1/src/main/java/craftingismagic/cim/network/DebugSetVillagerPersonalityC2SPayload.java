package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;

/**
 * Sent whenever any field in the villager debug editor's Personality tab changes (Creative-only) -
 * carries the whole {@link Personality} record, same reasoning as
 * {@link DebugSetVillagerGeneticsC2SPayload}.
 */
public record DebugSetVillagerPersonalityC2SPayload(int villagerEntityId, Personality personality)
		implements CustomPayload {
	public static final Id<DebugSetVillagerPersonalityC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_personality"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerPersonalityC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerPersonalityC2SPayload::villagerEntityId,
			Personality.PACKET_CODEC.cast(), DebugSetVillagerPersonalityC2SPayload::personality,
			DebugSetVillagerPersonalityC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
