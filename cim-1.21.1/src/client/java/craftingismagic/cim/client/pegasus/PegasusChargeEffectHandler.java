package craftingismagic.cim.client.pegasus;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.PegasusChargeEndS2CPayload;
import craftingismagic.cim.network.PegasusChargeStartS2CPayload;

/**
 * Spawns/removes the Sonic Rainboom charge-up cone effect on whichever
 * entity the server says is charging. {@code setAttachedEntity} alone
 * doesn't keep a spawned particle following its entity tick to tick - it
 * only affects where new particles spawn from - so this re-positions the
 * emitter to the entity's current position every client tick itself.
 */
public final class PegasusChargeEffectHandler {
	private static final Identifier EMITTER_ID = Identifier.of(Cim.MOD_ID, "rainboom_charge");

	private static final Map<Integer, ParticleEmitter> activeEmitters = new HashMap<>();

	private PegasusChargeEffectHandler() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PegasusChargeStartS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> start(payload)));
		ClientPlayNetworking.registerGlobalReceiver(PegasusChargeEndS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> end(payload.entityId())));

		ClientTickEvents.END_CLIENT_TICK.register(PegasusChargeEffectHandler::tick);
	}

	/** On leaving a world - keyed by entity id, which the next world reuses; Veil drops the emitters themselves along with the old world. */
	public static void reset() {
		activeEmitters.clear();
	}

	private static void start(PegasusChargeStartS2CPayload payload) {
		end(payload.entityId());
		PegasusChargeVisualState.start(payload.entityId(), payload.durationTicks());

		try {
			ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();
			ParticleEmitter emitter = manager.createEmitter(EMITTER_ID);
			if (emitter == null) {
				Cim.LOGGER.warn("Rainboom charge emitter '{}' could not be created", EMITTER_ID);
				return;
			}

			Entity entity = MinecraftClient.getInstance().world.getEntityById(payload.entityId());
			if (entity == null) {
				return;
			}

			emitter.setAttachedEntity(entity);
			emitter.setPosition(entity.getPos());
			manager.addParticleSystem(emitter);
			activeEmitters.put(payload.entityId(), emitter);
		} catch (Exception e) {
			Cim.LOGGER.warn("Failed to spawn Rainboom charge effect", e);
		}
	}

	private static void end(int entityId) {
		PegasusChargeVisualState.end(entityId);
		ParticleEmitter emitter = activeEmitters.remove(entityId);
		if (emitter != null) {
			emitter.remove();
		}
	}

	private static void tick(MinecraftClient client) {
		if (activeEmitters.isEmpty() || client.world == null) {
			return;
		}

		Iterator<Map.Entry<Integer, ParticleEmitter>> iterator = activeEmitters.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, ParticleEmitter> entry = iterator.next();
			ParticleEmitter emitter = entry.getValue();
			if (emitter.isRemoved()) {
				iterator.remove();
				continue;
			}

			Entity entity = client.world.getEntityById(entry.getKey());
			if (entity == null) {
				continue;
			}

			Vec3d pos = entity.getPos().add(entity.getRotationVec(1.0F).multiply(0.5));
			emitter.setPosition(pos);
		}
	}
}
