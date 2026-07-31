package craftingismagic.cim.client.levitation;

import craftingismagic.cim.levitation.LevitationState;

/** Client-side mirror of what the local player is currently doing with Levitation. */
public final class LevitationClientState {
	private static LevitationState state = LevitationState.NONE;

	private LevitationClientState() {
	}

	public static LevitationState get() {
		return state;
	}

	public static void set(LevitationState value) {
		state = value;
	}

	public static boolean isSelecting() {
		return state == LevitationState.SELECTING;
	}

	public static boolean isHolding() {
		return state == LevitationState.HOLDING;
	}
}
