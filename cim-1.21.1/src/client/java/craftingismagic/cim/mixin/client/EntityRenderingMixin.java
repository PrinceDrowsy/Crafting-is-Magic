package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

import craftingismagic.cim.client.magic.MagicAuraRenderer;

/** Remembers which entity is being rendered, so a horn rendered deep inside its model knows whose it is. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderingMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private <E extends Entity> void cim$beginEntity(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		MagicAuraRenderer.setRendering(entity);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private <E extends Entity> void cim$endEntity(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		MagicAuraRenderer.setRendering(null);
	}
}
