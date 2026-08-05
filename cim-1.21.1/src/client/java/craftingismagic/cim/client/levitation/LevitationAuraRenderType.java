package craftingismagic.cim.client.levitation;

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

/** Same plumbing as the Sonic Rainboom ring/charge-cone render types, bound to the Levitation aura shader instead. */
public final class LevitationAuraRenderType {
	private static final RenderPhase.ShaderProgram SHADER =
			VeilRenderBridge.shaderState(Identifier.of(Cim.MOD_ID, "quasar/levitation_aura"));

	private static final BiFunction<Identifier, Boolean, RenderLayer> LEVITATION_AURA = Util.memoize((texture, additive) -> {
		RenderLayer.MultiPhaseParameters state = RenderLayer.MultiPhaseParameters.builder()
				.program(SHADER)
				.texture(new RenderPhase.Texture(texture, false, false))
				.transparency(additive ? VeilRenderType.additiveTransparencyShard() : VeilRenderType.translucentTransparencyShard())
				.lightmap(VeilRenderType.lightmap())
				// A held group's aura is centered on the group's own bounding-box center - for a
				// long selection viewed close to end-on, that center sits behind the nearer blocks
				// from the camera's point of view, so a normal depth test would hide most of the
				// aura behind the very blocks it's supposed to glow around. Disabling the depth
				// test (and correspondingly not writing depth either) makes it always render
				// through solid geometry, the same "always visible" treatment as other glow/aura
				// effects that need to read at any viewing angle rather than just from the side.
				.depthTest(VeilRenderType.noDepthTestShard())
				.writeMaskState(VeilRenderType.colorWriteShard())
				.build(false);
		return RenderLayer.of(Cim.MOD_ID + ":levitation_aura_particle", VeilVertexFormat.QUASAR_PARTICLE,
				VertexFormat.DrawMode.QUADS, 256, false, true, state);
	});

	private LevitationAuraRenderType() {
	}

	public static RenderLayer get(Identifier texture, boolean additive) {
		return LEVITATION_AURA.apply(texture, additive);
	}
}
