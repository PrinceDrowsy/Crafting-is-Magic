package craftingismagic.cim;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import craftingismagic.cim.client.ability.RadialMenuKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuOpener;
import craftingismagic.cim.client.gui.SpeciesSelectScreen;
import craftingismagic.cim.client.species.EarthPonyJumpBarHud;
import craftingismagic.cim.client.species.EarthPonyJumpHandler;
import craftingismagic.cim.client.species.EarthPonyJumpKeyBinding;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;

public class CimClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(OpenSpeciesSelectS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new SpeciesSelectScreen())));

		EarthPonyJumpKeyBinding.register();
		EarthPonyJumpHandler.register();
		EarthPonyJumpBarHud.register();

		RadialMenuKeyBinding.register();
		RadialMenuOpener.register();
	}
}
