package craftingismagic.cim.client.levitation;

import net.minecraft.util.math.BlockPos;

import craftingismagic.cim.levitation.LevitationState;

/** Client-side mirror of what the local player is currently doing with Levitation. */
public final class LevitationClientState {
	private static LevitationState state = LevitationState.NONE;

	/** Latest known bounding box of the in-progress selection, for {@link LevitationSelectionHighlightRenderer} - stale/meaningless whenever {@link #isSelecting()} is false. */
	private static BlockPos selectionMin;
	private static BlockPos selectionMax;

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

	public static void setSelectionBox(BlockPos min, BlockPos max) {
		selectionMin = min;
		selectionMax = max;
	}

	public static BlockPos getSelectionMin() {
		return selectionMin;
	}

	public static BlockPos getSelectionMax() {
		return selectionMax;
	}
}
