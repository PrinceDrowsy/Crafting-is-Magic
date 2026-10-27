package craftingismagic.cim.client.levitation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import craftingismagic.cim.client.magic.MagicAuraRenderer;
import craftingismagic.cim.network.LevitationAuraS2CPayload;

/**
 * Starts and stops the magic aura on a held Levitation group, drawn on each held entity by
 * {@link MagicAuraRenderer}. The server tells every nearby client, not just the caster: floating,
 * glowing things are public, like the things themselves.
 */
public final class LevitationAuraEffectHandler {
	private LevitationAuraEffectHandler() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LevitationAuraS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> MagicAuraRenderer.set(payload.entityIds(), payload.active(), payload.color())));
		MagicAuraRenderer.register();
	}

	/** On leaving a world: entity ids are reused in the next one. */
	public static void reset() {
		MagicAuraRenderer.reset();
	}
}
