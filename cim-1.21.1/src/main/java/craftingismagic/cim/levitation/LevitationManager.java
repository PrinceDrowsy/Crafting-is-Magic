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

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;

import craftingismagic.cim.network.LevitationAuraS2CPayload;
import craftingismagic.cim.network.LevitationSelectionBoxS2CPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.sound.ModSounds;
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
	private static final double SCROLL_VELOCITY_DECAY = 0.75;
	private static final double MAX_SCROLL_VELOCITY = 4.0;
	/**
	 * Below this, releasing just places gently instead of throwing, and a throw is a plain forward
	 * one. A single scroll notch is worth exactly 1.0, so this needs to sit clearly above that -
	 * otherwise any incidental distance nudge right before releasing (a single notch takes ~0.7s to
	 * decay past a 0.1 dead zone at the old decay rate) reads as deliberate throw momentum.
	 */
	private static final double SCROLL_VELOCITY_DEAD_ZONE = 1.2;
	private static final double SCROLL_THROW_SPEED_MULTIPLIER = 1.0;

	/** Roughly matches the loop clips' own length (~2.3-2.4s) so retriggers land close to seamlessly. */
	private static final int LOOP_INTERVAL_TICKS = 48;

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

	public static void adjustSelectionCount(ServerPlayerEntity player, float scrollDelta, LevitationSelectionAxis axis) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.SELECTING) {
			return;
		}

		int direction = (int) Math.signum(scrollDelta);
		session.selectionCount = MathHelper.clamp(session.selectionCount + direction, 1, MAX_SELECTION_COUNT);
		session.selectionAxis = axis;
		sendSelectionFeedback(player, session);
	}

	private static void sendSelectionFeedback(ServerPlayerEntity player, LevitationSession session) {
		List<BlockPos> positions = computeSelection(session.world, session.anchorPos, session.selectionCount, session.selectionAxis);
		int selected = positions.size();
		player.sendMessage(Text.literal("Levitation: " + selected + " block" + (selected == 1 ? "" : "s") + " selected"), true);

		BlockPos min = session.anchorPos;
		BlockPos max = session.anchorPos;
		for (BlockPos pos : positions) {
			min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
			max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
		}
		ServerPlayNetworking.send(player, new LevitationSelectionBoxS2CPayload(min, max));
	}

	public static void confirmSelection(ServerPlayerEntity player) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.SELECTING) {
			return;
		}

		ServerWorld world = session.world;
		List<BlockPos> positions = computeSelection(world, session.anchorPos, session.selectionCount, session.selectionAxis);
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
		// Start at the distance the group actually was when picked up, rather than snapping it to
		// a fixed default - a block that was far away shouldn't visually teleport toward the
		// player the instant it's confirmed; scrolling closer/farther is the player's own choice.
		double actualDistance = player.getEyePos().distanceTo(Vec3d.ofCenter(session.anchorPos));
		session.distance = MathHelper.clamp(actualDistance, MIN_DISTANCE, MAX_DISTANCE);
		session.phase = LevitationSession.Phase.HELD;
		updateHeldPositions(player, session);
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.HOLDING));
		playLevitationSound(world, player.getPos(), ModSounds.LEVITATION_PICKUP);
		broadcastAura(world, player.getPos(), blocks, true);
	}

	private static void broadcastAura(ServerWorld world, Vec3d pos, List<LevitationBlock> blocks, boolean active) {
		List<Integer> entityIds = blocks.stream().map(block -> block.displayEntity().getId()).toList();
		LevitationAuraS2CPayload payload = new LevitationAuraS2CPayload(entityIds, active);
		for (ServerPlayerEntity nearby : PlayerLookup.around(world, pos, 128.0)) {
			ServerPlayNetworking.send(nearby, payload);
		}
	}

	/**
	 * Breadth-first from the anchor through directly-adjacent selectable blocks, up to {@code
	 * count} of them. {@code axis} hard-restricts which directions may be explored at all - X
	 * limits growth to east/west, Z to north/south, so scrolling with Shift or Alt held shapes the
	 * selection strictly along one line rather than merely biasing which of the six directions
	 * gets picked first (that softer bias-only approach didn't produce a clearly different result
	 * for most selections, which read as "not working").
	 */
	private static List<BlockPos> computeSelection(ServerWorld world, BlockPos anchor, int count, LevitationSelectionAxis axis) {
		List<BlockPos> ordered = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(anchor);
		visited.add(anchor);

		Direction[] directions = allowedDirections(axis);
		while (!queue.isEmpty() && ordered.size() < count) {
			BlockPos current = queue.poll();
			ordered.add(current);

			for (Direction direction : directions) {
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

	private static final Direction[] X_AXIS_ONLY = { Direction.EAST, Direction.WEST };
	private static final Direction[] Z_AXIS_ONLY = { Direction.NORTH, Direction.SOUTH };

	private static Direction[] allowedDirections(LevitationSelectionAxis axis) {
		return switch (axis) {
			case X -> X_AXIS_ONLY;
			case Z -> Z_AXIS_ONLY;
			case ANY -> Direction.values();
		};
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
		playLevitationSound(world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(world, player.getPos(), session.blocks, false);
	}

	private static void throwGroup(ServerPlayerEntity player, LevitationSession session) {
		session.phase = LevitationSession.Phase.THROWN;

		Vec3d lookDir = player.getRotationVec(1.0F);
		double scrollVelocity = session.scrollVelocity;
		double speed = THROW_SPEED + Math.abs(scrollVelocity) * SCROLL_THROW_SPEED_MULTIPLIER;
		Vec3d direction = scrollVelocity < 0 ? lookDir.multiply(-1) : lookDir;
		session.velocity = direction.multiply(speed);

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(session.world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(session.world, player.getPos(), session.blocks, false);
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
			broadcastAura(session.world, player.getPos(), session.blocks, false);
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

			if (session.heldTicks++ % LOOP_INTERVAL_TICKS == 0) {
				SoundEvent loopSound = session.nextLoopIsSecond ? ModSounds.LEVITATION_LOOP_2 : ModSounds.LEVITATION_LOOP_1;
				session.nextLoopIsSecond = !session.nextLoopIsSecond;
				playLevitationSound(session.world, player.getPos(), loopSound);
			}
		}
	}

	private static void playLevitationSound(ServerWorld world, Vec3d pos, SoundEvent sound) {
		world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
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
