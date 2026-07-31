package craftingismagic.cim.levitation;

import java.util.List;
import java.util.UUID;

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

	List<LevitationBlock> blocks = List.of();
	double distance;
	double scrollVelocity = 0.0;
	Vec3d velocity = Vec3d.ZERO;
	int ticksThrown = 0;

	LevitationSession(BlockPos anchorPos, ServerWorld world, UUID throwerUuid) {
		this.anchorPos = anchorPos;
		this.world = world;
		this.throwerUuid = throwerUuid;
		this.phase = Phase.SELECTING;
	}
}
