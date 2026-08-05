package craftingismagic.cim.client.levitation;

import java.util.Map;
import java.util.WeakHashMap;

import foundry.veil.api.quasar.data.module.CodeModule;
import foundry.veil.api.quasar.emitters.module.InitParticleModule;
import foundry.veil.api.quasar.emitters.module.UpdateParticleModule;
import foundry.veil.api.quasar.particle.ParticleModuleSet;
import foundry.veil.api.quasar.particle.QuasarParticle;

/**
 * Locks a particle's own position and radius to a shared, externally-updated
 * {@link GroupAuraState} every tick - unlike {@link
 * craftingismagic.cim.client.pegasus.EntityFollowCodeModule}, which follows
 * a single entity's own position, this follows a computed group centroid so
 * one aura particle can cover an entire held group of blocks (sized to
 * their combined bounding box) instead of each block getting its own
 * separate aura.
 *
 * <p>Applies the state both on init and on every update, not just update:
 * {@code RenderData}'s interpolation baseline ({@code prevRadius}/{@code
 * prevPosition}) gets captured from the particle's own fields the moment
 * its first tick runs, which happens before any {@code UpdateParticleModule}
 * gets a chance to run that same tick. Since this aura's emitter gets
 * destroyed and recreated periodically (see {@code
 * LevitationAuraEffectHandler}'s refresh cycle), an update-only version
 * would visibly "grow in" every single refresh - from the particle
 * settings' static {@code base_particle_size} up to the true group size -
 * since that mismatched baseline is what the interpolation would render
 * from for that first tick. Setting it correctly on init instead means
 * there's no wrong baseline to grow out of in the first place.
 *
 * <p>Smuggles {@link GroupAuraState#shapeFill} and {@link
 * GroupAuraState#hullCount} through the particle's own color channel (red
 * and alpha respectively) - there's no dedicated per-particle data slot on
 * Quasar particles for this, and this avoids extending the vertex format
 * just for two scalars. The 16 hull-point floats ({@link
 * GroupAuraState#hullX}/{@link GroupAuraState#hullY}), though, are too much
 * data to fit through color/rotation channels at all - {@link
 * #STATE_BY_PARTICLE} instead lets {@link LevitationAuraRenderStyle}, which
 * only ever receives the bare {@code QuasarParticle} instance from Veil (not
 * this class or its state), look the owning {@link GroupAuraState} back up
 * by particle identity and read the hull arrays directly. It's a {@link
 * WeakHashMap} so entries for removed/GC'd particles clean up on their own.
 */
public final class GroupAuraCodeModule implements CodeModule {
	static final Map<QuasarParticle, GroupAuraState> STATE_BY_PARTICLE = new WeakHashMap<>();

	private final GroupAuraState state;

	public GroupAuraCodeModule(GroupAuraState state) {
		this.state = state;
	}

	@Override
	public void addModules(ParticleModuleSet.Builder builder) {
		builder.addModule((InitParticleModule) this::apply);
		builder.addModule((UpdateParticleModule) this::apply);
	}

	private void apply(QuasarParticle particle) {
		particle.getPosition().set(state.center.x, state.center.y, state.center.z);
		particle.setRadius(state.radius);
		particle.getRenderData().setColor(state.shapeFill, 1.0F, 1.0F, (float) state.hullCount);
		STATE_BY_PARTICLE.put(particle, state);
	}
}
