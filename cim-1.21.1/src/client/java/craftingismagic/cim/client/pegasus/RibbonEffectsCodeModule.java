package craftingismagic.cim.client.pegasus;

import foundry.veil.api.quasar.data.module.CodeModule;
import foundry.veil.api.quasar.emitters.module.UpdateParticleModule;
import foundry.veil.api.quasar.fx.Trail;
import foundry.veil.api.quasar.particle.ParticleModuleSet;
import foundry.veil.api.quasar.particle.QuasarParticle;

/**
 * Per-tick fixups for the rainboom trail ribbon that Quasar's JSON "trail"
 * module can't express on its own:
 *
 * <ul>
 * <li>Width: the built-in width function always tapers a ribbon down to
 * zero at both ends (a hardcoded sin curve keyed off position along the
 * point list), which pinches the ribbon down to nothing right where it
 * connects to the player. This overrides it with one that keeps the same
 * peak width but floors the width near the newest (index-0,
 * player-connected) end instead of letting it taper to zero.</li>
 * <li>Fade: the ribbon's alpha is a fixed value set once at construction
 * (from the JSON "trailColor") - it is NOT modulated by the particle's own
 * "color" module (that module only affects the tiny, invisible carrier
 * billboard, not {@link Trail}'s own separate render path), so without this
 * the whole ribbon stays fully opaque until the particle is removed and
 * then vanishes instantly. This fades {@link Trail#setColor(int)} down
 * directly as the carrier particle ages so the whole ribbon fades out
 * together instead of popping off.</li>
 * </ul>
 */
public final class RibbonEffectsCodeModule implements CodeModule {
	/** Must match rainboom_trail_ribbon_particle_data.json's "trailWidthModifier". */
	private static final float PEAK_WIDTH_MULTIPLIER = 5.0F;
	/** Minimum width right at the player-connected end. */
	private static final float MIN_START_WIDTH = 1.5F;
	/** Fraction of the ribbon's current length over which the floor fades back out to the normal taper. */
	private static final float START_RAMP = 0.12F;

	/** Fraction of the carrier particle's own lifetime after which the ribbon starts fading out. */
	private static final float FADE_START_AGE_PERCENT = 0.75F;

	@Override
	public void addModules(ParticleModuleSet.Builder builder) {
		builder.addModule((UpdateParticleModule) RibbonEffectsCodeModule::update);
	}

	private static void update(QuasarParticle particle) {
		float agePercent = particle.getRenderData().getAgePercent();
		float alpha = agePercent <= FADE_START_AGE_PERCENT
				? 1.0F
				: Math.max(0.0F, 1.0F - (agePercent - FADE_START_AGE_PERCENT) / (1.0F - FADE_START_AGE_PERCENT));
		int color = (Math.round(alpha * 255.0F) << 24) | 0xFFFFFF;

		for (Trail trail : particle.getRenderData().getTrails()) {
			trail.setWidthFunction(RibbonEffectsCodeModule::width);
			trail.setColor(color);
		}
	}

	private static Float width(Float t) {
		float raw = (float) (Math.sin(t * 3.15) / 2.0) * PEAK_WIDTH_MULTIPLIER;
		float floor = MIN_START_WIDTH * Math.max(0.0F, 1.0F - t / START_RAMP);
		return Math.max(raw, floor);
	}
}
