package craftingismagic.cim.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import com.mojang.serialization.Codec;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3i;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.Cim;
import craftingismagic.cim.block.CimBlocks;
import craftingismagic.cim.block.PaintedBed;
import craftingismagic.cim.block.RoomMarkerBlock;
import craftingismagic.cim.block.WallPaintBlock;
import net.minecraft.util.DyeColor;
import craftingismagic.cim.block.RoomMarkerBlockEntity;
import craftingismagic.cim.block.RoomType;
import craftingismagic.cim.villager.PersonalityAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment;

/**
 * Furnishes a home for whoever lives there (README: Village identity - interiors, decided 2026-10-21).
 * A village is generated before anyone lives in it, so a house shell's rooms start bare, each holding
 * a room marker; when a pony moves in - claims a bed there - each marked room is furnished with a
 * room set that suits them ({@link RoomSets}). New residents redecorate at once: the furnishing a
 * room had is cleared (exactly the blocks it placed - never the house) and the new one placed.
 * <ul>
 * <li>Family members who move in together share the furnishing (a foal moving into their parent's home
 * doesn't redo it); a room still furnished for someone who lives there is left alone.</li>
 * <li>The room's size is measured by filling its air from the marker, so builders never set it.</li>
 * <li>Checked from each pony every few seconds - a cheap comparison of their bed - never by scanning.</li>
 * </ul>
 */
public final class HomeDecorator {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/structures");
	/** How far from a pony's bed their house's rooms can be. */
	private static final int HOUSE_REACH = 10;
	private static final int HOUSE_HEIGHT = 6;
	/** A room is at most this wide and tall - past that, the fill stops (an open plan, a doorway left open). */
	private static final int ROOM_REACH = 8;
	/** Rooms are measured this many blocks up from their marker - the 3 clear blocks every room has, and no
	 * higher, so a room never leaks up a stairwell into the floor above. */
	private static final int ROOM_HEIGHT = 3;
	/** Blocks paint never goes on - the thatch roof, glass - so only real walls get painted. Data: add any block to it. */
	public static final net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> UNPAINTABLE = net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.RegistryKeys.BLOCK, Identifier.of(Cim.MOD_ID, "unpaintable"));
	/** How far paint follows a wall up under a roof. */
	private static final int ROOF_PAINT_REACH = 10;
	private static final int ROOM_CELLS = 1200;

	/** The bed a pony's home was last furnished for, so a move is noticed once. */
	public static final AttachmentType<Long> FURNISHED_HOME = AttachmentRegistry.create(Identifier.of(Cim.MOD_ID, "furnished_home"),
			builder -> builder.persistent(Codec.LONG));

	private HomeDecorator() {
	}

	/** Every few seconds, from each pony: has their home changed? Then they're moving in. */
	public static void check(VillagerEntity pony) {
		if (!(pony.getWorld() instanceof ServerWorld world)) return;
		Optional<GlobalPos> home = pony.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME);
		if (home == null || home.isEmpty() || !home.get().dimension().equals(world.getRegistryKey())) return;
		long bed = home.get().pos().asLong();
		Long furnished = pony.getAttached(FURNISHED_HOME);
		if (furnished != null && furnished == bed) return;
		pony.setAttached(FURNISHED_HOME, bed);
		movedIn(world, pony, home.get().pos());
	}

	/** {@code pony} moved into the home around {@code bed}: each marked room is furnished for them. */
	public static int movedIn(ServerWorld world, VillagerEntity pony, BlockPos bed) {
		int furnished = 0;
		for (BlockPos marker : BlockPos.iterate(bed.add(-HOUSE_REACH, -HOUSE_HEIGHT / 2, -HOUSE_REACH),
				bed.add(HOUSE_REACH, HOUSE_HEIGHT, HOUSE_REACH))) {
			if (world.getBlockState(marker).getBlock() instanceof RoomMarkerBlock block
					&& world.getBlockEntity(marker) instanceof RoomMarkerBlockEntity entity) {
				if (furnish(world, marker.toImmutable(), block.room(), entity, pony)) furnished++;
			}
		}
		return furnished;
	}

	/** Furnishes one room for {@code pony} - unless it's furnished for someone who still lives there with them. */
	private static boolean furnish(ServerWorld world, BlockPos marker, RoomType room, RoomMarkerBlockEntity entity, VillagerEntity pony) {
		Optional<UUID> before = entity.resident();
		if (before.isPresent() && !before.get().equals(pony.getUuid())) {
			Entity other = world.getEntity(before.get());
			if (other instanceof VillagerEntity neighbor && neighbor.isAlive() && livesNear(neighbor, marker)) return false;
		}
		clear(world, entity.placed());
		Set<BlockPos> cells = fill(world, marker);
		if (cells.isEmpty()) return false;
		Keepsakes.clear(world, box(cells)); // the last residents' keepsakes go with them

		BlockBox space = box(cells);
		boolean foals = !PonyFamilyAttachment.children(pony).isEmpty();
		Vec3i size = new Vec3i(space.getBlockCountX(), space.getBlockCountY(), space.getBlockCountZ());
		RoomSets.RoomSet set = RoomSets.choose(room, pony, foals,
				candidate -> rotationFor(world, candidate.structure(), size) != null, pony.getRandom());

		// The walls are painted first: a flat layer on every wall face in the room - only where it touches a
		// full block, so never across a window or a doorway - and the room keeps its full size.
		DyeColor color = paint(set, pony);
		List<Long> painted = new ArrayList<>();
		for (BlockPos cell : cells) {
			if (cell.equals(marker)) continue;
			BlockState paint = CimBlocks.WALL_PAINT.getDefaultState().with(WallPaintBlock.COLOR, color);
			boolean any = false;
			for (Direction side : Direction.Type.HORIZONTAL) {
				BlockPos wall = cell.offset(side);
				if (paintable(world, wall)) {
					paint = paint.with(WallPaintBlock.face(side), true);
					any = true;
				}
			}
			if (any) {
				world.setBlockState(cell, paint, Block.NOTIFY_LISTENERS);
				painted.add(cell.asLong());
			}
		}
		// Under a roof the walls go higher than the room's 3 blocks: the paint follows them up to the roof
		// (but never up a stairwell - the room above is someone else's to paint).
		for (BlockPos cell : cells) {
			if (cell.getY() != marker.getY() + ROOM_HEIGHT - 1 || overStairs(world, cell, marker)) continue;
			BlockPos up = cell.up();
			for (int i = 0; i < ROOF_PAINT_REACH && world.getBlockState(up).isAir(); i++, up = up.up()) {
				BlockState paint = CimBlocks.WALL_PAINT.getDefaultState().with(WallPaintBlock.COLOR, color);
				boolean any = false;
				for (Direction side : Direction.Type.HORIZONTAL) {
					BlockPos wall = up.offset(side);
					if (paintable(world, wall)) {
						paint = paint.with(WallPaintBlock.face(side), true);
						any = true;
					}
				}
				if (!any) break;
				world.setBlockState(up, paint, Block.NOTIFY_LISTENERS);
				painted.add(up.asLong());
			}
		}
		// A bed fills its cell, so it carries the paint for the wall behind it (PaintedBed) - like carpet under a bed.
		for (BlockPos pos : BlockPos.iterate(space.getMinX(), space.getMinY(), space.getMinZ(), space.getMaxX(), space.getMaxY(), space.getMaxZ())) {
			if (!(world.getBlockState(pos).getBlock() instanceof BedBlock) || !(world.getBlockEntity(pos) instanceof PaintedBed bed)) continue;
			int faces = 0;
			for (Direction side : Direction.Type.HORIZONTAL) {
				if (paintable(world, pos.offset(side))) faces |= PaintedBed.bit(side);
			}
			bed.cim$paint(color, faces);
		}
		if (set == null) {
			entity.furnished(pony.getUuid(), painted.stream().mapToLong(Long::longValue).toArray());
			return !painted.isEmpty();
		}
		StructureTemplate template = world.getStructureTemplateManager().getTemplate(set.structure()).orElse(null);
		BlockRotation rotation = rotationFor(world, set.structure(), size);
		if (template == null || rotation == null) return false;

		// What's air in the room now: whatever the set fills in is furniture, cleared again when someone new moves in.
		List<BlockPos> air = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(space.getMinX(), space.getMinY(), space.getMinZ(), space.getMaxX(), space.getMaxY(), space.getMaxZ())) {
			BlockState state = world.getBlockState(pos);
			if (state.isAir() || state.getBlock() instanceof WallPaintBlock) air.add(pos.toImmutable());
		}
		StructurePlacementData placement = new StructurePlacementData().setRotation(rotation)
				.addProcessor(BlockIgnoreStructureProcessor.IGNORE_AIR_AND_STRUCTURE_BLOCKS)
				.addProcessor(OnlyIntoAirProcessor.INSTANCE); // never over the shell's bed or anything else already there
		BlockBox footprint = template.calculateBoundingBox(placement, BlockPos.ORIGIN);
		BlockPos origin = new BlockPos(space.getMinX() - footprint.getMinX(), space.getMinY() - footprint.getMinY(),
				space.getMinZ() - footprint.getMinZ());
		BlockState markerState = world.getBlockState(marker);
		template.place(world, origin, origin, placement, pony.getRandom(), Block.NOTIFY_LISTENERS);

		// The marker stays (it's how the room is redecorated later) - if furniture landed on it, it's put back.
		if (world.getBlockState(marker) != markerState) {
			world.setBlockState(marker, markerState, Block.NOTIFY_LISTENERS);
		}
		RoomMarkerBlockEntity kept = world.getBlockEntity(marker) instanceof RoomMarkerBlockEntity again ? again : entity;
		Set<Long> placed = new HashSet<>(painted);
		for (BlockPos pos : air) {
			if (!pos.equals(marker) && !world.getBlockState(pos).isAir()) placed.add(pos.asLong());
		}
		kept.furnished(pony.getUuid(), placed.stream().mapToLong(Long::longValue).toArray());
		return true;
	}

	/** A rotation that fits the set in the room, preferring none; null if it doesn't fit at all. */
	private static BlockRotation rotationFor(ServerWorld world, Identifier structure, Vec3i room) {
		StructureTemplate template = world.getStructureTemplateManager().getTemplate(structure).orElse(null);
		if (template == null) {
			LOGGER.warn("Room set structure {} is missing", structure);
			return null;
		}
		for (BlockRotation rotation : List.of(BlockRotation.NONE, BlockRotation.CLOCKWISE_90, BlockRotation.CLOCKWISE_180,
				BlockRotation.COUNTERCLOCKWISE_90)) {
			Vec3i size = template.getRotatedSize(rotation);
			if (size.getX() <= room.getX() && size.getY() <= room.getY() && size.getZ() <= room.getZ()) return rotation;
		}
		return null;
	}

	/** The wall color: what the room set asks for, else one that suits the resident's personality. First pass. */
	private static DyeColor paint(RoomSets.RoomSet set, VillagerEntity pony) {
		if (set != null && set.walls() != null) {
			DyeColor asked = DyeColor.byName(set.walls(), null);
			if (asked != null) return asked;
		}
		if (!PersonalityAttachment.hasPersonality(pony)) return DyeColor.WHITE;
		return switch (PersonalityAttachment.getPersonality(pony).baseType()) {
			case PEPPY -> DyeColor.PINK;
			case STEADFAST -> DyeColor.LIGHT_BLUE;
			case BOOKISH -> DyeColor.PURPLE;
			case DARING -> DyeColor.RED;
			case GENTLE -> DyeColor.LIME;
			case REFINED -> DyeColor.MAGENTA;
			case CRANKY -> DyeColor.LIGHT_GRAY;
			case LAZY -> DyeColor.YELLOW;
		};
	}

	/** A wall that takes paint: a full block, and not roofing or glass ({@link #UNPAINTABLE}). */
	private static boolean paintable(ServerWorld world, BlockPos wall) {
		BlockState state = world.getBlockState(wall);
		return state.isFullCube(world, wall) && !state.isIn(UNPAINTABLE);
	}

	/** Whether stairs run through this column of the room - its opening above is a stairwell. */
	private static boolean overStairs(ServerWorld world, BlockPos cell, BlockPos marker) {
		for (int y = marker.getY(); y <= cell.getY(); y++) {
			if (world.getBlockState(new BlockPos(cell.getX(), y, cell.getZ())).getBlock() instanceof net.minecraft.block.StairsBlock) return true;
		}
		return false;
	}

	private static BlockBox box(Set<BlockPos> cells) {
		BlockBox box = null;
		for (BlockPos pos : cells) box = box == null ? new BlockBox(pos) : box.encompass(pos);
		return box;
	}

	/** The room's air, filled outward from the marker - every cell of it (empty if there's no room there). */
	private static Set<BlockPos> fill(ServerWorld world, BlockPos marker) {
		Set<BlockPos> seen = new HashSet<>();
		Set<BlockPos> room = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(marker);
		seen.add(marker);
		while (!queue.isEmpty() && seen.size() < ROOM_CELLS) {
			BlockPos pos = queue.poll();
			room.add(pos);
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.offset(direction);
				if (Math.abs(next.getX() - marker.getX()) > ROOM_REACH || Math.abs(next.getZ() - marker.getZ()) > ROOM_REACH
						|| next.getY() < marker.getY() || next.getY() >= marker.getY() + ROOM_HEIGHT || !seen.add(next)) continue;
				BlockState state = world.getBlockState(next);
				if (state.isAir() || state.getBlock() instanceof RoomMarkerBlock) queue.add(next);
			}
		}
		return room;
	}

	/** Takes out what an earlier furnishing placed - only what's still there. */
	private static void clear(ServerWorld world, long[] placed) {
		for (long packed : placed) {
			BlockPos pos = BlockPos.fromLong(packed);
			if (!world.getBlockState(pos).isAir() && !(world.getBlockState(pos).getBlock() instanceof RoomMarkerBlock)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	private static boolean livesNear(VillagerEntity pony, BlockPos marker) {
		return pony.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME)
				.map(home -> home.pos().isWithinDistance(marker, HOUSE_REACH + 2))
				.orElse(false);
	}
}
