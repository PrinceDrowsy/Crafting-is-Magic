package craftingismagic.cim.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import craftingismagic.cim.network.TeleportS2CPayload;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.unicorn.TeleportManager;

/**
 * Dev-only: right-click to show one end of a teleport (the departure, or the arrival) where you're
 * looking, sized as if you'd stood there, in your own magic color - without casting one.
 */
public class TeleportDebugStickItem extends Item {
	private static final double REACH = 16.0;
	private static final int COOLDOWN_TICKS = 10;

	private final boolean arrival;

	public TeleportDebugStickItem(Settings settings, boolean arrival) {
		super(settings);
		this.arrival = arrival;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (world instanceof ServerWorld serverWorld) {
			HitResult hit = user.raycast(REACH, 1.0F, false);
			Vec3d at = hit.getPos().add(0, user.getHeight() / 2, 0);
			int color = StaminaColorAttachment.getColors(user).fillColor() & 0xFFFFFF;
			TeleportManager.showEnd(serverWorld, new TeleportS2CPayload(-1, at, Math.max(user.getWidth(), user.getHeight()), color, arrival));
		}
		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
	}
}
