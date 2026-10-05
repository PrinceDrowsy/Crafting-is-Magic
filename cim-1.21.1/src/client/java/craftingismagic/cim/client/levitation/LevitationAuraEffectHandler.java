package craftingismagic.cim.client.levitation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.LevitationAuraS2CPayload;

/**
 * Spawns the pink/magenta magic aura glow + twinkling star effect on a held
 * Levitation group - one shared aura sized to the group's combined bounding
 * box, not one per block, keyed by the group's first member entity id (an
 * arbitrary but stable identifier for the group as a whole, also used below
 * as the "anchor" for live position tracking). The server tells every
 * nearby client (not just the caster) which member entity ids make up the
 * group and when to start/stop, since the floating glowing blocks are a
 * public visual effect like the blocks themselves.
 *
 * <p>The group's shape (bounding-box size and each block's offset from the
 * anchor) is computed once, when it's fully known, and never recomputed
 * after that - the whole group is rigid and only ever translates as a unit
 * (see {@code LevitationManager#updateHeldPositions}), so the shape can't
 * actually change mid-hold. Only the anchor's own live position is read
 * every tick after that. This isn't just an optimization: recomputing the
 * bounding box from every member's live position each tick caused a visible
 * strobe/pulse with multiple blocks, because Minecraft's own entity
 * tracking doesn't necessarily deliver each block's position update to the
 * client on the same tick (it staggers updates across entities) - so the
 * "live" box would genuinely flicker in size from that inter-entity timing
 * skew, something a single block can't exhibit against itself. Tracking one
 * entity's position sidesteps the skew entirely, the same way a
 * single-block aura always did.
 *
 * <p>"Fully known" specifically means every member entity has been seen at
 * least once - our own S2C payload isn't guaranteed to arrive only after
 * every member block's own entity-spawn packet has already been processed,
 * so computing (and locking) the shape from whatever happens to be loaded
 * on the very first tick risked locking in a too-small box from only some
 * of the group, which then either stayed wrong or "corrected" itself later
 * with a visible snap. No emitters are spawned at all until the shape is
 * confirmed complete (or {@link #SHAPE_LOCK_TIMEOUT_TICKS} elapses as a
 * safety net).
 *
 * <p>The emitters are periodically destroyed and recreated (see {@link
 * #REFRESH_INTERVAL_TICKS}) rather than relying on one emitter surviving for
 * the entire (unbounded) hold duration - empirically, calling {@link
 * ParticleEmitter#remove()} on a very long-lived (72000-tick) emitter did
 * not reliably clean it up, leaking exactly one emitter per hold/release
 * cycle. Short-lived emitters that die of natural expiry are the
 * already-proven-reliable pattern used elsewhere in this mod (Sonic
 * Rainboom's ring/trail), so this refreshes rather than trying to keep one
 * emitter alive indefinitely.
 */
public final class LevitationAuraEffectHandler {
	private static final Identifier AURA_EMITTER_ID = Identifier.of(Cim.MOD_ID, "levitation_aura");
	private static final Identifier STARS_EMITTER_ID = Identifier.of(Cim.MOD_ID, "levitation_stars");

	/**
	 * Close to (but a couple ticks under) the emitters' own 20-tick max_lifetime - not "well
	 * under" it. {@link ParticleEmitter#remove()} was only ever confirmed unreliable for
	 * very-long-lived (72000-tick) emitters; it's never been re-verified for these short-lived
	 * ones. If it turns out not to matter either, an old particle would still be alive via its own
	 * natural expiry when a refresh creates its replacement - and with a refresh interval much
	 * shorter than max_lifetime (this used to be 10), that means a permanent steady-state overlap
	 * of two generations, not a momentary one, which is exactly what looked like "two auras"
	 * persisting rather than flashing. Keeping the two intervals close means naturally expiring is
	 * sufficient to prevent overlap on its own, regardless of whether remove() helps.
	 */
	private static final int REFRESH_INTERVAL_TICKS = 18;

	/** Give up waiting for every member entity to be loaded client-side after this many ticks and lock in whatever's found. */
	private static final int SHAPE_LOCK_TIMEOUT_TICKS = 20;

	/**
	 * Cap on how many vertices of the group's projected screen-space silhouette (its 2D convex
	 * hull, from the camera's current angle) get sent through to the shader - see {@link
	 * GroupAuraState#hullCount}. A simple box-like or cross-like selection's hull rarely has more
	 * than 8 corners in practice; if it ever does, {@link #updateViewSilhouette} just drops the
	 * excess rather than trying to simplify the polygon further.
	 */
	/**
	 * A vertex format can only have so many attribute slots before the shader fails to link at
	 * all (confirmed on this hardware: locations 0-18, 19 slots total between the fixed particle
	 * attributes and 8 hull vec4s, produced a hard link failure - GL_INVALID_VALUE "Program handle
	 * does not refer to an object generated by OpenGL" on every subsequent use, i.e. the aura
	 * vanished completely, not just a visual glitch). The fixed (non-hull) particle attributes use
	 * locations 0-10 (11 slots: Position, Normal, the mat4 at 4 slots, Scale, UV0Min, UV0Max, Color,
	 * UV2), leaving 5 more vec4 slots - 10 hull points - to stay within the standard 16-slot
	 * guarantee most GPUs/drivers actually enforce as a hard cap, not just a spec minimum.
	 */
	static final int MAX_HULL_POINTS = 10;

	/**
	 * How much bigger the quad's world-space size is than the silhouette's own measured extent -
	 * leaves headroom (in shader p-space, from {@code 1/SILHOUETTE_MARGIN} out to {@code 1.0}) for
	 * the wavy rim to extend past the true edge before clipping at the quad's physical border. Must
	 * be used as the SAME divisor for both {@link GroupAuraState#radius} and the {@link
	 * GroupAuraState#hullX}/{@link GroupAuraState#hullY} normalization below - see {@link
	 * #updateViewSilhouette}'s comment on why those two have to agree.
	 */
	private static final double SILHOUETTE_MARGIN = 1.6;

	private static final class Tracked {
		final List<Integer> memberEntityIds;
		final GroupAuraState state = new GroupAuraState();
		/** Fixed offset from the anchor entity's own (live) position to the group's true center - set once the shape locks. */
		Vec3d centerOffsetFromAnchor;
		/**
		 * Each member block's fixed min-corner offset from the anchor (every block is exactly a
		 * 1x1x1 cell, so the max-corner is always {@code min + (1,1,1)}) - set once the shape
		 * locks, alongside {@link #centerOffsetFromAnchor}. Kept per-block (rather than just the
		 * aggregate bounding box) so the on-screen silhouette can be re-derived every tick as the
		 * camera's viewing angle changes - see {@link #updateViewSilhouette}.
		 */
		List<Vec3d> relativeBlockMins;
		/** False until every member entity has been seen at least once (or the timeout elapses). */
		boolean shapeLocked = false;
		int shapeWaitTicks = 0;
		List<ParticleEmitter> emitters;
		int age = 0;

		Tracked(List<Integer> memberEntityIds, List<ParticleEmitter> emitters) {
			this.memberEntityIds = memberEntityIds;
			this.emitters = emitters;
		}
	}

	/** Keyed by the group's own first member entity id, which also serves as the position-tracking anchor. */
	private static final Map<Integer, Tracked> active = new HashMap<>();

	private LevitationAuraEffectHandler() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LevitationAuraS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> handle(payload)));

		ClientTickEvents.END_CLIENT_TICK.register(LevitationAuraEffectHandler::tick);
	}

	/** On leaving a world - entries are keyed by entity id, and ids are reused in the next world, so a
	 * stale entry would make {@link #start} silently skip a real new hold that happens to share an id.
	 * Veil drops the emitters themselves along with the old world. */
	public static void reset() {
		active.clear();
	}

	private static void handle(LevitationAuraS2CPayload payload) {
		if (payload.entityIds().isEmpty()) {
			return;
		}

		int groupKey = payload.entityIds().get(0);
		if (payload.active()) {
			start(groupKey, payload.entityIds());
		} else {
			stop(groupKey);
		}
	}

	private static void start(int groupKey, List<Integer> memberEntityIds) {
		if (active.containsKey(groupKey)) {
			return;
		}

		if (MinecraftClient.getInstance().world == null) {
			return;
		}

		// Deliberately doesn't spawn anything yet - see the class doc on why the shape has to be
		// confirmed complete first, handled by tick() below.
		active.put(groupKey, new Tracked(memberEntityIds, new ArrayList<>()));
	}

	private static List<ParticleEmitter> spawnEmitters(GroupAuraState state) {
		List<ParticleEmitter> emitters = new ArrayList<>();
		try {
			ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();

			ParticleEmitter aura = manager.createEmitter(AURA_EMITTER_ID);
			if (aura == null) {
				Cim.LOGGER.warn("Levitation aura emitter '{}' could not be created", AURA_EMITTER_ID);
			} else {
				aura.setPosition(state.center);
				// Must be added before addParticleSystem (which triggers the initial spawn) so the
				// aura particle is built with this module already in its set.
				aura.addCodeModule(new GroupAuraCodeModule(state));
				manager.addParticleSystem(aura);
				emitters.add(aura);
			}

			ParticleEmitter stars = manager.createEmitter(STARS_EMITTER_ID);
			if (stars == null) {
				Cim.LOGGER.warn("Levitation stars emitter '{}' could not be created", STARS_EMITTER_ID);
			} else {
				stars.setPosition(state.center);
				manager.addParticleSystem(stars);
				emitters.add(stars);
			}
		} catch (Exception e) {
			Cim.LOGGER.warn("Failed to spawn Levitation aura effect", e);
		}
		return emitters;
	}

	private static void stop(int groupKey) {
		Tracked tracked = active.remove(groupKey);
		if (tracked != null) {
			tracked.emitters.forEach(ParticleEmitter::remove);
		}
	}

	/**
	 * Computes the group's bounding box relative to the anchor entity's current position, from
	 * whichever member entities are currently loaded client-side - see the class doc for why this
	 * is called repeatedly until every member is found (or a timeout), rather than trusting
	 * whatever's available on the first call.
	 *
	 * @return true if every member entity was found this call (i.e. the shape is now trustworthy)
	 */
	private static boolean computeFixedGroupShape(Tracked tracked, MinecraftClient client) {
		Entity anchor = client.world.getEntityById(tracked.memberEntityIds.get(0));
		if (anchor == null) {
			return false;
		}
		Vec3d anchorPos = anchor.getPos();

		// Start from the anchor block's own 1x1x1 cell so a single-block group is exactly that cell.
		double minX = 0, minY = 0, minZ = 0;
		double maxX = 1, maxY = 1, maxZ = 1;
		int found = 0;
		List<Vec3d> relativeMins = new ArrayList<>();

		for (int id : tracked.memberEntityIds) {
			Entity entity = client.world.getEntityById(id);
			if (entity == null) {
				continue;
			}

			found++;
			Vec3d rel = entity.getPos().subtract(anchorPos);
			relativeMins.add(rel);
			minX = Math.min(minX, rel.x);
			minY = Math.min(minY, rel.y);
			minZ = Math.min(minZ, rel.z);
			maxX = Math.max(maxX, rel.x + 1.0);
			maxY = Math.max(maxY, rel.y + 1.0);
			maxZ = Math.max(maxZ, rel.z + 1.0);
		}

		tracked.centerOffsetFromAnchor = new Vec3d((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
		tracked.relativeBlockMins = relativeMins;
		double dx = maxX - minX;
		double dy = maxY - minY;
		double dz = maxZ - minZ;
		// A placeholder only, for the brief window before the first updateViewSilhouette call sets
		// the real, view-dependent radius from the silhouette's own measured extent (see that
		// method) - matches the same "diagonal-ish" ballpark so nothing looks obviously wrong if a
		// frame ever renders before that first update lands.
		tracked.state.radius = (float) Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.52);

		// How much of the bounding box's own volume the group actually fills - 1.0 for a single
		// block or a solid stack, lower for a sparse/concave arrangement (e.g. a cross). The shader
		// uses this to shrink and sharpen the aura for sparse groups instead of always blowing out
		// to the full bounding box's rounded blob regardless of how little of it is actually filled.
		double volume = dx * dy * dz;
		tracked.state.shapeFill = (float) MathHelper.clamp(found / volume, 0.35, 1.0);

		return found == tracked.memberEntityIds.size();
	}

	/** Per-tick (once locked): follows only the anchor entity's own live position, plus the fixed offset computed once above. */
	private static boolean updateAnchorCenter(Tracked tracked, MinecraftClient client) {
		Entity anchor = client.world.getEntityById(tracked.memberEntityIds.get(0));
		if (anchor == null) {
			return false;
		}

		tracked.state.center = anchor.getPos().add(tracked.centerOffsetFromAnchor);
		return true;
	}

	/**
	 * Computes the group's actual projected screen-space silhouette every tick: every block corner
	 * is projected onto the camera's current right/up basis (negating "right" up front so the
	 * result lines up directly with the shader's own {@code centered} coordinate space - see {@code
	 * LevitationAuraRenderStyle}'s PLANE_POSITIONS/UV pairing for why), then reduced to its 2D
	 * convex hull (see {@link #convexHull}) - the true outline a viewer would see from this exact
	 * angle, not an ellipse or box approximating it. The hull is centered on its own centroid and
	 * normalized by its own bounding radius before being stored in {@link GroupAuraState#hullX}/
	 * {@link GroupAuraState#hullY}, so {@code levitation_aura.fsh} can compare it directly against
	 * its UV-space "centered" coordinate without needing to know the group's absolute size.
	 */
	private static void updateViewSilhouette(Tracked tracked, MinecraftClient client) {
		// Derived from the exact same rotation LevitationAuraRenderStyle uses for the billboard's
		// own camera-facing orientation (not Camera#getHorizontalPlane/getVerticalPlane, which -
		// contrary to what their names suggest - do not track the live view direction; using them
		// left the hull frozen in place while the quad itself kept rotating to face the camera,
		// so the two coordinate frames silently drifted apart every time the player looked around).
		Quaternionf rotation = new Quaternionf(client.getEntityRenderDispatcher().getRotation());
		Vector3f right = rotation.transform(new Vector3f(1, 0, 0));
		Vector3f up = rotation.transform(new Vector3f(0, 1, 0));

		List<double[]> points = new ArrayList<>();
		for (Vec3d min : tracked.relativeBlockMins) {
			for (int cx = 0; cx <= 1; cx++) {
				for (int cy = 0; cy <= 1; cy++) {
					for (int cz = 0; cz <= 1; cz++) {
						double x = min.x + cx;
						double y = min.y + cy;
						double z = min.z + cz;
						double sx = -(x * right.x() + y * right.y() + z * right.z());
						double sy = x * up.x() + y * up.y() + z * up.z();
						points.add(new double[] { sx, sy });
					}
				}
			}
		}

		List<double[]> hull = convexHull(points);
		if (hull.size() > MAX_HULL_POINTS) {
			// The true hull can easily have more vertices than the vertex format has room for (a
			// non-uniform/concave multi-block group's silhouette hit 10-11 in testing, and
			// MAX_HULL_POINTS is capped at 10 by this hardware's vertex attribute limit - see that
			// constant's doc). Naively truncating the vertex LIST used to just drop whichever
			// vertices happened to sort last, leaving a bogus "shortcut" edge that cut straight
			// across the true shape instead of tracing it - visibly slicing through solid block
			// area. Simplifying down to the budget instead (dropping the least visually significant
			// vertices first) keeps the traced outline faithful to the real silhouette regardless of
			// how many vertices the true hull has.
			hull = simplifyHull(hull, MAX_HULL_POINTS);
		}

		double centroidX = 0, centroidY = 0;
		for (double[] p : hull) {
			centroidX += p[0];
			centroidY += p[1];
		}
		centroidX /= hull.size();
		centroidY /= hull.size();

		double maxDist = 0;
		for (double[] p : hull) {
			double dx = p[0] - centroidX;
			double dy = p[1] - centroidY;
			maxDist = Math.max(maxDist, Math.sqrt(dx * dx + dy * dy));
		}
		if (maxDist < 1.0E-6) {
			maxDist = 1.0;
		}

		// The quad's own WORLD-SPACE size (radius/Scale) is inflated beyond the true silhouette's
		// measured extent so the rim has room to extend outward before clipping at the quad's
		// physical texCoord edge (p == +-1 in the shader) - but the hull points handed to the
		// shader MUST be normalized by that exact same inflated value, not by maxDist alone, or the
		// two coordinate spaces disagree about where the true edge (hullDist == 0) actually sits.
		// (An earlier version divided the hull by maxDist while sizing the quad by maxDist * 1.8 -
		// that mismatch put the "true" silhouette boundary 1.8x farther from center than the real
		// block edge, which is why the interior glow used to balloon out well past the block itself
		// before any wave/rim margin was even added.)
		double scale = maxDist * SILHOUETTE_MARGIN;
		tracked.state.radius = (float) scale;

		int count = Math.min(hull.size(), MAX_HULL_POINTS);
		tracked.state.hullCount = count;
		for (int i = 0; i < MAX_HULL_POINTS; i++) {
			double[] p = hull.get(Math.min(i, count - 1));
			tracked.state.hullX[i] = (float) ((p[0] - centroidX) / scale);
			tracked.state.hullY[i] = (float) ((p[1] - centroidY) / scale);
		}
	}

	/**
	 * Visvalingam-Whyatt polygon simplification: repeatedly drops whichever vertex contributes the
	 * LEAST area to the polygon (the triangle it forms with its two immediate neighbors) until only
	 * {@code maxPoints} remain. Unlike truncating the hull's vertex list, this keeps the overall
	 * shape faithful regardless of which vertices happen to need dropping - a vertex only gets
	 * removed once every other vertex would have distorted the outline more by being removed
	 * instead.
	 */
	private static List<double[]> simplifyHull(List<double[]> hull, int maxPoints) {
		List<double[]> result = new ArrayList<>(hull);
		while (result.size() > maxPoints) {
			int n = result.size();
			int removeIndex = 0;
			double minArea = Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				double[] prev = result.get((i - 1 + n) % n);
				double[] curr = result.get(i);
				double[] next = result.get((i + 1) % n);
				double area = Math.abs((curr[0] - prev[0]) * (next[1] - prev[1]) - (next[0] - prev[0]) * (curr[1] - prev[1]));
				if (area < minArea) {
					minArea = area;
					removeIndex = i;
				}
			}
			result.remove(removeIndex);
		}
		return result;
	}

	/**
	 * Standard Andrew's monotone chain convex hull, O(n log n) - {@code points} may contain
	 * duplicates or interior points (most of the 8 corners per block usually are), which this
	 * naturally discards. Returns hull vertices in counter-clockwise order.
	 */
	private static List<double[]> convexHull(List<double[]> points) {
		List<double[]> sorted = new ArrayList<>(points);
		sorted.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));

		int n = sorted.size();
		if (n < 3) {
			return sorted;
		}

		double[][] hull = new double[2 * n][];
		int k = 0;

		for (int i = 0; i < n; i++) {
			while (k >= 2 && cross(hull[k - 2], hull[k - 1], sorted.get(i)) <= 0) {
				k--;
			}
			hull[k++] = sorted.get(i);
		}

		int lower = k + 1;
		for (int i = n - 2; i >= 0; i--) {
			while (k >= lower && cross(hull[k - 2], hull[k - 1], sorted.get(i)) <= 0) {
				k--;
			}
			hull[k++] = sorted.get(i);
		}

		List<double[]> result = new ArrayList<>();
		for (int i = 0; i < k - 1; i++) {
			result.add(hull[i]);
		}
		return result.isEmpty() ? sorted : result;
	}

	private static double cross(double[] o, double[] a, double[] b) {
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}

	private static void tick(MinecraftClient client) {
		if (active.isEmpty() || client.world == null) {
			return;
		}

		Iterator<Map.Entry<Integer, Tracked>> iterator = active.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Tracked> entry = iterator.next();
			Tracked tracked = entry.getValue();

			if (!tracked.shapeLocked) {
				boolean allFound = computeFixedGroupShape(tracked, client);
				tracked.shapeWaitTicks++;
				if (!allFound && tracked.shapeWaitTicks < SHAPE_LOCK_TIMEOUT_TICKS) {
					continue;
				}
				if (tracked.centerOffsetFromAnchor == null) {
					// The anchor itself never showed up within the timeout - give up on this group.
					iterator.remove();
					continue;
				}

				tracked.shapeLocked = true;
				tracked.emitters = spawnEmitters(tracked.state);
				if (tracked.emitters.isEmpty()) {
					iterator.remove();
					continue;
				}
			} else if (tracked.relativeBlockMins.size() < tracked.memberEntityIds.size()) {
				// Locking the shape at the timeout doesn't mean every member is actually accounted
				// for - a block whose own entity-spawn packet simply took longer than
				// SHAPE_LOCK_TIMEOUT_TICKS to arrive (more likely the bigger the selection) would
				// otherwise be permanently missing from relativeBlockMins forever, silently excluded
				// from the traced silhouette even though it's still visibly floating as part of the
				// group. Keep retrying (cheap - just an entity lookup per member) until every member
				// has actually been seen at least once, growing the shape in place rather than
				// giving up after the initial window.
				computeFixedGroupShape(tracked, client);
			}

			if (!updateAnchorCenter(tracked, client)) {
				tracked.emitters.forEach(ParticleEmitter::remove);
				iterator.remove();
				continue;
			}
			updateViewSilhouette(tracked, client);

			if (++tracked.age >= REFRESH_INTERVAL_TICKS) {
				// Remove the old emitter before spawning the replacement - now that
				// GroupAuraCodeModule sets the correct position/radius/aspect on init (not just on
				// update), a freshly-spawned particle renders correctly from its very first frame,
				// so there's no reason to briefly overlap old and new (which was itself visible as
				// "two auras" while the new one still had a stale interpolation baseline).
				tracked.emitters.forEach(ParticleEmitter::remove);
				tracked.emitters = spawnEmitters(tracked.state);
				tracked.age = 0;
				continue;
			}

			for (ParticleEmitter emitter : tracked.emitters) {
				emitter.setPosition(tracked.state.center);
			}
		}
	}
}
