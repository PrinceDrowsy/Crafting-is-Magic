package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Ponies have ledge sense: a pony walking on the ground stops at the edge of a drop deeper than a
 * safe fall, the way a sneaking player does. Vanilla's path planning already avoids such drops, but
 * crowding, cut corners and panic still walked villagers off cliffs - deaths that only showed how
 * simple the AI is. Real causes still apply: a hit's knockback, an explosion, magic, a leash, or
 * anything that starts while the pony is already in the air.
 */
@Mixin(Entity.class)
public abstract class PonyLedgeSenseMixin {
	/** Drops down to here are safe (vanilla's own safe fall is 3 blocks); any deeper and the pony stops. */
	private static final double SAFE_DROP = 3.5;
	/** Faster than a pony walks on its own: something pushed them (knockback, a blast, a spell). */
	private static final double PUSHED_SPEED_SQUARED = 0.35 * 0.35;
	private static final double STEP = 0.05;

	@Inject(method = "adjustMovementForSneaking", at = @At("HEAD"), cancellable = true)
	private void cim$ledgeSense(Vec3d movement, MovementType type, CallbackInfoReturnable<Vec3d> cir) {
		if (!((Object) this instanceof VillagerEntity pony) || type != MovementType.SELF) return;
		if (!pony.isOnGround() || pony.hurtTime > 0 || pony.isLeashed() || pony.hasVehicle() || pony.isTouchingWater()
				|| movement.horizontalLengthSquared() > PUSHED_SPEED_SQUARED) {
			return;
		}
		Box box = pony.getBoundingBox();
		double x = movement.x;
		double z = movement.z;
		// The same edge clipping a sneaking player gets, measured against a safe fall instead of a step.
		while (x != 0 && pony.getWorld().isSpaceEmpty(pony, box.offset(x, -SAFE_DROP, 0))) {
			x = Math.abs(x) < STEP ? 0 : x - Math.signum(x) * STEP;
		}
		while (z != 0 && pony.getWorld().isSpaceEmpty(pony, box.offset(0, -SAFE_DROP, z))) {
			z = Math.abs(z) < STEP ? 0 : z - Math.signum(z) * STEP;
		}
		while (x != 0 && z != 0 && pony.getWorld().isSpaceEmpty(pony, box.offset(x, -SAFE_DROP, z))) {
			x = Math.abs(x) < STEP ? 0 : x - Math.signum(x) * STEP;
			z = Math.abs(z) < STEP ? 0 : z - Math.signum(z) * STEP;
		}
		if (x != movement.x || z != movement.z) {
			cir.setReturnValue(new Vec3d(x, movement.y, z));
		}
	}
}
