package craftingismagic.cim.magic;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.levitation.LevitationManager;
import craftingismagic.cim.network.HornGlowS2CPayload;
import craftingismagic.cim.stamina.StaminaColorAttachment;
import craftingismagic.cim.unicorn.TeleportManager;

/**
 * A unicorn's horn glows while they channel magic (docs/magic-aura.md: the horn is the lightning rod -
 * gathering, holding, releasing). Rather than hooking every way a spell can start and end, each tick asks
 * one question per player - are they channelling? - and tells everyone who can see them when the answer
 * changes, and anyone who comes into view while it's lit. The look itself is all client-side.
 */
public final class HornGlow {
	private static final Set<UUID> LIT = new HashSet<>();

	private HornGlow() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HornGlow::tick);
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayerEntity caster && LIT.contains(caster.getUuid())) {
				ServerPlayNetworking.send(viewer, payload(caster, true));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LIT.remove(handler.player.getUuid()));
	}

	/** Whether this player is channelling magic right now: levitating something, or charging a teleport. */
	private static boolean channelling(ServerPlayerEntity player) {
		return LevitationManager.isChannelling(player) || TeleportManager.isCharging(player);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean lit = channelling(player);
			if (lit == LIT.contains(player.getUuid())) {
				continue;
			}
			if (lit) {
				LIT.add(player.getUuid());
			} else {
				LIT.remove(player.getUuid());
			}
			HornGlowS2CPayload payload = payload(player, lit);
			ServerPlayNetworking.send(player, payload);
			for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private static HornGlowS2CPayload payload(ServerPlayerEntity caster, boolean lit) {
		return new HornGlowS2CPayload(caster.getId(), lit, StaminaColorAttachment.getColors(caster).fillColor() & 0xFFFFFF);
	}
}
