package craftingismagic.cim.block;

import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;

/**
 * Wall paint behind a bed (mixed into {@link net.minecraft.block.entity.BedBlockEntity}): the bed's cell can't
 * hold a {@link WallPaintBlock}, so the bed carries the paint for the walls beside it, and draws it with itself.
 */
public interface PaintedBed {
	/** Paints the walls on these sides ({@link #bit} flags) in {@code color}; null and 0 take the paint off. */
	void cim$paint(DyeColor color, int faces);

	/** The paint color, or null for none. */
	DyeColor cim$paintColor();

	/** Which sides are painted, as {@link #bit} flags. */
	int cim$paintFaces();

	static int bit(Direction side) {
		return 1 << side.getHorizontal();
	}
}
