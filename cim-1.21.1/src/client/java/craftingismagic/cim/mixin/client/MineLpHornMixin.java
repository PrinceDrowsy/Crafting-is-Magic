package craftingismagic.cim.mixin.client;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.minelittlepony.api.model.ModelAttributes;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

import craftingismagic.cim.client.magic.MagicAuraRenderer;

/**
 * MineLittlePony's unicorn horn: while its owner channels magic, the horn itself is drawn into the magic
 * aura's mask (docs/magic-aura.md), so the glow takes its real shape and follows every head movement -
 * and MineLittlePony's own horn glow is hidden meanwhile, so it doesn't glow twice. Only applies if
 * MineLittlePony is installed.
 */
@Pseudo
@Mixin(targets = "com.minelittlepony.client.model.part.UnicornHorn", remap = false)
public abstract class MineLpHornMixin {
	/** The horn cube (minelittlepony:components/horn): from (-0.5, -11, -3.5), 1 x 4 x 1 pixels - its tip. */
	private static final Vector3f CIM$TIP = new Vector3f(0f, -11f / 16f, -3f / 16f);

	@Shadow(remap = false)
	@Final
	private ModelPart horn;

	@Shadow(remap = false)
	@Final
	private ModelPart glow;

	@Inject(method = "renderPart", at = @At("HEAD"), remap = false)
	private void cim$magicAura(MatrixStack stack, VertexConsumer vertices, int light, int overlay, int color, ModelAttributes attributes,
			CallbackInfo ci) {
		if (!horn.visible) {
			return;
		}
		if (MagicAuraRenderer.onHornRendered(horn, stack, light, overlay, CIM$TIP)) {
			glow.visible = false;
		}
	}
}
