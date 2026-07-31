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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import foundry.veil.api.client.render.vertex.VeilVertexFormat;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;
import foundry.veil.api.quasar.particle.QuasarParticle;
import foundry.veil.api.quasar.particle.RenderData;
import foundry.veil.api.quasar.particle.RenderStyle;
import foundry.veil.api.quasar.particle.SpriteData;

/**
 * Renders the Sonic Rainboom ring as a single camera-facing plane using a
 * custom shader ({@code assets/cim/pinwheel/shaders/program/quasar/rainbow_ring})
 * instead of Veil's default particle shader - the ring pattern itself is
 * drawn procedurally in the fragment shader from distance-to-center, not
 * from a sprite texture. The mesh/buffer-packing logic below is copied
 * from Veil's own {@code RenderStyle.Billboard} (which is {@code final}
 * and can't be subclassed or reused directly) since only the shader
 * binding needs to differ - growth (via the "size" module) and fade (via
 * the "color" module's alpha) are driven entirely by JSON/Molang on the
 * particle itself, not by anything in this class.
 */
public final class RainbowRingRenderStyle extends RenderStyle {
	private static final Vector3fc[] PLANE_POSITIONS = {
			new Vector3f(1, -1, 0), new Vector3f(1, 1, 0), new Vector3f(-1, 1, 0), new Vector3f(-1, -1, 0),
	};

	public RainbowRingRenderStyle() {
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

	/** Vanilla's own fire animation - sampled as a shape/motion mask for the ring's flame-licked inner edge, tinted with our own rainbow hue rather than shown in its native orange. */
	private static final Identifier FIRE_TEXTURE = Identifier.of("minecraft", "textures/block/fire_0.png");

	@Override
	public RenderLayer getRenderType(QuasarParticle particle, RenderData renderData) {
		return RainbowRingRenderType.get(FIRE_TEXTURE, renderData.isAdditive());
	}
}
