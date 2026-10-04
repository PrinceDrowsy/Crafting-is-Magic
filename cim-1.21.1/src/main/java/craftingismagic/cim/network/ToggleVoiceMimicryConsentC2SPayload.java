package craftingismagic.cim.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Sent when the player presses the voice-mimicry-consent keybind, flipping whether a Changeling may ever capture/mimic their real voice. */
public record ToggleVoiceMimicryConsentC2SPayload() implements CustomPayload {
	public static final Id<ToggleVoiceMimicryConsentC2SPayload> ID =
			new Id<>(Identifier.of(Cim.MOD_ID, "toggle_voice_mimicry_consent"));
	public static final PacketCodec<RegistryByteBuf, ToggleVoiceMimicryConsentC2SPayload> CODEC =
			PacketCodec.unit(new ToggleVoiceMimicryConsentC2SPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
