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
 * Tells nearby clients to start or stop the magic aura (and its twinkling stars) on a set of held
 * entities - block/item displays or a held mob - identified by their network ids, in the caster's
 * own magic color (opaque RGB; ignored when stopping). Broadcast to everyone watching, not just the
 * caster, since the floating glowing things are a public visual effect like the things themselves.
 */
public record LevitationAuraS2CPayload(List<Integer> entityIds, boolean active, int color) implements CustomPayload {
	public static final Id<LevitationAuraS2CPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "levitation_aura"));
	public static final PacketCodec<RegistryByteBuf, LevitationAuraS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.collection(ArrayList::new, PacketCodecs.VAR_INT), LevitationAuraS2CPayload::entityIds,
			PacketCodecs.BOOL, LevitationAuraS2CPayload::active,
			PacketCodecs.INTEGER, LevitationAuraS2CPayload::color,
			LevitationAuraS2CPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
