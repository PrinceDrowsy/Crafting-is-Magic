package craftingismagic.cim.levitation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;

import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * Server-authoritative Unicorn Levitation.
 *
 * <p>Flow: holding the activation key while looking at a valid block
 * begins a {@link LevitationSession.Phase#SELECTING} phase - scrolling
 * while still held grows/shrinks a connected-block selection (up to
 * {@link #MAX_SELECTION_COUNT}, a 4x4 worth of blocks), shown to the
 * player as an action bar count. Releasing the key locks the selection
 * in: the blocks are removed from the world and become
 * {@link LevitationSession.Phase#HELD}, visualized with networked
 * {@link DisplayEntity.BlockDisplayEntity} entities (so it's correctly
 * synced to everyone, not just the caster) kept in their original
 * relative arrangement. From there scrolling moves the whole group
 * closer/farther, and pressing the activation key again either places it
 * gently or, if there's leftover scroll momentum, throws it - see
 * {@link #place}.
 *
 * <p>Still-deferred scope: no Stamina cost yet (Stamina doesn't exist as
 * a system at all yet). Session state is kept in memory only, keyed by
 * player UUID — it's inherently transient, not the kind of thing that
 * needs to survive a server restart.
 */
public final class LevitationManager {
	private static final double MAX_REACH = 20.0;
	private static final double MIN_DISTANCE = 2.0;
	private static final double MAX_DISTANCE = 20.0;
	private static final double DEFAULT_DISTANCE = 4.0;
	private static final double SCROLL_STEP = 0.5;

	/** 4x4 worth of blocks. */
	private static final int MAX_SELECTION_COUNT = 16;

	private static final double THROW_SPEED = 1.5;
	private static final double THROWN_GRAVITY_PER_TICK = 0.03;
	private static final int MAX_THROWN_TICKS = 100;
	private static final float DAMAGE_PER_HARDNESS = 1.0F;
	private static final float DAMAGE_PER_SPEED = 1.0F;
	private static final float MIN_THROW_DAMAGE = 1.0F;
	private static final float MAX_THROW_DAMAGE = 20.0F;

	/** Per-tick decay of {@link LevitationSession#scrollVelocity} - how quickly a scroll "goes stale". */
	private static final double SCROLL_VELOCITY_DECAY = 0.85;
	private static final double MAX_SCROLL_VELOCITY = 4.0;
	/** Below this, releasing just places gently instead of throwing, and a throw is a plain forward one. */
	private static final double SCROLL_VELOCITY_DEAD_ZONE = 0.1;
	private static final double SCROLL_THROW_SPEED_MULTIPLIER = 1.0;

	private static final Map<UUID, LevitationSession> SESSIONS = new HashMap<>();

	private LevitationManager() {
	}

	public static void startSelection(ServerPlayerEntity player) {
		if (SESSIONS.containsKey(player.getUuid())) {
			return;
		}

		if (!SpeciesAttachment.hasChosenSpecies(player) || SpeciesAttachment.getSpecies(player) != Species.UNICORN) {
			return;
		}

		HitResult hit = player.raycast(MAX_REACH, 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
			return;
		}

		ServerWorld world = player.getServerWorld();
		BlockPos anchor = blockHit.getBlockPos();
		if (!isSelectable(world, anchor)) {
			return;
		}

		LevitationSession session = new LevitationSession(anchor, world, player.getUuid());
		SESSIONS.put(player.getUuid(), session);
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.SELECTING));
		sendSelectionFeedback(player, session);
	}

	public static void adjustSelectionCount(ServerPlayerEntity player, float scrollDelta) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.SELECTING) {
			return;
		}

		int direction = (int) Math.signum(scrollDelta);
		session.selectionCount = MathHelper.clamp(session.selectionCount + direction, 1, MAX_SELECTION_COUNT);
		sendSelectionFeedback(player, session);
	}

	private static void sendSelectionFeedback(ServerPlayerEntity player, LevitationSession session) {
		int selected = computeSelection(session.world, session.anchorPos, session.selectionCount).size();
		player.sendMessage(Text.literal("Levitation: " + selected + " block" + (selected == 1 ? "" : "s") + " selected"), true);
	}

	public static void confirmSelection(ServerPlayerEntity player) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.SELECTING) {
			return;
		}

		ServerWorld world = session.world;
		List<BlockPos> positions = computeSelection(world, session.anchorPos, session.selectionCount);
		if (positions.isEmpty()) {
			SESSIONS.remove(player.getUuid());
			ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
			return;
		}

		List<LevitationBlock> blocks = new ArrayList<>();
		for (BlockPos pos : positions) {
			BlockState state = world.getBlockState(pos);
			world.removeBlock(pos, false);

			DisplayEntity.BlockDisplayEntity display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
			display.setBlockState(state);
			display.setNoGravity(true);
			world.spawnEntity(display);

			blocks.add(new LevitationBlock(pos, state, display));
		}

		session.blocks = blocks;
		session.distance = DEFAULT_DISTANCE;
		session.phase = LevitationSession.Phase.HELD;
		updateHeldPositions(player, session);
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.HOLDING));
	}

	/** Breadth-first from the anchor through directly-adjacent selectable blocks, up to {@code count} of them. */
	private static List<BlockPos> computeSelection(ServerWorld world, BlockPos anchor, int count) {
		List<BlockPos> ordered = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(anchor);
		visited.add(anchor);

		while (!queue.isEmpty() && ordered.size() < count) {
			BlockPos current = queue.poll();
			ordered.add(current);

			for (Direction direction : Direction.values()) {
				BlockPos neighbor = current.offset(direction);
				if (visited.add(neighbor) && isSelectable(world, neighbor)) {
					queue.add(neighbor);
				}
			}
		}

		return ordered;
	}

	private static boolean isSelectable(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return !state.isAir() && !state.hasBlockEntity() && state.getHardness(world, pos) >= 0;
	}

	public static void adjustDistance(ServerPlayerEntity player, float scrollDelta) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.HELD) {
			return;
		}

		session.distance = MathHelper.clamp(session.distance + scrollDelta * SCROLL_STEP, MIN_DISTANCE, MAX_DISTANCE);
		session.scrollVelocity = MathHelper.clamp(session.scrollVelocity + scrollDelta, -MAX_SCROLL_VELOCITY, MAX_SCROLL_VELOCITY);
	}

	/**
	 * Gently places the held group - or, if there's leftover scroll
	 * momentum, throws it instead - unless {@code forceGentle} (Shift+R)
	 * overrides that and guarantees a gentle placement regardless.
	 */
	public static void place(ServerPlayerEntity player, boolean forceGentle) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.HELD) {
			return;
		}

		if (!forceGentle && Math.abs(session.scrollVelocity) >= SCROLL_VELOCITY_DEAD_ZONE) {
			throwGroup(player, session);
			return;
		}

		SESSIONS.remove(player.getUuid());

		ServerWorld world = session.world;
		BlockPos targetAnchor = BlockPos.ofFloored(heldWorldPosition(player, session.distance));
		boolean allReplaceable = session.blocks.stream()
				.allMatch(block -> world.getBlockState(targetAnchor.add(block.originalPos().subtract(session.anchorPos))).isReplaceable());

		for (LevitationBlock block : session.blocks) {
			BlockPos finalPos = allReplaceable
					? targetAnchor.add(block.originalPos().subtract(session.anchorPos))
					: block.originalPos();
			world.setBlockState(finalPos, block.state());
			block.displayEntity().discard();
		}

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
	}

	private static void throwGroup(ServerPlayerEntity player, LevitationSession session) {
		session.phase = LevitationSession.Phase.THROWN;

		Vec3d lookDir = player.getRotationVec(1.0F);
		double scrollVelocity = session.scrollVelocity;
		double speed = THROW_SPEED + Math.abs(scrollVelocity) * SCROLL_THROW_SPEED_MULTIPLIER;
		Vec3d direction = scrollVelocity < 0 ? lookDir.multiply(-1) : lookDir;
		session.velocity = direction.multiply(speed);

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
	}

	/** Ends a session without a deliberate placement - e.g. on disconnect. Restores any held blocks where they came from. */
	public static void cancel(ServerPlayerEntity player) {
		LevitationSession session = SESSIONS.remove(player.getUuid());
		if (session == null) {
			return;
		}

		if (session.phase == LevitationSession.Phase.HELD) {
			for (LevitationBlock block : session.blocks) {
				session.world.setBlockState(block.originalPos(), block.state());
				block.displayEntity().discard();
			}
		}
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, LevitationSession>> iterator = SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, LevitationSession> entry = iterator.next();
			LevitationSession session = entry.getValue();

			if (session.phase == LevitationSession.Phase.THROWN) {
				if (tickThrown(session)) {
					iterator.remove();
				}
				continue;
			}

			if (session.phase == LevitationSession.Phase.SELECTING) {
				continue;
			}

			session.scrollVelocity *= SCROLL_VELOCITY_DECAY;

			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}

			updateHeldPositions(player, session);
		}
	}

	private static void updateHeldPositions(ServerPlayerEntity player, LevitationSession session) {
		Vec3d anchorRenderPos = heldRenderPosition(player, session.distance);
		for (LevitationBlock block : session.blocks) {
			BlockPos offset = block.originalPos().subtract(session.anchorPos);
			Vec3d pos = anchorRenderPos.add(offset.getX(), offset.getY(), offset.getZ());
			block.displayEntity().refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		}
	}

	/** Advances a thrown group one tick. Returns true once its session is over (hit something, or timed out). */
	private static boolean tickThrown(LevitationSession session) {
		session.ticksThrown++;
		if (session.ticksThrown > MAX_THROWN_TICKS) {
			discardAll(session);
			return true;
		}

		ServerWorld world = session.world;
		LevitationBlock anchorBlock = session.blocks.get(0);
		Vec3d currentAnchorPos = anchorBlock.displayEntity().getPos().add(0.5, 0.5, 0.5);
		Vec3d nextAnchorPos = currentAnchorPos.add(session.velocity);

		Box sweptBox = anchorBlock.displayEntity().getBoundingBox().stretch(session.velocity).expand(0.5);
		List<Entity> hitEntities = world.getOtherEntities(anchorBlock.displayEntity(), sweptBox,
				entity -> entity instanceof LivingEntity && !entity.getUuid().equals(session.throwerUuid));

		if (!hitEntities.isEmpty()) {
			LivingEntity target = (LivingEntity) hitEntities.get(0);
			float speed = (float) session.velocity.length();
			float hardness = Math.max(anchorBlock.state().getHardness(world, BlockPos.ofFloored(currentAnchorPos)), 0.5F);
			float damage = MathHelper.clamp(hardness * DAMAGE_PER_HARDNESS * session.blocks.size() + speed * DAMAGE_PER_SPEED,
					MIN_THROW_DAMAGE, MAX_THROW_DAMAGE);
			target.damage(world.getDamageSources().fallingBlock(anchorBlock.displayEntity()), damage);
			world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, BlockPos.ofFloored(currentAnchorPos), Block.getRawIdFromState(anchorBlock.state()));
			discardAll(session);
			return true;
		}

		BlockState blockingState = world.getBlockState(BlockPos.ofFloored(nextAnchorPos));
		if (!blockingState.isReplaceable()) {
			landThrownGroup(session, currentAnchorPos);
			return true;
		}

		session.velocity = session.velocity.add(0, -THROWN_GRAVITY_PER_TICK, 0);
		for (LevitationBlock block : session.blocks) {
			Vec3d pos = block.displayEntity().getPos().add(session.velocity);
			block.displayEntity().refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		}
		return false;
	}

	private static void landThrownGroup(LevitationSession session, Vec3d anchorLandPos) {
		ServerWorld world = session.world;
		BlockPos anchorTarget = BlockPos.ofFloored(anchorLandPos);
		boolean allReplaceable = session.blocks.stream()
				.allMatch(block -> world.getBlockState(anchorTarget.add(block.originalPos().subtract(session.anchorPos))).isReplaceable());

		for (LevitationBlock block : session.blocks) {
			BlockPos finalPos = allReplaceable
					? anchorTarget.add(block.originalPos().subtract(session.anchorPos))
					: block.originalPos();
			world.setBlockState(finalPos, block.state());
			block.displayEntity().discard();
		}
	}

	private static void discardAll(LevitationSession session) {
		for (LevitationBlock block : session.blocks) {
			block.displayEntity().discard();
		}
	}

	/** Where the group's anchor display entity origin should sit (offset to roughly center the block model on the aim point). */
	private static Vec3d heldRenderPosition(ServerPlayerEntity player, double distance) {
		return heldWorldPosition(player, distance).add(-0.5, -0.5, -0.5);
	}

	/** The actual world-space point the player is aiming the held group's anchor at. */
	private static Vec3d heldWorldPosition(ServerPlayerEntity player, double distance) {
		return player.getEyePos().add(player.getRotationVec(1.0F).multiply(distance));
	}
}
