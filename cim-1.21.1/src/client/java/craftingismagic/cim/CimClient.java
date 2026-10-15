package craftingismagic.cim;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import craftingismagic.cim.client.ability.AbilityActivationHandler;
import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuOpener;
import craftingismagic.cim.client.ability.SelectedAbility;
import craftingismagic.cim.client.changeling.ChangelingInfiltratorFallbackRenderer;
import craftingismagic.cim.client.changeling.ChangelingInfiltratorRenderer;
import craftingismagic.cim.client.changeling.VoiceMimicryConsentKeyHandler;
import craftingismagic.cim.client.debug.DebugOverlayHud;
import craftingismagic.cim.client.gui.DialogueScreen;
import craftingismagic.cim.client.gui.SpeciesSelectScreen;
import craftingismagic.cim.client.gui.VillagerDebugEditorScreen;
import craftingismagic.cim.client.journal.JournalKeyBinding;
import craftingismagic.cim.client.journal.JournalOpener;
import craftingismagic.cim.client.levitation.LevitationAuraEffectHandler;
import craftingismagic.cim.client.levitation.LevitationClientState;
import craftingismagic.cim.client.levitation.LevitationSelectionHandler;
import craftingismagic.cim.client.levitation.LevitationSelectionHighlightRenderer;
import craftingismagic.cim.client.pegasus.PegasusChargeEffectHandler;
import craftingismagic.cim.client.pegasus.PegasusChargeVisualState;
import craftingismagic.cim.client.pegasus.PegasusDashKeyHandler;
import craftingismagic.cim.client.pegasus.PegasusRainboomTrailEffectHandler;
import craftingismagic.cim.client.pegasus.RainboomFlameTexture;
import craftingismagic.cim.client.pegasus.RainbowRingRegistry;
import craftingismagic.cim.client.pegasus.SonicRainboomEffectHandler;
import craftingismagic.cim.client.rogue.CimRoguePonyRenderer;
import craftingismagic.cim.client.rogue.RoguePonyFallbackRenderer;
import craftingismagic.cim.client.species.EarthPonyJumpBarHud;
import craftingismagic.cim.client.species.EarthPonyJumpHandler;
import craftingismagic.cim.client.stamina.ClientStaminaState;
import craftingismagic.cim.client.stamina.DebugStaminaDrainKeyHandler;
import craftingismagic.cim.client.stamina.StaminaBarHud;
import craftingismagic.cim.client.stamina.StaminaColorKeyHandler;
import craftingismagic.cim.client.unicorn.TeleportChargeBarHud;
import craftingismagic.cim.client.unicorn.TeleportChargeHandler;
import craftingismagic.cim.client.unicorn.TeleportChargeState;
import craftingismagic.cim.client.villager.ChangelingTrueFormSkins;
import craftingismagic.cim.client.villager.MineLpVillagerSkins;
import craftingismagic.cim.client.villager.PonyGeneticsSkins;
import craftingismagic.cim.network.DebugInfoS2CPayload;
import craftingismagic.cim.network.DebugMemoriesS2CPayload;
import craftingismagic.cim.client.dialogue.SpeechSounds;
import craftingismagic.cim.client.verse.VerseClient;
import craftingismagic.cim.client.verse.PlayerSpellSkins;
import craftingismagic.cim.client.verse.VerseLights;
import craftingismagic.cim.network.ChatVoiceS2CPayload;
import craftingismagic.cim.network.HumS2CPayload;
import craftingismagic.cim.network.DialogueBeatS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import craftingismagic.cim.network.MomentS2CPayload;
import craftingismagic.cim.client.mood.MomentDirector;
import craftingismagic.cim.network.LevitationSelectionBoxS2CPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;
import craftingismagic.cim.network.OpenVillagerDebugEditorS2CPayload;
import craftingismagic.cim.network.StaminaSyncS2CPayload;
import craftingismagic.cim.network.TeleportChargeEndS2CPayload;
import craftingismagic.cim.network.TeleportChargeStartS2CPayload;

public class CimClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RainbowRingRegistry.register();
		RainboomFlameTexture.register();

		ClientPlayNetworking.registerGlobalReceiver(OpenSpeciesSelectS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> context.client().setScreen(new SpeciesSelectScreen())));

		ClientPlayNetworking.registerGlobalReceiver(LevitationStateS2CPayload.ID,
				(payload, context) -> LevitationClientState.set(payload.state()));
		ClientPlayNetworking.registerGlobalReceiver(LevitationSelectionBoxS2CPayload.ID,
				(payload, context) -> LevitationClientState.setSelectionBox(payload.min(), payload.max()));
		MomentDirector.register();
		ClientPlayNetworking.registerGlobalReceiver(MomentS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> MomentDirector.play(payload.moment())));
		ClientPlayNetworking.registerGlobalReceiver(DialogueBeatS2CPayload.ID, (payload, context) -> context.client()
				.execute(() -> {
					if (context.client().currentScreen instanceof DialogueScreen screen && screen.entityId() == payload.entityId()) {
						screen.showBeat(payload);
					} else {
						context.client().setScreen(new DialogueScreen(payload));
					}
				}));
		ClientPlayNetworking.registerGlobalReceiver(ChatVoiceS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> SpeechSounds.chatted(payload)));
		ClientPlayNetworking.registerGlobalReceiver(HumS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> SpeechSounds.hummed(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(SpeechSounds::tick);
		VerseClient.register();
		VerseLights.register();
		PlayerSpellSkins.register();
		ClientPlayNetworking.registerGlobalReceiver(OpenVillagerDebugEditorS2CPayload.ID, (payload, context) -> context.client()
				.execute(() -> context.client().setScreen(VillagerDebugEditorScreen.fromPayload(payload))));

		ClientPlayNetworking.registerGlobalReceiver(StaminaSyncS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> ClientStaminaState.set(payload.stamina())));
		ClientPlayNetworking.registerGlobalReceiver(DebugMemoriesS2CPayload.ID, (payload, context) -> context.client().execute(() -> {
			if (context.client().currentScreen instanceof VillagerDebugEditorScreen editor) {
				editor.receiveMemories(payload.villagerEntityId(), payload.lines());
			}
		}));
		ClientPlayNetworking.registerGlobalReceiver(DebugInfoS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> DebugOverlayHud.setServerLines(payload.lines())));
		DebugOverlayHud.register();

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

		// Every piece of client-side ability/effect state is static and per-world, but nothing used
		// to clear it - quitting mid-hold left Levitation stuck thinking it was still holding in the
		// next world, and entity-id-keyed effect maps carried stale entries into a world that
		// reuses those ids.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			LevitationClientState.reset();
			ClientStaminaState.reset();
			DebugOverlayHud.reset();
			TeleportChargeState.end();
			SelectedAbility.set(null);
			PegasusChargeVisualState.reset();
			PegasusChargeEffectHandler.reset();
			PegasusRainboomTrailEffectHandler.reset();
			LevitationAuraEffectHandler.reset();
		}));

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
