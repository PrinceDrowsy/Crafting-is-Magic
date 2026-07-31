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

/** Same pattern as {@link RainbowRingRenderType}, bound to the charge-cone shader instead. */
public final class ChargeConeRenderType {
	private static final RenderPhase.ShaderProgram SHADER =
			VeilRenderBridge.shaderState(Identifier.of(Cim.MOD_ID, "quasar/charge_cone"));

	private static final BiFunction<Identifier, Boolean, RenderLayer> CHARGE_CONE = Util.memoize((texture, additive) -> {
		RenderLayer.MultiPhaseParameters state = RenderLayer.MultiPhaseParameters.builder()
				.program(SHADER)
				.texture(new RenderPhase.Texture(texture, false, false))
				.transparency(additive ? VeilRenderType.additiveTransparencyShard() : VeilRenderType.translucentTransparencyShard())
				.lightmap(VeilRenderType.lightmap())
				.writeMaskState(VeilRenderType.colorDepthWriteShard())
				.build(false);
		return RenderLayer.of(Cim.MOD_ID + ":charge_cone_particle", VeilVertexFormat.QUASAR_PARTICLE,
				VertexFormat.DrawMode.QUADS, 256, false, true, state);
	});

	private ChargeConeRenderType() {
	}

	public static RenderLayer get(Identifier texture, boolean additive) {
		return CHARGE_CONE.apply(texture, additive);
	}
}
