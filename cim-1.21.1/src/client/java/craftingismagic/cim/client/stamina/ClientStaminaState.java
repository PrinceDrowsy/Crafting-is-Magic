package craftingismagic.cim.client.stamina;

import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;

/**
 * The client's copy of its own Stamina, fed by {@code StaminaSyncS2CPayload} - the server only sends
 * on meaningful changes (see {@code StaminaSync}), so between updates the HUD eases its displayed
 * value toward the latest one instead of jumping, keeping the notch bar's fill smooth.
 */
public final class ClientStaminaState {
	/** Fraction of the remaining gap closed per rendered frame - quick enough to keep up with a
	 * drain, slow enough to hide the gaps between throttled updates. */
	private static final float EASE_PER_FRAME = 0.25f;

	private static Stamina target = defaultStamina();
	private static float displayedCurrent = target.current();

	private ClientStaminaState() {
	}

	public static void set(Stamina stamina) {
		// Max or Exhaustion changing is a state change, not a fill animation - snap to it.
		if (stamina.max() != target.max() || stamina.awaitingSleep() != target.awaitingSleep()) {
			displayedCurrent = stamina.current();
		}
		target = stamina;
	}

	public static Stamina get() {
		return target;
	}

	/** The smoothed current value for drawing; call once per rendered frame. */
	public static float displayedCurrent() {
		displayedCurrent += (target.current() - displayedCurrent) * EASE_PER_FRAME;
		if (Math.abs(target.current() - displayedCurrent) < 0.01f) {
			displayedCurrent = target.current();
		}
		return displayedCurrent;
	}

	public static void reset() {
		target = defaultStamina();
		displayedCurrent = target.current();
	}

	private static Stamina defaultStamina() {
		return new Stamina(StaminaAttachment.BASE_MAX_STAMINA, StaminaAttachment.BASE_MAX_STAMINA, false);
	}
}
