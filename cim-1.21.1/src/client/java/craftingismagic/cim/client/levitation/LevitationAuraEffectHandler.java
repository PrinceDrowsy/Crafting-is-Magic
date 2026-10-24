package craftingismagic.cim.client.levitation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.Cim;
import craftingismagic.cim.client.magic.MagicAuraRenderer;
import craftingismagic.cim.network.LevitationAuraS2CPayload;

/**
 * Starts and stops the magic aura on a held Levitation group: the aura itself is drawn on each held
 * entity by {@link MagicAuraRenderer} (docs/magic-aura.md), and this keeps the twinkling stars
 * around the group, keyed by the group's first member entity id (stable for the whole hold, and the
 * anchor for following it). The server tells every nearby client, not just the caster, since the
 * floating glowing things are a public visual effect like the things themselves.
 *
 * <p>The group is rigid and only ever translates as a unit, so its center is found once - from each
 * member's offset to the anchor, once every member entity has been seen client-side (our payload can
 * arrive before their spawn packets), or {@link #SHAPE_LOCK_TIMEOUT_TICKS} passes - and then only the
 * anchor's live position is followed. Recomputing from every member each tick strobed, since vanilla
 * staggers entity position updates across ticks.
 *
 * <p>The star emitter is periodically recreated rather than kept alive for the whole hold: {@link
 * ParticleEmitter#remove()} didn't reliably clean up a very long-lived emitter, while short-lived
 * ones that expire naturally (the Sonic Rainboom's pattern) do.
 */
public final class LevitationAuraEffectHandler {
	private static final Identifier STARS_EMITTER_ID = Identifier.of(Cim.MOD_ID, "levitation_stars");

	/** Just under the emitter's own 20-tick max_lifetime, so generations never overlap. */
	private static final int REFRESH_INTERVAL_TICKS = 18;

	/** Give up waiting for every member entity to be loaded client-side after this many ticks and lock in whatever's found. */
	private static final int SHAPE_LOCK_TIMEOUT_TICKS = 20;

	private static final class Tracked {
		final List<Integer> memberEntityIds;
		/** Fixed offset from the anchor entity's own (live) position to the group's center - set once the shape locks. */
		Vec3d centerOffsetFromAnchor;
		Vec3d center = Vec3d.ZERO;
		int seen;
		boolean shapeLocked = false;
		int shapeWaitTicks = 0;
		ParticleEmitter stars;
		int age = 0;

		Tracked(List<Integer> memberEntityIds) {
			this.memberEntityIds = memberEntityIds;
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
		MagicAuraRenderer.register();
	}

	/** On leaving a world - entries are keyed by entity id, and ids are reused in the next world, so a
	 * stale entry would make {@link #start} silently skip a real new hold that happens to share an id.
	 * Veil drops the emitters themselves along with the old world. */
	public static void reset() {
		active.clear();
		MagicAuraRenderer.reset();
	}

	private static void handle(LevitationAuraS2CPayload payload) {
		if (payload.entityIds().isEmpty()) {
			return;
		}

		MagicAuraRenderer.set(payload.entityIds(), payload.active(), payload.color());
		int groupKey = payload.entityIds().get(0);
		if (payload.active()) {
			start(groupKey, payload.entityIds());
		} else {
			stop(groupKey);
		}
	}

	private static void start(int groupKey, List<Integer> memberEntityIds) {
		if (active.containsKey(groupKey) || MinecraftClient.getInstance().world == null) {
			return;
		}
		// Nothing spawns yet - the shape has to be confirmed complete first (see tick()).
		active.put(groupKey, new Tracked(new ArrayList<>(memberEntityIds)));
	}

	private static ParticleEmitter spawnStars(Vec3d center) {
		try {
			ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();
			ParticleEmitter stars = manager.createEmitter(STARS_EMITTER_ID);
			if (stars == null) {
				Cim.LOGGER.warn("Levitation stars emitter '{}' could not be created", STARS_EMITTER_ID);
				return null;
			}
			stars.setPosition(center);
			manager.addParticleSystem(stars);
			return stars;
		} catch (Exception e) {
			Cim.LOGGER.warn("Failed to spawn Levitation stars", e);
			return null;
		}
	}

	private static void stop(int groupKey) {
		Tracked tracked = active.remove(groupKey);
		if (tracked != null && tracked.stars != null) {
			tracked.stars.remove();
		}
	}

	/**
	 * The group's bounding-box center relative to the anchor entity, from whichever member entities
	 * are loaded client-side so far (every member is a 1x1x1 block display, or a single entity).
	 */
	private static void computeCenterOffset(Tracked tracked, MinecraftClient client) {
		Entity anchor = client.world.getEntityById(tracked.memberEntityIds.get(0));
		if (anchor == null) {
			return;
		}
		Vec3d anchorPos = anchor.getPos();
		double minX = 0, minY = 0, minZ = 0;
		double maxX = 1, maxY = 1, maxZ = 1;
		int found = 0;
		for (int id : tracked.memberEntityIds) {
			Entity entity = client.world.getEntityById(id);
			if (entity == null) {
				continue;
			}
			found++;
			Vec3d rel = entity.getPos().subtract(anchorPos);
			minX = Math.min(minX, rel.x);
			minY = Math.min(minY, rel.y);
			minZ = Math.min(minZ, rel.z);
			maxX = Math.max(maxX, rel.x + 1.0);
			maxY = Math.max(maxY, rel.y + 1.0);
			maxZ = Math.max(maxZ, rel.z + 1.0);
		}
		tracked.centerOffsetFromAnchor = new Vec3d((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
		tracked.seen = found;
	}

	private static void tick(MinecraftClient client) {
		if (active.isEmpty() || client.world == null) {
			return;
		}

		Iterator<Map.Entry<Integer, Tracked>> iterator = active.entrySet().iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next().getValue();

			if (!tracked.shapeLocked || tracked.seen < tracked.memberEntityIds.size()) {
				// Keeps retrying after locking too, in case a member's spawn packet arrived late.
				computeCenterOffset(tracked, client);
			}
			if (!tracked.shapeLocked) {
				tracked.shapeWaitTicks++;
				if (tracked.seen < tracked.memberEntityIds.size() && tracked.shapeWaitTicks < SHAPE_LOCK_TIMEOUT_TICKS) {
					continue;
				}
				if (tracked.centerOffsetFromAnchor == null) {
					// The anchor itself never showed up within the timeout - give up on this group.
					iterator.remove();
					continue;
				}
				tracked.shapeLocked = true;
			}

			Entity anchor = client.world.getEntityById(tracked.memberEntityIds.get(0));
			if (anchor == null) {
				if (tracked.stars != null) {
					tracked.stars.remove();
				}
				iterator.remove();
				continue;
			}
			tracked.center = anchor.getPos().add(tracked.centerOffsetFromAnchor);

			if (tracked.stars == null || ++tracked.age >= REFRESH_INTERVAL_TICKS) {
				if (tracked.stars != null) {
					tracked.stars.remove();
				}
				tracked.stars = spawnStars(tracked.center);
				tracked.age = 0;
			} else {
				tracked.stars.setPosition(tracked.center);
			}
		}
	}
}
