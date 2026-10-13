package craftingismagic.cim.debug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.network.DebugInfoS2CPayload;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;
import craftingismagic.cim.stamina.StaminaSync;
import craftingismagic.cim.villager.VillagerFamilyHandler;

/**
 * Server side of the debug HUD overlay (docs/implementation-plan.md: Debug and testing tools). A
 * player who toggles the overlay on is subscribed; once a second, every registered provider writes
 * its lines for that player and the lot is sent as one packet. Nothing is built or sent for anyone
 * not subscribed. Each phase adds a provider via {@link #addProvider} for whatever it's testing.
 */
public final class DebugOverlay {
	private static final int SEND_INTERVAL_TICKS = 20;

	private static final Set<UUID> subscribers = new HashSet<>();
	private static final List<BiConsumer<ServerPlayerEntity, List<String>>> providers = new ArrayList<>();

	private DebugOverlay() {
	}

	public static void register() {
		addProvider((player, lines) -> {
			Stamina stamina = StaminaAttachment.getStamina(player);
			lines.add(String.format("Stamina (server): %.2f / %.1f%s", stamina.current(), stamina.max(),
					stamina.awaitingSleep() ? "  EXHAUSTED" : ""));
			lines.add("Stamina packets/sec: " + StaminaSync.takeSendCount(player));
		});
		addProvider((player, lines) -> lines.add("Growing villagers tracked: " + VillagerFamilyHandler.growingCount()));
	}

	public static void addProvider(BiConsumer<ServerPlayerEntity, List<String>> provider) {
		providers.add(provider);
	}

	public static void setSubscribed(ServerPlayerEntity player, boolean subscribed) {
		if (subscribed && CimDebug.allowed(player)) {
			subscribers.add(player.getUuid());
		} else {
			subscribers.remove(player.getUuid());
		}
	}

	public static void tick(MinecraftServer server) {
		if (subscribers.isEmpty() || server.getTicks() % SEND_INTERVAL_TICKS != 0) {
			return;
		}
		subscribers.removeIf(uuid -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player == null || !CimDebug.allowed(player)) {
				return true;
			}
			List<String> lines = new ArrayList<>();
			for (BiConsumer<ServerPlayerEntity, List<String>> provider : providers) {
				provider.accept(player, lines);
			}
			ServerPlayNetworking.send(player, new DebugInfoS2CPayload(lines));
			return false;
		});
	}
}
