package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import craftingismagic.cim.client.pegasus.PegasusFlightVisuals;

/** Adds the same wing-flap bob to the first-person camera - third person already gets it via the player model itself. */
@Mixin(Camera.class)
public abstract class PegasusFlightCameraBobMixin {
	@Shadow
	private Vec3d pos;

	@Shadow
	protected abstract void setPos(Vec3d pos);

	@Inject(method = "update", at = @At("TAIL"))
	private void cim$bobPassiveFlightCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
			boolean inverseView, float tickDelta, CallbackInfo ci) {
		if (thirdPerson || !(focusedEntity instanceof AbstractClientPlayerEntity player)
				|| !PegasusFlightVisuals.isInPassiveFlight(player)) {
			return;
		}

		this.setPos(this.pos.add(0.0, PegasusFlightVisuals.wingFlapOffset(player, tickDelta), 0.0));
	}
}
