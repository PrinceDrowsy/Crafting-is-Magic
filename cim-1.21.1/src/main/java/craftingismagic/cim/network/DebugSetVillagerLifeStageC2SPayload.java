package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.LifeStage;

/**
 * Sent when the player changes the Life Stage (or growth-ticks) field in the villager debug
 * editor (Creative-only). {@code growthTicks} is only meaningful for {@code CHILD}/{@code
 * TEENAGER} (see {@code VillagerFamilyHandler#tickGrowth}) but is always sent for simplicity.
 */
public record DebugSetVillagerLifeStageC2SPayload(int villagerEntityId, LifeStage lifeStage, int growthTicks)
		implements CustomPayload {
	public static final Id<DebugSetVillagerLifeStageC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "debug_set_villager_life_stage"));
	public static final PacketCodec<RegistryByteBuf, DebugSetVillagerLifeStageC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DebugSetVillagerLifeStageC2SPayload::villagerEntityId,
			LifeStage.PACKET_CODEC.cast(), DebugSetVillagerLifeStageC2SPayload::lifeStage,
			PacketCodecs.INTEGER.cast(), DebugSetVillagerLifeStageC2SPayload::growthTicks,
			DebugSetVillagerLifeStageC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
