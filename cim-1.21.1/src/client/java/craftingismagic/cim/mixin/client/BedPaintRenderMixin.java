package craftingismagic.cim.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BedBlockEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import craftingismagic.cim.block.PaintedBed;

/** Draws the wall paint a bed carries ({@link PaintedBed}) - the same flat concrete layer as {@code cim:wall_paint}. */
@Mixin(BedBlockEntityRenderer.class)
public abstract class BedPaintRenderMixin {
	/** How far off the wall the paint sits - the wall_paint model's 0.1 pixels. */
	private static final float OFFSET = 0.1f / 16f;

	@Inject(method = "render(Lnet/minecraft/block/entity/BedBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
			at = @At("HEAD"))
	private void cim$renderPaint(BedBlockEntity bed, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay,
			CallbackInfo ci) {
		PaintedBed painted = (PaintedBed) bed;
		DyeColor color = painted.cim$paintColor();
		int faces = painted.cim$paintFaces();
		if (color == null || faces == 0) return;
		Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
				.apply(Identifier.ofVanilla("block/" + color.getName() + "_concrete"));
		VertexConsumer consumer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
		MatrixStack.Entry entry = matrices.peek();
		for (Direction side : Direction.Type.HORIZONTAL) {
			if ((faces & PaintedBed.bit(side)) != 0) face(consumer, entry, side, sprite, light, overlay);
		}
	}

	/** One flat quad just off the wall on {@code side}, facing into the room. */
	private static void face(VertexConsumer consumer, MatrixStack.Entry entry, Direction side, Sprite sprite, int light, int overlay) {
		// The wall's plane, nudged into the cell; the quad runs along it.
		float plane = side.getDirection() == Direction.AxisDirection.NEGATIVE ? OFFSET : 1 - OFFSET;
		boolean alongX = side.getAxis() == Direction.Axis.Z;
		float nx = -side.getOffsetX(), nz = -side.getOffsetZ();
		float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}}; // (along, up)
		Matrix4f matrix = entry.getPositionMatrix();
		for (float[] corner : corners) {
			float x = alongX ? corner[0] : plane;
			float z = alongX ? plane : corner[0];
			float u = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * corner[0];
			float v = sprite.getMaxV() - (sprite.getMaxV() - sprite.getMinV()) * corner[1];
			consumer.vertex(matrix, x, corner[1], z).color(0xFFFFFFFF).texture(u, v).overlay(overlay).light(light).normal(entry, nx, 0, nz);
		}
	}
}
