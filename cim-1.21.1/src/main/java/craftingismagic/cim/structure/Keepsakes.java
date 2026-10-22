package craftingismagic.cim.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;

import craftingismagic.cim.block.KeepsakeSpotBlock;
import craftingismagic.cim.mixin.DisplayEntityAccessor;
import craftingismagic.cim.mixin.ItemDisplayAccessor;
import craftingismagic.cim.villager.FriendshipBonds;

/**
 * Little things that appear in your friends' homes, standing for what you've shared - a gift you gave
 * them on a shelf, the bracelet that made you friends (README: Village identity - keepsakes, decided
 * 2026-10-21). They appear at the keepsake spots builders put in room sets, only in friends' homes, and
 * only sometimes - so noticing one is a small surprise. A new resident's redecorating clears them.
 */
public final class Keepsakes {
	/** Tag on keepsake displays, so redecorating can find and clear them. */
	public static final String TAG = "cim_keepsake";
	private static final int HOUSE_REACH = 10;
	private static final int HOUSE_HEIGHT = 6;

	private Keepsakes() {
	}

	/** Maybe ({@code chance}) a keepsake of {@code item} appears in {@code pony}'s home - if you're friends, and there's a free spot. */
	public static boolean offer(VillagerEntity pony, ServerPlayerEntity player, ItemStack item, float chance) {
		if (item.isEmpty() || !FriendshipBonds.isFriend(pony, player.getUuid()) || pony.getRandom().nextFloat() >= chance) return false;
		if (!(pony.getWorld() instanceof ServerWorld world)) return false;
		Optional<GlobalPos> home = pony.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME);
		if (home == null || home.isEmpty() || !home.get().dimension().equals(world.getRegistryKey())) return false;
		BlockPos bed = home.get().pos();
		List<BlockPos> free = new ArrayList<>();
		for (BlockPos spot : BlockPos.iterate(bed.add(-HOUSE_REACH, -HOUSE_HEIGHT / 2, -HOUSE_REACH), bed.add(HOUSE_REACH, HOUSE_HEIGHT, HOUSE_REACH))) {
			if (world.getBlockState(spot).getBlock() instanceof KeepsakeSpotBlock && !taken(world, spot)) free.add(spot.toImmutable());
		}
		if (free.isEmpty()) return false;
		BlockPos spot = free.get(pony.getRandom().nextInt(free.size()));
		DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
		display.setPosition(spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5);
		((ItemDisplayAccessor) display).cim$setItemStack(item.copyWithCount(1));
		float turn = pony.getRandom().nextFloat() * (float) Math.PI * 2;
		((DisplayEntityAccessor) display).cim$setTransformation(new AffineTransformation(new Vector3f(),
				new Quaternionf().rotationY(turn), new Vector3f(0.45f), new Quaternionf()));
		display.addCommandTag(TAG);
		world.spawnEntity(display);
		return true;
	}

	/** Clears the keepsakes in a room (when new residents redecorate it). */
	public static void clear(ServerWorld world, BlockBox room) {
		Box box = new Box(room.getMinX(), room.getMinY(), room.getMinZ(), room.getMaxX() + 1, room.getMaxY() + 1, room.getMaxZ() + 1);
		for (DisplayEntity.ItemDisplayEntity display : world.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, box,
				entity -> entity.getCommandTags().contains(TAG))) {
			display.discard();
		}
	}

	private static boolean taken(ServerWorld world, BlockPos spot) {
		return !world.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, new Box(spot),
				entity -> entity.getCommandTags().contains(TAG)).isEmpty();
	}
}
