package craftingismagic.cim.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import craftingismagic.cim.pegasus.SonicRainboomEffect;

/**
 * Dev-only debug tool (not meant to ship in a real playthrough): right-click
 * to trigger the Sonic Rainboom effect some distance away along the look
 * direction, so it can be viewed and critiqued from outside the effect
 * without needing to actually build up dash speed to trigger it for real.
 */
public class SonicRainboomDebugStickItem extends Item {
	private static final double TRIGGER_DISTANCE = 80.0;

	public SonicRainboomDebugStickItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (world instanceof ServerWorld serverWorld) {
			Vec3d pos = user.getEyePos().add(user.getRotationVec(1.0F).multiply(TRIGGER_DISTANCE));
			SonicRainboomEffect.trigger(serverWorld, pos);
		}

		return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
	}
}
