package craftingismagic.cim.client.pegasus;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.Cim;
import craftingismagic.cim.network.SonicRainboomS2CPayload;

/** Spawns the Veil/Quasar Sonic Rainboom particle effect (rainbow ring + fading light) wherever the server says a Pegasus broke the sound barrier. */
public final class SonicRainboomEffectHandler {
	private static final Identifier EMITTER_ID = Identifier.of(Cim.MOD_ID, "sonic_rainboom");

	private SonicRainboomEffectHandler() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SonicRainboomS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> spawn(payload)));
	}

	private static void spawn(SonicRainboomS2CPayload payload) {
		try {
			ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();
			ParticleEmitter emitter = manager.createEmitter(EMITTER_ID);
			if (emitter == null) {
				Cim.LOGGER.warn("Sonic Rainboom emitter '{}' could not be created (createEmitter returned null)", EMITTER_ID);
				return;
			}

			emitter.setPosition(new Vec3d(payload.x(), payload.y(), payload.z()));
			manager.addParticleSystem(emitter);
			Cim.LOGGER.info("Sonic Rainboom emitter spawned at {} {} {}", payload.x(), payload.y(), payload.z());
		} catch (Exception e) {
			Cim.LOGGER.warn("Failed to spawn Sonic Rainboom particle effect", e);
		}
	}
}
