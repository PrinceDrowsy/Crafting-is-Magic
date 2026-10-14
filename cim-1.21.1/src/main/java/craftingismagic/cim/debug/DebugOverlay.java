package craftingismagic.cim.debug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.network.DebugInfoS2CPayload;
import craftingismagic.cim.rogue.RoguePonyEntity;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;
import craftingismagic.cim.stamina.StaminaSync;
import craftingismagic.cim.villager.Feeling;
import craftingismagic.cim.villager.FeelingBand;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.FriendshipAttachment.RelationshipData;
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
		addProvider(DebugOverlay::lookedAtFeelings);
	}

	private static final double LOOK_RANGE = 8.0;

	/** The player's three feelings with whatever pony they're looking at (within {@link #LOOK_RANGE}
	 * blocks) - exact values plus bands, the numbers players themselves never see. */
	private static void lookedAtFeelings(ServerPlayerEntity player, List<String> lines) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1f).multiply(LOOK_RANGE));
		Box searchBox = player.getBoundingBox().stretch(player.getRotationVec(1f).multiply(LOOK_RANGE)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, searchBox,
				entity -> entity instanceof VillagerEntity || entity instanceof RoguePonyEntity, LOOK_RANGE * LOOK_RANGE);
		if (hit == null) {
			return;
		}
		Entity target = hit.getEntity();
		RelationshipData data = FriendshipAttachment.get(target, player.getUuid());
		lines.add("Looking at: " + target.getName().getString() + " (" + data.relationshipType().name().toLowerCase(Locale.ROOT) + ")");
		for (Feeling feeling : Feeling.values()) {
			int value = data.feeling(feeling);
			lines.add(String.format("  %s: %d (%s)", feeling.name().charAt(0) + feeling.name().substring(1).toLowerCase(Locale.ROOT),
					value, FeelingBand.of(value).name().toLowerCase(Locale.ROOT)));
		}

		List<Memory> memories = MemoryStore.getAll(target);
		lines.add("  Memories: " + memories.size() + " (" + MemoryStore.about(target, player.getUuid()).size() + " about you)");
		for (int i = memories.size() - 1; i >= Math.max(0, memories.size() - 3); i--) {
			Memory memory = memories.get(i);
			lines.add("    " + memory.type() + " - " + memory.importance().name().toLowerCase(Locale.ROOT)
					+ memory.emotion().map(e -> ", " + e.name().toLowerCase(Locale.ROOT)).orElse(""));
		}
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
