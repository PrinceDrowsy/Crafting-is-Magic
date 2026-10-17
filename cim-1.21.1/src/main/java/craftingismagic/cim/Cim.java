package craftingismagic.cim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.changeling.ChangelingCommand;
import craftingismagic.cim.changeling.DisguiseTargetAttachment;
import craftingismagic.cim.changeling.VoiceMimicryConsentAttachment;
import craftingismagic.cim.debug.CimDebug;
import craftingismagic.cim.debug.DebugCommand;
import craftingismagic.cim.debug.DebugOverlay;
import craftingismagic.cim.entity.CimEntities;
import craftingismagic.cim.item.CimItemGroups;
import craftingismagic.cim.item.CimItems;
import craftingismagic.cim.levitation.LevitationManager;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.narrative.TaleLibrary;
import craftingismagic.cim.network.AdjustLevitationDistanceC2SPayload;
import craftingismagic.cim.network.AdjustLevitationSelectionCountC2SPayload;
import craftingismagic.cim.network.CancelTeleportChargeC2SPayload;
import craftingismagic.cim.network.ConfirmLevitationSelectionC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerFeelingC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerFavoriteGiftC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerGeneticsC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerLifeStageC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerNameC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerPersonalityC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerRelationshipTypeC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerSpeciesC2SPayload;
import craftingismagic.cim.network.DebugDrainStaminaC2SPayload;
import craftingismagic.cim.network.DebugDeleteMemoryC2SPayload;
import craftingismagic.cim.network.DebugInfoS2CPayload;
import craftingismagic.cim.network.DebugMemoriesS2CPayload;
import craftingismagic.cim.network.DebugOverlayToggleC2SPayload;
import craftingismagic.cim.network.DebugToggleChangelingDisguiseC2SPayload;
import craftingismagic.cim.network.ChatVoiceS2CPayload;
import craftingismagic.cim.network.HumS2CPayload;
import craftingismagic.cim.network.DialogueBringUpC2SPayload;
import craftingismagic.cim.network.DialogueFollowUpC2SPayload;
import craftingismagic.cim.network.DialogueBeatS2CPayload;
import craftingismagic.cim.network.DialogueChooseC2SPayload;
import craftingismagic.cim.network.LevitationAuraS2CPayload;
import craftingismagic.cim.network.LevitationSelectionBoxS2CPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;
import craftingismagic.cim.network.OpenVillagerDebugEditorS2CPayload;
import craftingismagic.cim.network.PegasusChargeEndS2CPayload;
import craftingismagic.cim.network.PegasusChargeReleaseC2SPayload;
import craftingismagic.cim.network.PegasusChargeStartS2CPayload;
import craftingismagic.cim.network.PegasusDashC2SPayload;
import craftingismagic.cim.network.PegasusRainboomTrailS2CPayload;
import craftingismagic.cim.network.PlaceLevitatedBlockC2SPayload;
import craftingismagic.cim.network.SaveTeleportShortcutC2SPayload;
import craftingismagic.cim.network.SelectSpeciesC2SPayload;
import craftingismagic.cim.network.SetStaminaColorC2SPayload;
import craftingismagic.cim.network.SonicRainboomS2CPayload;
import craftingismagic.cim.network.StaminaSyncS2CPayload;
import craftingismagic.cim.network.StartLevitationC2SPayload;
import craftingismagic.cim.network.StartTeleportChargeC2SPayload;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;
import craftingismagic.cim.network.TeleportChargeEndS2CPayload;
import craftingismagic.cim.network.TeleportChargeStartS2CPayload;
import craftingismagic.cim.network.ToggleFlightC2SPayload;
import craftingismagic.cim.network.ToggleVoiceMimicryConsentC2SPayload;
import craftingismagic.cim.network.UseTeleportShortcutC2SPayload;
import craftingismagic.cim.pegasus.PegasusDashManager;
import craftingismagic.cim.pegasus.PegasusFlightManager;
import craftingismagic.cim.pegasus.PegasusRainboomCharge;
import craftingismagic.cim.sound.ModSounds;
import craftingismagic.cim.spawn.VillageSpawnHandler;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.speech.ChatVoices;
import craftingismagic.cim.speech.HumDirector;
import craftingismagic.cim.speech.HumLibrary;
import craftingismagic.cim.network.VerseLightS2CPayload;
import craftingismagic.cim.network.VerseStrikeC2SPayload;
import craftingismagic.cim.network.VerseWatchS2CPayload;
import craftingismagic.cim.verse.VerseLanguage;
import craftingismagic.cim.verse.VerseRuntime;
import craftingismagic.cim.speech.SpeakingVoices;
import craftingismagic.cim.stamina.CimStatusEffects;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment.StaminaColors;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.stamina.StaminaSync;
import craftingismagic.cim.story.CimTaleVocabulary;
import craftingismagic.cim.story.StoryFacts;
import craftingismagic.cim.story.StoryThreads;
import craftingismagic.cim.story.PlayerNews;
import craftingismagic.cim.unicorn.TeleportManager;
import craftingismagic.cim.unicorn.TeleportShortcutAttachment;
import craftingismagic.cim.villager.ChangelingAppearanceAttachment;
import craftingismagic.cim.villager.ChangelingDisguiseAttachment;
import craftingismagic.cim.villager.ChangelingDisguiseHandler;
import craftingismagic.cim.villager.FavoriteGiftAttachment;
import craftingismagic.cim.villager.FriendGroupAttachment;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.PersonalityAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyGeneticsAttachment;
import craftingismagic.cim.villager.VillagerDebugEditorHandler;
import craftingismagic.cim.villager.VillagerDialogue;
import craftingismagic.cim.villager.VillagerGriefHandler;
import craftingismagic.cim.network.MomentS2CPayload;
import craftingismagic.cim.network.DebugSetPonyMovementC2SPayload;
import craftingismagic.cim.debug.DebugPonyMovement;
import craftingismagic.cim.villager.VillagerFamilyHandler;
import craftingismagic.cim.villager.VillagerFriendGroupHandler;
import craftingismagic.cim.villager.VillagerInteractionHandler;
import craftingismagic.cim.villager.VillagerSpeciesHandler;
import craftingismagic.cim.voice.CimVoices;
import craftingismagic.cim.voice.VoiceLeanings;

public class Cim implements ModInitializer {
	public static final String MOD_ID = "cim";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.bootstrap();
		CimItems.register();
		CimItemGroups.register();
		CimEntities.register();
		CimStatusEffects.register();
		preloadAttachmentTypes();
		CimGameRules.register();
		VillagerSpeciesHandler.register();
		ChangelingDisguiseHandler.register();
		VillagerFamilyHandler.register();
		VillagerFriendGroupHandler.register();
		VillagerInteractionHandler.register();
		VillageSpawnHandler.register();
		CimTaleVocabulary.register();
		TaleLibrary.register();
		HumLibrary.register();
		VerseLanguage.register();
		craftingismagic.cim.verse.SpellColors.register();
		craftingismagic.cim.memory.GossipRules.register();
		craftingismagic.cim.villager.GossipInbox.register();
		craftingismagic.cim.villager.VillagerGossip.register();
		VillagerDialogue.register();
		StoryThreads.register();
		CimVoices.register();
		VillagerGriefHandler.register();
		PlayerNews.register();
		DebugPonyMovement.register();
		// Keeps the .tale editor's vocabulary fresh while developing (see DebugCommand#exportVocabulary).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (craftingismagic.cim.debug.DebugConfig.get().enabled) {
				DebugCommand.exportVocabulary();
			}
		});
		CommandRegistrationCallback.EVENT.register(ChangelingCommand::register);
		CommandRegistrationCallback.EVENT.register(DebugCommand::register);
		DebugOverlay.register();

		// Rare, wandering natural spawn - roughly "occasional Pillager sighting" commonality, not
		// zombie-tier common. Weight/group-size are tunable, not load-bearing.
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, CimEntities.ROGUE_PONY, 5, 1, 2);

		PayloadTypeRegistry.playS2C().register(OpenSpeciesSelectS2CPayload.ID, OpenSpeciesSelectS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectSpeciesC2SPayload.ID, SelectSpeciesC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StartLevitationC2SPayload.ID, StartLevitationC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AdjustLevitationSelectionCountC2SPayload.ID, AdjustLevitationSelectionCountC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ConfirmLevitationSelectionC2SPayload.ID, ConfirmLevitationSelectionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StartTeleportChargeC2SPayload.ID, StartTeleportChargeC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CancelTeleportChargeC2SPayload.ID, CancelTeleportChargeC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SaveTeleportShortcutC2SPayload.ID, SaveTeleportShortcutC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UseTeleportShortcutC2SPayload.ID, UseTeleportShortcutC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TeleportChargeStartS2CPayload.ID, TeleportChargeStartS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TeleportChargeEndS2CPayload.ID, TeleportChargeEndS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AdjustLevitationDistanceC2SPayload.ID, AdjustLevitationDistanceC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PlaceLevitatedBlockC2SPayload.ID, PlaceLevitatedBlockC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleFlightC2SPayload.ID, ToggleFlightC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PegasusDashC2SPayload.ID, PegasusDashC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PegasusChargeReleaseC2SPayload.ID, PegasusChargeReleaseC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LevitationStateS2CPayload.ID, LevitationStateS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SonicRainboomS2CPayload.ID, SonicRainboomS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PegasusChargeStartS2CPayload.ID, PegasusChargeStartS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PegasusChargeEndS2CPayload.ID, PegasusChargeEndS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PegasusRainboomTrailS2CPayload.ID, PegasusRainboomTrailS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LevitationAuraS2CPayload.ID, LevitationAuraS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LevitationSelectionBoxS2CPayload.ID, LevitationSelectionBoxS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StopVillagerInteractionC2SPayload.ID, StopVillagerInteractionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DialogueChooseC2SPayload.ID, DialogueChooseC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DialogueBringUpC2SPayload.ID, DialogueBringUpC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DialogueFollowUpC2SPayload.ID, DialogueFollowUpC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DialogueBeatS2CPayload.ID, DialogueBeatS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ChatVoiceS2CPayload.ID, ChatVoiceS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HumS2CPayload.ID, HumS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VerseWatchS2CPayload.ID, VerseWatchS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VerseLightS2CPayload.ID, VerseLightS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VerseStrikeC2SPayload.ID, VerseStrikeC2SPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VerseStrikeC2SPayload.ID, (payload, context) -> VerseRuntime.strike(context.player()));
		PayloadTypeRegistry.playS2C().register(MomentS2CPayload.ID, MomentS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetPonyMovementC2SPayload.ID, DebugSetPonyMovementC2SPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DebugSetPonyMovementC2SPayload.ID,
				(payload, context) -> VillagerDebugEditorHandler.setMovement(context.player(), payload.entityId(), payload.mode()));
		PayloadTypeRegistry.playS2C().register(OpenVillagerDebugEditorS2CPayload.ID, OpenVillagerDebugEditorS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerNameC2SPayload.ID, DebugSetVillagerNameC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerSpeciesC2SPayload.ID, DebugSetVillagerSpeciesC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerLifeStageC2SPayload.ID, DebugSetVillagerLifeStageC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerGeneticsC2SPayload.ID, DebugSetVillagerGeneticsC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerPersonalityC2SPayload.ID, DebugSetVillagerPersonalityC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerFeelingC2SPayload.ID, DebugSetVillagerFeelingC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerRelationshipTypeC2SPayload.ID, DebugSetVillagerRelationshipTypeC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerFavoriteGiftC2SPayload.ID, DebugSetVillagerFavoriteGiftC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugToggleChangelingDisguiseC2SPayload.ID, DebugToggleChangelingDisguiseC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugDrainStaminaC2SPayload.ID, DebugDrainStaminaC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetStaminaColorC2SPayload.ID, SetStaminaColorC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleVoiceMimicryConsentC2SPayload.ID, ToggleVoiceMimicryConsentC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StaminaSyncS2CPayload.ID, StaminaSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugOverlayToggleC2SPayload.ID, DebugOverlayToggleC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DebugInfoS2CPayload.ID, DebugInfoS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DebugMemoriesS2CPayload.ID, DebugMemoriesS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugDeleteMemoryC2SPayload.ID, DebugDeleteMemoryC2SPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectSpeciesC2SPayload.ID, (payload, context) -> {
			// Species is a one-time, game-start choice - the selection screen is only ever opened for
			// a player who hasn't picked yet, so a repeat packet is never legitimate. Without this
			// check a modified client could swap species at will (and a Pegasus switching away kept
			// creative-style flight, since flight ticking just stops for non-Pegasi).
			if (SpeciesAttachment.hasChosenSpecies(context.player())) {
				LOGGER.warn("{} tried to change species after already choosing one - ignored", context.player().getName().getString());
				return;
			}
			SpeciesAttachment.setSpecies(context.player(), payload.species());
			LOGGER.info("{} chose species {}", context.player().getName().getString(), payload.species());
		});

		ServerPlayNetworking.registerGlobalReceiver(StartLevitationC2SPayload.ID,
				(payload, context) -> LevitationManager.startSelection(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AdjustLevitationSelectionCountC2SPayload.ID,
				(payload, context) -> LevitationManager.adjustSelectionCount(context.player(), payload.delta(), payload.axis()));
		ServerPlayNetworking.registerGlobalReceiver(ConfirmLevitationSelectionC2SPayload.ID,
				(payload, context) -> LevitationManager.confirmSelection(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(StartTeleportChargeC2SPayload.ID,
				(payload, context) -> TeleportManager.startCharge(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(CancelTeleportChargeC2SPayload.ID,
				(payload, context) -> TeleportManager.cancelCharge(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(SaveTeleportShortcutC2SPayload.ID,
				(payload, context) -> TeleportManager.saveShortcut(context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(UseTeleportShortcutC2SPayload.ID,
				(payload, context) -> TeleportManager.useShortcut(context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(AdjustLevitationDistanceC2SPayload.ID,
				(payload, context) -> LevitationManager.adjustDistance(context.player(), payload.delta()));
		ServerPlayNetworking.registerGlobalReceiver(PlaceLevitatedBlockC2SPayload.ID,
				(payload, context) -> LevitationManager.place(context.player(), payload.forceGentle()));
		ServerPlayNetworking.registerGlobalReceiver(ToggleFlightC2SPayload.ID,
				(payload, context) -> PegasusFlightManager.toggleFlight(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(PegasusDashC2SPayload.ID, (payload, context) -> {
			if (!PegasusRainboomCharge.tryStartCharge(context.player())) {
				PegasusDashManager.tryDash(context.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(PegasusChargeReleaseC2SPayload.ID,
				(payload, context) -> PegasusRainboomCharge.cancelFromRelease(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(DialogueChooseC2SPayload.ID,
				(payload, context) -> VillagerDialogue.choose(context.player(), payload.entityId(), payload.option()));
		ServerPlayNetworking.registerGlobalReceiver(DialogueBringUpC2SPayload.ID,
				(payload, context) -> VillagerDialogue.bringUp(context.player(), payload.entityId(), payload.request(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(DialogueFollowUpC2SPayload.ID,
				(payload, context) -> VillagerDialogue.followUp(context.player(), payload.entityId()));
		ServerPlayNetworking.registerGlobalReceiver(StopVillagerInteractionC2SPayload.ID,
				(payload, context) -> VillagerInteractionHandler.stopInteracting(context.player(), payload.villagerEntityId()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerNameC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setName(context.player(), payload.villagerEntityId(), payload.name()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerSpeciesC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setSpecies(context.player(), payload.villagerEntityId(), payload.species()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerLifeStageC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setLifeStage(context.player(), payload.villagerEntityId(), payload.lifeStage(), payload.growthTicks()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerGeneticsC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setGenetics(context.player(), payload.villagerEntityId(), payload.genetics()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerPersonalityC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setPersonality(context.player(), payload.villagerEntityId(), payload.personality()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerFeelingC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setFeeling(context.player(), payload.villagerEntityId(), payload.feeling(), payload.value()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerRelationshipTypeC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setRelationshipType(context.player(), payload.villagerEntityId(), payload.relationshipType()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerFavoriteGiftC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setFavoriteGift(context.player(), payload.villagerEntityId(), payload.favoriteGift()));
		ServerPlayNetworking.registerGlobalReceiver(DebugToggleChangelingDisguiseC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.toggleDisguise(context.player(), payload.villagerEntityId()));
		ServerPlayNetworking.registerGlobalReceiver(DebugDrainStaminaC2SPayload.ID, (payload, context) -> {
			// Test lever for the Stamina system (see DebugDrainStaminaC2SPayload's own doc comment) -
			// re-checks the debug gate server-side, not just the client's own gating.
			if (CimDebug.allowed(context.player())) {
				StaminaManager.drain(context.player(), 10.0f);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DebugDeleteMemoryC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.deleteMemory(context.player(), payload.villagerEntityId(), payload.index()));
		ServerPlayNetworking.registerGlobalReceiver(DebugOverlayToggleC2SPayload.ID,
				(payload, context) -> DebugOverlay.setSubscribed(context.player(), payload.enabled()));
		ServerPlayNetworking.registerGlobalReceiver(SetStaminaColorC2SPayload.ID, (payload, context) ->
				StaminaColorAttachment.setColors(context.player(), new StaminaColors(payload.fillColor(), payload.backgroundColor())));
		ServerPlayNetworking.registerGlobalReceiver(ToggleVoiceMimicryConsentC2SPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			boolean newConsent = !VoiceMimicryConsentAttachment.hasConsented(player);
			VoiceMimicryConsentAttachment.setConsent(player, newConsent);
			player.sendMessage(Text.literal("Voice mimicry: " + (newConsent ? "ON" : "OFF")), true);
		});

		ChatVoices.register();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!SpeciesAttachment.hasChosenSpecies(handler.getPlayer())) {
				ServerPlayNetworking.send(handler.getPlayer(), new OpenSpeciesSelectS2CPayload());
			}
			StaminaManager.restoreExhaustionEffectIfPending(handler.getPlayer());
			StaminaSync.invalidate(handler.getPlayer());
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			StaminaSync.invalidate(handler.getPlayer());
			DebugOverlay.setSubscribed(handler.getPlayer(), false);
			LevitationManager.cancel(handler.getPlayer());
			PegasusDashManager.cancel(handler.getPlayer());
			PegasusRainboomCharge.cancel(handler.getPlayer());
			TeleportManager.cancel(handler.getPlayer());
			VillagerInteractionHandler.cancel(handler.getPlayer());
			VerseRuntime.cancel(handler.getPlayer());
		});

		// Disconnect isn't the only way an in-progress ability gets cut off - dying, or changing
		// dimension (portal, or the End's exit, which goes through respawn instead), used to leave
		// a Levitation hold or Teleport/Rainboom charge running against the wrong world.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {
				interruptAbilities(player);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			interruptAbilities(player);
			StaminaSync.invalidate(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			interruptAbilities(newPlayer);
			StaminaSync.invalidate(newPlayer);
			// Status effects don't survive respawn the way the (copyOnDeath) Stamina attachment does.
			StaminaManager.restoreExhaustionEffectIfPending(newPlayer);
		});

		ServerTickEvents.END_SERVER_TICK.register(LevitationManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusFlightManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusDashManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusRainboomCharge::tick);
		ServerTickEvents.END_SERVER_TICK.register(TeleportManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(VillagerInteractionHandler::tick);
		ServerTickEvents.END_SERVER_TICK.register(HumDirector::tick);
		ServerTickEvents.END_SERVER_TICK.register(VerseRuntime::tick);
		// Lights placed and blocks in flight are put right before the world saves for the last time.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> VerseRuntime.stopAll());
		// Registered last deliberately - any future Stamina-spending ability's own tick handler
		// (Herding, etc.) needs to register BEFORE this one so its drain always wins over regen
		// within the same tick, rather than the two partially canceling out. See StaminaManager's
		// own doc comment.
		ServerTickEvents.END_SERVER_TICK.register(StaminaManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(DebugOverlay::tick);

		// 100 ticks is vanilla's own threshold for "slept enough to count" (the same one that
		// triggers the night skip and the spawn-point-setting/advancement rewards) - reused here so
		// a player can't cheese the wait by hopping in and out of bed early.
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayerEntity player && player.getSleepTimer() >= 100) {
				StaminaManager.onWokeUp(player);
			}
		});
	}

	/** Like the disconnect cleanup, but for a player who's still connected - so each ability also
	 * tells their client it ended, rather than leaving a charge bar or held-object state stuck. */
	private static void interruptAbilities(ServerPlayerEntity player) {
		LevitationManager.interrupt(player);
		PegasusRainboomCharge.cancelFromRelease(player);
		TeleportManager.cancelCharge(player);
	}

	/**
	 * Every {@code *Attachment} class registers its {@code AttachmentType} in a static initializer,
	 * which the JVM only actually runs the first time something touches that class - normally the
	 * first time a handler's {@code ENTITY_LOAD} lambda body executes for some entity. That's too
	 * late: Fabric deserializes an entity's saved attachment NBT (matching it against a global
	 * registry of known type ids) before firing {@code ENTITY_LOAD} for that same entity, so
	 * whichever villagers happen to be in the very first chunks loaded this session raced ahead of
	 * their own attachment classes ever being touched, and their real saved data got logged as
	 * "Unknown attachment type ... skipping" and silently dropped - visible in-game as those
	 * villagers' genetics/family/etc. reverting to a default/re-rolled look after a world reload.
	 * Referencing each type's field here forces registration during mod init, well before any world
	 * or chunk can possibly load.
	 */
	private static void preloadAttachmentTypes() {
		Object[] forceClassLoad = {
				SpeciesAttachment.SPECIES,
				StaminaAttachment.STAMINA,
				StaminaColorAttachment.STAMINA_COLORS,
				PonyGeneticsAttachment.GENETICS,
				PonyFamilyAttachment.FAMILY,
				FriendGroupAttachment.FRIEND_GROUP,
				ChangelingDisguiseAttachment.DISGUISE,
				ChangelingAppearanceAttachment.APPEARANCE,
				FavoriteGiftAttachment.FAVORITE_GIFT,
				FriendshipAttachment.AFFECTION,
				PersonalityAttachment.PERSONALITY,
				TeleportShortcutAttachment.TELEPORT_SHORTCUTS,
				VoiceMimicryConsentAttachment.VOICE_MIMICRY_CONSENT,
				DisguiseTargetAttachment.DISGUISE_TARGET,
				MemoryStore.MEMORIES,
				StoryFacts.PERSONAL_FACTS,
				StoryFacts.RELATIONSHIP_FACTS,
				StoryThreads.THREADS,
				VoiceLeanings.LEANINGS,
				PlayerNews.PENDING,
				PlayerNews.NAMES,
				PonyFamilyAttachment.CHILDREN,
				SpeakingVoices.VOICE,
				craftingismagic.cim.verse.SpellColors.COLORS,
		};
		LOGGER.debug("Preloaded {} attachment types", forceClassLoad.length);
	}
}
