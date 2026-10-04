package craftingismagic.cim.stamina;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.stamina.StaminaAttachment.Stamina;

/**
 * The shared drain/regen coordinator every Stamina-spending ability calls into, so nothing has to
 * reinvent this bookkeeping per-ability - Levitation/Teleport/Herding/Cultivation all eventually
 * spend from the same pool (see the README's Magic System overview), this is where that pool lives.
 * <p>
 * Regeneration only ever happens through {@link #tick}, which skips anyone drained this same tick
 * (tracked in {@link #drainedThisTick}) - an ability's own tick handler just needs to be registered
 * BEFORE {@code StaminaManager::tick} in {@code Cim.onInitialize()} for its drain to correctly "win"
 * over regen within the same tick, rather than the two partially canceling out.
 */
public final class StaminaManager {
	private static final float REGEN_PER_TICK = 0.5f;
	/** The Stamina bar renders as this many discrete notches ({@code max / NOTCH_VALUE}) rather than
	 * a smooth fill - see {@code StaminaBarHud}. Public so the client-side renderer has one shared
	 * source of truth instead of duplicating the magic number. */
	public static final float NOTCH_VALUE = 25.0f;
	/** Only granted on the specific tick a player's Stamina crosses from above 0 down to exactly 0 -
	 * see {@link #drain} - not from accumulating partial use. Exactly one notch per depletion. */
	private static final float GROWTH_INCREMENT = NOTCH_VALUE;

	private static final Set<UUID> drainedThisTick = new HashSet<>();

	private StaminaManager() {
	}

	/**
	 * Check-only affordability test for costs that can't "partially" happen - a flat/burst ability
	 * (a dash, an unaffordable Levitation pickup) should refuse outright rather than calling {@link
	 * #drain} and discovering after the fact that it only got part of what it needed. Continuous
	 * costs (a held object, active flight, a charging spell) should keep calling {@link #drain}
	 * directly instead and react to it returning {@code false}, not use this first.
	 */
	public static boolean hasEnough(ServerPlayerEntity player, float amount) {
		return StaminaAttachment.getStamina(player).current() >= amount;
	}

	/**
	 * Drains up to {@code amount} Stamina, partially draining down to exactly 0 rather than refusing
	 * outright when the request exceeds what's left - a flat refuse-if-insufficient design would let
	 * a caller bounce off "not enough" without the player ever actually landing on exactly 0, which
	 * would starve the max-Stamina growth trigger below of its only condition. Returns {@code true}
	 * if the full amount was available, {@code false} if it was partially or fully unavailable (the
	 * caller should treat {@code false} as "that's all you got, deactivate now").
	 */
	public static boolean drain(ServerPlayerEntity player, float amount) {
		Stamina stamina = StaminaAttachment.getStamina(player);
		float drained = Math.min(amount, stamina.current());
		float newCurrent = stamina.current() - drained;
		boolean awaitingSleep = stamina.awaitingSleep();

		// Growth only fires on the exact tick Stamina crosses from >0 down to 0 - not every tick
		// spent sitting at 0, which is why this checks the PREVIOUS value was still positive. The
		// max increase itself is deferred (see #onWokeUp) rather than applied here - only the flag
		// is set now, so both the new notch and regen unlocking arrive together on waking.
		if (stamina.current() > 0f && newCurrent <= 0f) {
			newCurrent = 0f;
			awaitingSleep = true;
			applyExhaustionEffect(player);
		}

		StaminaAttachment.setStamina(player, new Stamina(newCurrent, stamina.max(), awaitingSleep));
		drainedThisTick.add(player.getUuid());
		return drained >= amount;
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!drainedThisTick.contains(player.getUuid())) {
				regen(player);
			}
		}
		drainedThisTick.clear();
	}

	private static void regen(ServerPlayerEntity player) {
		Stamina stamina = StaminaAttachment.getStamina(player);
		if (stamina.awaitingSleep() || stamina.current() >= stamina.max()) {
			return;
		}
		float newCurrent = Math.min(stamina.max(), stamina.current() + REGEN_PER_TICK);
		StaminaAttachment.setStamina(player, new Stamina(newCurrent, stamina.max(), false));
	}

	/**
	 * Releases a pending depletion: grants the max-Stamina notch earned by hitting 0 and unlocks
	 * regen again, both together - called once per full night's sleep (see the {@code
	 * EntitySleepEvents.STOP_SLEEPING} listener registered in {@code Cim.onInitialize}), a no-op if
	 * the player wasn't actually awaiting one.
	 */
	public static void onWokeUp(ServerPlayerEntity player) {
		Stamina stamina = StaminaAttachment.getStamina(player);
		if (!stamina.awaitingSleep()) {
			return;
		}
		StaminaAttachment.setStamina(player, new Stamina(stamina.current(), stamina.max() + GROWTH_INCREMENT, false));
		player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(CimStatusEffects.EXHAUSTION));
	}

	/**
	 * Re-applies the Exhaustion icon on rejoin if a player logged out while still awaiting sleep -
	 * status effects aren't persisted the way the attachment is, so without this the icon would
	 * silently vanish across a relog despite {@code awaitingSleep} still being true underneath.
	 */
	public static void restoreExhaustionEffectIfPending(ServerPlayerEntity player) {
		if (StaminaAttachment.getStamina(player).awaitingSleep()) {
			applyExhaustionEffect(player);
		}
	}

	private static void applyExhaustionEffect(ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(CimStatusEffects.EXHAUSTION),
				StatusEffectInstance.INFINITE, 0, false, false, true));
	}
}
