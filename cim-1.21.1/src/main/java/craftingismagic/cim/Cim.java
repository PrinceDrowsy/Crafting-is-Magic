package craftingismagic.cim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.item.CimItems;
import craftingismagic.cim.levitation.LevitationManager;
import craftingismagic.cim.network.AdjustLevitationDistanceC2SPayload;
import craftingismagic.cim.network.AdjustLevitationSelectionCountC2SPayload;
import craftingismagic.cim.network.ConfirmLevitationSelectionC2SPayload;
import craftingismagic.cim.network.LevitationStateS2CPayload;
import craftingismagic.cim.network.OpenSpeciesSelectS2CPayload;
import craftingismagic.cim.network.PegasusChargeEndS2CPayload;
import craftingismagic.cim.network.PegasusChargeReleaseC2SPayload;
import craftingismagic.cim.network.PegasusChargeStartS2CPayload;
import craftingismagic.cim.network.PegasusDashC2SPayload;
import craftingismagic.cim.network.PlaceLevitatedBlockC2SPayload;
import craftingismagic.cim.network.SelectSpeciesC2SPayload;
import craftingismagic.cim.network.SonicRainboomS2CPayload;
import craftingismagic.cim.network.StartLevitationC2SPayload;
import craftingismagic.cim.network.ToggleFlightC2SPayload;
import craftingismagic.cim.pegasus.PegasusDashManager;
import craftingismagic.cim.pegasus.PegasusFlightManager;
import craftingismagic.cim.pegasus.PegasusRainboomCharge;
import craftingismagic.cim.species.SpeciesAttachment;

public class Cim implements ModInitializer {
	public static final String MOD_ID = "cim";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CimItems.register();

		PayloadTypeRegistry.playS2C().register(OpenSpeciesSelectS2CPayload.ID, OpenSpeciesSelectS2CPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectSpeciesC2SPayload.ID, SelectSpeciesC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StartLevitationC2SPayload.ID, StartLevitationC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AdjustLevitationSelectionCountC2SPayload.ID, AdjustLevitationSelectionCountC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ConfirmLevitationSelectionC2SPayload.ID, ConfirmLevitationSelectionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AdjustLevitationDistanceC2SPayload.ID, AdjustLevitationDistanceC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PlaceLevitatedBlockC2SPayload.ID, PlaceLevitatedBlockC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ToggleFlightC2SPayload.ID, ToggleFlightC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PegasusDashC2SPayload.ID, PegasusDashC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PegasusChargeReleaseC2SPayload.ID, PegasusChargeReleaseC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LevitationStateS2CPayload.ID, LevitationStateS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SonicRainboomS2CPayload.ID, SonicRainboomS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PegasusChargeStartS2CPayload.ID, PegasusChargeStartS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PegasusChargeEndS2CPayload.ID, PegasusChargeEndS2CPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectSpeciesC2SPayload.ID, (payload, context) -> {
			SpeciesAttachment.setSpecies(context.player(), payload.species());
			LOGGER.info("{} chose species {}", context.player().getName().getString(), payload.species());
		});

		ServerPlayNetworking.registerGlobalReceiver(StartLevitationC2SPayload.ID,
				(payload, context) -> LevitationManager.startSelection(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AdjustLevitationSelectionCountC2SPayload.ID,
				(payload, context) -> LevitationManager.adjustSelectionCount(context.player(), payload.delta()));
		ServerPlayNetworking.registerGlobalReceiver(ConfirmLevitationSelectionC2SPayload.ID,
				(payload, context) -> LevitationManager.confirmSelection(context.player()));
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

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!SpeciesAttachment.hasChosenSpecies(handler.getPlayer())) {
				ServerPlayNetworking.send(handler.getPlayer(), new OpenSpeciesSelectS2CPayload());
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LevitationManager.cancel(handler.getPlayer());
			PegasusDashManager.cancel(handler.getPlayer());
			PegasusRainboomCharge.cancel(handler.getPlayer());
		});

		ServerTickEvents.END_SERVER_TICK.register(LevitationManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusFlightManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusDashManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PegasusRainboomCharge::tick);
	}
}
