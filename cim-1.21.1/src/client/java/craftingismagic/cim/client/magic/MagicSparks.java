package craftingismagic.cim.client.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderType;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.Cim;

/**
 * The loose, bright parts of unicorn magic (docs/magic-aura.md), drawn by this class itself so each
 * takes its caster's own color: the white wisps circling a horn while a spell is held and the glints
 * jumping across it, and a release's flash, its shards flung upward and outward, and the sparkle dust
 * left drifting. Each spark is a quad facing the camera, shaped by the {@code cim:magic/spark} shader -
 * a streak stretched along its flight, or a four-point star.
 *
 * <p>Cheap at every scale: everything here lives only on each viewer's own game, a horn's wisps and
 * glints only show within {@link #HOLD_RANGE}, and there are never more than {@link #MAX_SPARKS} at once.
 */
final class MagicSparks {
	private static final RenderPhase.ShaderProgram SHADER = VeilRenderBridge.shaderState(Identifier.of(Cim.MOD_ID, "magic/spark"));
	private static final RenderLayer LAYER = RenderLayer.of(Cim.MOD_ID + ":magic_spark", VertexFormats.POSITION_TEXTURE_COLOR,
			VertexFormat.DrawMode.QUADS, 4096, false, true,
			RenderLayer.MultiPhaseParameters.builder()
					.program(SHADER)
					.transparency(VeilRenderType.translucentTransparencyShard())
					.cull(VeilRenderType.noCullShard())
					.depthTest(VeilRenderType.lequalDepthTestShard())
					.writeMaskState(VeilRenderType.colorWriteShard())
					.build(false));

	private static final int MAX_SPARKS = 512;
	/** Beyond this many blocks a held spell's horn shows only its glow - no wisps or glints. */
	private static final double HOLD_RANGE = 24.0;

	/** A release (docs/magic-aura.md, releasing): 15-20 shards, gone by about 0.8 s; dust lingers about 0.6 s. */
	private static final int SHARDS_MIN = 15;
	private static final int SHARDS_MAX = 20;
	private static final float FLASH_LIFE = 0.12f;

	/** A held spell: a wisp runs up the horn's spiral this often, and a glint jumps somewhere on it this often. */
	private static final float WISP_EVERY = 0.16f;
	private static final float WISP_LIFE = 0.45f;
	private static final float GLINT_EVERY_MIN = 0.3f;
	private static final float GLINT_EVERY_MAX = 0.7f;

	private enum Shape {
		STREAK, STAR
	}

	private static final class Spark {
		Shape shape;
		Vec3d pos;
		Vec3d velocity = Vec3d.ZERO;
		/** How fast it slows (per second); negative-free. */
		float drag;
		float age;
		float life;
		/** Width, and for a streak its length per block-a-second of speed on top of its width. */
		float size;
		float stretch;
		int color;
		/** A wisp: circles its horn's spiral instead of flying free. */
		Horn horn;
		float turn;
	}

	/** A lit horn as last seen: its base and tip in the world, refreshed every frame it's drawn. */
	private static final class Horn {
		Vec3d base;
		Vec3d tip;
		int color;
		long seenFrame;
		float untilWisp;
		float untilGlint;
	}

	private static final List<Spark> SPARKS = new ArrayList<>();
	private static final Map<Integer, Horn> HORNS = new HashMap<>();
	private static final Random RANDOM = Random.create();
	private static long frame;
	private static long lastNanos;
	private static VertexConsumerProvider.Immediate buffers;

	private MagicSparks() {
	}

	static void reset() {
		SPARKS.clear();
		HORNS.clear();
	}

	/**
	 * A horn holding a spell, seen this frame: keeps its wisps circling and its glints jumping. {@code key}
	 * is the horn aura's own key.
	 */
	static void hold(int key, Vec3d base, Vec3d tip, int color) {
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		if (tip.squaredDistanceTo(camera) > HOLD_RANGE * HOLD_RANGE) {
			return;
		}
		Horn horn = HORNS.computeIfAbsent(key, k -> {
			Horn fresh = new Horn();
			fresh.untilGlint = RANDOM.nextFloat() * GLINT_EVERY_MAX;
			return fresh;
		});
		horn.base = base;
		horn.tip = tip;
		horn.color = color;
		horn.seenFrame = frame;
	}

	/**
	 * Letting a spell go: a white flash at the tip, then the gathered glow breaks into shards - the color
	 * with white cores, stretched along their flight - flung upward and outward, rising, spreading, shrinking
	 * and fading, with a little sparkle dust left drifting after them.
	 */
	static void release(Vec3d tip, int color) {
		Spark flash = add(Shape.STAR, tip, Vec3d.ZERO, 0f, FLASH_LIFE, 0.35f, color);
		if (flash == null) {
			return;
		}
		int shards = SHARDS_MIN + RANDOM.nextInt(SHARDS_MAX - SHARDS_MIN + 1);
		for (int i = 0; i < shards; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double out = 0.5 + RANDOM.nextDouble() * 0.9;
			double up = 1.0 + RANDOM.nextDouble() * 1.4;
			Vec3d velocity = new Vec3d(Math.cos(angle) * out, up, Math.sin(angle) * out);
			Spark shard = add(Shape.STREAK, tip, velocity, 2.5f, 0.5f + RANDOM.nextFloat() * 0.3f, 0.035f + RANDOM.nextFloat() * 0.02f, color);
			if (shard != null) {
				shard.stretch = 0.07f;
			}
		}
		for (int i = 0; i < 5; i++) {
			Vec3d drift = new Vec3d(RANDOM.nextGaussian() * 0.15, 0.1 + RANDOM.nextDouble() * 0.2, RANDOM.nextGaussian() * 0.15);
			add(Shape.STAR, tip.add(RANDOM.nextGaussian() * 0.12, RANDOM.nextDouble() * 0.2, RANDOM.nextGaussian() * 0.12), drift, 1f,
					0.5f + RANDOM.nextFloat() * 0.2f, 0.05f + RANDOM.nextFloat() * 0.03f, color);
		}
	}

	private static int whiten(int color, float amount) {
		int r = Math.round(MathHelper.lerp(amount, (color >> 16) & 0xFF, 255));
		int g = Math.round(MathHelper.lerp(amount, (color >> 8) & 0xFF, 255));
		int b = Math.round(MathHelper.lerp(amount, color & 0xFF, 255));
		return r << 16 | g << 8 | b;
	}

	private static Spark add(Shape shape, Vec3d pos, Vec3d velocity, float drag, float life, float size, int color) {
		if (SPARKS.size() >= MAX_SPARKS) {
			return null;
		}
		Spark spark = new Spark();
		spark.shape = shape;
		spark.pos = pos;
		spark.velocity = velocity;
		spark.drag = drag;
		spark.life = life;
		spark.size = size;
		spark.color = color;
		SPARKS.add(spark);
		return spark;
	}

	/** After the world's translucent things: everything moves on, the horns make new wisps and glints, and it's all drawn. */
	static void render(WorldRenderContext context) {
		long now = System.nanoTime();
		float seconds = lastNanos == 0 ? 0f : MathHelper.clamp((now - lastNanos) / 1.0e9f, 0f, 0.1f);
		lastNanos = now;
		frame++;
		if (SPARKS.isEmpty() && HORNS.isEmpty()) {
			lastNanos = 0;
			return;
		}
		updateHorns(seconds);
		update(seconds);
		if (SPARKS.isEmpty()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d camera = context.camera().getPos();
		Quaternionf rotation = client.getEntityRenderDispatcher().getRotation();
		Vector3f right = rotation.transform(new Vector3f(1f, 0f, 0f));
		Vector3f up = rotation.transform(new Vector3f(0f, 1f, 0f));
		if (buffers == null) {
			buffers = VertexConsumerProvider.immediate(new BufferAllocator(1 << 16));
		}
		VertexConsumer out = buffers.getBuffer(LAYER);
		for (Spark spark : SPARKS) {
			float t = spark.age / spark.life;
			Vector3f at = new Vector3f((float) (spark.pos.x - camera.x), (float) (spark.pos.y - camera.y), (float) (spark.pos.z - camera.z));
			if (spark.shape == Shape.STAR) {
				// A glint is lit only a moment; dust and the flash fade as they go.
				float alpha = spark.horn == null && spark.drag == 0f ? 1f - t : 1f - t * t;
				float size = spark.size * (spark.drag == 0f ? 1f - 0.5f * t : 1f);
				star(out, at, right, up, size, spark.color, alpha);
			} else {
				streak(out, at, spark, t);
			}
		}
		buffers.draw();
	}

	private static void updateHorns(float seconds) {
		Iterator<Horn> horns = HORNS.values().iterator();
		while (horns.hasNext()) {
			Horn horn = horns.next();
			// Not drawn last frame: let go of, gone from view, or too far off.
			if (horn.seenFrame < frame - 1) {
				horns.remove();
				continue;
			}
			horn.untilWisp -= seconds;
			if (horn.untilWisp <= 0f) {
				horn.untilWisp += WISP_EVERY * (0.7f + RANDOM.nextFloat() * 0.6f);
				// White with only a hint of the color: a wisp is too thin for the streak's white core to show.
				Spark wisp = add(Shape.STREAK, horn.base, Vec3d.ZERO, 0f, WISP_LIFE, 0.026f, whiten(horn.color, 0.8f));
				if (wisp != null) {
					wisp.horn = horn;
					wisp.turn = RANDOM.nextFloat() * MathHelper.TAU;
					wisp.stretch = 0.06f;
				}
			}
			horn.untilGlint -= seconds;
			if (horn.untilGlint <= 0f) {
				horn.untilGlint = GLINT_EVERY_MIN + RANDOM.nextFloat() * (GLINT_EVERY_MAX - GLINT_EVERY_MIN);
				Vec3d along = horn.base.lerp(horn.tip, 0.2 + RANDOM.nextDouble() * 0.85);
				Vec3d jitter = new Vec3d(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).multiply(0.02);
				Spark glint = add(Shape.STAR, along.add(jitter), Vec3d.ZERO, 0f, 0.08f + RANDOM.nextFloat() * 0.08f, 0.085f, whiten(horn.color, 0.6f));
				if (glint != null) {
					glint.horn = horn;
				}
			}
		}
	}

	private static void update(float seconds) {
		Iterator<Spark> sparks = SPARKS.iterator();
		while (sparks.hasNext()) {
			Spark spark = sparks.next();
			spark.age += seconds;
			if (spark.age >= spark.life || (spark.horn != null && !HORNS.containsValue(spark.horn))) {
				sparks.remove();
				continue;
			}
			if (spark.horn != null && spark.shape == Shape.STREAK) {
				// A wisp: up the horn's spiral, a turn and a half from base to just past the tip, drawing in as it goes.
				Vec3d before = onSpiral(spark, Math.max(spark.age - 0.03f, 0f) / spark.life);
				spark.pos = onSpiral(spark, spark.age / spark.life);
				spark.velocity = spark.pos.subtract(before).multiply(1.0 / 0.03);
				continue;
			}
			spark.velocity = spark.velocity.multiply(Math.exp(-spark.drag * seconds));
			spark.pos = spark.pos.add(spark.velocity.multiply(seconds));
		}
	}

	private static Vec3d onSpiral(Spark spark, float t) {
		Horn horn = spark.horn;
		Vec3d axis = horn.tip.subtract(horn.base);
		double length = axis.length();
		if (length < 1.0e-4) {
			return horn.tip;
		}
		Vec3d along = axis.multiply(1.0 / length);
		Vec3d side = Math.abs(along.y) < 0.9 ? along.crossProduct(new Vec3d(0, 1, 0)).normalize() : along.crossProduct(new Vec3d(1, 0, 0)).normalize();
		Vec3d other = along.crossProduct(side);
		double angle = spark.turn + t * Math.PI * 3;
		double radius = length * 0.16 * (1.0 - 0.4 * t);
		return horn.base.add(axis.multiply(0.1 + 1.0 * t))
				.add(side.multiply(Math.cos(angle) * radius))
				.add(other.multiply(Math.sin(angle) * radius));
	}

	/** A streak: stretched along its flight, turned on its own axis to face the camera as well as it can. */
	private static void streak(VertexConsumer out, Vector3f at, Spark spark, float t) {
		Vector3f flight = new Vector3f((float) spark.velocity.x, (float) spark.velocity.y, (float) spark.velocity.z);
		float speed = flight.length();
		float alpha;
		float width;
		if (spark.horn != null) {
			// A wisp brightens and dims as it runs.
			alpha = MathHelper.sin(t * MathHelper.PI);
			width = spark.size;
		} else {
			// A shard keeps its brightness a while, then shrinks and fades.
			alpha = t < 0.4f ? 1f : 1f - (t - 0.4f) / 0.6f;
			width = spark.size * (1f - 0.7f * t);
		}
		Vector3f toCamera = new Vector3f(at).negate();
		if (speed < 1.0e-3 || toCamera.lengthSquared() < 1.0e-6) {
			return;
		}
		flight.div(speed);
		Vector3f side = new Vector3f(flight).cross(toCamera);
		if (side.lengthSquared() < 1.0e-8) {
			return;
		}
		side.normalize(width);
		float half = (width + speed * spark.stretch) / 2f;
		Vector3f head = new Vector3f(flight).mul(half).add(at);
		Vector3f tail = new Vector3f(flight).mul(-half).add(at);
		int r = (spark.color >> 16) & 0xFF;
		int g = (spark.color >> 8) & 0xFF;
		int b = spark.color & 0xFF;
		int a = Math.round(MathHelper.clamp(alpha, 0f, 1f) * 255);
		out.vertex(tail.x - side.x, tail.y - side.y, tail.z - side.z).texture(0f, 0f).color(r, g, b, a);
		out.vertex(tail.x + side.x, tail.y + side.y, tail.z + side.z).texture(0f, 1f).color(r, g, b, a);
		out.vertex(head.x + side.x, head.y + side.y, head.z + side.z).texture(1f, 1f).color(r, g, b, a);
		out.vertex(head.x - side.x, head.y - side.y, head.z - side.z).texture(1f, 0f).color(r, g, b, a);
	}

	/** A four-point star, square to the camera. */
	private static void star(VertexConsumer out, Vector3f at, Vector3f right, Vector3f up, float size, int color, float alpha) {
		float half = size / 2f;
		float rx = right.x * half, ry = right.y * half, rz = right.z * half;
		float ux = up.x * half, uy = up.y * half, uz = up.z * half;
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int a = Math.round(MathHelper.clamp(alpha, 0f, 1f) * 255);
		out.vertex(at.x - rx - ux, at.y - ry - uy, at.z - rz - uz).texture(2f, 1f).color(r, g, b, a);
		out.vertex(at.x + rx - ux, at.y + ry - uy, at.z + rz - uz).texture(3f, 1f).color(r, g, b, a);
		out.vertex(at.x + rx + ux, at.y + ry + uy, at.z + rz + uz).texture(3f, 0f).color(r, g, b, a);
		out.vertex(at.x - rx + ux, at.y - ry + uy, at.z - rz + uz).texture(2f, 0f).color(r, g, b, a);
	}
}
