package craftingismagic.cim.stamina;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.network.StaminaSyncS2CPayload;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;

/**
 * Sends a player's Stamina to their own client only when it matters. The attachment used to sync
 * itself on every write, and regen (or any continuous drain, like a held Levitation) writes every
 * single tick - one packet per player per tick for a value the HUD only needs to be roughly right
 * between notches. Now the attachment is server-side storage only, and this sends:
 * <ul>
 * <li>immediately on anything gameplay-relevant - max changed, Exhaustion ({@code awaitingSleep})
 * changed, a notch boundary crossed, or hitting empty/full;</li>
 * <li>otherwise at most every {@link #MIN_INTERVAL_TICKS} while the value keeps changing.</li>
 * </ul>
 * The client eases toward each received value (see {@code ClientStaminaState}), so the bar still
 * fills smoothly. Called once per player per tick from {@link StaminaManager#tick}, after every
 * ability's drain and the regen pass, so it always sees the tick's final value.
 */
public final class StaminaSync {
	private static final int MIN_INTERVAL_TICKS = 10;

	private static final Map<UUID, Sent> lastSent = new HashMap<>();
	/** Packets sent per player since the debug overlay last asked - see {@link #takeSendCount}. */
	private static final Map<UUID, Integer> sendCounts = new HashMap<>();

	private StaminaSync() {
	}

	static void tick(ServerPlayerEntity player, int serverTick) {
		Stamina stamina = StaminaAttachment.getStamina(player);
		Sent previous = lastSent.get(player.getUuid());
		if (previous == null) {
			send(player, stamina, serverTick);
			return;
		}
		Stamina old = previous.stamina();
		if (old.equals(stamina)) {
			return;
		}
		boolean important = old.max() != stamina.max()
				|| old.awaitingSleep() != stamina.awaitingSleep()
				|| notchOf(old.current()) != notchOf(stamina.current())
				|| stamina.current() <= 0f
				|| stamina.current() >= stamina.max();
		if (important || serverTick - previous.tick() >= MIN_INTERVAL_TICKS) {
			send(player, stamina, serverTick);
		}
	}

	/** Forces a fresh send on the next tick - for a new connection, or a respawned/teleported player
	 * entity whose client-side state may not match what was last sent. */
	public static void invalidate(ServerPlayerEntity player) {
		lastSent.remove(player.getUuid());
		sendCounts.remove(player.getUuid());
	}

	/** How many Stamina packets went to this player since the last call - the debug overlay calls it
	 * once a second, so the result reads as packets per second. */
	public static int takeSendCount(ServerPlayerEntity player) {
		Integer count = sendCounts.remove(player.getUuid());
		return count == null ? 0 : count;
	}

	private static void send(ServerPlayerEntity player, Stamina stamina, int serverTick) {
		ServerPlayNetworking.send(player, new StaminaSyncS2CPayload(stamina));
		lastSent.put(player.getUuid(), new Sent(stamina, serverTick));
		sendCounts.merge(player.getUuid(), 1, Integer::sum);
	}

	private static int notchOf(float current) {
		return (int) Math.floor(current / StaminaManager.NOTCH_VALUE);
	}

	private record Sent(Stamina stamina, int tick) {
	}
}
