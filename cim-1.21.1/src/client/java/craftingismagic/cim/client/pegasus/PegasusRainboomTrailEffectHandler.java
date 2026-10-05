package craftingismagic.cim.client.pegasus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.PegasusRainboomTrailS2CPayload;

/**
 * Spawns the rainbow ribbon + white sparkle trail behind a player right
 * after their Sonic Rainboom fires. The ribbon is a single long-lived
 * particle whose own position is locked to the entity every tick via
 * {@link EntityFollowCodeModule} - Quasar's "trail" module records a new
 * connected-ribbon point from wherever that particle currently is, so
 * this is what keeps the ribbon growing continuously behind the player
 * rather than fading with distance. The sparkle emitter instead spawns
 * many short-lived puffs and just has its own spawn point re-positioned
 * to the entity every tick (same approach as the charge cone).
 */
public final class PegasusRainboomTrailEffectHandler {
	private static final Identifier RIBBON_EMITTER_ID = Identifier.of(Cim.MOD_ID, "rainboom_trail_ribbon");
	private static final Identifier SPARKLE_EMITTER_ID = Identifier.of(Cim.MOD_ID, "rainboom_trail_sparkle");

	/** Individual particles live a bit past the emitter's own spawn window, plus margin for error. */
	private static final int REMOVAL_SAFETY_BUFFER_TICKS = 40;

	private record Tracked(int entityId, List<ParticleEmitter> emitters, long removeAtTick) {
	}

	private static final List<Tracked> active = new ArrayList<>();

	private PegasusRainboomTrailEffectHandler() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PegasusRainboomTrailS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> start(payload)));

		ClientTickEvents.END_CLIENT_TICK.register(PegasusRainboomTrailEffectHandler::tick);
	}

	/** On leaving a world - entries hold an entity id and a removal tick from the old world's clock, neither meaningful in the next one. */
	public static void reset() {
		active.clear();
	}

	private static void start(PegasusRainboomTrailS2CPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity entity = client.world != null ? client.world.getEntityById(payload.entityId()) : null;
		if (entity == null) {
			return;
		}

		try {
			ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();
			List<ParticleEmitter> emitters = new ArrayList<>();
			for (Identifier emitterId : List.of(RIBBON_EMITTER_ID, SPARKLE_EMITTER_ID)) {
				ParticleEmitter emitter = manager.createEmitter(emitterId);
				if (emitter == null) {
					Cim.LOGGER.warn("Rainboom trail emitter '{}' could not be created", emitterId);
					continue;
				}

				emitter.setPosition(entity.getPos());
				if (emitterId.equals(RIBBON_EMITTER_ID)) {
					// Must be added before the emitter spawns its particle (addParticleSystem
					// below triggers the initial spawn), so the ribbon particle is built with
					// these modules already in its set.
					emitter.addCodeModule(new EntityFollowCodeModule(entity));
					emitter.addCodeModule(new RibbonEffectsCodeModule());
				}
				manager.addParticleSystem(emitter);
				emitters.add(emitter);
			}

			if (!emitters.isEmpty()) {
				long removeAtTick = client.world.getTime() + payload.durationTicks() + REMOVAL_SAFETY_BUFFER_TICKS;
				active.add(new Tracked(payload.entityId(), emitters, removeAtTick));
			}

			client.getSoundManager().play(new RainboomTrailShimmerSound(entity, payload.durationTicks()));
		} catch (Exception e) {
			Cim.LOGGER.warn("Failed to spawn Rainboom trail effect", e);
		}
	}

	private static void tick(MinecraftClient client) {
		if (active.isEmpty() || client.world == null) {
			return;
		}

		long now = client.world.getTime();
		Iterator<Tracked> iterator = active.iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next();

			if (now >= tracked.removeAtTick()) {
				tracked.emitters().forEach(ParticleEmitter::remove);
				iterator.remove();
				continue;
			}

			tracked.emitters().removeIf(ParticleEmitter::isRemoved);
			if (tracked.emitters().isEmpty()) {
				iterator.remove();
				continue;
			}

			Entity entity = client.world.getEntityById(tracked.entityId());
			if (entity == null) {
				continue;
			}

			for (ParticleEmitter emitter : tracked.emitters()) {
				emitter.setPosition(entity.getPos());
			}
		}
	}
}
