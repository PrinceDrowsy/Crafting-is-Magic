package craftingismagic.cim.client.pegasus;

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
 * Renders the Sonic Rainboom charge-up effect (a dome that lengthens into a
 * pointed, sparking cone as the charge builds) - same camera-facing plane
 * technique as {@link RainbowRingRenderStyle} (copied from Veil's own
 * {@code RenderStyle.Billboard}, which can't be subclassed directly), with
 * a different shader drawing the cone shape and electric sparks instead of
 * the ring pattern. Charge progress is carried entirely through the
 * particle's own age (its lifetime is set to match the charge duration),
 * not anything in this class.
 */
public final class ChargeConeRenderStyle extends RenderStyle {
	private static final Vector3fc[] PLANE_POSITIONS = {
			new Vector3f(1, -1, 0), new Vector3f(1, 1, 0), new Vector3f(-1, 1, 0), new Vector3f(-1, -1, 0),
	};

	public ChargeConeRenderStyle() {
		super(Float.BYTES * 25 + Short.BYTES * 2);
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
	}

	@Override
	protected void putBufferData(QuasarParticle particle, Camera camera, ByteBuffer buffer) {
		RenderData renderData = particle.getRenderData();
		Quaternionf faceCameraRotation = new Quaternionf(
				MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
		Vector3fc rotation = renderData.getRenderRotation();
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
				.rotate(faceCameraRotation.rotateLocalX(rotation.x()).rotateLocalY(rotation.y()).rotateLocalZ(rotation.z()));
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
	}

	@Override
	public RenderLayer getRenderType(QuasarParticle particle, RenderData renderData) {
		return ChargeConeRenderType.get(RenderData.BLANK, renderData.isAdditive());
	}
}
