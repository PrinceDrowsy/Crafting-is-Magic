package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BedBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.block.CimBlocks;
import craftingismagic.cim.block.PaintedBed;
import craftingismagic.cim.block.WallPaintBlock;

/**
 * Draws the wall paint a bed carries ({@link PaintedBed}) - the {@code cim:wall_paint} model itself, through the
 * block renderer (as pistons draw moving blocks), so it's lit exactly like the paint beside it.
 */
@Mixin(BedBlockEntityRenderer.class)
public abstract class BedPaintRenderMixin {
	@Inject(method = "render(Lnet/minecraft/block/entity/BedBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
			at = @At("HEAD"))
	private void cim$renderPaint(BedBlockEntity bed, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay,
			CallbackInfo ci) {
		PaintedBed painted = (PaintedBed) bed;
		DyeColor color = painted.cim$paintColor();
		int faces = painted.cim$paintFaces();
		if (color == null || faces == 0 || bed.getWorld() == null) return;
		BlockState paint = CimBlocks.WALL_PAINT.getDefaultState().with(WallPaintBlock.COLOR, color);
		for (Direction side : Direction.Type.HORIZONTAL) {
			if ((faces & PaintedBed.bit(side)) != 0) paint = paint.with(WallPaintBlock.face(side), true);
		}
		BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
		blocks.getModelRenderer().render(bed.getWorld(), blocks.getModel(paint), paint, bed.getPos(), matrices,
				vertices.getBuffer(RenderLayers.getMovingBlockLayer(paint)), false, Random.create(), paint.getRenderingSeed(bed.getPos()), overlay);
	}
}
