package craftingismagic.cim.client.magic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.joml.Matrix4f;
import org.joml.Vector3f;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.HornGlowS2CPayload;

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
		/** Where the horn's tip was last seen (for its flame and the release burst), or null. */
		Vec3d tip;
		/** Whether the horn was drawn this frame - its flame only shows then. */
		boolean tipSeen;
		/** This frame's slot (1-8), or 0 if it isn't drawn this frame. */
		int slot;
	}

	/** Held entity id (or a horn's key - see {@link #setHorn}) -> its aura. */
	private static final Map<Integer, Aura> AURAS = new HashMap<>();
	/** How many auras can be drawn at once (the shader's uniform slots). */
	private static final int SLOTS = 8;
	private static final int[] SLOT_COLORS = new int[SLOTS];
	private static final float[] SLOT_FADES = new float[SLOTS];
	/** How wide each slot's rim and haze are, against the usual width. */
	private static final float[] SLOT_WIDTHS = new float[SLOTS];
	/** How much each slot's glow swells upward (a horn's flame; 0 for a held thing). */
	private static final float[] SLOT_RISES = new float[SLOTS];
	private static int slotsUsed;
	private static long lastFrameNanos;

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
		ClientPlayNetworking.registerGlobalReceiver(HornGlowS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> setHorn(payload.casterId(), payload.active(), payload.color())));
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
	}

	/** Which entity is rendering (null when it's done) - the horn hook needs to know whose horn it is. */
	public static void setRendering(Entity entity) {
		rendering = entity;
	}

	/**
	 * A horn is being rendered (MineLittlePony's, or a Figura avatar's labelled horn part): if its owner is
	 * channelling magic, draw the part itself into the mask in their aura's slot - so the glow takes the
	 * horn's own shape, and its flame rises off that shape (the shader's upward swell). {@code tipLocal} is
	 * the tip in the part's own space (after its transform), in blocks - where a release's sparks fly from.
	 *
	 * @return whether this horn is lit (so the caller can hide its own glow)
	 */
	public static boolean onHornRendered(ModelPart part, MatrixStack matrices, int light, int overlay, Vector3f tipLocal) {
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
		Vector3f tip = matrices.peek().getPositionMatrix().transformPosition(new Vector3f(tipLocal));
		matrices.pop();
		aura.tip = new Vec3d(tip.x, tip.y, tip.z).add(MinecraftClient.getInstance().gameRenderer.getCamera().getPos());
		aura.tipSeen = true;
		return true;
	}

	/** Advances every aura's fade, and keeps hold of each thing's entity while it's still in the world. */
	private static void updateFades(MinecraftClient client) {
		long now = System.nanoTime();
		float seconds = lastFrameNanos == 0 ? 0f : MathHelper.clamp((now - lastFrameNanos) / 1.0e9f, 0f, 0.1f);
		lastFrameNanos = now;
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
			SLOT_RISES[slot] = aura.horn ? HORN_RISE : 0f;
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
		for (Aura aura : AURAS.values()) {
			Entity entity = aura.entity;
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
			double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camera.x;
			double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camera.y;
			double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camera.z;
			dispatcher.render(entity, x, y, z, entity.getYaw(tickDelta), tickDelta, matrices, consumers, dispatcher.getLight(entity, tickDelta));
		}
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
				pipeline.getUniformSafe("AuraRise" + i).setFloat(i < slotsUsed ? SLOT_RISES[i] : 0f);
			}
		}
	}

	private static int mix(int from, int to, float amount) {
		int r = Math.round(MathHelper.lerp(amount, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(MathHelper.lerp(amount, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(MathHelper.lerp(amount, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}

	/**
	 * Letting a spell go (docs/magic-aura.md, releasing): sparks flung upward and outward off the horn's
	 * tip, white and in the caster's color, rising and fading.
	 */
	private static void burst(Aura aura) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity entity = client.world == null ? null : client.world.getEntityById(aura.casterId);
		if (entity == null) {
			return;
		}
		Vec3d at = aura.tip != null ? aura.tip : entity.getEyePos().add(0, 0.3, 0);
		Random random = client.world.getRandom();
		Vector3f color = new Vector3f(((aura.color >> 16) & 0xFF) / 255f, ((aura.color >> 8) & 0xFF) / 255f, (aura.color & 0xFF) / 255f);
		for (int i = 0; i < 16; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = 0.03 + random.nextDouble() * 0.05;
			double vx = Math.cos(angle) * out, vz = Math.sin(angle) * out, vy = 0.06 + random.nextDouble() * 0.08;
			if (i % 2 == 0) {
				client.world.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, vx, vy, vz);
			} else {
				client.world.addParticle(new DustParticleEffect(color, 1.0f), at.x, at.y, at.z, vx * 10, vy * 10, vz * 10);
			}
		}
	}

	/**
	 * Hands every quad the entity's renderer draws to the mask instead, in the aura's slot and through the
	 * original texture (so see-through pixels stay holes). Shadows, glints, text and lines aren't part of
	 * the thing's outline and are dropped.
	 */
	private record MaskConsumers(VertexConsumerProvider.Immediate buffers, int slot) implements VertexConsumerProvider {
		/** For shapes drawn whole (a horn, its flame): solid all over, both sides. */
		VertexConsumer solid() {
			return new Tinted(buffers.getBuffer(MASK_LAYERS.apply(VeilRenderType.WHITE_TEXTURE, VeilRenderType.noCullShard())), slot);
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
			return new Tinted(buffers.getBuffer(MASK_LAYERS.apply(shards.textureState(), shards.cullState())), slot);
		}
	}

	/** Passes vertices through with the aura's slot (in red) in place of the model's own color. */
	private record Tinted(VertexConsumer inner, int slot) implements VertexConsumer {
		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			inner.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			inner.color(slot, 0, 0, 255);
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
