package craftingismagic.cim;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import craftingismagic.cim.client.ability.AbilityActivationHandler;
import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuOpener;
import craftingismagic.cim.client.gui.SpeciesSelectScreen;
import craftingismagic.cim.client.levitation.LevitationClientState;
import craftingismagic.cim.client.levitation.LevitationSelectionHandler;
import craftingismagic.cim.client.pegasus.PegasusChargeEffectHandler;
import craftingismagic.cim.client.pegasus.PegasusDashKeyHandler;
import craftingismagic.cim.client.pegasus.RainbowRingRegistry;
import craftingismagic.cim.client.pegasus.SonicRainboomEffectHandler;
import craftingismagic.cim.client.species.EarthPonyJumpBarHud;
import craftingismagic.cim.client.species.EarthPonyJumpHandler;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;

public class CimClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RainbowRingRegistry.register();

		ClientPlayNetworking.registerGlobalReceiver(OpenSpeciesSelectS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new SpeciesSelectScreen())));

		ClientPlayNetworking.registerGlobalReceiver(LevitationStateS2CPayload.ID,
				(payload, context) -> LevitationClientState.set(payload.state()));

		EarthPonyJumpHandler.register();
		EarthPonyJumpBarHud.register();

		RadialMenuKeyBinding.register();
		RadialMenuOpener.register();

		AbilityActivationKeyBinding.register();
		AbilityActivationHandler.register();

		LevitationSelectionHandler.register();

		PegasusDashKeyHandler.register();
		SonicRainboomEffectHandler.register();
		PegasusChargeEffectHandler.register();
	}
}
