package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;

import craftingismagic.cim.client.pegasus.PegasusChargeVisualState;

/** Stretches the player model lengthwise while charging a Sonic Rainboom, growing with charge progress. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PegasusChargeStretchMixin {
	@Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
	private void cim$stretchForCharge(AbstractClientPlayerEntity player, MatrixStack matrices, float amount, CallbackInfo ci) {
		float progress = PegasusChargeVisualState.getProgress(player.getId());
		if (progress < 0.0F) {
			return;
		}

		float stretch = 1.0F + progress * 0.6F;
		float squeeze = 1.0F - progress * 0.15F;
		matrices.scale(squeeze, squeeze, stretch);
	}
}
