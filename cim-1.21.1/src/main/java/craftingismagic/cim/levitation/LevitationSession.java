package craftingismagic.cim.levitation;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * One player's Levitation activity, from initial selection through holding
 * and (optionally) throwing.
 */
final class LevitationSession {
	enum Phase {
		/** Growing a selection with scroll; nothing has been picked up yet. */
		SELECTING,
		/** Selection locked in, blocks removed from the world and floating in front of the player. */
		HELD,
		/** Launched; flying under its own simple projectile physics. */
		THROWN
	}

	final BlockPos anchorPos;
	final ServerWorld world;
	final UUID throwerUuid;

	Phase phase;
	int selectionCount = 1;
	/** Which directions to prefer when growing the selection - set by whichever modifier was held on the most recent scroll. */
	LevitationSelectionAxis selectionAxis = LevitationSelectionAxis.ANY;

	List<LevitationBlock> blocks = List.of();
	/** Non-null when this session is holding a single mob instead of a block group or item -
	 * mutually exclusive with {@link #blocks} and {@link #heldItemStack}. A live entity is
	 * repositioned in place each tick, since vanilla tracks living-entity position updates
	 * frequently enough for that to already look smooth on its own. */
	Entity heldEntity;
	/** Non-null when this session is holding a dropped item - mutually exclusive with {@link
	 * #heldEntity} and {@link #blocks}. Items are represented as data + {@link #itemDisplay} rather
	 * than a repositioned live entity (mirroring how blocks work): a bare {@code ItemEntity}'s
	 * position updates sync to clients much less often than a living entity's, which read as
	 * jittery/teleporting when actively moved every tick - the real item is discarded on pickup and
	 * a fresh one respawned from this stack on release. */
	ItemStack heldItemStack;
	/** The frequently-synced visual stand-in shown while {@link #heldItemStack} is set. */
	DisplayEntity.ItemDisplayEntity itemDisplay;
	/** The pickup cost still left to burst-drain before falling back to the normal per-tick hold
	 * drain rate - set once when the hold starts, ticked down in {@code LevitationManager.tick}. Not
	 * a separate up-front deduction: the "pickup cost" is spent quickly over the first few ticks of
	 * the hold instead of vanishing instantly, so lifting something reads as an effort spike rather
	 * than a flat toll. */
	float pickupCostRemaining = 0f;
	/** The eased position everything actually renders/positions relative to - null until the first
	 * tick after pickup (which snaps it to the real starting position rather than lerping from
	 * nowhere). Heavier held things ease toward the aim point more slowly than light ones, so a
	 * Heavy Core visibly resists being swung around while a Feather tracks the crosshair almost
	 * instantly - see {@code LevitationManager#updateHeldPositions}. */
	Vec3d followPos;
	/** How fast {@link #followPos} is moving (blocks/tick) - it follows the aim point on a spring, so a
	 * fast flick overshoots and rebounds - and where the aim point was last tick. */
	Vec3d followVelocity = Vec3d.ZERO;
	Vec3d lastTargetPos;
	/** The held thing's recent swing (blocks/tick, relative to the player), fading like {@link
	 * #scrollVelocity} - shaking it round with the camera builds throw energy, aimed the way it's going. */
	Vec3d swingMomentum = Vec3d.ZERO;
	Vec3d lastPlayerPos;
	/** How far {@link #followPos} moved on the most recent tick (blocks/tick) - a proxy for "how
	 * hard was this just swung," read by {@code LevitationManager#checkHeldHit} to size impact
	 * damage from hitting an entity while still holding something, not just from throwing it. */
	double lastSwingSpeed = 0.0;
	/** Ticks left before a held object can deal impact damage again - prevents standing in contact
	 * with a target from dealing damage every single tick. */
	int heldHitCooldown = 0;
	double distance;
	double scrollVelocity = 0.0;
	Vec3d velocity = Vec3d.ZERO;
	int ticksThrown = 0;
	int heldTicks = 0;
	/** Alternates which of the two loop clips plays next, so one leads into the other and back rather than picking randomly. */
	boolean nextLoopIsSecond = false;

	LevitationSession(BlockPos anchorPos, ServerWorld world, UUID throwerUuid) {
		this.anchorPos = anchorPos;
		this.world = world;
		this.throwerUuid = throwerUuid;
		this.phase = Phase.SELECTING;
	}
}
