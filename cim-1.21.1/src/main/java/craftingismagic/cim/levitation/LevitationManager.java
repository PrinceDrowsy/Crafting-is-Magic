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

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.WorldEvents;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.LevitationAuraS2CPayload;
import craftingismagic.cim.network.LevitationSelectionBoxS2CPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.sound.ModSounds;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.stamina.StaminaManager;

/**
 * Server-authoritative Unicorn Levitation. Picks up either a connected group of blocks, or a
 * single mob/dropped item (a real show mechanic - Twilight levitates Spike the same way) - which
 * one depends purely on what the initial raycast in {@link #startSelection} finds.
 *
 * <p>Block flow: holding the activation key while looking at a valid block
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
 * gently or, if there's leftover scroll momentum, throws it (dealing
 * impact damage via {@link #tickThrown}'s own simple projectile physics) -
 * see {@link #place}.
 *
 * <p>Mob/item flow: a single entity is grabbed outright (see {@link #startEntityHold}), skipping
 * SELECTING entirely - there's no growable selection for one creature. A held mob is repositioned
 * in place each tick; a held item is instead represented by a synced {@code ItemDisplayEntity}
 * stand-in (the real {@code ItemEntity} is discarded on pickup and respawned on release - see
 * {@link #releaseItem}), since vanilla tracks a bare item entity's position too infrequently for
 * per-tick repositioning to read as smooth. Release/throw work the same way as blocks
 * distance/momentum-wise, but a released entity is just handed back to vanilla's own physics/AI
 * with an optional velocity kick - no custom impact damage.
 *
 * <p>Stamina: picking anything up costs {@link #PICKUP_COST_SECONDS} seconds' worth of its own
 * per-second drain rate (burst-drained over {@link #PICKUP_BURST_TICKS}, not deducted instantly),
 * then that same rate keeps draining for as long as it's held (see {@link #computeDrainRate} and
 * {@link #resolvedDrainRate} for how that rate is determined per block/item/mob). Session state is
 * kept in memory only, keyed by player UUID — it's inherently transient, not the kind of thing that
 * needs to survive a server restart.
 */
public final class LevitationManager {
	private static final double MAX_REACH = 20.0;
	/** How many consecutive grass/fern-like blocks {@link #blockRaycast} will walk past before
	 * giving up - comfortably more than any real patch of stacked replaceable blocks. */
	private static final int MAX_REPLACEABLE_SKIPS = 8;
	/** How far past a skipped block's hit point the next raycast leg starts from - just enough to
	 * land inside the next block instead of re-hitting the same face immediately. */
	private static final double REPLACEABLE_SKIP_NUDGE = 0.01;
	private static final double MIN_DISTANCE = 2.0;
	private static final double MAX_DISTANCE = 20.0;
	private static final double SCROLL_STEP = 0.5;

	/** 4x4 worth of blocks. */
	private static final int MAX_SELECTION_COUNT = 16;

	/**
	 * Both of these are the baseline at reference Stamina AND reference weight (see {@link
	 * #computeThrowVelocity}) - a fresh, un-grown, full Stamina pool throwing a reference-weight
	 * thing at max scroll momentum should only clear a few blocks. Deliberately much lower than
	 * this feature's original un-scaled values (1.5 / 1.0), which threw far past that.
	 */
	private static final double THROW_SPEED = 0.13;
	private static final double SCROLL_THROW_SPEED_MULTIPLIER = 0.1;
	private static final double THROWN_GRAVITY_PER_TICK = 0.03;
	private static final int MAX_THROWN_TICKS = 100;
	private static final float DAMAGE_PER_HARDNESS = 1.0F;
	private static final float DAMAGE_PER_SPEED = 1.0F;
	private static final float MIN_THROW_DAMAGE = 1.0F;
	private static final float MAX_THROW_DAMAGE = 20.0F;

	/** A held object can hit entities without being thrown - swinging it into something deals
	 * impact damage sized by both how heavy it is and how fast it was just swung ({@code
	 * LevitationSession#lastSwingSpeed}), reusing the same {@link #MIN_THROW_DAMAGE}/{@link
	 * #MAX_THROW_DAMAGE} clamp range as an actual throw for a consistent feel. */
	private static final float HELD_HIT_DAMAGE_PER_WEIGHT = 1.0F;
	private static final float HELD_HIT_DAMAGE_PER_SPEED = 2.0F;
	/** Ticks between held-hit damage instances - without this, standing in contact with a target
	 * would deal damage every single tick. */
	private static final int HELD_HIT_COOLDOWN_TICKS = 10;
	private static final double HELD_HIT_RADIUS = 0.75;

	/**
	 * Catching an in-flight projectile (an arrow, a trident, or a thrown potion) drains its own
	 * velocity over time instead of the normal weight-based hold-drain: each tick, its speed drops
	 * by {@link #PROJECTILE_VELOCITY_DRAIN_PER_TICK}, and the Stamina cost is proportional to
	 * however much speed was actually removed. Running out of Stamina, or releasing early, lets it
	 * resume flight at whatever velocity remains; fully draining it to a stop converts it into a
	 * normal held item instead (see {@link #convertCaughtProjectileToItem}) - a potion caught this
	 * way never gets the chance to shatter/apply its effect, since it's intercepted before hitting
	 * anything.
	 */
	private static final double PROJECTILE_VELOCITY_DRAIN_PER_TICK = 0.1;
	private static final double PROJECTILE_STAMINA_PER_VELOCITY = 2.0;
	private static final double MIN_CATCHABLE_PROJECTILE_SPEED = 0.05;

	/**
	 * "Weight" isn't an abstract multiplier - it's the literal Stamina-per-second cost of holding
	 * something, a property of the thing itself, independent of the holder's own max Stamina. A
	 * bigger Stamina pool doesn't drain any slower in absolute terms; it just has more to spend
	 * before running out, which is exactly why holding the same object *feels* more sustainable to a
	 * higher-Stamina player without the drain rate itself needing to change. 1.0 = reference (a
	 * single generic block, a villager/player-sized mob, a light item stack) - see {@link
	 * #computeDrainRate}.
	 */
	private static final double REFERENCE_ITEM_COUNT = 16.0;
	/** Roughly a villager/player-sized mob (width 0.6, height 1.95). */
	private static final double REFERENCE_ENTITY_VOLUME = 0.6 * 0.6 * 1.95;
	private static final double MIN_DRAIN_RATE = 0.25;
	private static final int TICKS_PER_SECOND = 20;

	/**
	 * Explicit Stamina/second drain-rate overrides for specific items/blocks whose real weight
	 * doesn't match the generic count-based formula - checked before falling back to it, and NOT
	 * clamped by {@link #MIN_DRAIN_RATE} (that floor exists to stop the generic formula producing a
	 * near-zero rate from a tiny stack count, but Feather is deliberately below it on purpose).
	 * Absolute rates, not a per-unit multiplier - Heavy Core and the Mace are both unstackable
	 * anyway, so a per-unit scale wouldn't mean anything for them. Keyed by {@code Item} so a
	 * picked-up BLOCK (via {@code Block#asItem()}) and a picked-up ITEM stack can share one table.
	 */
	private static final Map<Item, Double> DRAIN_RATE_OVERRIDES = Map.ofEntries(
			Map.entry(Items.HEAVY_CORE, 20.0),
			Map.entry(Items.MACE, 20.0),
			Map.entry(Items.ANVIL, 20.0),
			Map.entry(Items.CHIPPED_ANVIL, 20.0),
			Map.entry(Items.DAMAGED_ANVIL, 20.0),
			Map.entry(Items.FEATHER, 0.05),
			// Netherite is explicitly lore-heavy in vanilla itself (it's the one material that sinks
			// in lava instead of floating) - heavier than the generic Metal class, though not quite
			// at the "can't lift it at all" tier the block/tool overrides above sit at.
			Map.entry(Items.NETHERITE_INGOT, 10.0),
			Map.entry(Items.NETHERITE_BLOCK, 10.0),
			Map.entry(Items.NETHERITE_SCRAP, 10.0),
			Map.entry(Items.NETHERITE_SWORD, 10.0),
			Map.entry(Items.NETHERITE_PICKAXE, 10.0),
			Map.entry(Items.NETHERITE_AXE, 10.0),
			Map.entry(Items.NETHERITE_SHOVEL, 10.0),
			Map.entry(Items.NETHERITE_HOE, 10.0),
			Map.entry(Items.NETHERITE_HELMET, 10.0),
			Map.entry(Items.NETHERITE_CHESTPLATE, 10.0),
			Map.entry(Items.NETHERITE_LEGGINGS, 10.0),
			Map.entry(Items.NETHERITE_BOOTS, 10.0)
	);

	/**
	 * Broad weight classes, checked after {@link #DRAIN_RATE_OVERRIDES} (individually-profiled
	 * exceptions) and before the generic count/volume fallback - tag-driven where a suitable vanilla
	 * tag exists, rather than hand-listing every member item. Not exhaustive; anything that matches
	 * neither an exception nor a class keeps using the existing generic formula, same as before this
	 * was added.
	 */
	private static final double FEATHERWEIGHT_RATE = 0.1;
	private static final List<TagKey<Item>> FEATHERWEIGHT_TAGS = List.of(
			ItemTags.WOOL, ItemTags.WOOL_CARPETS, ItemTags.SAPLINGS, ItemTags.FLOWERS
	);
	private static final double LIGHT_RATE = 0.4;
	private static final List<TagKey<Item>> LIGHT_TAGS = List.of(
			ItemTags.LOGS, ItemTags.PLANKS, ItemTags.LEAVES
	);
	/** No single vanilla tag covers "metal ingots/blocks" as a group, unlike the classes above - an
	 * explicit list here instead of a tag lookup. */
	private static final double METAL_RATE = 5.0;
	private static final Set<Item> METAL_ITEMS = Set.of(
			Items.IRON_INGOT, Items.IRON_BLOCK, Items.RAW_IRON, Items.RAW_IRON_BLOCK,
			Items.GOLD_INGOT, Items.GOLD_BLOCK, Items.RAW_GOLD, Items.RAW_GOLD_BLOCK,
			Items.COPPER_INGOT, Items.COPPER_BLOCK, Items.RAW_COPPER, Items.RAW_COPPER_BLOCK
	);

	/** Mobs get the same exceptions-before-generic-formula treatment as items/blocks - the generic
	 * {@link #entityWeight} formula is pure bounding-box volume, which doesn't know an Iron Golem is
	 * made of iron or a Warden is a living slab of dense flesh/rock. Not exhaustive. */
	private static final Map<EntityType<?>, Double> MOB_DRAIN_RATE_OVERRIDES = Map.of(
			EntityType.IRON_GOLEM, 12.0,
			EntityType.RAVAGER, 10.0,
			EntityType.WARDEN, 20.0
	);

	/** Heavier things ease toward the aim point more slowly instead of snapping there instantly -
	 * see {@code LevitationSession#followPos}. Higher {@code WEIGHT_SLUGGISHNESS} makes drain rate
	 * matter more for how laggy the follow feels; {@code MIN_LERP_FACTOR} keeps even the heaviest
	 * things from feeling literally frozen. */
	private static final double WEIGHT_SLUGGISHNESS = 0.3;
	private static final double MIN_LERP_FACTOR = 0.05;
	/**
	 * The held thing follows the aim point on a spring, like a rubber band: a fast flick of the camera
	 * leaves it lagging, then it overshoots past the aim and rebounds before settling. Its stiffness is
	 * this times the follow factor above (so heavy things are softer and swing wider), and its damping
	 * this fraction of what would stop it overshooting at all. Damped against the aim point's own motion,
	 * so just walking along carries it without dragging behind.
	 */
	private static final double SPRING_STIFFNESS = 0.4;
	private static final double SPRING_DAMPING_RATIO = 0.45;
	/** An aim point this far from the held thing in one go (a teleport) snaps it there instead. */
	private static final double SPRING_SNAP_DISTANCE = 16.0;
	/**
	 * Swinging builds throw energy as scrolling does: the held thing's own speed (blocks/tick, relative to
	 * the player), fading at the same rate as a scroll's. A flick past {@link #SWING_DEAD_ZONE} makes
	 * letting go a throw - a slow, steady turn never gets there - flung the way it's going, this fraction
	 * of its speed, up to a limit.
	 */
	private static final double SWING_DEAD_ZONE = 0.6;
	private static final double SWING_THROW_MULTIPLIER = 0.4;
	private static final double MAX_SWING_THROW = 0.8;

	/** Pickup cost is expressed as "how many seconds' worth of holding this would normally cost,"
	 * not a separate flat number - ties the one-time pickup toll and the ongoing hold drain to the
	 * same underlying rate instead of two independently-tuned constants. */
	private static final double PICKUP_COST_SECONDS = 5.0;
	/** The pickup cost isn't a flat up-front toll - it's burst-drained over this many ticks right as
	 * the hold starts (an effort spike proportional to drain rate, since the burst amount is the
	 * same total cost squeezed into a fixed window), then the normal per-second rate takes over. */
	private static final int PICKUP_BURST_TICKS = 6;

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

	/** Roughly matches the loop clips' own length (~2.3-2.4s) so retriggers land close to seamlessly. */
	private static final int LOOP_INTERVAL_TICKS = 48;

	/**
	 * How close a held item/block group must be scrolled in before Shift+activate pockets it into
	 * the inventory instead of placing it (see {@link #place}) - happens to equal {@link
	 * #MIN_DISTANCE} today (the closest scroll distance already allowed), but kept as its own named
	 * constant since that's a coincidence of the current tuning, not a guarantee. Never applies to a
	 * held mob or a still-in-flight caught projectile - only a real item or block group.
	 */
	private static final double POCKET_DISTANCE_THRESHOLD = 2.0;

	private static final Map<UUID, LevitationSession> SESSIONS = new HashMap<>();

	private LevitationManager() {
	}

	/** Whether the player is channelling Levitation - choosing what to lift, or holding it (not once it's thrown). */
	public static boolean isChannelling(ServerPlayerEntity player) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		return session != null && session.phase != LevitationSession.Phase.THROWN;
	}

	public static void startSelection(ServerPlayerEntity player) {
		if (SESSIONS.containsKey(player.getUuid())) {
			return;
		}

		if (!SpeciesAttachment.hasChosenSpecies(player) || SpeciesAttachment.getSpecies(player) != Species.UNICORN) {
			return;
		}

		// A block hit caps how far the entity search can reach - without this, an entity standing
		// behind a wall the player is looking at would still get picked up right through it, since
		// ProjectileUtil.getEntityCollision has no concept of block occlusion on its own.
		HitResult hit = blockRaycast(player);
		double maxEntityDistance = hit.getType() == HitResult.Type.BLOCK ? player.getEyePos().distanceTo(hit.getPos()) : MAX_REACH;

		Vec3d rayStart = player.getCameraPosVec(1.0F);
		Vec3d lookDir = player.getRotationVec(1.0F);
		Vec3d rayEnd = rayStart.add(lookDir.multiply(maxEntityDistance));
		Box searchBox = player.getBoundingBox().stretch(lookDir.multiply(maxEntityDistance)).expand(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityCollision(player.getServerWorld(), player, rayStart, rayEnd, searchBox,
				entity -> entity != player && isLevitatableEntity(entity));
		if (entityHit != null) {
			startEntityHold(player, entityHit.getEntity());
			return;
		}

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

	/**
	 * A plain raycast stops on grass/ferns/etc. ({@link BlockTags#REPLACEABLE}) just like a solid
	 * block, since they have a real (if thin) outline hitbox for interaction purposes even though
	 * they have no collision shape for movement - meaning without this, aiming through a patch of
	 * grass at the actual intended target (a block behind/under it, or an entity standing in it)
	 * would grab the grass instead. This walks the ray past any such block instead of stopping on
	 * it, up to {@link #MAX_REPLACEABLE_SKIPS} times (comfortably more than any real patch of
	 * consecutive grass/tall-grass layers), continuing from just past each skipped block.
	 */
	private static HitResult blockRaycast(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d lookDir = player.getRotationVec(1.0F);
		Vec3d start = player.getCameraPosVec(1.0F);
		double remaining = MAX_REACH;

		for (int i = 0; i < MAX_REPLACEABLE_SKIPS && remaining > 0; i++) {
			Vec3d end = start.add(lookDir.multiply(remaining));
			BlockHitResult hit = world.raycast(new RaycastContext(start, end,
					RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() != HitResult.Type.BLOCK || !isGroundClutter(world.getBlockState(hit.getBlockPos()))) {
				return hit;
			}
			remaining -= start.distanceTo(hit.getPos()) + REPLACEABLE_SKIP_NUDGE;
			start = hit.getPos().add(lookDir.multiply(REPLACEABLE_SKIP_NUDGE));
		}
		return BlockHitResult.createMissed(start, Direction.getFacing(lookDir.x, lookDir.y, lookDir.z), BlockPos.ofFloored(start));
	}

	/** {@link BlockTags#REPLACEABLE} covers snow layers too (more snow can accumulate on top of/
	 * through them, same as grass), but snow is deliberately exempted - a real accumulated block
	 * someone might actually want to Levitate, unlike grass/ferns/dead bush/etc., which are pure
	 * ground clutter nobody ever means to target. */
	private static boolean isGroundClutter(BlockState state) {
		return state.isIn(BlockTags.REPLACEABLE) && !state.isOf(Blocks.SNOW);
	}

	/** A mob (any non-player {@code LivingEntity} - villagers included, matching the show reference
	 * of Twilight levitating Spike), a dropped item, or a still-in-flight projectile (see {@link
	 * #isCatchableProjectile}). */
	private static boolean isLevitatableEntity(Entity entity) {
		return entity instanceof ItemEntity
				|| (entity instanceof LivingEntity && !(entity instanceof PlayerEntity))
				|| isCatchableProjectile(entity);
	}

	/** Only a projectile that's still actually moving counts as "in flight" - one that's already
	 * stuck in a block (or, for a potion, already shattered) reads as velocity near zero, which is
	 * exactly what "caught" already means for a held one anyway (see {@link
	 * #convertCaughtProjectileToItem}), so there's nothing left to meaningfully catch. Covers arrows
	 * and tridents ({@link PersistentProjectileEntity}) and thrown potions ({@link PotionEntity}). */
	private static boolean isCatchableProjectile(Entity entity) {
		return isCatchableProjectileType(entity) && entity.getVelocity().length() > MIN_CATCHABLE_PROJECTILE_SPEED;
	}

	private static boolean isCatchableProjectileType(Entity entity) {
		return entity instanceof PersistentProjectileEntity || entity instanceof PotionEntity;
	}

	/** {@link PersistentProjectileEntity} and {@link PotionEntity} expose their underlying item
	 * stack under different method names ({@code getItemStack()} vs. {@code getStack()}) - this is
	 * the one place that needs to know that. */
	private static ItemStack caughtProjectileItemStack(Entity projectile) {
		if (projectile instanceof PersistentProjectileEntity persistent) {
			return persistent.getItemStack().copy();
		}
		return ((PotionEntity) projectile).getStack().copy();
	}

	/**
	 * Grabbing a mob or item skips {@link LevitationSession.Phase#SELECTING} entirely and goes
	 * straight to {@link LevitationSession.Phase#HELD} - there's nothing to grow a selection into
	 * (unlike a connected block region, a single creature is just grabbed outright). A held mob is
	 * repositioned in place each tick (see {@link #updateHeldEntityPosition}) with no stand-in
	 * needed - vanilla tracks living entities' position updates frequently enough that this reads
	 * smoothly on its own. A held item is different: vanilla tracks a bare {@code ItemEntity}'s
	 * position much less often (items normally just sit still), which reads as jittery/teleporting
	 * once it's actively moved every tick - and simply hiding the original behind an invisible flag
	 * didn't actually fix that (still tracked at the same slow rate, invisible or not). So it gets
	 * the same treatment as blocks instead: the real item is discarded outright, a frequently-synced
	 * {@code ItemDisplayEntity} stands in for it while held, and release respawns a fresh
	 * {@code ItemEntity} from the saved stack - see {@link #releaseItem}.
	 */
	private static void startEntityHold(ServerPlayerEntity player, Entity target) {
		double weight = target instanceof ItemEntity item ? itemWeight(item.getStack()) : entityWeight(target);
		float pickupCost = (float) (PICKUP_COST_SECONDS * weight);
		if (!StaminaManager.hasEnough(player, pickupCost)) {
			playFizzledPickupAttempt(player);
			return;
		}

		ServerWorld world = player.getServerWorld();
		LevitationSession session = new LevitationSession(BlockPos.ofFloored(target.getPos()), world, player.getUuid());
		session.pickupCostRemaining = pickupCost;
		double actualDistance = player.getEyePos().distanceTo(target.getPos());
		session.distance = MathHelper.clamp(actualDistance, MIN_DISTANCE, MAX_DISTANCE);
		session.phase = LevitationSession.Phase.HELD;

		int auraEntityId;
		if (target instanceof ItemEntity item) {
			session.heldItemStack = item.getStack().copy();
			DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
			display.getStackReference(0).set(session.heldItemStack);
			display.setNoGravity(true);
			spawnDisplay(world, display);
			session.itemDisplay = display;
			item.discard();
			auraEntityId = display.getId();
		} else {
			session.heldEntity = target;
			target.setNoGravity(true);
			if (target instanceof MobEntity mob) {
				mob.getNavigation().stop();
			}
			auraEntityId = target.getId();
		}

		SESSIONS.put(player.getUuid(), session);
		updateHeldPositions(player, session);
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.HOLDING));
		playLevitationSound(world, player.getPos(), ModSounds.LEVITATION_PICKUP);
		broadcastAura(world, player.getPos(), List.of(auraEntityId), true, magicColor(player));
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

		float pickupCost = (float) (PICKUP_COST_SECONDS * blockGroupWeight(world, positions));
		if (!StaminaManager.hasEnough(player, pickupCost)) {
			SESSIONS.remove(player.getUuid());
			ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
			playFizzledPickupAttempt(player);
			return;
		}
		session.pickupCostRemaining = pickupCost;

		List<LevitationBlock> blocks = new ArrayList<>();
		for (BlockPos pos : positions) {
			BlockState state = world.getBlockState(pos);
			world.removeBlock(pos, false);

			DisplayEntity.BlockDisplayEntity display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
			display.setBlockState(state);
			display.setNoGravity(true);
			spawnDisplay(world, display);

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
		broadcastAura(world, player.getPos(), blockDisplayIds(blocks), true, magicColor(player));
	}

	/** Marks every stand-in display levitation spawns, so a stale one can be recognised when it loads. */
	private static final String DISPLAY_TAG = Cim.MOD_ID + "_levitation";
	/** The stand-in displays of holds still going on (the UUIDs of the ones spawned and not yet discarded). */
	private static final Set<UUID> LIVE_DISPLAYS = new HashSet<>();

	private static void spawnDisplay(ServerWorld world, DisplayEntity display) {
		display.addCommandTag(DISPLAY_TAG);
		LIVE_DISPLAYS.add(display.getUuid());
		world.spawnEntity(display);
	}

	/**
	 * A held thing is only a display entity standing in for it, and a hold never outlives the game - so a
	 * stand-in that loads from a save (the world autosaved mid-hold, then the game closed without saving
	 * again, say) belongs to no hold and would float there for good, unbreakable. And the thing it stood in
	 * for was taken out of the world when it was picked up, in that same save. So each such one, as it
	 * loads, gives back what it held - a block set back where it floated (or dropped, if that spot's taken),
	 * an item dropped - and is removed. Done on the next tick: removing an entity while it's still being
	 * loaded leaves the client drawing it.
	 */
	public static void registerStaleDisplayCleanup() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof DisplayEntity && entity.getCommandTags().contains(DISPLAY_TAG) && !LIVE_DISPLAYS.contains(entity.getUuid())) {
				world.getServer().execute(() -> restoreStaleDisplay(world, entity));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				LIVE_DISPLAYS.remove(entity.getUuid());
			}
		});
	}

	private static void restoreStaleDisplay(ServerWorld world, Entity entity) {
		if (entity.isRemoved()) {
			return;
		}
		if (entity instanceof DisplayEntity.BlockDisplayEntity display) {
			// A block display stands at its block's lower corner.
			setOrDrop(world, BlockPos.ofFloored(display.getPos().add(0.5, 0.5, 0.5)), display.getBlockState());
		} else if (entity instanceof DisplayEntity.ItemDisplayEntity display) {
			ItemStack stack = display.getStackReference(0).get();
			if (!stack.isEmpty()) {
				world.spawnEntity(new ItemEntity(world, display.getX(), display.getY(), display.getZ(), stack.copy()));
			}
		}
		entity.discard();
	}

	private static List<Integer> blockDisplayIds(List<LevitationBlock> blocks) {
		return blocks.stream().map(block -> block.displayEntity().getId()).toList();
	}

	private static void broadcastAura(ServerWorld world, Vec3d pos, List<Integer> entityIds, boolean active) {
		broadcastAura(world, pos, entityIds, active, 0);
	}

	/** The caster's magic color (docs/magic-aura.md: one color per caster) - their Stamina color for
	 * now, the same color Verse already treats as their aura (see {@code VerseRuntime#auraColor}). */
	private static int magicColor(ServerPlayerEntity player) {
		return StaminaColorAttachment.getColors(player).fillColor() & 0xFFFFFF;
	}

	private static void broadcastAura(ServerWorld world, Vec3d pos, List<Integer> entityIds, boolean active, int color) {
		LevitationAuraS2CPayload payload = new LevitationAuraS2CPayload(entityIds, active, color);
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
		// isGroundClutter also excludes grass/ferns/etc. (but not snow) from being swept into a
		// growing block-group selection, not just from being picked as the initial anchor (see
		// blockRaycast).
		return !state.isAir() && !isGroundClutter(state) && !state.hasBlockEntity() && state.getHardness(world, pos) >= 0;
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
	 *
	 * <p>Shift+activate scrolled in close ({@link #POCKET_DISTANCE_THRESHOLD}) is instead read as
	 * "pocket it" (see {@link #pocketHeldThing}) - but only for a real item or block group, never a
	 * held mob or a still-in-flight caught projectile, both of which fall straight through to the
	 * normal gentle release below regardless of distance.
	 */
	public static void place(ServerPlayerEntity player, boolean forceGentle) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase != LevitationSession.Phase.HELD) {
			return;
		}

		boolean pocketable = session.heldItemStack != null || !session.blocks.isEmpty();
		if (forceGentle && pocketable && session.distance <= POCKET_DISTANCE_THRESHOLD) {
			SESSIONS.remove(player.getUuid());
			pocketHeldThing(player, session);
			return;
		}

		boolean throwing = !forceGentle && (Math.abs(session.scrollVelocity) >= SCROLL_VELOCITY_DEAD_ZONE
				|| session.swingMomentum.length() >= SWING_DEAD_ZONE);

		if (session.heldItemStack != null) {
			SESSIONS.remove(player.getUuid());
			releaseItem(player, session, throwing);
			return;
		}

		if (session.heldEntity != null) {
			SESSIONS.remove(player.getUuid());
			releaseEntity(player, session, throwing);
			return;
		}

		if (throwing) {
			// Deliberately NOT removed from SESSIONS here - throwGroup transitions to THROWN phase,
			// which the session needs to stay in the map for so tick()'s THROWN branch (tickThrown)
			// keeps advancing it.
			throwGroup(player, session);
			return;
		}

		SESSIONS.remove(player.getUuid());
		placeBlocksGently(player, session);
	}

	/**
	 * Puts a held item or block group straight into the player's inventory instead of placing it
	 * back in the world - the real stack/block state was already removed from the world back when
	 * it was picked up (the world only ever held a visual display stand-in since then), so there's
	 * no separate world-side copy to worry about duplicating: this just discards the display(s) and
	 * hands the same stack data to the inventory, never spawning a real dropped-item/block entity in
	 * the world first. {@link PlayerEntity#giveItemStack} falls back to dropping at the player's feet
	 * on its own if the inventory has no room, matching vanilla's own give-or-drop behavior.
	 */
	private static void pocketHeldThing(ServerPlayerEntity player, LevitationSession session) {
		ServerWorld world = session.world;
		List<Integer> displayIds;

		if (session.heldItemStack != null) {
			player.giveItemStack(session.heldItemStack);
			session.itemDisplay.discard();
			displayIds = List.of(session.itemDisplay.getId());
		} else {
			for (LevitationBlock block : session.blocks) {
				player.giveItemStack(new ItemStack(block.state().getBlock().asItem()));
				block.displayEntity().discard();
			}
			displayIds = blockDisplayIds(session.blocks);
		}

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(world, player.getPos(), displayIds, false);
	}

	private static void placeBlocksGently(ServerPlayerEntity player, LevitationSession session) {
		ServerWorld world = session.world;
		BlockPos targetAnchor = BlockPos.ofFloored(heldWorldPosition(player, session.distance));
		boolean allReplaceable = session.blocks.stream()
				.allMatch(block -> world.getBlockState(targetAnchor.add(block.originalPos().subtract(session.anchorPos))).isReplaceable());

		for (LevitationBlock block : session.blocks) {
			BlockPos finalPos = allReplaceable
					? targetAnchor.add(block.originalPos().subtract(session.anchorPos))
					: block.originalPos();
			setOrDrop(world, finalPos, block.state());
			block.displayEntity().discard();
		}

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(world, player.getPos(), blockDisplayIds(session.blocks), false);
	}

	/**
	 * Dispatches to whichever gentle release fits what's held, WITHOUT touching {@link #SESSIONS}
	 * itself - unlike {@link #place}, this is called from inside {@link #tick}'s iteration over
	 * {@link #SESSIONS}, so the caller must remove the entry via the iterator itself (calling {@link
	 * java.util.Map#remove} here instead would corrupt that iteration).
	 */
	private static void releaseGentlyOnEmpty(ServerPlayerEntity player, LevitationSession session) {
		if (session.heldItemStack != null) {
			releaseItem(player, session, false);
			return;
		}
		if (session.heldEntity != null) {
			releaseEntity(player, session, false);
			return;
		}
		placeBlocksGently(player, session);
	}

	/**
	 * Lets go of a still-flying caught projectile before its velocity has been fully drained - it
	 * just continues on its own already-reduced trajectory (vanilla's own physics take it from
	 * here), not a fresh throw computed from the player's aim the way a deliberate block/item/mob
	 * throw is. Called from inside {@link #tick}'s iteration, same {@link #SESSIONS} caveat as
	 * {@link #releaseGentlyOnEmpty}.
	 */
	private static void resumeProjectileFlight(ServerPlayerEntity player, LevitationSession session, Entity projectile) {
		projectile.setNoGravity(false);
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(session.world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(session.world, player.getPos(), List.of(projectile.getId()), false);
	}

	/**
	 * A caught projectile whose velocity has been fully drained to a stop converts into a normal
	 * held item mid-hold - the projectile entity is discarded and its own item form (a real arrow/
	 * trident stack) takes over as {@link LevitationSession#heldItemStack}, reusing every bit of the
	 * existing item-hold machinery (weight, drain, gentle-place/throw, respawn-on-release) for free
	 * rather than needing a separate "caught projectile" release path.
	 */
	private static void convertCaughtProjectileToItem(LevitationSession session) {
		Entity projectile = session.heldEntity;
		ItemStack stack = caughtProjectileItemStack(projectile);
		ServerWorld world = session.world;

		DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
		display.getStackReference(0).set(stack);
		display.setNoGravity(true);
		Vec3d pos = session.followPos != null ? session.followPos : projectile.getPos();
		display.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		spawnDisplay(world, display);

		projectile.discard();
		session.heldEntity = null;
		session.heldItemStack = stack;
		session.itemDisplay = display;
	}

	private static void throwGroup(ServerPlayerEntity player, LevitationSession session) {
		session.phase = LevitationSession.Phase.THROWN;
		session.velocity = computeThrowVelocity(player, session);

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(session.world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(session.world, player.getPos(), blockDisplayIds(session.blocks), false);
	}

	/**
	 * Both current Stamina and how heavy the held thing is affect throw distance - a fresh, un-grown
	 * Stamina pool (see {@link StaminaAttachment#BASE_MAX_STAMINA}) at full current Stamina throwing
	 * something around reference weight (a single block, a villager-sized mob, a light item stack)
	 * should only clear a few blocks; heavier things fall short of that, lighter ones exceed it, and
	 * both draining Stamina in the moment and growing the pool over a playthrough scale the whole
	 * thing up or down together. Tunable/testable numbers, not derived from any real physics model.
	 *
	 * <p>Two kinds of energy add together: scrolling throws it straight along the look direction (or back
	 * toward the player), and swinging it flings it the way it's swinging - so a sideways flick while
	 * scrolling forward sends it off on a diagonal. Each only counts past its own dead zone. A swing's
	 * already slower for something heavy (it's on a softer spring), so only Stamina scales it.
	 */
	private static Vec3d computeThrowVelocity(ServerPlayerEntity player, LevitationSession session) {
		double staminaFactor = StaminaAttachment.getStamina(player).current() / StaminaAttachment.BASE_MAX_STAMINA;
		Vec3d velocity = Vec3d.ZERO;
		double scrollVelocity = session.scrollVelocity;
		if (Math.abs(scrollVelocity) >= SCROLL_VELOCITY_DEAD_ZONE) {
			Vec3d lookDir = player.getRotationVec(1.0F);
			double baseSpeed = THROW_SPEED + Math.abs(scrollVelocity) * SCROLL_THROW_SPEED_MULTIPLIER;
			double weightFactor = computeDrainRate(session);
			double speed = baseSpeed * staminaFactor / weightFactor;
			velocity = (scrollVelocity < 0 ? lookDir.multiply(-1) : lookDir).multiply(speed);
		}
		if (session.swingMomentum.length() >= SWING_DEAD_ZONE) {
			Vec3d swing = session.swingMomentum.multiply(SWING_THROW_MULTIPLIER);
			if (swing.length() > MAX_SWING_THROW) {
				swing = swing.normalize().multiply(MAX_SWING_THROW);
			}
			velocity = velocity.add(swing.multiply(staminaFactor));
		}
		return velocity;
	}

	/** 1.0 = reference weight (a single block, a villager/player-sized mob, a light item stack) -
	 * below 1.0 for lighter-than-reference things (throws farther), above for heavier ones (falls
	 * short). Split into per-category helpers (below) so pickup-cost pre-checks can compute weight
	 * from the raw candidate (an {@code ItemStack}, an {@code Entity}, a block-position list) before
	 * a {@link LevitationSession} even exists yet, not just from an already-established hold. */
	private static double computeDrainRate(LevitationSession session) {
		if (session.heldItemStack != null) {
			return itemWeight(session.heldItemStack);
		}
		if (session.heldEntity != null) {
			return entityWeight(session.heldEntity);
		}
		return blockGroupWeight(session.blocks);
	}

	/**
	 * Resolves an exception or weight-class rate for this item, or {@code null} if it matches
	 * neither - the caller falls back to its own generic per-category default in that case. Shared
	 * between item-stack and block-group lookups so both check the same exceptions/classes in the
	 * same order. {@code getDefaultStack()} is just a cheap probe for tag membership, not a real
	 * held stack.
	 */
	private static Double resolvedDrainRate(Item item) {
		Double override = DRAIN_RATE_OVERRIDES.get(item);
		if (override != null) {
			return override;
		}

		ItemStack probe = item.getDefaultStack();
		for (TagKey<Item> tag : FEATHERWEIGHT_TAGS) {
			if (probe.isIn(tag)) {
				return FEATHERWEIGHT_RATE;
			}
		}
		for (TagKey<Item> tag : LIGHT_TAGS) {
			if (probe.isIn(tag)) {
				return LIGHT_RATE;
			}
		}
		if (METAL_ITEMS.contains(item)) {
			return METAL_RATE;
		}
		return null;
	}

	/** Clamped away from 0 by {@link #MIN_DRAIN_RATE} so an especially tiny/light thing can't
	 * approach infinite throw speed - {@link #resolvedDrainRate} results skip that floor on purpose
	 * (Feather is deliberately below it). */
	private static double itemWeight(ItemStack stack) {
		Double resolved = resolvedDrainRate(stack.getItem());
		if (resolved != null) {
			return resolved;
		}
		return Math.max(MIN_DRAIN_RATE, stack.getCount() / REFERENCE_ITEM_COUNT);
	}

	private static double entityWeight(Entity entity) {
		Double override = MOB_DRAIN_RATE_OVERRIDES.get(entity.getType());
		if (override != null) {
			return override;
		}
		double volume = entity.getWidth() * entity.getWidth() * entity.getHeight();
		return Math.max(MIN_DRAIN_RATE, volume / REFERENCE_ENTITY_VOLUME);
	}

	/** Sum of each block's own resolved rate (exception/class if one matches, else the generic
	 * 1.0-per-block that makes a plain group's weight equal to its block count, matching the
	 * original pre-override behavior exactly when nothing in the group is special-cased). */
	private static double blockGroupWeight(List<LevitationBlock> blocks) {
		double weight = 0;
		for (LevitationBlock block : blocks) {
			Double resolved = resolvedDrainRate(block.state().getBlock().asItem());
			weight += resolved != null ? resolved : 1.0;
		}
		return weight;
	}

	private static double blockGroupWeight(ServerWorld world, List<BlockPos> positions) {
		double weight = 0;
		for (BlockPos pos : positions) {
			Double resolved = resolvedDrainRate(world.getBlockState(pos).getBlock().asItem());
			weight += resolved != null ? resolved : 1.0;
		}
		return weight;
	}

	/**
	 * Releases a held mob - unlike thrown blocks (which fly under {@link #tickThrown}'s own simple
	 * projectile physics and deal impact damage on hit), a released mob just becomes a normal live
	 * entity again with an optional velocity kick: vanilla's own physics/AI/fall damage take it from
	 * here, no custom collision-sweep or damage formula needed.
	 */
	private static void releaseEntity(ServerPlayerEntity player, LevitationSession session, boolean throwing) {
		Entity entity = session.heldEntity;
		entity.setNoGravity(false);

		if (throwing) {
			entity.setVelocity(computeThrowVelocity(player, session));
			entity.velocityModified = true;
		}

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(session.world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(session.world, player.getPos(), List.of(entity.getId()), false);
	}

	/** Releases a held item - respawns a real {@code ItemEntity} from the saved stack (see {@link
	 * #startEntityHold}) at wherever the display was floating, then discards the display. */
	private static void releaseItem(ServerPlayerEntity player, LevitationSession session, boolean throwing) {
		// Where it's actually floating - mid-swing, that may not be the aim point.
		Vec3d spawnPos = session.followPos != null ? session.followPos : heldWorldPosition(player, session.distance);
		ItemEntity item = new ItemEntity(session.world, spawnPos.x, spawnPos.y, spawnPos.z, session.heldItemStack);

		if (throwing) {
			item.setVelocity(computeThrowVelocity(player, session));
		}

		session.world.spawnEntity(item);
		session.itemDisplay.discard();

		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(session.world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(session.world, player.getPos(), List.of(item.getId()), false);
	}

	/** Ends a session without a deliberate placement - e.g. on disconnect. Restores any held blocks
	 * where they came from, drops a held item in place, or releases a held mob in place (undoing
	 * the gravity/AI override). A group that's already been thrown is left alone - it's out of the
	 * player's hands, and stays in {@link #SESSIONS} so {@link #tick} still lands it rather than
	 * leaving it frozen mid-air with its blocks gone. The aura-off broadcast is sent around the held
	 * thing itself, not the player, who may already be in another dimension (see {@link #interrupt}). */
	public static void cancel(ServerPlayerEntity player) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase == LevitationSession.Phase.THROWN) {
			return;
		}
		SESSIONS.remove(player.getUuid());

		if (session.phase != LevitationSession.Phase.HELD) {
			return;
		}

		Vec3d auraPos = session.followPos != null ? session.followPos : player.getPos();

		if (session.heldItemStack != null) {
			Vec3d pos = session.itemDisplay.getPos();
			ItemEntity item = new ItemEntity(session.world, pos.x, pos.y, pos.z, session.heldItemStack);
			session.world.spawnEntity(item);
			session.itemDisplay.discard();
			broadcastAura(session.world, auraPos, List.of(item.getId()), false);
			return;
		}

		if (session.heldEntity != null) {
			session.heldEntity.setNoGravity(false);
			broadcastAura(session.world, auraPos, List.of(session.heldEntity.getId()), false);
			return;
		}

		for (LevitationBlock block : session.blocks) {
			setOrDrop(session.world, block.originalPos(), block.state());
			block.displayEntity().discard();
		}
		broadcastAura(session.world, auraPos, blockDisplayIds(session.blocks), false);
	}

	/**
	 * {@link #cancel}, plus telling the client its hold is over - for interruptions where the player
	 * is still connected (death, changing dimension), unlike a disconnect. Without this, a death or
	 * portal trip mid-hold left the server steering the held thing around its old dimension using the
	 * player's coordinates in the new one.
	 */
	public static void interrupt(ServerPlayerEntity player) {
		LevitationSession session = SESSIONS.get(player.getUuid());
		if (session == null || session.phase == LevitationSession.Phase.THROWN) {
			return;
		}
		cancel(player);
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
	}

	/**
	 * Puts a levitated block back into the world at {@code pos}, or - if something else has moved
	 * into that spot since it was picked up - drops it as items there instead. Every fallback
	 * placement goes through this, so returning a block "home" can never overwrite (and silently
	 * delete) whatever is standing there now.
	 */
	private static void setOrDrop(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.getBlockState(pos).isReplaceable()) {
			world.setBlockState(pos, state);
		} else {
			Block.dropStacks(state, world, pos);
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

			// Killed, despawned, or picked off mid-hold - there's nothing left to hold, so stop
			// draining Stamina for it instead of steering (and paying for) a dead entity forever.
			if (session.heldEntity != null && !session.heldEntity.isAlive()) {
				iterator.remove();
				session.heldEntity.setNoGravity(false);
				ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
				Vec3d auraPos = session.followPos != null ? session.followPos : player.getPos();
				broadcastAura(session.world, auraPos, List.of(session.heldEntity.getId()), false);
				continue;
			}

			Entity heldProjectile = session.heldEntity != null && isCatchableProjectileType(session.heldEntity)
					? session.heldEntity : null;
			boolean draining = heldProjectile != null && heldProjectile.getVelocity().length() > MIN_CATCHABLE_PROJECTILE_SPEED
					&& session.pickupCostRemaining <= 0f;

			double weight = computeDrainRate(session);
			float drainThisTick;
			double velocityReduction = 0.0;
			if (session.pickupCostRemaining > 0f) {
				// Burst-drains the pickup cost over a fixed number of ticks rather than an instant
				// up-front deduction - the same total cost, just spent as a quick effort spike
				// (proportionally bigger per-tick for something heavy) instead of vanishing all at
				// once, falling back to the flat hold-drain rate below once it's paid off.
				float burstPerTick = (float) ((PICKUP_COST_SECONDS * weight) / PICKUP_BURST_TICKS);
				drainThisTick = Math.min(burstPerTick, session.pickupCostRemaining);
				session.pickupCostRemaining -= drainThisTick;
			} else if (draining) {
				velocityReduction = Math.min(heldProjectile.getVelocity().length(), PROJECTILE_VELOCITY_DRAIN_PER_TICK);
				drainThisTick = (float) (velocityReduction * PROJECTILE_STAMINA_PER_VELOCITY);
			} else {
				// weight IS the Stamina/second rate now - per-tick drain is just that divided down.
				drainThisTick = (float) (weight / TICKS_PER_SECOND);
			}

			if (!StaminaManager.drain(player, drainThisTick)) {
				if (draining) {
					resumeProjectileFlight(player, session, heldProjectile);
				} else {
					releaseGentlyOnEmpty(player, session);
				}
				iterator.remove();
				continue;
			}

			if (draining) {
				double newSpeed = heldProjectile.getVelocity().length() - velocityReduction;
				heldProjectile.setVelocity(newSpeed > 0.0 ? heldProjectile.getVelocity().normalize().multiply(newSpeed) : Vec3d.ZERO);
				if (newSpeed <= 0.0) {
					convertCaughtProjectileToItem(session);
				}
			}

			updateHeldPositions(player, session);
			if (checkHeldHit(player, session)) {
				iterator.remove();
				continue;
			}

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

	/**
	 * An unaffordable pickup attempt (Heavy Core/Mace at base Stamina, or just an overly ambitious
	 * block selection) - no hold is ever established, so there's no real entity to key a full aura
	 * flash around; a lower-pitched pickup sound plus action-bar feedback stands in for "the magic
	 * fizzled out" for now. A proper brief-aura-then-fizzle visual is a nice-to-have follow-up, not
	 * required for the affordability gate itself to work correctly.
	 */
	private static void playFizzledPickupAttempt(ServerPlayerEntity player) {
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				ModSounds.LEVITATION_PICKUP, SoundCategory.PLAYERS, 1.0F, 0.5F);
		player.sendMessage(Text.literal("Too heavy to lift."), true);
	}

	/**
	 * Follows the aim point on a spring rather than snapping straight to it - {@link LevitationSession
	 * #followPos} tracks the actual position everything renders/positions relative to, and how stiff
	 * the spring is depends on how heavy the held thing is (see {@link #lerpFactorFor}): a Heavy Core
	 * visibly resists being swung around and sails wide on a flick, a Feather tracks the crosshair
	 * almost instantly.
	 */
	private static void updateHeldPositions(ServerPlayerEntity player, LevitationSession session) {
		double lerpFactor = lerpFactorFor(session);

		if (session.heldItemStack != null) {
			Vec3d targetPos = heldWorldPosition(player, session.distance);
			applyFollowPos(player, session, targetPos, lerpFactor);
			session.itemDisplay.refreshPositionAndAngles(session.followPos.x, session.followPos.y, session.followPos.z, player.getYaw(), 0);
			return;
		}

		if (session.heldEntity != null) {
			updateHeldEntityPosition(player, session, lerpFactor);
			return;
		}

		Vec3d targetAnchorRenderPos = heldRenderPosition(player, session.distance);
		applyFollowPos(player, session, targetAnchorRenderPos, lerpFactor);
		for (LevitationBlock block : session.blocks) {
			BlockPos offset = block.originalPos().subtract(session.anchorPos);
			Vec3d pos = session.followPos.add(offset.getX(), offset.getY(), offset.getZ());
			block.displayEntity().refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		}
	}

	/** 1.0 = catches up instantly (today's old behavior); lower is laggier. Derived from the same
	 * drain rate used for cost, so "heavy" means the same thing for both Stamina and movement feel. */
	private static double lerpFactorFor(LevitationSession session) {
		double drainRate = computeDrainRate(session);
		return MathHelper.clamp(1.0 / (1.0 + drainRate * WEIGHT_SLUGGISHNESS), MIN_LERP_FACTOR, 1.0);
	}

	/**
	 * Springs {@code session.followPos} toward {@code targetPos} (snapping instead on the first tick, when
	 * there's no prior position to spring from, or after a teleport), and records how far it actually
	 * moved this tick in {@code session.lastSwingSpeed} - {@link #checkHeldHit} reads that to size a
	 * swing's damage - and its swing relative to the player, as throw energy.
	 */
	private static void applyFollowPos(ServerPlayerEntity player, LevitationSession session, Vec3d targetPos, double lerpFactor) {
		Vec3d previous = session.followPos;
		Vec3d playerPos = player.getPos();
		if (previous == null || previous.distanceTo(targetPos) > SPRING_SNAP_DISTANCE) {
			session.followPos = targetPos;
			session.followVelocity = Vec3d.ZERO;
			session.swingMomentum = Vec3d.ZERO;
			session.lastSwingSpeed = 0.0;
			session.lastTargetPos = targetPos;
			session.lastPlayerPos = playerPos;
			return;
		}
		double stiffness = SPRING_STIFFNESS * lerpFactor;
		double damping = 2.0 * SPRING_DAMPING_RATIO * Math.sqrt(stiffness);
		Vec3d targetVelocity = session.lastTargetPos != null ? targetPos.subtract(session.lastTargetPos) : Vec3d.ZERO;
		Vec3d pull = targetPos.subtract(previous).multiply(stiffness).subtract(session.followVelocity.subtract(targetVelocity).multiply(damping));
		session.followVelocity = session.followVelocity.add(pull);
		session.followPos = previous.add(session.followVelocity);
		session.lastTargetPos = targetPos;
		session.lastSwingSpeed = previous.distanceTo(session.followPos);

		// Its swing, apart from the player's own walking - kept while it's the biggest lately, fading after.
		Vec3d playerMoved = session.lastPlayerPos != null ? playerPos.subtract(session.lastPlayerPos) : Vec3d.ZERO;
		session.lastPlayerPos = playerPos;
		Vec3d swing = session.followVelocity.subtract(playerMoved);
		Vec3d faded = session.swingMomentum.multiply(SCROLL_VELOCITY_DECAY);
		session.swingMomentum = swing.lengthSquared() > faded.lengthSquared() ? swing : faded;
	}

	/**
	 * Keeps a held mob centered on the aim point, roughly matching how a held block's model is
	 * centered on the crosshair - the entity's own vertical center (not its feet) lands on the aim
	 * point, so tall and short creatures alike hang in the same visually-centered spot. Velocity is
	 * zeroed and navigation is stopped every tick (the same per-tick override pattern {@code
	 * VillagerInteractionHandler} already uses for foreign entities) so the held creature can't
	 * drift off or path away mid-hold.
	 */
	private static void updateHeldEntityPosition(ServerPlayerEntity player, LevitationSession session, double lerpFactor) {
		Entity entity = session.heldEntity;
		Vec3d targetPos = heldWorldPosition(player, session.distance);
		double halfHeight = entity.getHeight() / 2.0;
		Vec3d targetCenterPos = new Vec3d(targetPos.x, targetPos.y - halfHeight, targetPos.z);
		applyFollowPos(player, session, targetCenterPos, lerpFactor);
		entity.refreshPositionAndAngles(session.followPos.x, session.followPos.y, session.followPos.z, entity.getYaw(), entity.getPitch());
		entity.setVelocity(Vec3d.ZERO);
		if (entity instanceof MobEntity mob) {
			mob.getNavigation().stop();
		}
	}

	/**
	 * Lets a held (not thrown) object hit entities too - swinging a levitated block/mob/item into
	 * something deals impact damage right where it's currently floating, sized by weight and how
	 * fast it was just swung ({@code lastSwingSpeed}). Cools down per-session afterward rather than
	 * per-target, so standing an object in contact with someone can't repeatedly clock them, but
	 * swinging into a second target during the same cooldown window also has to wait.
	 *
	 * <p>A held block or item that lands a hit is knocked loose: the hold ends and it drops where it
	 * struck, as items - so Levitation can land one blow with it, not keep clubbing. (A held mob stays
	 * held.)
	 *
	 * @return whether the hold ended (the caller removes the session)
	 */
	private static boolean checkHeldHit(ServerPlayerEntity player, LevitationSession session) {
		if (session.heldHitCooldown > 0) {
			session.heldHitCooldown--;
			return false;
		}
		if (session.followPos == null) {
			return false;
		}

		Box hitBox = new Box(session.followPos, session.followPos).expand(HELD_HIT_RADIUS);
		List<Entity> hitEntities = session.world.getOtherEntities(null, hitBox,
				entity -> entity instanceof LivingEntity && !entity.getUuid().equals(session.throwerUuid)
						&& entity != session.heldEntity);
		if (hitEntities.isEmpty()) {
			return false;
		}

		LivingEntity target = (LivingEntity) hitEntities.get(0);
		double weight = computeDrainRate(session);
		float damage = MathHelper.clamp((float) (weight * HELD_HIT_DAMAGE_PER_WEIGHT + session.lastSwingSpeed * HELD_HIT_DAMAGE_PER_SPEED),
				MIN_THROW_DAMAGE, MAX_THROW_DAMAGE);
		target.damage(session.world.getDamageSources().playerAttack(player), damage);
		session.heldHitCooldown = HELD_HIT_COOLDOWN_TICKS;
		if (session.heldEntity != null) {
			return false;
		}
		knockLoose(player, session);
		return true;
	}

	/**
	 * A held block or item that struck something falls out of the magic: each block drops as its own item
	 * (the block itself, not what breaking it would give - nothing's lost), a held item just drops, all
	 * where they were floating and carrying a little of the swing.
	 */
	private static void knockLoose(ServerPlayerEntity player, LevitationSession session) {
		ServerWorld world = session.world;
		Vec3d carried = session.followVelocity.multiply(0.3);
		List<Integer> displayIds;
		if (session.heldItemStack != null) {
			ItemEntity item = new ItemEntity(world, session.followPos.x, session.followPos.y, session.followPos.z, session.heldItemStack);
			item.setVelocity(carried);
			world.spawnEntity(item);
			session.itemDisplay.discard();
			displayIds = List.of(session.itemDisplay.getId());
		} else {
			for (LevitationBlock block : session.blocks) {
				ItemStack stack = new ItemStack(block.state().getBlock().asItem());
				if (!stack.isEmpty()) {
					Vec3d at = block.displayEntity().getPos().add(0.5, 0.5, 0.5);
					ItemEntity item = new ItemEntity(world, at.x, at.y, at.z, stack);
					item.setVelocity(carried);
					world.spawnEntity(item);
				}
				block.displayEntity().discard();
			}
			displayIds = blockDisplayIds(session.blocks);
		}
		ServerPlayNetworking.send(player, new LevitationStateS2CPayload(LevitationState.NONE));
		playLevitationSound(world, player.getPos(), ModSounds.LEVITATION_RELEASE);
		broadcastAura(world, session.followPos, displayIds, false);
	}

	/** Advances a thrown group one tick. Returns true once its session is over (hit something, or timed out). */
	private static boolean tickThrown(LevitationSession session) {
		session.ticksThrown++;
		if (session.ticksThrown > MAX_THROWN_TICKS) {
			shatter(session);
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
			shatter(session);
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
			setOrDrop(world, finalPos, block.state());
			block.displayEntity().discard();
		}
	}

	/** A thrown group that smashes into a mob, or flies too long without landing, breaks apart -
	 * each block drops its normal loot where it was, rather than the blocks simply ceasing to exist. */
	private static void shatter(LevitationSession session) {
		for (LevitationBlock block : session.blocks) {
			Block.dropStacks(block.state(), session.world, BlockPos.ofFloored(block.displayEntity().getPos().add(0.5, 0.5, 0.5)));
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
