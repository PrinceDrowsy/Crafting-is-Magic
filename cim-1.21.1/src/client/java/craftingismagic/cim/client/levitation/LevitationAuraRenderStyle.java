package craftingismagic.cim.client.levitation;

import java.nio.ByteBuffer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.Vec3d;

import foundry.veil.api.client.render.vertex.VeilVertexFormat;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;
import foundry.veil.api.quasar.particle.QuasarParticle;
import foundry.veil.api.quasar.particle.RenderData;
import foundry.veil.api.quasar.particle.RenderStyle;
import foundry.veil.api.quasar.particle.SpriteData;

/**
 * Renders the Levitation aura as a single camera-facing plane using a custom
 * shader ({@code assets/cim/pinwheel/shaders/program/quasar/levitation_aura})
 * - the wavy-rimmed glow shape is drawn procedurally in the fragment shader
 * from distance/angle-to-center, not from a sprite texture, so this binds no
 * texture at all (see {@link #getRenderType}). The mesh/buffer-packing logic
 * below is boilerplate every custom-shader Quasar particle in this mod needs
 * (Veil's own {@code RenderStyle.Billboard} is {@code final} and can't be
 * subclassed or reused directly) - it's mechanically identical to e.g. the
 * Sonic Rainboom ring's, but the actual visual shader is unrelated.
 */
public final class LevitationAuraRenderStyle extends RenderStyle {
	private static final Vector3fc[] PLANE_POSITIONS = {
			new Vector3f(1, -1, 0), new Vector3f(1, 1, 0), new Vector3f(-1, 1, 0), new Vector3f(-1, -1, 0),
	};

	/** Byte offset where the extra hull-point data (see below) starts, right after the existing Matrix/Scale/UV/Color/Light block. */
	private static final int HULL_DATA_OFFSET = Float.BYTES * 25 + Short.BYTES * 2;

	public LevitationAuraRenderStyle() {
		super(HULL_DATA_OFFSET + Float.BYTES * LevitationAuraEffectHandler.MAX_HULL_POINTS * 2);
	}

	@Override
	protected BuiltBuffer createMesh() {
		BufferBuilder builder = RenderSystem.renderThreadTesselator()
				.begin(VertexFormat.DrawMode.QUADS, VeilVertexFormat.QUASAR_PARTICLE);

		for (Vector3fc pos : PLANE_POSITIONS) {
			builder.vertex(pos.x(), pos.y(), pos.z());
			builder.normal(0, 0, -1);
		}

		return builder.end();
	}

	@Override
	protected void setupBufferState(VertexArrayBuilder builder) {
		builder.setVertexAttribute(2, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, 0); // Model Matrix[0]
		builder.setVertexAttribute(3, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 4); // Model Matrix[1]
		builder.setVertexAttribute(4, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 8); // Model Matrix[2]
		builder.setVertexAttribute(5, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 12); // Model Matrix[3]
		builder.setVertexAttribute(6, 2, 1, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 16); // Scale
		builder.setVertexAttribute(7, 2, 2, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 17); // UV0 min
		builder.setVertexAttribute(8, 2, 2, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 19); // UV0 max
		builder.setVertexAttribute(9, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 21); // Color
		builder.setVertexIAttribute(10, 2, 2, VertexArrayBuilder.DataType.SHORT, Float.BYTES * 25); // UV2 / Light
		// The group's projected screen-space silhouette (its 2D convex hull - see
		// LevitationAuraEffectHandler#updateViewSilhouette), packed 2 points per vec4 attribute since
		// there's no dedicated per-particle data slot big enough for MAX_HULL_POINTS points otherwise.
		builder.setVertexAttribute(11, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, HULL_DATA_OFFSET); // Hull points 0-1
		builder.setVertexAttribute(12, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, HULL_DATA_OFFSET + Float.BYTES * 4); // Hull points 2-3
		builder.setVertexAttribute(13, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, HULL_DATA_OFFSET + Float.BYTES * 8); // Hull points 4-5
		builder.setVertexAttribute(14, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, HULL_DATA_OFFSET + Float.BYTES * 12); // Hull points 6-7
		builder.setVertexAttribute(15, 2, 4, VertexArrayBuilder.DataType.FLOAT, false, HULL_DATA_OFFSET + Float.BYTES * 16); // Hull points 8-9
	}

	@Override
	protected void putBufferData(QuasarParticle particle, Camera camera, ByteBuffer buffer) {
		RenderData renderData = particle.getRenderData();
		Quaternionf faceCameraRotation = new Quaternionf(
				MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
		SpriteData spriteData = renderData.getSpriteData();

		Vec3d projectedView = camera.getPos();
		Vector3f renderOffset = new Vector3f();
		Vector3dc renderPosition = renderData.getRenderPosition();
		renderOffset.set(
				(float) (renderPosition.x() - projectedView.x),
				(float) (renderPosition.y() - projectedView.y),
				(float) (renderPosition.z() - projectedView.z));

		Matrix4f transformationMatrix = new Matrix4f()
				.translate(renderOffset.x, renderOffset.y, renderOffset.z)
				.rotate(faceCameraRotation);
		transformationMatrix.get(buffer.position(), buffer);

		buffer.position(buffer.position() + Float.BYTES * 16);

		buffer.putFloat(renderData.getRenderRadius());

		float uMin = 0;
		float vMin = 0;
		float uMax = 1;
		float vMax = 1;
		if (spriteData != null) {
			uMin = spriteData.u(renderData.getRenderAge(), renderData.getAgePercent(), uMin);
			uMax = spriteData.u(renderData.getRenderAge(), renderData.getAgePercent(), uMax);
			vMin = spriteData.v(renderData.getRenderAge(), renderData.getAgePercent(), vMin);
			vMax = spriteData.v(renderData.getRenderAge(), renderData.getAgePercent(), vMax);
		}
		buffer.putFloat(uMin);
		buffer.putFloat(vMin);
		buffer.putFloat(uMax);
		buffer.putFloat(vMax);

		buffer.putFloat(renderData.getRed());
		buffer.putFloat(renderData.getGreen());
		buffer.putFloat(renderData.getBlue());
		buffer.putFloat(renderData.getAlpha());

		int packedLight = renderData.getPackedLight();
		buffer.putShort((short) ((packedLight & 65535)));
		buffer.putShort((short) ((packedLight >> 16 & 65535)));

		// GroupAuraCodeModule can't hand this RenderStyle its GroupAuraState directly - Veil only
		// ever passes us the bare QuasarParticle - so it stashes the association in a map keyed by
		// particle identity instead (see that class's doc). Falls back to a single point at the
		// origin (a tiny degenerate "hull") if the lookup ever misses, which the shader's polygon
		// distance function treats as a harmless zero-size shape rather than reading garbage.
		GroupAuraState state = GroupAuraCodeModule.STATE_BY_PARTICLE.get(particle);
		for (int i = 0; i < LevitationAuraEffectHandler.MAX_HULL_POINTS; i++) {
			buffer.putFloat(state != null ? state.hullX[i] : 0.0F);
			buffer.putFloat(state != null ? state.hullY[i] : 0.0F);
		}
	}

	@Override
	public RenderLayer getRenderType(QuasarParticle particle, RenderData renderData) {
		return LevitationAuraRenderType.get(RenderData.BLANK, renderData.isAdditive());
	}
}
