package craftingismagic.cim.client.accessibility;

import java.util.ArrayDeque;
import java.util.Iterator;

import foundry.veil.api.client.render.VeilRenderSystem;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * What every visual effect asks before it flashes, flickers or scatters particles (README: Accessibility):
 * the chosen flash intensity, particle amount and steadiness, and - for everyone, whatever the settings - a
 * cap of three bright flashes a second from all sources together, the line the photosensitivity guidelines
 * draw, which a crowd of casters on a server could otherwise cross.
 */
public final class VisualComfort {
	private static final int MAX_FLASHES_PER_SECOND = 3;
	private static final long SECOND = 1_000_000_000L;
	/** The shader flag for steady effects (see the charge cone's program). */
	private static final String STEADY_FLAG = "CIM_STEADY";

	/** When each recent or planned bright flash goes off (System.nanoTime). */
	private static final ArrayDeque<Long> FLASHES = new ArrayDeque<>();
	private static Boolean steadyFlagSet;

	private VisualComfort() {
	}

	public static void register() {
		WorldRenderEvents.START.register(context -> syncShaderFlag());
	}

	private static void syncShaderFlag() {
		boolean steady = steady();
		if (steadyFlagSet != null && steadyFlagSet == steady) {
			return;
		}
		if (steady) {
			VeilRenderSystem.renderer().getShaderDefinitions().set(STEADY_FLAG);
		} else {
			VeilRenderSystem.renderer().getShaderDefinitions().remove(STEADY_FLAG);
		}
		steadyFlagSet = steady;
	}

	/**
	 * Asks to show a bright flash {@code delay} seconds from now. False if that would make more than three
	 * within a second of each other - the flash should then be left out - or if flashes are turned right down.
	 */
	public static boolean flash(float delay) {
		if (flashIntensity() < 0.05f) {
			return false;
		}
		long now = System.nanoTime();
		long at = now + (long) (delay * SECOND);
		int near = 0;
		for (Iterator<Long> it = FLASHES.iterator(); it.hasNext();) {
			long other = it.next();
			if (other < now - SECOND) {
				it.remove();
			} else if (Math.abs(other - at) < SECOND) {
				near++;
			}
		}
		if (near >= MAX_FLASHES_PER_SECOND) {
			return false;
		}
		FLASHES.add(at);
		return true;
	}

	public static float flashIntensity() {
		return Math.max(0f, Math.min(1f, AccessibilityConfig.get().flashIntensity));
	}

	public static boolean colorFlashes() {
		return AccessibilityConfig.get().colorFlashes;
	}

	public static boolean steady() {
		return AccessibilityConfig.get().steadyEffects;
	}

	/** How many of {@code count} particles to show at the chosen amount - none only when it's turned all the way down. */
	public static int particles(int count) {
		float amount = Math.max(0f, Math.min(1f, AccessibilityConfig.get().particleAmount));
		return amount <= 0f ? 0 : Math.max(1, Math.round(count * amount));
	}

	/** How much more seldom ongoing particles (glints, dust) appear at the chosen amount - infinitely, at none. */
	public static float particleGap() {
		float amount = Math.max(0f, Math.min(1f, AccessibilityConfig.get().particleAmount));
		return amount <= 0f ? Float.POSITIVE_INFINITY : 1f / amount;
	}
}
