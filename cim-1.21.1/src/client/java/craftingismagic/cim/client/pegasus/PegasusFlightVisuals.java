package craftingismagic.cim.client.pegasus;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * Purely cosmetic wing-flap bobbing for passive-mode Pegasus flight -
 * applied as a render-only offset (model position and, first-person only,
 * camera position), never touching real entity position/velocity. Doesn't
 * apply in active (elytra-glide) flight, since that's meant to read as
 * full aerodynamic movement rather than hovering.
 */
public final class PegasusFlightVisuals {
	private static final double BOB_AMPLITUDE = 0.08;
	/** Ticks spent on the upward flap itself - short, so it reads as a quick wingbeat rather than a smooth float. */
	private static final double RISE_TICKS = 18.0;
	/** Ticks spent settling back down after each flap, before the next one starts. */
	private static final double FALL_TICKS = 45.0;
	private static final double PERIOD_TICKS = RISE_TICKS + FALL_TICKS;

	private PegasusFlightVisuals() {
	}

	public static boolean isInPassiveFlight(AbstractClientPlayerEntity player) {
		return SpeciesAttachment.hasChosenSpecies(player) && SpeciesAttachment.getSpecies(player) == Species.PEGASUS
				&& player.getAbilities().flying && !player.isFallFlying();
	}

	public static boolean isInActiveFlight(AbstractClientPlayerEntity player) {
		return SpeciesAttachment.hasChosenSpecies(player) && SpeciesAttachment.getSpecies(player) == Species.PEGASUS
				&& player.isFallFlying();
	}

	public static double wingFlapOffset(Entity entity, float tickDelta) {
		double t = (entity.age + tickDelta) % PERIOD_TICKS;

		double shape;
		if (t < RISE_TICKS) {
			double local = t / RISE_TICKS;
			shape = 1.0 - (1.0 - local) * (1.0 - local);
		} else {
			double local = (t - RISE_TICKS) / FALL_TICKS;
			shape = 1.0 - local * local;
		}

		return shape * BOB_AMPLITUDE;
	}
}
