package craftingismagic.cim.client.pegasus;

import java.util.function.BiFunction;

import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.vertex.VeilVertexFormat;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import craftingismagic.cim.Cim;

/**
 * A Quasar particle render layer identical in structure to Veil's own
 * {@code quasarParticle}, except bound to our own rainbow-ring shader
 * instead of Veil's default particle shader - Veil's own binding is
 * hardcoded internally, so this exists purely to swap that one thing.
 */
public final class RainbowRingRenderType {
	private static final RenderPhase.ShaderProgram SHADER =
			VeilRenderBridge.shaderState(Identifier.of(Cim.MOD_ID, "quasar/rainbow_ring"));

	private static final BiFunction<Identifier, Boolean, RenderLayer> RAINBOW_RING = Util.memoize((texture, additive) -> {
		RenderLayer.MultiPhaseParameters state = RenderLayer.MultiPhaseParameters.builder()
				.program(SHADER)
				.texture(new RenderPhase.Texture(texture, false, false))
				.transparency(additive ? VeilRenderType.additiveTransparencyShard() : VeilRenderType.translucentTransparencyShard())
				.lightmap(VeilRenderType.lightmap())
				.writeMaskState(VeilRenderType.colorDepthWriteShard())
				.build(false);
		return RenderLayer.of(Cim.MOD_ID + ":rainbow_ring_particle", VeilVertexFormat.QUASAR_PARTICLE,
				VertexFormat.DrawMode.QUADS, 256, false, true, state);
	});

	private RainbowRingRenderType() {
	}

	public static RenderLayer get(Identifier texture, boolean additive) {
		return RAINBOW_RING.apply(texture, additive);
	}
}
