package craftingismagic.cim.client.pegasus;

import foundry.veil.api.quasar.data.module.CodeModule;
import foundry.veil.api.quasar.emitters.module.UpdateParticleModule;
import foundry.veil.api.quasar.particle.ParticleModuleSet;

import net.minecraft.entity.Entity;

/**
 * Locks a particle's own position to a followed entity's real position
 * every tick. Needed for the Sonic Rainboom trail ribbon specifically:
 * {@link foundry.veil.api.quasar.emitters.module.render.TrailParticleModule}
 * records a new trail point from wherever the carrier particle currently
 * is each tick, and re-positioning the emitter alone (as used elsewhere,
 * e.g. the charge cone) only affects where new particles spawn, not an
 * already-live particle's own position - so the trail needs the particle
 * itself actively following, not just the emitter.
 */
public final class EntityFollowCodeModule implements CodeModule {
	private final Entity target;
	private final double offsetX;
	private final double offsetY;
	private final double offsetZ;

	public EntityFollowCodeModule(Entity target) {
		this(target, 0.0, 0.0, 0.0);
	}

	/**
	 * @param offsetX/Y/Z added to the target's own position every tick - e.g. (0.5, 0.5, 0.5) when
	 * following a {@link net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity}, whose
	 * own position is its block model's min-corner rather than its geometric center.
	 */
	public EntityFollowCodeModule(Entity target, double offsetX, double offsetY, double offsetZ) {
		this.target = target;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
	}

	@Override
	public void addModules(ParticleModuleSet.Builder builder) {
		builder.addModule((UpdateParticleModule) particle -> {
			if (!target.isRemoved()) {
				particle.getPosition().set(target.getX() + offsetX, target.getY() + offsetY, target.getZ() + offsetZ);
			}
		});
	}
}
