package craftingismagic.cim.client.verse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.client.accessibility.VisualComfort;
import craftingismagic.cim.network.VerseLightS2CPayload;
import craftingismagic.cim.verse.VerseRuntime;

/**
 * Verse's Light made real (README: Verse): Veil dynamic lights that actually light the world - one glow
 * for "shine" and for a glowing or twinkling creature, and for a crown, several small lights circling
 * above the head with the sparkles, lighting the face beneath.
 * <p>
 * Positions are updated every frame (interpolated), so a moving creature's light doesn't stutter; every
 * handle is freed when its time is up, when its creature is gone, or when the player leaves the world.
 */
public final class VerseLights {
	/** More than this at once and the oldest make way - lights are cheap, but not free. */
	private static final int MAX_LIGHTS = 64;

	private static final class Glow {
		final VerseLightS2CPayload source;
		final List<LightRenderHandle<PointLightData>> handles = new ArrayList<>();
		int ticksLeft;
		final int fadeIn = 6;
		int age;

		Glow(VerseLightS2CPayload source) {
			this.source = source;
			this.ticksLeft = source.ticks();
		}
	}

	private static final List<Glow> glows = new ArrayList<>();

	private VerseLights() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VerseLightS2CPayload.ID, (payload, context) -> context.client().execute(() -> begin(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(VerseLights::tick);
		WorldRenderEvents.START.register(context -> place(context.tickCounter().getTickDelta(false)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(VerseLights::clear));
	}

	private static void begin(VerseLightS2CPayload payload) {
		if (payload.ticks() <= 0) {
			// A zero-length light: the lights here (or on this creature) ended early - their caster let go.
			for (Iterator<Glow> it = glows.iterator(); it.hasNext();) {
				Glow glow = it.next();
				boolean same = glow.source.entityId() == payload.entityId()
						&& (payload.entityId() >= 0 || glow.source.pos().squaredDistanceTo(payload.pos()) < 0.01);
				if (same) {
					free(glow);
					it.remove();
				}
			}
			return;
		}
		Glow glow = new Glow(payload);
		int lights = payload.crown() ? MathHelper.clamp(payload.count() / 2, 2, 6) : 1;
		for (int i = 0; i < lights; i++) {
			if (count() >= MAX_LIGHTS) {
				free(glows.remove(0));
			}
			PointLightData data = new PointLightData();
			data.setPosition(payload.pos().x, payload.pos().y, payload.pos().z);
			data.setRadius(payload.radius());
			data.setColor(payload.color());
			data.setBrightness(0f); // fades in
			glow.handles.add(VeilRenderSystem.renderer().getLightRenderer().addLight(data));
		}
		glows.add(glow);
	}

	private static int count() {
		return glows.stream().mapToInt(glow -> glow.handles.size()).sum();
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null) {
			clear();
			return;
		}
		for (Iterator<Glow> it = glows.iterator(); it.hasNext();) {
			Glow glow = it.next();
			glow.age++;
			Entity follow = glow.source.entityId() >= 0 ? client.world.getEntityById(glow.source.entityId()) : null;
			if (--glow.ticksLeft <= 0 || glow.source.entityId() >= 0 && (follow == null || !follow.isAlive())) {
				free(glow);
				it.remove();
			}
		}
	}

	/** Every frame: each light where it should be now, brightness fading in and out. */
	private static void place(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || glows.isEmpty()) return;
		for (Glow glow : glows) {
			VerseLightS2CPayload source = glow.source;
			Entity follow = source.entityId() >= 0 ? client.world.getEntityById(source.entityId()) : null;
			Vec3d base = follow != null ? follow.getLerpedPos(tickDelta) : source.pos();
			float fade = Math.min(1f, (glow.age + tickDelta) / glow.fadeIn) * Math.min(1f, Math.max(0f, (glow.ticksLeft - tickDelta) / 10f));
			float brightness = source.brightness() * fade;
			int lights = glow.handles.size();
			double time = (glow.age + tickDelta) * 0.35 / 4.0;
			for (int i = 0; i < lights; i++) {
				LightRenderHandle<PointLightData> handle = glow.handles.get(i);
				if (!handle.isValid()) continue;
				PointLightData data = handle.getLightData();
				// In a crown each light sits on one of the sparkles - and twinkles with it.
				int sparkle = source.crown() ? i * Math.max(1, source.count()) / lights : i;
				if (source.crown() && follow != null) {
					// Circling above the head, in step with the crown's sparkles.
					double angle = time + sparkle * Math.PI * 2 / Math.max(1, source.count());
					double radius = 0.35 + follow.getWidth() * 0.35;
					data.setPosition(base.x + Math.cos(angle) * radius, base.y + follow.getHeight() + 0.25, base.z + Math.sin(angle) * radius);
				} else if (follow != null) {
					data.setPosition(base.x, base.y + follow.getHeight() * 0.6, base.z);
				} else {
					data.setPosition(base.x, base.y, base.z);
				}
				// A twinkling light flashes on its rhythm - gentler as flashes are turned down, and held at a
				// steady glow for a player who's asked for steady effects.
				float flash = 1f;
				if (source.twinkle()) {
					float twinkle = VisualComfort.steady() ? 0.5f : 0.1f + 0.9f * VerseRuntime.twinkle(glow.age + tickDelta, sparkle);
					flash = MathHelper.lerp(VisualComfort.flashIntensity(), 0.5f, twinkle);
				}
				data.setBrightness(brightness * flash);
				handle.markDirty();
			}
		}
	}

	private static void free(Glow glow) {
		for (LightRenderHandle<PointLightData> handle : glow.handles) {
			if (handle.isValid()) handle.free();
		}
		glow.handles.clear();
	}

	private static void clear() {
		glows.forEach(VerseLights::free);
		glows.clear();
	}
}
