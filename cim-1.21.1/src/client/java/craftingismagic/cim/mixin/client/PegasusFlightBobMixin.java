package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.client.pegasus.PegasusFlightVisuals;

/** Adds the wing-flap bob to the rendered player model - visible in third person, and to other clients watching this player. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PegasusFlightBobMixin {
	@Inject(method = "getPositionOffset", at = @At("RETURN"), cancellable = true)
	private void cim$bobPassiveFlightModel(AbstractClientPlayerEntity player, float tickDelta,
			CallbackInfoReturnable<Vec3d> cir) {
		if (!PegasusFlightVisuals.isInPassiveFlight(player)) {
			return;
		}

		Vec3d offset = cir.getReturnValue();
		cir.setReturnValue(offset.add(0.0, PegasusFlightVisuals.wingFlapOffset(player, tickDelta), 0.0));
	}
}
