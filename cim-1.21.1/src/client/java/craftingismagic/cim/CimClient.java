package craftingismagic.cim;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import craftingismagic.cim.client.ability.AbilityActivationHandler;
import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuOpener;
import craftingismagic.cim.client.changeling.ChangelingInfiltratorFallbackRenderer;
import craftingismagic.cim.client.changeling.ChangelingInfiltratorRenderer;
import craftingismagic.cim.client.changeling.VoiceMimicryConsentKeyHandler;
import craftingismagic.cim.client.gui.IdleChatterScreen;
import craftingismagic.cim.client.gui.SpeciesSelectScreen;
import craftingismagic.cim.client.gui.VillagerDebugEditorScreen;
import craftingismagic.cim.client.gui.VillagerMenuScreen;
import craftingismagic.cim.client.journal.JournalKeyBinding;
import craftingismagic.cim.client.journal.JournalOpener;
import craftingismagic.cim.client.levitation.LevitationAuraEffectHandler;
import craftingismagic.cim.client.levitation.LevitationClientState;
import craftingismagic.cim.client.levitation.LevitationSelectionHandler;
import craftingismagic.cim.client.levitation.LevitationSelectionHighlightRenderer;
import craftingismagic.cim.client.pegasus.PegasusChargeEffectHandler;
import craftingismagic.cim.client.pegasus.PegasusDashKeyHandler;
import craftingismagic.cim.client.pegasus.PegasusRainboomTrailEffectHandler;
import craftingismagic.cim.client.pegasus.RainbowRingRegistry;
import craftingismagic.cim.client.pegasus.SonicRainboomEffectHandler;
import craftingismagic.cim.client.rogue.CimRoguePonyRenderer;
import craftingismagic.cim.client.rogue.RoguePonyFallbackRenderer;
import craftingismagic.cim.client.species.EarthPonyJumpBarHud;
import craftingismagic.cim.client.species.EarthPonyJumpHandler;
import craftingismagic.cim.client.stamina.DebugStaminaDrainKeyHandler;
import craftingismagic.cim.client.stamina.StaminaBarHud;
import craftingismagic.cim.client.stamina.StaminaColorKeyHandler;
import craftingismagic.cim.client.unicorn.TeleportChargeBarHud;
import craftingismagic.cim.client.unicorn.TeleportChargeHandler;
import craftingismagic.cim.client.unicorn.TeleportChargeState;
import craftingismagic.cim.client.villager.ChangelingTrueFormSkins;
import craftingismagic.cim.client.villager.MineLpVillagerSkins;
import craftingismagic.cim.client.villager.PonyGeneticsSkins;
import craftingismagic.cim.network.IdleChatterReactionS2CPayload;
import craftingismagic.cim.network.LevitationSelectionBoxS2CPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.network.OpenIdleChatterS2CPayload;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;
import craftingismagic.cim.network.OpenVillagerDebugEditorS2CPayload;
import craftingismagic.cim.network.OpenVillagerMenuS2CPayload;
import craftingismagic.cim.network.TeleportChargeEndS2CPayload;
import craftingismagic.cim.network.TeleportChargeStartS2CPayload;

public class CimClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RainbowRingRegistry.register();

		ClientPlayNetworking.registerGlobalReceiver(OpenSpeciesSelectS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new SpeciesSelectScreen())));

		ClientPlayNetworking.registerGlobalReceiver(LevitationStateS2CPayload.ID,
				(payload, context) -> LevitationClientState.set(payload.state()));
		ClientPlayNetworking.registerGlobalReceiver(LevitationSelectionBoxS2CPayload.ID,
				(payload, context) -> LevitationClientState.setSelectionBox(payload.min(), payload.max()));
		ClientPlayNetworking.registerGlobalReceiver(OpenVillagerMenuS2CPayload.ID, (payload, context) -> context.client()
				.execute(() -> context.client().setScreen(
						new VillagerMenuScreen(payload.villagerEntityId(), payload.affection(), payload.canTrade(), payload.villagerName()))));
		ClientPlayNetworking.registerGlobalReceiver(OpenIdleChatterS2CPayload.ID, (payload, context) -> context.client()
				.execute(() -> context.client().setScreen(
						new IdleChatterScreen(payload.villagerEntityId(), payload.villagerName(), payload.openingLine()))));
		ClientPlayNetworking.registerGlobalReceiver(IdleChatterReactionS2CPayload.ID, (payload, context) -> context.client()
				.execute(() -> {
					if (context.client().currentScreen instanceof IdleChatterScreen chatterScreen) {
						chatterScreen.showReaction(payload.reactionLine(), payload.closingLine());
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(OpenVillagerDebugEditorS2CPayload.ID, (payload, context) -> context.client()
				.execute(() -> context.client().setScreen(VillagerDebugEditorScreen.fromPayload(payload))));

		ClientPlayNetworking.registerGlobalReceiver(TeleportChargeStartS2CPayload.ID,
				(payload, context) -> TeleportChargeState.start(payload.durationTicks()));
		ClientPlayNetworking.registerGlobalReceiver(TeleportChargeEndS2CPayload.ID,
				(payload, context) -> TeleportChargeState.end());
		TeleportChargeHandler.register();
		TeleportChargeBarHud.register();

		EarthPonyJumpHandler.register();
		EarthPonyJumpBarHud.register();

		StaminaBarHud.register();
		DebugStaminaDrainKeyHandler.register();
		StaminaColorKeyHandler.register();
		VoiceMimicryConsentKeyHandler.register();

		RadialMenuKeyBinding.register();
		RadialMenuOpener.register();

		JournalKeyBinding.register();
		JournalOpener.register();

		AbilityActivationKeyBinding.register();
		AbilityActivationHandler.register();

		LevitationSelectionHandler.register();
		LevitationSelectionHighlightRenderer.register();

		PegasusDashKeyHandler.register();
		SonicRainboomEffectHandler.register();
		PegasusChargeEffectHandler.register();
		PegasusRainboomTrailEffectHandler.register();
		LevitationAuraEffectHandler.register();

		if (FabricLoader.getInstance().isModLoaded("minelp")) {
			MineLpVillagerSkins.register();
			PonyGeneticsSkins.register();
			ChangelingTrueFormSkins.register();
			CimRoguePonyRenderer.register();
			ChangelingInfiltratorRenderer.register();
		} else {
			// Villagers still fall back to vanilla's own renderer untouched (Mson only ever adds to
			// the dispatcher, never removes it) - but Rogue Ponies (and the test Changeling) are
			// wholly custom entity types with no vanilla renderer to fall back to, so they need an
			// explicit stand-in or the client crashes the moment one enters view.
			RoguePonyFallbackRenderer.register();
			ChangelingInfiltratorFallbackRenderer.register();
		}
	}
}
