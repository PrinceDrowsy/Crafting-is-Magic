package craftingismagic.cim.client.magic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeAccessor;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.HornGlowS2CPayload;
import craftingismagic.cim.network.TeleportS2CPayload;

/**
 * Draws the magic aura (docs/magic-aura.md, the visual profile): on whatever a unicorn holds, and on
 * their horn while they channel magic - the thing itself tinted, a saturated rim hugging its outline,
 * and a soft haze beyond, each its own aura, attached to it.
 *
 * <p>How: each frame, at its start ({@link #prepare}), every aura gets a slot and the
 * {@code cim:magic_mask} framebuffer is cleared. Horns are drawn into the mask while the world's
 * entities render, as each horn renders ({@link #onHornRendered} - the real horn part, so the glow takes
 * its shape); held things are drawn into it after ({@link #draw}), through their own textures so
 * see-through pixels stay holes. The mask records only each aura's slot; each slot's color, fade and
 * width go to the {@code cim:magic_aura} post pipeline as uniforms. That pipeline hides the mask where
 * the world is in front of it, finds every pixel's nearest aura, and paints the tint inside and the rim
 * and haze outside, sized in world units, the edge waving smoothly. It only runs while there's an aura.
 */
public final class MagicAuraRenderer {
	private static final Identifier MASK = Identifier.of(Cim.MOD_ID, "magic_mask");
	private static final Identifier PIPELINE = Identifier.of(Cim.MOD_ID, "magic_aura");
	private static final RenderPhase.ShaderProgram MASK_SHADER = VeilRenderBridge.shaderState(Identifier.of(Cim.MOD_ID, "magic/aura_mask"));

	/** Rim and haze widths in blocks, and how fast the edge's wave rolls (blocks of drift a second, roughly). */
	private static final float RIM_WIDTH = 0.1f;
	private static final float HAZE_WIDTH = 0.08f;
	private static final float WAVE_SPEED = 0.6f;
	/** Minecraft's own near plane (GameRenderer#getBasicProjectionMatrix). */
	private static final float NEAR_PLANE = 0.05f;

	/** Seconds for a held thing's aura to fade in as it's grabbed, and out as it's let go. */
	private static final float FADE_IN = 0.5f;
	private static final float FADE_OUT = 0.7f;

	/**
	 * The horn (docs/magic-aura.md, the life of a spell): it gathers over HORN_GATHER seconds - a wide,
	 * pale haze drawing in tight and taking on the color - and on release flashes white for HORN_FLASH
	 * seconds and is back to normal by HORN_FADE.
	 */
	private static final float HORN_GATHER = 0.6f;
	private static final float HORN_FADE = 0.3f;
	private static final float HORN_FLASH = 0.12f;
	/**
	 * A horn is small, so its rim and haze are a fraction of a held thing's - about a horn's width out,
	 * per the profile - starting wide as it gathers.
	 */
	private static final float HORN_WIDTH = 0.45f;
	private static final float HORN_GATHER_WIDTH = 1.6f;
	/**
	 * How much a horn's glow swells on its upper side - the flame rising off it (docs/magic-aura.md: tallest
	 * above the tip) - drawn from the horn's own shape, so it fits any horn, however curved.
	 */
	private static final float HORN_RISE = 2.2f;

	/**
	 * Motion (docs/magic-aura.md, how it moves): anything moving trails its glow behind it, this many seconds
	 * of its motion long, up to a limit (in blocks) - a horn's shorter, as it's smaller.
	 */
	private static final float TRAIL_SECONDS = 0.1f;
	private static final float TRAIL_MAX = 0.8f;
	private static final float HORN_TRAIL_SECONDS = 0.05f;
	private static final float HORN_TRAIL_MAX = 0.35f;
	/**
	 * The magic has weight: its trail follows the motion on a spring, so it lags behind a sudden move, then
	 * swings past when the move stops - billowing ahead - before settling. Underdamped on purpose.
	 */
	private static final float TRAIL_STIFFNESS = 120f;
	private static final float TRAIL_DAMPING = 9f;
	/**
	 * A trail out of place by more than this (in blocks; a horn's half that) flings droplets - this many a
	 * second for every block past it - so a sudden stop throws a handful.
	 */
	private static final float DROPLET_THRESHOLD = 0.12f;
	private static final float DROPLET_RATE = 40f;
	/** However hard it's swung, a held thing (or a horn) has only a few drops off it at once, a moment apart. */
	private static final int DROPS_PER_AURA = 3;
	private static final float DROP_GAP = 0.18f;
	/**
	 * Droplets are blobs of the aura itself, solid in its rim's color: each is handed to the composite as a
	 * circle on screen, which flows it into its aura's own outline where they meet - so it swells out of
	 * the rim like a bubble being blown, a neck joining them that thins and pinches off as it drifts
	 * clear, like a lava lamp's. Slow, swelling and settling as it goes, rising a little, and shrinking
	 * away. Only the {@link #DROPS_SHOWN} nearest the camera are drawn (the shader's uniform slots).
	 */
	private static final int DROPS_MAX = 24;
	private static final int DROPS_SHOWN = 12;
	private static final float DROP_RISE = 0.2f;
	private static final float DROP_DRAG = 1.5f;
	/** A drop grows out of the surface over this share of its life. */
	private static final float DROP_GROW = 0.25f;
	/** When the aura they came off fades out, its drops shrink away this much faster, with it. */
	private static final float DROP_HURRY = 3f;
	private static final float[] DROP_X = new float[DROPS_SHOWN];
	private static final float[] DROP_Y = new float[DROPS_SHOWN];
	private static final float[] DROP_RADIUS = new float[DROPS_SHOWN];
	private static final float[] DROP_DEPTH = new float[DROPS_SHOWN];
	private static final int[] DROP_SLOT = new int[DROPS_SHOWN];
	private static int dropsShown;

	private static final class Drop {
		/** The aura it came off (its key in {@link #AURAS}) - it takes that aura's slot. */
		int key;
		Vec3d pos;
		Vec3d velocity;
		float age;
		float life;
		float radius;
		float phase;
	}

	private static final List<Drop> DROPS = new ArrayList<>();
	private static final Random RANDOM = Random.create();

	/**
	 * A fast horn swing leaves a crescent along the tip's path (docs/magic-aura.md, how it moves). The path
	 * is kept relative to the caster's body, so running alone draws none. Only drawn once the last
	 * CRESCENT_SECONDS of path are longer than CRESCENT_MIN; it narrows from CRESCENT_WIDTH at the horn, and
	 * its oldest end hooks over (HOOK_SHARE of its length, turning HOOK_TURN radians) to a point.
	 */
	private static final float CRESCENT_SECONDS = 0.3f;
	private static final double CRESCENT_MIN = 0.3;
	private static final float CRESCENT_WIDTH = 0.05f;
	private static final float HOOK_SHARE = 0.25f;
	private static final float HOOK_TURN = 2.6f;
	private static final int HOOK_STEPS = 6;
	/** Seconds the renderer has been running, for timing the crescent's path. */
	private static float clock;

	/** A thing that moves further than this in one frame jumped (a teleport) - it leaves no trail. */
	private static final double JUMP = 4.0;
	/** The longest stretch the shader is asked to draw, in rim widths. */
	private static final float STRETCH_MAX = 14f;

	/** One aura: on a held thing, or on a caster's horn. Its caster's color, and how far it has faded in (0-1). */
	private static final class Aura {
		int color;
		/** Kept, not looked up afresh, so the aura can still fade out after the thing's display entity is gone. */
		Entity entity;
		float strength;
		boolean released;
		/** Where a released block landed in the world - its aura fades out there, snapped to the grid with it. */
		BlockPos placed;
		BlockState placedState;
		/** A horn's glow rather than a held thing's: drawn where the horn renders, and gathers and releases. */
		boolean horn;
		int casterId;
		/** Seconds since it was released (for the release flash). */
		float sinceRelease;
		/** Where the horn's tip and base were last seen (for its wisps and the release burst), or null. */
		Vec3d tip;
		Vec3d base;
		/** Whether the horn was drawn this frame - its flame only shows then. */
		boolean tipSeen;
		/** This frame's slot (1-8), or 0 if it isn't drawn this frame. */
		int slot;
		/** Where it was last frame (a held thing's middle, a horn's tip), or null if it wasn't seen. */
		Vec3d lastAt;
		/** Its trail, in blocks behind it in the world, and how fast that's changing (the spring's state). */
		final Vector3f trail = new Vector3f();
		final Vector3f trailVelocity = new Vector3f();
		/** Droplets owed but not yet flung (a fraction carried from frame to frame), and how soon the next may go. */
		float dropletsDue;
		float dropGap;
		/** A horn tip's recent path relative to its caster: x, y, z, and when. Newest last. */
		final ArrayDeque<double[]> swing = new ArrayDeque<>();
	}

	/** Held entity id (or a horn's key - see {@link #setHorn}) -> its aura. */
	private static final Map<Integer, Aura> AURAS = new HashMap<>();
	/** How many auras can be drawn at once (the shader's uniform slots). */
	private static final int SLOTS = 8;
	private static final int[] SLOT_COLORS = new int[SLOTS];
	private static final float[] SLOT_FADES = new float[SLOTS];
	/** How wide each slot's rim and haze are, against the usual width. */
	private static final float[] SLOT_WIDTHS = new float[SLOTS];
	/** Which way and how far each slot's glow is drawn out on screen, in rim widths (a horn's flame, a trail). */
	private static final float[] SLOT_STRETCH_X = new float[SLOTS];
	private static final float[] SLOT_STRETCH_Y = new float[SLOTS];
	private static int slotsUsed;
	private static long lastFrameNanos;
	private static float frameSeconds;

	/** True between the start of the world's frame and its entities being done - the only time horns are taken. */
	private static boolean entityPass;
	/** True while this class itself redraws things into the mask, so a horn met then isn't taken twice. */
	private static boolean maskPass;
	/** The entity the game is rendering right now (see {@code EntityRenderingMixin}). */
	private static Entity rendering;

	private static final BiFunction<RenderPhase.TextureBase, RenderPhase.Cull, RenderLayer> MASK_LAYERS = Util.memoize((texture, cull) ->
			RenderLayer.of(Cim.MOD_ID + ":magic_mask", VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 1536, false, false,
					RenderLayer.MultiPhaseParameters.builder()
							.program(MASK_SHADER)
							.texture(texture)
							.cull(cull)
							.depthTest(VeilRenderType.lequalDepthTestShard())
							.writeMaskState(VeilRenderType.colorDepthWriteShard())
							.target(VeilRenderBridge.outputState(MASK))
							.build(false)));

	private static VertexConsumerProvider.Immediate buffers;
	private static boolean pipelineOn;

	private MagicAuraRenderer() {
	}

	public static void register() {
		WorldRenderEvents.START.register(MagicAuraRenderer::prepare);
		WorldRenderEvents.AFTER_ENTITIES.register(MagicAuraRenderer::draw);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(MagicSparks::render);
		ClientPlayNetworking.registerGlobalReceiver(HornGlowS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> setHorn(payload.casterId(), payload.active(), payload.color())));
		ClientPlayNetworking.registerGlobalReceiver(TeleportS2CPayload.ID, (payload, context) -> context.client().execute(() -> teleported(payload)));
	}

	/**
	 * One end of a caster's teleport. The spell is spent in the jump, so the horn flashes and goes out
	 * without throwing shards (the glow going out arrives just after this, and is then a no-op).
	 */
	private static void teleported(TeleportS2CPayload payload) {
		Aura aura = AURAS.get(-payload.casterId() - 1);
		if (aura != null && !aura.released) {
			aura.released = true;
			aura.sinceRelease = 0f;
		}
		if (payload.arrival()) {
			MagicSparks.arrive(payload.at(), payload.size(), payload.color());
		} else {
			MagicSparks.depart(payload.at(), payload.size(), payload.color());
		}
	}

	/** Grabbing fades an aura in (or back in, if it was still fading out); letting go fades it out. */
	public static void set(List<Integer> entityIds, boolean active, int color) {
		for (int id : entityIds) {
			if (active) {
				Aura aura = AURAS.computeIfAbsent(id, key -> new Aura());
				aura.color = color & 0xFFFFFF;
				aura.released = false;
			} else {
				Aura aura = AURAS.get(id);
				if (aura != null) {
					aura.released = true;
				}
			}
		}
	}

	/**
	 * A caster's horn lights up (gathering) or goes out (a flash, and a burst of sparks flung up off it).
	 * Keyed apart from held things' entity ids.
	 */
	public static void setHorn(int casterId, boolean active, int color) {
		int key = -casterId - 1;
		if (active) {
			Aura aura = AURAS.computeIfAbsent(key, k -> new Aura());
			aura.horn = true;
			aura.casterId = casterId;
			aura.color = color & 0xFFFFFF;
			aura.released = false;
			aura.sinceRelease = 0f;
			return;
		}
		Aura aura = AURAS.get(key);
		if (aura != null && !aura.released) {
			aura.released = true;
			aura.sinceRelease = 0f;
			burst(aura);
		}
	}

	/** On leaving a world: entity ids are reused in the next one. */
	public static void reset() {
		AURAS.clear();
		DROPS.clear();
		MagicSparks.reset();
	}

	/** Which entity is rendering (null when it's done) - the horn hook needs to know whose horn it is. */
	public static void setRendering(Entity entity) {
		rendering = entity;
	}

	/**
	 * A horn is being rendered (MineLittlePony's, or a Figura avatar's labelled horn part): if its owner is
	 * channelling magic, draw the part itself into the mask in their aura's slot - so the glow takes the
	 * horn's own shape, and its flame rises off that shape (the shader's upward swell). {@code baseLocal}
	 * and {@code tipLocal} are the horn's base and tip in the part's own space (after its transform), in
	 * blocks - what its wisps circle, and where a release's shards fly from.
	 *
	 * @return whether this horn is lit (so the caller can hide its own glow)
	 */
	public static boolean onHornRendered(ModelPart part, MatrixStack matrices, int light, int overlay, Vector3f baseLocal, Vector3f tipLocal) {
		if (!entityPass || maskPass || rendering == null || buffers == null) {
			return false;
		}
		Aura aura = AURAS.get(-rendering.getId() - 1);
		if (aura == null || aura.slot == 0) {
			return aura != null && !aura.released;
		}
		VertexConsumer out = new MaskConsumers(buffers, aura.slot).solid();
		part.render(matrices, out, light, overlay);
		matrices.push();
		part.rotate(matrices);
		Matrix4f pose = matrices.peek().getPositionMatrix();
		Vector3f tip = pose.transformPosition(new Vector3f(tipLocal));
		Vector3f base = pose.transformPosition(new Vector3f(baseLocal));
		matrices.pop();
		Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		aura.tip = new Vec3d(tip.x, tip.y, tip.z).add(camera);
		aura.base = new Vec3d(base.x, base.y, base.z).add(camera);
		aura.tipSeen = true;
		return true;
	}

	/** Advances every aura's fade, and keeps hold of each thing's entity while it's still in the world. */
	private static void updateFades(MinecraftClient client) {
		long now = System.nanoTime();
		float seconds = lastFrameNanos == 0 ? 0f : MathHelper.clamp((now - lastFrameNanos) / 1.0e9f, 0f, 0.1f);
		lastFrameNanos = now;
		frameSeconds = seconds;
		clock += seconds;
		AURAS.values().removeIf(aura -> {
			float in = aura.horn ? HORN_GATHER : FADE_IN;
			float out = aura.horn ? HORN_FADE : FADE_OUT;
			aura.strength = MathHelper.clamp(aura.strength + (aura.released ? -seconds / out : seconds / in), 0f, 1f);
			if (aura.released) {
				aura.sinceRelease += seconds;
			}
			return aura.released && (aura.strength <= 0f || aura.entity == null);
		});
		for (Map.Entry<Integer, Aura> entry : AURAS.entrySet()) {
			Aura aura = entry.getValue();
			Entity entity = client.world.getEntityById(aura.horn ? aura.casterId : entry.getKey());
			if (entity != null) {
				aura.entity = entity;
			} else if (aura.released && aura.placed == null && aura.entity instanceof DisplayEntity.BlockDisplayEntity display) {
				findPlacedBlock(client, aura, display);
			}
		}
	}

	/**
	 * A placed block's floating display vanishes and the real block appears on the grid beside where it
	 * floated: finds that block (the same block state, within a block of the last spot), so the fading
	 * aura snaps into place with it.
	 */
	private static void findPlacedBlock(MinecraftClient client, Aura aura, DisplayEntity.BlockDisplayEntity display) {
		BlockState state = display.getBlockState();
		Vec3d corner = display.getPos();
		BlockPos rounded = BlockPos.ofFloored(corner.add(0.5, 0.5, 0.5));
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(rounded.add(-1, -1, -1), rounded.add(1, 1, 1))) {
			double distance = Vec3d.of(pos).squaredDistanceTo(corner);
			if (distance < best && client.world.getBlockState(pos) == state) {
				best = distance;
				aura.placed = pos.toImmutable();
				aura.placedState = state;
			}
		}
	}

	/** Start of the world's frame: fades move on, each aura gets its slot, and the mask is cleared for this frame. */
	private static void prepare(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		entityPass = false;
		slotsUsed = 0;
		if (client.world == null) {
			return;
		}
		updateFades(client);
		if (AURAS.isEmpty()) {
			lastFrameNanos = 0;
			return;
		}
		for (Aura aura : AURAS.values()) {
			aura.slot = 0;
			aura.tipSeen = false;
			Entity entity = aura.entity;
			if (entity == null || aura.strength <= 0f || slotsUsed >= SLOTS) {
				continue;
			}
			// Your own horn would fill the view in first person.
			if (aura.horn && entity == client.player && client.options.getPerspective().isFirstPerson()) {
				continue;
			}
			int slot = slotsUsed++;
			aura.slot = slot + 1;
			float eased = aura.strength * aura.strength * (3f - 2f * aura.strength);
			SLOT_COLORS[slot] = aura.color;
			SLOT_FADES[slot] = eased;
			// A held thing's glow gathers out to its full width.
			SLOT_WIDTHS[slot] = 0.4f + 0.6f * eased;
			if (aura.horn) {
				float flash = aura.released ? MathHelper.clamp(1f - aura.sinceRelease / HORN_FLASH, 0f, 1f) : 0f;
				// A horn's draws in from wide as it gathers, and swells a little in the release flash.
				SLOT_WIDTHS[slot] = aura.released ? HORN_WIDTH * (1f + 0.6f * flash) : MathHelper.lerp(eased, HORN_GATHER_WIDTH, HORN_WIDTH);
				// Gathering, it starts pale and takes on the color; releasing, it flares white first.
				SLOT_COLORS[slot] = mix(aura.color, 0xFFFFFF, aura.released ? 0.85f * flash : 0.6f * (1f - eased));
				if (aura.released) {
					SLOT_FADES[slot] = Math.max(eased, flash);
				}
			}
		}
		if (slotsUsed == 0) {
			return;
		}
		AdvancedFbo mask = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(MASK);
		if (mask == null) {
			slotsUsed = 0;
			return;
		}
		mask.bind(false);
		mask.clear(0f, 0f, 0f, 0f, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		client.getFramebuffer().beginWrite(false);
		if (buffers == null) {
			buffers = VertexConsumerProvider.immediate(new BufferAllocator(1 << 18));
		}
		entityPass = true;
	}

	/** After the world's entities: held things into the mask, everything drawn, and the aura painted. */
	private static void draw(WorldRenderContext context) {
		boolean wasEntityPass = entityPass;
		entityPass = false;
		PostProcessingManager posts = VeilRenderSystem.renderer().getPostProcessingManager();
		MinecraftClient client = MinecraftClient.getInstance();
		if (!wasEntityPass || slotsUsed == 0 || client.world == null) {
			if (pipelineOn) {
				posts.remove(PIPELINE);
				pipelineOn = false;
			}
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		boolean shadows = client.options.getEntityShadows().getValue();
		dispatcher.setRenderShadows(false);
		maskPass = true;
		for (Map.Entry<Integer, Aura> entry : AURAS.entrySet()) {
			Aura aura = entry.getValue();
			Entity entity = aura.entity;
			// A horn holding its spell, gathered and seen: its wisps circle and its glints jump.
			if (aura.horn && aura.tipSeen && !aura.released && aura.strength >= 1f) {
				MagicSparks.hold(entry.getKey(), aura.base, aura.tip, aura.color);
			}
			Vec3d lerped = entity == null ? null : new Vec3d(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
					MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()), MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()));
			Vec3d at = null;
			if (aura.slot != 0 && entity != null) {
				if (aura.horn) {
					at = aura.tipSeen ? aura.tip : null;
				} else if (aura.placed == null) {
					// A block display stands at its block's corner; anything else, its box's middle.
					boolean block = entity instanceof DisplayEntity.BlockDisplayEntity;
					at = block ? lerped.add(0.5, 0.5, 0.5) : lerped.add(entity.getBoundingBox().getCenter().subtract(entity.getPos()));
					if (!aura.released) {
						Vec3d half = block ? new Vec3d(0.5, 0.5, 0.5)
								: new Vec3d(Math.max(entity.getWidth() / 2, 0.15), Math.max(entity.getHeight() / 2, 0.15), Math.max(entity.getWidth() / 2, 0.15));
						MagicSparks.shine(entry.getKey(), at, half, aura.color);
					}
				}
			}
			move(entry.getKey(), aura, at);
			if (aura.slot != 0) {
				stretch(aura, at, camera, context);
			}
			if (aura.horn) {
				crescent(aura, lerped, camera);
			}
			if (aura.horn || aura.slot == 0 || entity == null) {
				continue;
			}
			MaskConsumers consumers = new MaskConsumers(buffers, aura.slot);
			if (aura.placed != null) {
				matrices.push();
				matrices.translate(aura.placed.getX() - camera.x, aura.placed.getY() - camera.y, aura.placed.getZ() - camera.z);
				client.getBlockRenderManager().renderBlockAsEntity(aura.placedState, matrices, consumers,
						WorldRenderer.getLightmapCoordinates(client.world, aura.placed), OverlayTexture.DEFAULT_UV);
				matrices.pop();
				continue;
			}
			dispatcher.render(entity, lerped.x - camera.x, lerped.y - camera.y, lerped.z - camera.z, entity.getYaw(tickDelta), tickDelta, matrices, consumers, dispatcher.getLight(entity, tickDelta));
		}
		moveDrops(camera, context);
		maskPass = false;
		buffers.draw();
		dispatcher.setRenderShadows(shadows);

		if (!pipelineOn) {
			pipelineOn = posts.add(PIPELINE);
		}
		PostPipeline pipeline = posts.getPipeline(PIPELINE);
		if (pipeline != null) {
			float pixelsPerBlock = client.getFramebuffer().textureHeight * context.projectionMatrix().m11() / 2f;
			pipeline.getUniformSafe("AuraTime").setFloat((client.world.getTime() + tickDelta) / 20f);
			pipeline.getUniformSafe("PixelsPerBlock").setFloat(pixelsPerBlock);
			pipeline.getUniformSafe("NearPlane").setFloat(NEAR_PLANE);
			pipeline.getUniformSafe("FarPlane").setFloat(client.gameRenderer.getFarPlaneDistance());
			pipeline.getUniformSafe("RimWidth").setFloat(RIM_WIDTH);
			pipeline.getUniformSafe("HazeWidth").setFloat(HAZE_WIDTH);
			pipeline.getUniformSafe("WaveSpeed").setFloat(WAVE_SPEED);
			for (int i = 0; i < SLOTS; i++) {
				int color = i < slotsUsed ? SLOT_COLORS[i] : 0;
				pipeline.getUniformSafe("AuraColor" + i).setVector(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
				pipeline.getUniformSafe("AuraFade" + i).setFloat(i < slotsUsed ? SLOT_FADES[i] : 0f);
				pipeline.getUniformSafe("AuraWidth" + i).setFloat(i < slotsUsed ? SLOT_WIDTHS[i] : 1f);
				pipeline.getUniformSafe("AuraStretch" + i).setVector(i < slotsUsed ? SLOT_STRETCH_X[i] : 0f, i < slotsUsed ? SLOT_STRETCH_Y[i] : 0f);
			}
			for (int i = 0; i < DROPS_SHOWN; i++) {
				boolean shown = i < dropsShown;
				pipeline.getUniformSafe("Drop" + i).setVector(shown ? DROP_X[i] : 0f, shown ? DROP_Y[i] : 0f, shown ? DROP_RADIUS[i] : 0f, shown ? DROP_DEPTH[i] : 0f);
				pipeline.getUniformSafe("DropSlot" + i).setFloat(shown ? DROP_SLOT[i] : -1f);
			}
		}
	}

	/**
	 * Records the horn tip's path and, during a fast swing, draws it into the mask as a ribbon in the horn's
	 * slot: solid like a droplet, with the horn's rim and haze round it.
	 */
	private static void crescent(Aura aura, Vec3d body, Vec3d camera) {
		if (!aura.tipSeen || body == null || aura.released) {
			aura.swing.clear();
			return;
		}
		Vec3d tip = aura.tip.subtract(body);
		aura.swing.addLast(new double[] { tip.x, tip.y, tip.z, clock });
		while (clock - aura.swing.peekFirst()[3] > CRESCENT_SECONDS) {
			aura.swing.pollFirst();
		}
		if (aura.slot == 0 || aura.swing.size() < 3) return;

		double[][] path = aura.swing.toArray(new double[0][]);
		double length = 0;
		for (int i = 1; i < path.length; i++) {
			length += segment(path, i).length();
		}
		if (length < CRESCENT_MIN) return;
		// Fades in over a second CRESCENT_MIN of path, so a borderline flick doesn't pop.
		float strength = (float) Math.min(1.0, (length - CRESCENT_MIN) / CRESCENT_MIN);

		// The path, oldest first, after the hook's points; narrowing with age, but only so far - the hook ends it.
		Vector3f origin = new Vector3f((float) (body.x - camera.x), (float) (body.y - camera.y), (float) (body.z - camera.z));
		Vector3f[] points = new Vector3f[HOOK_STEPS + path.length];
		float[] widths = new float[points.length];
		for (int i = 0; i < path.length; i++) {
			points[HOOK_STEPS + i] = new Vector3f((float) path[i][0], (float) path[i][1], (float) path[i][2]).add(origin);
			widths[HOOK_STEPS + i] = CRESCENT_WIDTH * strength * (float) Math.sqrt(1 - 0.6 * (clock - path[i][3]) / CRESCENT_SECONDS);
		}
		// The hook curls on round the way the swing was turning (or across the view, if it ran straight).
		Vector3f turning = new Vector3f();
		for (int i = 2; i < path.length; i++) {
			turning.add(segment(path, i - 1).cross(segment(path, i)));
		}
		if (turning.lengthSquared() < 1.0e-12f) {
			turning.set(points[HOOK_STEPS]).negate();
		}
		turning.normalize();
		Vector3f heading = new Vector3f();
		for (int i = 1; i < path.length && heading.lengthSquared() < 1.0e-8f; i++) {
			heading.set(points[HOOK_STEPS]).sub(points[HOOK_STEPS + i]);
		}
		heading.normalize();
		float step = (float) length * HOOK_SHARE / HOOK_STEPS;
		for (int i = HOOK_STEPS - 1; i >= 0; i--) {
			heading.rotateAxis(-HOOK_TURN / HOOK_STEPS, turning.x, turning.y, turning.z);
			points[i] = new Vector3f(heading).mul(step).add(points[i + 1]);
			widths[i] = widths[HOOK_STEPS] * i / HOOK_STEPS;
		}
		ribbon(new MaskConsumers(buffers, aura.slot).body(), points, widths);
	}

	private static Vector3f segment(double[][] path, int i) {
		return new Vector3f((float) (path[i][0] - path[i - 1][0]), (float) (path[i][1] - path[i - 1][1]), (float) (path[i][2] - path[i - 1][2]));
	}

	/** A camera-facing ribbon through points relative to the camera, each with its half-width. */
	private static void ribbon(VertexConsumer out, Vector3f[] points, float[] widths) {
		for (int i = 1; i < points.length; i++) {
			Vector3f a = points[i - 1];
			Vector3f b = points[i];
			Vector3f side = new Vector3f(b).sub(a).cross(new Vector3f(a).add(b).mul(-0.5f));
			if (side.lengthSquared() < 1.0e-10f) continue;
			side.normalize();
			quad(out, new Vector3f(a).sub(new Vector3f(side).mul(widths[i - 1])), new Vector3f(a).add(new Vector3f(side).mul(widths[i - 1])),
					new Vector3f(b).add(new Vector3f(side).mul(widths[i])), new Vector3f(b).sub(new Vector3f(side).mul(widths[i])));
		}
	}

	private static void quad(VertexConsumer out, Vector3f... corners) {
		for (Vector3f corner : corners) {
			out.vertex(corner.x, corner.y, corner.z).color(255, 255, 255, 255).texture(0f, 0f);
		}
	}

	/**
	 * Moves an aura's trail on: aimed behind its motion this frame ({@code at}, or null if it isn't seen or
	 * doesn't move), and following that aim on a spring, so it lags and overshoots. A trail snapping back
	 * hard - a sudden stop, a sharp swing - flings droplets off ahead.
	 */
	private static void move(int key, Aura aura, Vec3d at) {
		Vector3f aim = new Vector3f();
		if (at != null && aura.lastAt != null && frameSeconds > 0f && at.squaredDistanceTo(aura.lastAt) < JUMP * JUMP) {
			float seconds = aura.horn ? HORN_TRAIL_SECONDS : TRAIL_SECONDS;
			Vec3d moved = aura.lastAt.subtract(at).multiply(seconds / frameSeconds);
			aim.set((float) moved.x, (float) moved.y, (float) moved.z);
			float max = aura.horn ? HORN_TRAIL_MAX : TRAIL_MAX;
			if (aim.length() > max) {
				aim.normalize(max);
			}
		} else if (at != null && aura.lastAt != null && at.squaredDistanceTo(aura.lastAt) >= JUMP * JUMP) {
			// It jumped: no trail across the gap.
			aura.trail.zero();
			aura.trailVelocity.zero();
		}
		aura.lastAt = at;
		float dt = frameSeconds;
		Vector3f pull = new Vector3f(aim).sub(aura.trail).mul(TRAIL_STIFFNESS).sub(new Vector3f(aura.trailVelocity).mul(TRAIL_DAMPING));
		aura.trailVelocity.add(pull.mul(dt));
		aura.trail.add(new Vector3f(aura.trailVelocity).mul(dt));

		float snap = new Vector3f(aura.trail).sub(aim).length() - (aura.horn ? DROPLET_THRESHOLD / 2f : DROPLET_THRESHOLD);
		if (at == null || snap <= 0f || aura.released) {
			aura.dropletsDue = 0f;
			return;
		}
		aura.dropletsDue = Math.min(aura.dropletsDue + snap * DROPLET_RATE * dt, 1f);
		aura.dropGap -= dt;
		if (aura.dropletsDue >= 1f && aura.dropGap <= 0f && dropsOff(key) < DROPS_PER_AURA) {
			aura.dropletsDue = 0f;
			aura.dropGap = DROP_GAP;
			fling(key, aura, at, new Vector3f(aura.trail).sub(aim).negate());
		}
	}

	private static int dropsOff(int key) {
		int count = 0;
		for (Drop drop : DROPS) {
			if (drop.key == key) {
				count++;
			}
		}
		return count;
	}

	/**
	 * A droplet breaks off a held thing's (or a horn's) aura, {@code ahead} being where its glow is swinging
	 * to: it starts at the surface, still joined to the glow, and is thrown gently on, with a little sparkle
	 * dust.
	 */
	private static void fling(int key, Aura aura, Vec3d at, Vector3f ahead) {
		boolean horn = aura.horn;
		float length = ahead.length();
		if (length < 1.0e-4f || DROPS.size() >= DROPS_MAX) {
			return;
		}
		float scale = horn ? 0.45f : 1f;
		Vec3d direction = new Vec3d(ahead.x / length + RANDOM.nextGaussian() * 0.3, ahead.y / length + RANDOM.nextGaussian() * 0.3,
				ahead.z / length + RANDOM.nextGaussian() * 0.3).normalize();
		Drop drop = new Drop();
		drop.key = key;
		drop.pos = at.add(direction.multiply(horn ? 0.02 : 0.5));
		drop.velocity = direction.multiply((0.35 + RANDOM.nextDouble() * 0.3) * scale);
		drop.life = 0.7f + RANDOM.nextFloat() * 0.3f;
		drop.radius = (0.09f + RANDOM.nextFloat() * 0.05f) * scale;
		drop.phase = RANDOM.nextFloat() * MathHelper.TAU;
		DROPS.add(drop);
		MagicSparks.dust(drop.pos, 2, aura.color);
	}

	/**
	 * Moves the droplets on and works out where each is on screen this frame - its middle and radius in
	 * pixels and its distance from the camera - for the composite to draw: growing out of the surface,
	 * swelling and settling slowly, and shrinking away at the end. Nearest the camera first, if there are
	 * more than it can take.
	 */
	private static void moveDrops(Vec3d camera, WorldRenderContext context) {
		dropsShown = 0;
		if (DROPS.isEmpty()) {
			return;
		}
		if (DROPS.size() > DROPS_SHOWN) {
			DROPS.sort((a, b) -> Double.compare(a.pos.squaredDistanceTo(camera), b.pos.squaredDistanceTo(camera)));
		}
		Matrix4f viewProjection = new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());
		int width = MinecraftClient.getInstance().getFramebuffer().textureWidth;
		int height = MinecraftClient.getInstance().getFramebuffer().textureHeight;
		float pixelsPerBlock = height * context.projectionMatrix().m11() / 2f;
		Iterator<Drop> drops = DROPS.iterator();
		while (drops.hasNext()) {
			Drop drop = drops.next();
			Aura aura = AURAS.get(drop.key);
			drop.age += frameSeconds * (aura != null && aura.released ? DROP_HURRY : 1f);
			if (aura == null || drop.age >= drop.life) {
				drops.remove();
				continue;
			}
			drop.velocity = drop.velocity.multiply(Math.exp(-DROP_DRAG * frameSeconds)).add(0, DROP_RISE * frameSeconds, 0);
			drop.pos = drop.pos.add(drop.velocity.multiply(frameSeconds));
			if (aura.slot == 0 || dropsShown >= DROPS_SHOWN) {
				continue;
			}
			float t = drop.age / drop.life;
			// Blown out of the surface like a bubble, then shrinking away over the last part of its life.
			float grow = MathHelper.clamp(t / DROP_GROW, 0f, 1f);
			grow = grow * grow * (3f - 2f * grow);
			float fade = MathHelper.clamp((t - 0.5f) / 0.5f, 0f, 1f);
			fade = fade * fade * (3f - 2f * fade);
			// Slowly swelling and settling, as a lava lamp's blobs do.
			float radius = drop.radius * grow * (1f - fade) * (1f + 0.08f * MathHelper.sin(drop.age * 3.5f + drop.phase));

			Vector4f clip = viewProjection.transform(new Vector4f((float) (drop.pos.x - camera.x), (float) (drop.pos.y - camera.y),
					(float) (drop.pos.z - camera.z), 1f));
			if (clip.w <= NEAR_PLANE || radius <= 0f) {
				continue;
			}
			int i = dropsShown++;
			DROP_X[i] = (clip.x / clip.w * 0.5f + 0.5f) * width;
			DROP_Y[i] = (clip.y / clip.w * 0.5f + 0.5f) * height;
			DROP_RADIUS[i] = radius * pixelsPerBlock / clip.w;
			DROP_DEPTH[i] = clip.w;
			DROP_SLOT[i] = aura.slot - 1;
		}
	}

	/**
	 * This frame's stretch for an aura's slot: its trail as it looks on screen, in rim widths (how far the
	 * shader draws its glow out, and which way), plus a horn's flame rising straight up.
	 */
	private static void stretch(Aura aura, Vec3d at, Vec3d camera, WorldRenderContext context) {
		int slot = aura.slot - 1;
		float x = 0f;
		float y = aura.horn ? HORN_RISE : 0f;
		if (at != null && aura.trail.lengthSquared() > 1.0e-6f) {
			Matrix4f viewProjection = new Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());
			Vector4f from = viewProjection.transform(new Vector4f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), 1f));
			Vector4f to = viewProjection.transform(new Vector4f((float) (at.x - camera.x) + aura.trail.x, (float) (at.y - camera.y) + aura.trail.y,
					(float) (at.z - camera.z) + aura.trail.z, 1f));
			if (from.w > NEAR_PLANE && to.w > NEAR_PLANE) {
				int width = MinecraftClient.getInstance().getFramebuffer().textureWidth;
				int height = MinecraftClient.getInstance().getFramebuffer().textureHeight;
				float pixelsX = (to.x / to.w - from.x / from.w) * 0.5f * width;
				float pixelsY = (to.y / to.w - from.y / from.w) * 0.5f * height;
				// The rim's width in pixels at that distance, as the shader works it out.
				float pixelsPerBlock = height * context.projectionMatrix().m11() / 2f;
				float rimPixels = RIM_WIDTH * SLOT_WIDTHS[slot] * pixelsPerBlock / from.w;
				x += pixelsX / rimPixels;
				y += pixelsY / rimPixels;
			}
		}
		float length = (float) Math.sqrt(x * x + y * y);
		if (length > STRETCH_MAX) {
			x *= STRETCH_MAX / length;
			y *= STRETCH_MAX / length;
		}
		SLOT_STRETCH_X[slot] = x;
		SLOT_STRETCH_Y[slot] = y;
	}

	private static int mix(int from, int to, float amount) {
		int r = Math.round(MathHelper.lerp(amount, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(MathHelper.lerp(amount, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(MathHelper.lerp(amount, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}

	/** Letting a spell go (docs/magic-aura.md, releasing): the flash and shards off the horn's tip (see {@link MagicSparks#release}). */
	private static void burst(Aura aura) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity entity = client.world == null ? null : client.world.getEntityById(aura.casterId);
		if (entity == null) {
			return;
		}
		// Where the horn was last frame; if it wasn't drawn (in first person, say), just above the eyes.
		MagicSparks.release(aura.tipSeen ? aura.tip : entity.getEyePos().add(0, 0.3, 0), aura.color);
	}

	/**
	 * Hands every quad the entity's renderer draws to the mask instead, in the aura's slot and through the
	 * original texture (so see-through pixels stay holes). Shadows, glints, text and lines aren't part of
	 * the thing's outline and are dropped.
	 */
	private record MaskConsumers(VertexConsumerProvider.Immediate buffers, int slot) implements VertexConsumerProvider {
		/** For shapes drawn whole (a horn, its flame): solid all over, both sides. */
		VertexConsumer solid() {
			return new Tinted(buffers.getBuffer(MASK_LAYERS.apply(VeilRenderType.WHITE_TEXTURE, VeilRenderType.noCullShard())), slot, 0);
		}

		/** Like {@link #solid}, but marked (green) for the composite to paint opaque in the aura's color, as a droplet is. */
		VertexConsumer body() {
			return new Tinted(buffers.getBuffer(MASK_LAYERS.apply(VeilRenderType.WHITE_TEXTURE, VeilRenderType.noCullShard())), slot, 255);
		}

		@Override
		public VertexConsumer getBuffer(RenderLayer layer) {
			// The layer's own name only - its toString() also lists its phases, and "texture" contains "text".
			String name = VeilRenderType.getName(layer);
			if (layer.getDrawMode() != VertexFormat.DrawMode.QUADS || name.contains("shadow") || name.contains("glint")
					|| name.contains("text") || name.contains("lines") || name.contains("leash")) {
				return Discard.INSTANCE;
			}
			VeilRenderTypeAccessor shards = VeilRenderType.getShards(layer);
			if (shards == null || shards.textureState() == null || shards.cullState() == null) {
				return Discard.INSTANCE;
			}
			return new Tinted(buffers.getBuffer(MASK_LAYERS.apply(shards.textureState(), shards.cullState())), slot, 0);
		}
	}

	/** Passes vertices through with the aura's slot (in red) in place of the model's own color. */
	private record Tinted(VertexConsumer inner, int slot, int body) implements VertexConsumer {
		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			inner.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			inner.color(slot, body, 0, 255);
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			inner.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}
	}

	private enum Discard implements VertexConsumer {
		INSTANCE;

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}
	}
}
