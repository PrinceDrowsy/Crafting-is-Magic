package craftingismagic.cim.client.levitation;

import net.minecraft.util.math.Vec3d;

/**
 * Shared, mutable per-group state for the Levitation aura - {@code
 * LevitationAuraEffectHandler} computes this once (from the held group's
 * combined bounding box) when the aura starts, and {@link
 * GroupAuraCodeModule} reads it every particle-update tick to reposition
 * and rescale the (single, shared) aura particle to match.
 */
final class GroupAuraState {
	Vec3d center = Vec3d.ZERO;
	float radius = 1.0F;
	/**
	 * How much of the group's own bounding-box volume is actually occupied by blocks (1.0 for a
	 * single block or a solid rectangular stack, lower for a sparse/concave arrangement like a
	 * cross). Lets the shader shrink and sharpen the aura's wave amplitude for sparse groups.
	 */
	float shapeFill = 1.0F;

	/**
	 * How many of {@link #hullX}/{@link #hullY} are actually meaningful hull vertices (the rest
	 * repeat the last valid point, so the shader doesn't need to branch on a variable count).
	 * Recomputed every tick (see {@code LevitationAuraEffectHandler#updateViewSilhouette}) as the
	 * group's actual projected screen-space silhouette - its 2D convex hull from the camera's
	 * current angle - rather than a generic squashed circle/box approximation.
	 */
	int hullCount = 0;
	/** Hull vertices' "right" (screen-x) coordinates, centered on the hull's own centroid and normalized by its own bounding radius - see {@link #hullCount}. */
	float[] hullX = new float[LevitationAuraEffectHandler.MAX_HULL_POINTS];
	/** Hull vertices' "up" (screen-y) coordinates - see {@link #hullX}. */
	float[] hullY = new float[LevitationAuraEffectHandler.MAX_HULL_POINTS];
}
