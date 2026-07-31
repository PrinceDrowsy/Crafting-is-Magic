package craftingismagic.cim.client.ability;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fires the currently-selected ability's {@code onActivate} on a fresh
 * press of {@link AbilityActivationKeyBinding#ACTIVATE_ABILITY}. Abilities
 * that care about the key being held rather than just pressed (like the
 * Earth Pony charge jump) poll the key and the current selection directly
 * instead - this handler still drains the press queue for them so presses
 * don't pile up if their onActivate is a no-op.
 */
public final class AbilityActivationHandler {
	private AbilityActivationHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (AbilityActivationKeyBinding.ACTIVATE_ABILITY.wasPressed()) {
				Ability ability = SelectedAbility.current();
				if (ability != null) {
					ability.onActivate().accept(client);
				}
			}
		});
	}
}
