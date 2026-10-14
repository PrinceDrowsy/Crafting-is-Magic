package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The player picked reply {@code option} of the current beat. */
public record DialogueChooseC2SPayload(int entityId, int option) implements CustomPayload {
	public static final Id<DialogueChooseC2SPayload> ID = new Id<>(Identifier.of(Cim.MOD_ID, "dialogue_choose"));
	public static final PacketCodec<RegistryByteBuf, DialogueChooseC2SPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.cast(), DialogueChooseC2SPayload::entityId,
			PacketCodecs.INTEGER.cast(), DialogueChooseC2SPayload::option,
			DialogueChooseC2SPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
