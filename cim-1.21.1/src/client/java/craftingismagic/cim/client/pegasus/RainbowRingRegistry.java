package craftingismagic.cim.client.pegasus;

import foundry.veil.api.quasar.registry.RenderStyleRegistry;
import foundry.veil.platform.registry.RegistrationProvider;

import craftingismagic.cim.Cim;

/** Registers our custom Quasar render styles into Veil's render style registry - must run before Veil's own client bootstrap calls its render styles' init(). */
public final class RainbowRingRegistry {
	private RainbowRingRegistry() {
	}

	public static void register() {
		RegistrationProvider<foundry.veil.api.quasar.particle.RenderStyle> provider =
				RegistrationProvider.get(RenderStyleRegistry.REGISTRY, Cim.MOD_ID);
		provider.register("rainbow_ring", RainbowRingRenderStyle::new);
		provider.register("charge_cone", ChargeConeRenderStyle::new);
	}
}
