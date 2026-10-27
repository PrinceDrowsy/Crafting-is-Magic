package craftingismagic.cim.client.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.rendertype.VeilRenderType;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;

import craftingismagic.cim.Cim;

/**
 * The loose, bright parts of unicorn magic (docs/magic-aura.md), drawn by this class itself so each
 * takes its caster's own color: the white wisps circling a horn while a spell is held, the glints
 * jumping across it and across whatever it holds, a release's flash and its shards flung upward and
 * outward, the sparkle dust a release or a sudden move leaves drifting, and a teleport's bubble, spikes
 * and arrival. Each spark is a quad facing the camera, shaped by the {@code cim:magic/spark} shader -
 * a streak stretched along its flight, a four-point star, or a bubble.
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

	/**
	 * A held spell: a wisp runs up the horn's spiral this often, and a glint jumps somewhere on the horn, or
	 * on a held thing, this often - each fading in and out within GLINT_LIFE, so a few are always lit, each
	 * somewhere new.
	 */
	private static final float WISP_EVERY = 0.16f;
	private static final float WISP_LIFE = 0.45f;
	private static final float GLINT_EVERY_MIN = 0.07f;
	private static final float GLINT_EVERY_MAX = 0.14f;
	private static final float GLINT_LIFE_MIN = 0.25f;
	private static final float GLINT_LIFE_MAX = 0.4f;
	/** A held spell gives off a speck of sparkle dust this often, even standing still. */
	private static final float DUST_EVERY_MIN = 0.2f;
	private static final float DUST_EVERY_MAX = 0.5f;

	/**
	 * A teleport (docs/magic-aura.md, teleporting): the bubble swells round the caster over TELEPORT_SWELL
	 * seconds, holds TELEPORT_HOLD while they vanish, then a star bursts out of its middle over TELEPORT_BURST,
	 * its points breaking through the wall as spikes, and it pops. (The shader's BURST_START is where the
	 * burst begins in that whole life.)
	 */
	private static final float TELEPORT_SWELL = 0.1f;
	private static final float TELEPORT_HOLD = 0.08f;
	private static final float TELEPORT_BURST = 0.08f;

	/** A bubble's quad reaches this many of its radii out, room for its spikes (the shader's BUBBLE_REACH). */
	private static final float BUBBLE_REACH = 4.5f;
	/** Its spikes are redrawn every BUBBLE_FRAME seconds - the show's own frame rate - repeating after BUBBLE_FRAMES (the shader's FRAMES). */
	private static final float BUBBLE_FRAME = 0.04f;
	private static final int BUBBLE_FRAMES = 16;
	/** How finely its progress through its life is told to the shader (the shader's LIFE_STEPS), for its curls' squash. */
	private static final int BUBBLE_LIFE_STEPS = 63;
	/** How many seeds there are. Kept small: the seed rides on v's whole part beside its fraction, and a big one costs the shape precision. */
	private static final int BUBBLE_SEEDS = 256;

	private enum Shape {
		STREAK, STAR, BUBBLE
	}

	private static final class Spark {
		Shape shape;
		Vec3d pos;
		Vec3d velocity = Vec3d.ZERO;
		/** How fast it slows (per second); negative-free. */
		float drag;
		/** Seconds since it appeared - negative while it's still to come. */
		float age;
		float life;
		/** Width, and for a streak its length per block-a-second of speed on top of its width. */
		float size;
		float stretch;
		int color;
		/** A glint: swells in and out again, rather than fading as it goes. */
		boolean twinkle;
		/** A wisp: circles its horn's spiral instead of flying free. */
		Horn horn;
		/** Where a wisp starts round its spiral, or how a bubble is turned to begin with (radians). */
		float turn;
		/** A glint on a held thing: kept at this offset from its middle as it moves. */
		Held held;
		Vec3d offset;
		/** A bubble's: which spikes it grows (the shader picks them from this), and how far below its middle the ground is (blocks; negative for none in reach). */
		int seed;
		float floorDrop = -1f;
		/** How fast it falls (blocks a second, each second). */
		float gravity;
		/** A firework sparkle's: how often it leaves a speck behind it (seconds; 0 for none), and how soon the next. */
		float trailEvery;
		float untilTrail;
	}

	/** A lit horn as last seen: its base and tip in the world, refreshed every frame it's drawn. */
	private static final class Horn {
		Vec3d base;
		Vec3d tip;
		int color;
		long seenFrame;
		float untilWisp;
		float untilGlint;
		float untilDust;
	}

	/** A held thing as last seen: its middle and half its size on each axis, refreshed every frame it's drawn. */
	private static final class Held {
		Vec3d middle;
		Vec3d half;
		int color;
		long seenFrame;
		float untilGlint;
		float untilDust;
	}

	/** A flash of real light (a Veil point light) that lights the world round it, brightening fast and dying off. */
	private static final class Flash {
		LightRenderHandle<PointLightData> handle;
		/** Seconds since it began - negative while it's still to come. */
		float age;
		float life;
		float brightness;
	}

	private static final List<Flash> FLASHES = new ArrayList<>();
	private static final List<Spark> SPARKS = new ArrayList<>();
	private static final Map<Integer, Horn> HORNS = new HashMap<>();
	private static final Map<Integer, Held> HELD = new HashMap<>();
	private static final Random RANDOM = Random.create();
	private static long frame;
	private static long lastNanos;
	private static VertexConsumerProvider.Immediate buffers;

	private MagicSparks() {
	}

	static void reset() {
		SPARKS.clear();
		HORNS.clear();
		HELD.clear();
		for (Flash flash : FLASHES) {
			if (flash.handle.isValid()) {
				flash.handle.free();
			}
		}
		FLASHES.clear();
	}

	private static void flash(Vec3d at, int color, float delay, float life, float radius, float brightness) {
		PointLightData data = new PointLightData();
		data.setPosition(at.x, at.y, at.z);
		data.setRadius(radius);
		data.setColor(color);
		data.setBrightness(0f);
		Flash flash = new Flash();
		flash.handle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
		flash.age = -delay;
		flash.life = life;
		flash.brightness = brightness;
		FLASHES.add(flash);
	}

	private static void updateFlashes(float seconds) {
		Iterator<Flash> flashes = FLASHES.iterator();
		while (flashes.hasNext()) {
			Flash flash = flashes.next();
			flash.age += seconds;
			if (flash.age >= flash.life || !flash.handle.isValid()) {
				if (flash.handle.isValid()) {
					flash.handle.free();
				}
				flashes.remove();
				continue;
			}
			float t = Math.max(flash.age, 0f) / flash.life;
			float fall = (1f - t) / 0.85f;
			float level = flash.age < 0f ? 0f : t < 0.15f ? t / 0.15f : fall * fall;
			flash.handle.getLightData().setBrightness(flash.brightness * level);
			flash.handle.markDirty();
		}
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

	/** A thing held in a spell, seen this frame: keeps glints jumping on its surface. {@code key} is its aura's own key. */
	static void shine(int key, Vec3d middle, Vec3d half, int color) {
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		if (middle.squaredDistanceTo(camera) > HOLD_RANGE * HOLD_RANGE) {
			return;
		}
		Held held = HELD.computeIfAbsent(key, k -> {
			Held fresh = new Held();
			fresh.untilGlint = RANDOM.nextFloat() * GLINT_EVERY_MAX;
			return fresh;
		});
		held.middle = middle;
		held.half = half;
		held.color = color;
		held.seenFrame = frame;
	}

	/** Sparkle dust - given off by a held spell, thrown off by a sudden move or a release: white specks drifting slowly, lingering about 0.6 s. */
	static void dust(Vec3d at, int count, int color) {
		for (int i = 0; i < count; i++) {
			Vec3d drift = new Vec3d(RANDOM.nextGaussian() * 0.15, 0.1 + RANDOM.nextDouble() * 0.2, RANDOM.nextGaussian() * 0.15);
			add(Shape.STAR, at.add(RANDOM.nextGaussian() * 0.12, RANDOM.nextDouble() * 0.2, RANDOM.nextGaussian() * 0.12), drift, 1f,
					0.5f + RANDOM.nextFloat() * 0.2f, 0.05f + RANDOM.nextFloat() * 0.03f, color);
		}
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
		dust(tip, 5, color);
	}

	/**
	 * Leaving in a teleport, round the caster's middle: the bubble swells round them, holds, then a star
	 * bursts out of its middle, its points breaking through as spikes, and it pops - gone at once, in a
	 * burst of white sparkles from its middle and a spray of shards off its surface, a few glints and
	 * lines after. The sparkles stream out from the start, a firework that peaks with the pop. Real light glows round it as it swells
	 * and flashes as it pops.
	 */
	static void depart(Vec3d from, float size, int color) {
		float radius = size * 0.75f;
		int pale = whiten(color, 0.3f);
		float gone = TELEPORT_SWELL + TELEPORT_HOLD + TELEPORT_BURST;
		// It lights the world round it as it swells, and flashes as it pops.
		flash(from, color, 0f, gone, 6f, 0.8f);
		flash(from, whiten(color, 0.5f), gone, 0.3f, 10f, 1.5f);
		Spark bubble = add(Shape.BUBBLE, from, Vec3d.ZERO, 0f, gone, radius * 2f, color);
		if (bubble != null) {
			bubble.turn = RANDOM.nextFloat() * MathHelper.TAU;
			bubble.seed = RANDOM.nextInt(BUBBLE_SEEDS);
			// The ground it rests on and squashes against, if any is within reach of it and its spikes.
			ClientWorld world = MinecraftClient.getInstance().world;
			if (world != null) {
				BlockHitResult ground = world.raycast(new RaycastContext(from, from.add(0, -radius * BUBBLE_REACH, 0), RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
				if (ground.getType() == HitResult.Type.BLOCK) {
					bubble.floorDrop = (float) (from.y - ground.getPos().y);
				}
			}
		}
		// A firework: white sparkles burst from its middle - a stream from the start, and a big burst with
		// the pop - slow, and fall a little as they fade, each leaving a trail of specks.
		for (int i = 0; i < 44; i++) {
			Spark sparkle = add(Shape.STAR, from, randomDirection().multiply(3.0 + RANDOM.nextDouble() * 3.0), 2.5f,
					0.7f + RANDOM.nextFloat() * 0.4f, 0.1f + RANDOM.nextFloat() * 0.05f, whiten(color, 0.9f));
			if (sparkle != null) {
				sparkle.gravity = 1.5f;
				sparkle.trailEvery = 0.035f;
			}
			delay(sparkle, i < 16 ? gone * i / 16f : gone);
		}
		for (int i = 0; i < 18; i++) {
			Vec3d out = randomDirection();
			Spark shard = add(Shape.STREAK, from.add(out.multiply(radius)), out.multiply(3.0 + RANDOM.nextDouble() * 2.0), 6f,
					0.2f + RANDOM.nextFloat() * 0.1f, 0.04f, pale);
			if (shard != null) {
				shard.stretch = 0.05f;
			}
			delay(shard, gone);
		}
		for (int i = 0; i < 3; i++) {
			Spark glint = add(Shape.STAR, from.add(randomDirection().multiply(0.25)), Vec3d.ZERO, 0f, 0.2f, 0.12f, whiten(color, 0.85f));
			if (glint != null) {
				glint.twinkle = true;
			}
			delay(glint, gone + RANDOM.nextFloat() * 0.05f);
			Spark line = add(Shape.STREAK, from, randomDirection().multiply(1.5), 4f, 0.2f, 0.02f, pale);
			if (line != null) {
				line.stretch = 0.08f;
			}
			delay(line, gone);
		}
	}

	/**
	 * Arriving from a teleport - at once, while the departure's bubble is still there: a flash round the
	 * caster's middle that lights the world, short burst lines, and glints twinkling round them for about 0.3 s.
	 */
	static void arrive(Vec3d to, float size, int color) {
		float radius = size * 0.75f;
		int pale = whiten(color, 0.3f);
		flash(to, whiten(color, 0.5f), 0f, 0.3f, 10f, 1.4f);
		add(Shape.STAR, to, Vec3d.ZERO, 0f, FLASH_LIFE, 0.5f, whiten(color, 0.7f));
		for (int i = 0; i < 8; i++) {
			Spark line = add(Shape.STREAK, to, randomDirection().multiply(6.0), 14f, 0.15f, 0.03f, pale);
			if (line != null) {
				line.stretch = 0.05f;
			}
		}
		for (int i = 0; i < 7; i++) {
			Spark glint = add(Shape.STAR, to.add(randomDirection().multiply(radius * (0.6 + RANDOM.nextDouble() * 0.4))), Vec3d.ZERO, 0f,
					0.12f + RANDOM.nextFloat() * 0.06f, 0.14f, whiten(color, 0.85f));
			if (glint != null) {
				glint.twinkle = true;
			}
			delay(glint, RANDOM.nextFloat() * 0.15f);
		}
	}

	private static void delay(Spark spark, float seconds) {
		if (spark != null) {
			spark.age = -seconds;
		}
	}

	private static Vec3d randomDirection() {
		return new Vec3d(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
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
		if (SPARKS.isEmpty() && HORNS.isEmpty() && HELD.isEmpty() && FLASHES.isEmpty()) {
			lastNanos = 0;
			return;
		}
		updateFlashes(seconds);
		updateHorns(seconds);
		updateHeld(seconds);
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
			if (spark.age < 0f) {
				continue;
			}
			float t = spark.age / spark.life;
			Vector3f at = new Vector3f((float) (spark.pos.x - camera.x), (float) (spark.pos.y - camera.y), (float) (spark.pos.z - camera.z));
			if (spark.shape == Shape.STAR) {
				// A glint swells in and out again within its moment; dust and the flash fade as they go.
				float rise = MathHelper.sin(t * MathHelper.PI);
				float alpha = spark.twinkle ? rise : spark.drag == 0f ? 1f - t : 1f - t * t;
				float size = spark.size * (spark.twinkle ? 0.6f + 0.4f * rise : spark.drag == 0f ? 1f - 0.5f * t : 1f);
				billboard(out, at, right, up, size, spark.color, alpha, 2f, 0);
			} else if (spark.shape == Shape.BUBBLE) {
				int frame = (int) (spark.age / BUBBLE_FRAME) % BUBBLE_FRAMES;
				int stage = Math.min(Math.round(t * BUBBLE_LIFE_STEPS), BUBBLE_LIFE_STEPS);
				// Turning slightly as it lives, everything on it with it.
				float turn = spark.turn + spark.age * 3f;
				Vector3f spunRight = new Vector3f(right).mul(MathHelper.cos(turn)).add(new Vector3f(up).mul(MathHelper.sin(turn)));
				Vector3f spunUp = new Vector3f(up).mul(MathHelper.cos(turn)).sub(new Vector3f(right).mul(MathHelper.sin(turn)));
				// Where the ground crosses it: which way is up on the quad (its own y runs down it), and how many
				// of its radii below its middle the floor line sits.
				float scale = bubbleScale(spark.age);
				float upX = spunRight.y;
				float upY = -spunUp.y;
				float tilt = (float) Math.sqrt(upX * upX + upY * upY);
				float floorAt = 1f;
				int facing = 0;
				if (spark.floorDrop >= 0f && tilt > 0.05f && scale > 0.01f) {
					floorAt = Math.min(spark.floorDrop / (tilt * spark.size / 2f * scale) / 4f, 0.99f);
					facing = Math.floorMod(Math.round((float) Math.atan2(upY, upX) / MathHelper.TAU * 64f), 64);
				}
				billboard(out, at, spunRight, spunUp, spark.size * BUBBLE_REACH * scale, spark.color, floorAt,
						4f + 2f * (stage + 64 * facing), spark.seed * BUBBLE_FRAMES + frame);
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
			horn.untilDust -= seconds;
			if (horn.untilDust <= 0f) {
				horn.untilDust = dustGap();
				dust(horn.base.lerp(horn.tip, 0.5 + RANDOM.nextDouble() * 0.6), 1, horn.color);
			}
			horn.untilGlint -= seconds;
			if (horn.untilGlint <= 0f) {
				horn.untilGlint = GLINT_EVERY_MIN + RANDOM.nextFloat() * (GLINT_EVERY_MAX - GLINT_EVERY_MIN);
				Vec3d along = horn.base.lerp(horn.tip, 0.2 + RANDOM.nextDouble() * 0.85);
				Vec3d jitter = new Vec3d(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).multiply(0.02);
				Spark glint = add(Shape.STAR, along.add(jitter), Vec3d.ZERO, 0f, glintLife(), 0.14f, whiten(horn.color, 0.85f));
				if (glint != null) {
					glint.horn = horn;
					glint.twinkle = true;
				}
			}
		}
	}

	private static void updateHeld(float seconds) {
		Iterator<Held> all = HELD.values().iterator();
		while (all.hasNext()) {
			Held held = all.next();
			if (held.seenFrame < frame - 1) {
				all.remove();
				continue;
			}
			held.untilDust -= seconds;
			if (held.untilDust <= 0f) {
				held.untilDust = dustGap();
				dust(held.middle.add(onSurface(held)), 1, held.color);
			}
			held.untilGlint -= seconds;
			if (held.untilGlint > 0f) {
				continue;
			}
			held.untilGlint = GLINT_EVERY_MIN + RANDOM.nextFloat() * (GLINT_EVERY_MAX - GLINT_EVERY_MIN);
			Vec3d offset = onSurface(held);
			Spark glint = add(Shape.STAR, held.middle.add(offset), Vec3d.ZERO, 0f, glintLife(), 0.22f, whiten(held.color, 0.85f));
			if (glint != null) {
				glint.held = held;
				glint.offset = offset;
				glint.twinkle = true;
			}
		}
	}

	/** A random spot on a held thing's surface, from its middle - just off it, so the thing itself doesn't hide what's there. */
	private static Vec3d onSurface(Held held) {
		double x = (RANDOM.nextDouble() * 2 - 1) * held.half.x;
		double y = (RANDOM.nextDouble() * 2 - 1) * held.half.y;
		double z = (RANDOM.nextDouble() * 2 - 1) * held.half.z;
		int face = RANDOM.nextInt(6);
		double out = face % 2 == 0 ? 1.0 : -1.0;
		if (face < 2) {
			x = out * (held.half.x + 0.03);
		} else if (face < 4) {
			y = out * (held.half.y + 0.03);
		} else {
			z = out * (held.half.z + 0.03);
		}
		return new Vec3d(x, y, z);
	}

	private static float dustGap() {
		return DUST_EVERY_MIN + RANDOM.nextFloat() * (DUST_EVERY_MAX - DUST_EVERY_MIN);
	}

	private static float glintLife() {
		return GLINT_LIFE_MIN + RANDOM.nextFloat() * (GLINT_LIFE_MAX - GLINT_LIFE_MIN);
	}

	private static void update(float seconds) {
		List<Spark> trail = new ArrayList<>();
		Iterator<Spark> sparks = SPARKS.iterator();
		while (sparks.hasNext()) {
			Spark spark = sparks.next();
			spark.age += seconds;
			if (spark.age >= spark.life || (spark.horn != null && !HORNS.containsValue(spark.horn))
					|| (spark.held != null && !HELD.containsValue(spark.held))) {
				sparks.remove();
				continue;
			}
			if (spark.age < 0f) {
				continue;
			}
			if (spark.held != null) {
				spark.pos = spark.held.middle.add(spark.offset);
				continue;
			}
			if (spark.horn != null && spark.shape == Shape.STREAK) {
				// A wisp: up the horn's spiral, a turn and a half from base to just past the tip, drawing in as it goes.
				Vec3d before = onSpiral(spark, Math.max(spark.age - 0.03f, 0f) / spark.life);
				spark.pos = onSpiral(spark, spark.age / spark.life);
				spark.velocity = spark.pos.subtract(before).multiply(1.0 / 0.03);
				continue;
			}
			spark.velocity = spark.velocity.multiply(Math.exp(-spark.drag * seconds)).add(0, -spark.gravity * seconds, 0);
			spark.pos = spark.pos.add(spark.velocity.multiply(seconds));
			if (spark.trailEvery > 0f) {
				spark.untilTrail -= seconds;
				if (spark.untilTrail <= 0f) {
					spark.untilTrail += spark.trailEvery;
					trail.add(spark);
				}
			}
		}
		// Added after, so the list isn't changed while it's walked.
		for (Spark spark : trail) {
			add(Shape.STAR, spark.pos, Vec3d.ZERO, 0f, 0.25f, spark.size * 0.45f, spark.color);
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

	/** A teleport bubble's size against its full size, {@code age} seconds in: swelling fast, then holding till it pops. */
	private static float bubbleScale(float age) {
		float s = 1f - Math.min(age / TELEPORT_SWELL, 1f);
		return 1f - s * s;
	}

	/**
	 * A square to the camera, its shape picked by {@code u} (the shader's: 2 a star, 4 on a bubble, which packs
	 * its life stage there too); {@code seed} rides on v's whole part, for a bubble's spikes and their frame.
	 */
	private static void billboard(VertexConsumer out, Vector3f at, Vector3f right, Vector3f up, float size, int color, float alpha, float u,
			int seed) {
		float half = size / 2f;
		float rx = right.x * half, ry = right.y * half, rz = right.z * half;
		float ux = up.x * half, uy = up.y * half, uz = up.z * half;
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int a = Math.round(MathHelper.clamp(alpha, 0f, 1f) * 255);
		out.vertex(at.x - rx - ux, at.y - ry - uy, at.z - rz - uz).texture(u, seed + 1f).color(r, g, b, a);
		out.vertex(at.x + rx - ux, at.y + ry - uy, at.z + rz - uz).texture(u + 1f, seed + 1f).color(r, g, b, a);
		out.vertex(at.x + rx + ux, at.y + ry + uy, at.z + rz + uz).texture(u + 1f, seed).color(r, g, b, a);
		out.vertex(at.x - rx + ux, at.y - ry + uy, at.z - rz + uz).texture(u, seed).color(r, g, b, a);
	}
}
