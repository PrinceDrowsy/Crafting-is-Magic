package craftingismagic.cim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.network.ServerPlayerEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.entity.CimEntities;
import craftingismagic.cim.item.CimItemGroups;
import craftingismagic.cim.item.CimItems;
import craftingismagic.cim.levitation.LevitationManager;
import craftingismagic.cim.network.AdjustLevitationDistanceC2SPayload;
import craftingismagic.cim.network.AdjustLevitationSelectionCountC2SPayload;
import craftingismagic.cim.network.ConfirmLevitationSelectionC2SPayload;
import craftingismagic.cim.network.ConfirmTeleportC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerAffectionC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerFavoriteGiftC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerGeneticsC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerLifeStageC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerNameC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerPersonalityC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerRelationshipTypeC2SPayload;
import craftingismagic.cim.network.DebugSetVillagerSpeciesC2SPayload;
import craftingismagic.cim.network.DebugDrainStaminaC2SPayload;
import craftingismagic.cim.network.DebugToggleChangelingDisguiseC2SPayload;
import craftingismagic.cim.network.IdleChatterReactionS2CPayload;
import craftingismagic.cim.network.IdleChatterReplyC2SPayload;
import craftingismagic.cim.network.LevitationAuraS2CPayload;
import craftingismagic.cim.network.LevitationSelectionBoxS2CPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.network.OpenIdleChatterS2CPayload;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;
import craftingismagic.cim.network.OpenVillagerDebugEditorS2CPayload;
import craftingismagic.cim.network.OpenVillagerMenuS2CPayload;
import craftingismagic.cim.network.PegasusChargeEndS2CPayload;
import craftingismagic.cim.network.PegasusChargeReleaseC2SPayload;
import craftingismagic.cim.network.PegasusChargeStartS2CPayload;
import craftingismagic.cim.network.PegasusDashC2SPayload;
import craftingismagic.cim.network.PegasusRainboomTrailS2CPayload;
import craftingismagic.cim.network.PlaceLevitatedBlockC2SPayload;
import craftingismagic.cim.network.SelectSpeciesC2SPayload;
import craftingismagic.cim.network.SetStaminaColorC2SPayload;
import craftingismagic.cim.network.SonicRainboomS2CPayload;
import craftingismagic.cim.network.StartLevitationC2SPayload;
import craftingismagic.cim.network.StopVillagerInteractionC2SPayload;
import craftingismagic.cim.network.ToggleFlightC2SPayload;
import craftingismagic.cim.network.VillagerGiveGiftC2SPayload;
import craftingismagic.cim.network.VillagerIdleChatterC2SPayload;
import craftingismagic.cim.network.VillagerOfferTradeC2SPayload;
import craftingismagic.cim.pegasus.PegasusDashManager;
import craftingismagic.cim.pegasus.PegasusFlightManager;
import craftingismagic.cim.pegasus.PegasusRainboomCharge;
import craftingismagic.cim.sound.ModSounds;
import craftingismagic.cim.spawn.VillageSpawnHandler;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.stamina.CimStatusEffects;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.stamina.StaminaColorAttachment.StaminaColors;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.unicorn.TeleportManager;
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
import craftingismagic.cim.villager.VillagerFamilyHandler;
import craftingismagic.cim.villager.VillagerFriendGroupHandler;
import craftingismagic.cim.villager.VillagerInteractionHandler;
import craftingismagic.cim.villager.VillagerSpeciesHandler;

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
		VillagerSpeciesHandler.register();
		ChangelingDisguiseHandler.register();
		VillagerFamilyHandler.register();
		VillagerFriendGroupHandler.register();
		VillagerInteractionHandler.register();
		VillageSpawnHandler.register();

		// Rare, wandering natural spawn - roughly "occasional Pillager sighting" commonality, not
		// zombie-tier common. Weight/group-size are tunable, not load-bearing.
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, CimEntities.ROGUE_PONY, 5, 1, 2);

		PayloadTypeRegistry.playS2C().register(OpenSpeciesSelectS2CPayload.ID, OpenSpeciesSelectS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectSpeciesC2SPayload.ID, SelectSpeciesC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StartLevitationC2SPayload.ID, StartLevitationC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AdjustLevitationSelectionCountC2SPayload.ID, AdjustLevitationSelectionCountC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ConfirmLevitationSelectionC2SPayload.ID, ConfirmLevitationSelectionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ConfirmTeleportC2SPayload.ID, ConfirmTeleportC2SPayload.CODEC);
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
		PayloadTypeRegistry.playC2S().register(VillagerIdleChatterC2SPayload.ID, VillagerIdleChatterC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VillagerOfferTradeC2SPayload.ID, VillagerOfferTradeC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(VillagerGiveGiftC2SPayload.ID, VillagerGiveGiftC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StopVillagerInteractionC2SPayload.ID, StopVillagerInteractionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IdleChatterReplyC2SPayload.ID, IdleChatterReplyC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenVillagerMenuS2CPayload.ID, OpenVillagerMenuS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenIdleChatterS2CPayload.ID, OpenIdleChatterS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IdleChatterReactionS2CPayload.ID, IdleChatterReactionS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenVillagerDebugEditorS2CPayload.ID, OpenVillagerDebugEditorS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerNameC2SPayload.ID, DebugSetVillagerNameC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerSpeciesC2SPayload.ID, DebugSetVillagerSpeciesC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerLifeStageC2SPayload.ID, DebugSetVillagerLifeStageC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerGeneticsC2SPayload.ID, DebugSetVillagerGeneticsC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerPersonalityC2SPayload.ID, DebugSetVillagerPersonalityC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerAffectionC2SPayload.ID, DebugSetVillagerAffectionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerRelationshipTypeC2SPayload.ID, DebugSetVillagerRelationshipTypeC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugSetVillagerFavoriteGiftC2SPayload.ID, DebugSetVillagerFavoriteGiftC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugToggleChangelingDisguiseC2SPayload.ID, DebugToggleChangelingDisguiseC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DebugDrainStaminaC2SPayload.ID, DebugDrainStaminaC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetStaminaColorC2SPayload.ID, SetStaminaColorC2SPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectSpeciesC2SPayload.ID, (payload, context) -> {
			SpeciesAttachment.setSpecies(context.player(), payload.species());
			LOGGER.info("{} chose species {}", context.player().getName().getString(), payload.species());
		});

		ServerPlayNetworking.registerGlobalReceiver(StartLevitationC2SPayload.ID,
				(payload, context) -> LevitationManager.startSelection(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AdjustLevitationSelectionCountC2SPayload.ID,
				(payload, context) -> LevitationManager.adjustSelectionCount(context.player(), payload.delta(), payload.axis()));
		ServerPlayNetworking.registerGlobalReceiver(ConfirmLevitationSelectionC2SPayload.ID,
				(payload, context) -> LevitationManager.confirmSelection(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ConfirmTeleportC2SPayload.ID,
				(payload, context) -> TeleportManager.tryTeleport(context.player()));
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
		ServerPlayNetworking.registerGlobalReceiver(VillagerIdleChatterC2SPayload.ID,
				(payload, context) -> VillagerInteractionHandler.idleChatter(context.player(), payload.villagerEntityId()));
		ServerPlayNetworking.registerGlobalReceiver(IdleChatterReplyC2SPayload.ID, (payload, context) ->
				VillagerInteractionHandler.replyToIdleChatter(context.player(), payload.villagerEntityId(), payload.tone()));
		ServerPlayNetworking.registerGlobalReceiver(VillagerOfferTradeC2SPayload.ID,
				(payload, context) -> VillagerInteractionHandler.offerTrade(context.player(), payload.villagerEntityId()));
		ServerPlayNetworking.registerGlobalReceiver(VillagerGiveGiftC2SPayload.ID,
				(payload, context) -> VillagerInteractionHandler.giveGift(context.player(), payload.villagerEntityId()));
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
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerAffectionC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setAffection(context.player(), payload.villagerEntityId(), payload.affection()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerRelationshipTypeC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setRelationshipType(context.player(), payload.villagerEntityId(), payload.relationshipType()));
		ServerPlayNetworking.registerGlobalReceiver(DebugSetVillagerFavoriteGiftC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.setFavoriteGift(context.player(), payload.villagerEntityId(), payload.favoriteGift()));
		ServerPlayNetworking.registerGlobalReceiver(DebugToggleChangelingDisguiseC2SPayload.ID, (payload, context) ->
				VillagerDebugEditorHandler.toggleDisguise(context.player(), payload.villagerEntityId()));
		ServerPlayNetworking.registerGlobalReceiver(DebugDrainStaminaC2SPayload.ID, (payload, context) -> {
			// Creative-only test lever for the Stamina system (see DebugDrainStaminaC2SPayload's own
			// doc comment) - re-checks Creative mode server-side, not just the client's own gating.
			if (context.player().isCreative()) {
				StaminaManager.drain(context.player(), 10.0f);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SetStaminaColorC2SPayload.ID, (payload, context) ->
				StaminaColorAttachment.setColors(context.player(), new StaminaColors(payload.fillColor(), payload.backgroundColor())));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!SpeciesAttachment.hasChosenSpecies(handler.getPlayer())) {
				ServerPlayNetworking.send(handler.getPlayer(), new OpenSpeciesSelectS2CPayload());
			}
			StaminaManager.restoreExhaustionEffectIfPending(handler.getPlayer());
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LevitationManager.cancel(handler.getPlayer());
			PegasusDashManager.cancel(handler.getPlayer());
			PegasusRainboomCharge.cancel(handler.getPlayer());
			VillagerInteractionHandler.cancel(handler.getPlayer());
		});

		ServerTickEvents.END_SERVER_TICK.register(LevitationManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusFlightManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusDashManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusRainboomCharge::tick);
		ServerTickEvents.END_SERVER_TICK.register(VillagerInteractionHandler::tick);
		// Registered last deliberately - any future Stamina-spending ability's own tick handler
		// (Herding, etc.) needs to register BEFORE this one so its drain always wins over regen
		// within the same tick, rather than the two partially canceling out. See StaminaManager's
		// own doc comment.
		ServerTickEvents.END_SERVER_TICK.register(StaminaManager::tick);

		// 100 ticks is vanilla's own threshold for "slept enough to count" (the same one that
		// triggers the night skip and the spawn-point-setting/advancement rewards) - reused here so
		// a player can't cheese the wait by hopping in and out of bed early.
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayerEntity player && player.getSleepTimer() >= 100) {
				StaminaManager.onWokeUp(player);
			}
		});
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
		};
		LOGGER.debug("Preloaded {} attachment types", forceClassLoad.length);
	}
}
