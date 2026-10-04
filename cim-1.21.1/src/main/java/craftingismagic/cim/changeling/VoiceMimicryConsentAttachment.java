package craftingismagic.cim.changeling;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Whether a player consents to having their real voice captured (and later mimicked) by a
 * Changeling - opt-out, defaults to {@code true} (confirmed with the user: matches the source
 * mod's always-on behavior this feature is inspired by). This governs voice capture/playback
 * ONLY - it never gates whether a Changeling can visually shapeshift into a player, which is
 * always allowed regardless of this setting.
 * <p>
 * Same owner-sync shape as {@code StaminaAttachment} - only the owning player's own client ever
 * needs to see their own consent state (to render the toggle's action-bar confirmation).
 */
public final class VoiceMimicryConsentAttachment {
	public static final AttachmentType<Boolean> VOICE_MIMICRY_CONSENT = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "voice_mimicry_consent"),
			builder -> builder.persistent(Codec.BOOL)
					.syncWith(PacketCodecs.BOOL.cast(), (target, player) -> target == player));

	private VoiceMimicryConsentAttachment() {
	}

	/** Defaults to {@code true} (opt-out) when nothing's been set yet. */
	public static boolean hasConsented(Entity entity) {
		return !entity.hasAttached(VOICE_MIMICRY_CONSENT) || entity.getAttached(VOICE_MIMICRY_CONSENT);
	}

	public static void setConsent(Entity entity, boolean consents) {
		entity.setAttached(VOICE_MIMICRY_CONSENT, consents);
	}
}
