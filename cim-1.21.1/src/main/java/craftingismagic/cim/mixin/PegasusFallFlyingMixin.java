package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.pegasus.PegasusFlightManager;

/**
 * Vanilla's {@code tickFallFlying} force-resets the fall-flying flag every
 * tick unless the entity has a real, usable Elytra equipped, which would
 * otherwise cancel Pegasus active flight almost immediately after it starts.
 * This bypasses that requirement for players actively flying via
 * {@link PegasusFlightManager}, keeping the flag set from the entity's own
 * ground/vehicle/levitation state instead.
 */
@Mixin(LivingEntity.class)
public abstract class PegasusFallFlyingMixin {
	@Inject(method = "tickFallFlying", at = @At("HEAD"), cancellable = true)
	private void cim$allowFlightWithoutElytra(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayerEntity player) || !PegasusFlightManager.isActivelyFlying(player)) {
			return;
		}

		boolean shouldGlide = self.isFallFlying() && !self.isOnGround() && !self.hasVehicle()
				&& !self.hasStatusEffect(StatusEffects.LEVITATION);
		((EntitySetFlagAccessor) self).cim$setFlag(7, shouldGlide);
		ci.cancel();
	}
}
