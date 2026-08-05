package craftingismagic.cim.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Tells nearby clients to start or stop the pink/magenta magic aura + twinkling
 * star effect on a set of block display entities (identified by their network
 * ids) - broadcast to everyone watching, not just the caster, since the
 * floating glowing blocks are a public visual effect like the blocks
 * themselves.
 */
public record LevitationAuraS2CPayload(List<Integer> entityIds, boolean active) implements CustomPayload {
	public static final Id<LevitationAuraS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "levitation_aura"));
	public static final PacketCodec<RegistryByteBuf, LevitationAuraS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.collection(ArrayList::new, PacketCodecs.VAR_INT), LevitationAuraS2CPayload::entityIds,
			PacketCodecs.BOOL, LevitationAuraS2CPayload::active,
			LevitationAuraS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
