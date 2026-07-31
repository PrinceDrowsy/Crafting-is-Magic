package craftingismagic.cim.client.species;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.AbilityRegistry;
import craftingismagic.cim.client.ability.SelectedAbility;

/**
 * Earth Pony increased jump height: holding the ability-activation key
 * while grounded charges up a jump, released when the key is let go —
 * the vanilla horse-riding jump model. Only does anything while Charged
 * Jump is the currently-selected ability (see {@link SelectedAbility}) -
 * the activation key is shared across all abilities, so this has to check
 * selection itself rather than assuming every press is meant for it.
 */
public final class EarthPonyJumpHandler {
	/** Vanilla's own default jump velocity, used as the uncharged baseline. */
	private static final float BASE_JUMP_VELOCITY = 0.42F;
	/**
	 * Velocity that produces a ~3 block high jump under vanilla gravity (0.08)
	 * and air drag (0.98), solved numerically. Placeholder balance value.
	 */
	private static final float MAX_JUMP_VELOCITY = 0.685F;
	/** Ticks held before the charge starts counting toward the bar/bonus. */
	private static final int HOLD_BUFFER_TICKS = 4;
	/** Total ticks held (including the buffer) for a fully-charged jump. */
	private static final int MAX_CHARGE_TICKS = 24;

	private static int chargeTicks = 0;
	private static boolean wasHoldingJump = false;

	private EarthPonyJumpHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(EarthPonyJumpHandler::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			reset();
			return;
		}

		boolean isSelected = SelectedAbility.isSelected(AbilityRegistry.CHARGED_JUMP);
		boolean holdingJump = isSelected && AbilityActivationKeyBinding.ACTIVATE_ABILITY.isPressed();
		boolean justReleased = wasHoldingJump && !holdingJump;
		wasHoldingJump = holdingJump;

		if (!isSelected || !player.isOnGround()) {
			reset();
			return;
		}

		if (holdingJump) {
			if (chargeTicks < MAX_CHARGE_TICKS) {
				chargeTicks++;
			}
		} else if (justReleased) {
			releaseJump(player);
		}
	}

	private static void releaseJump(ClientPlayerEntity player) {
		float chargeFraction = (float) chargeTicks / MAX_CHARGE_TICKS;
		float jumpVelocity = BASE_JUMP_VELOCITY + (MAX_JUMP_VELOCITY - BASE_JUMP_VELOCITY) * chargeFraction;
		Vec3d velocity = player.getVelocity();
		player.setVelocity(velocity.x, jumpVelocity, velocity.z);
		reset();
	}

	private static void reset() {
		chargeTicks = 0;
	}

	/** Fraction (0-1) of the current charge, for the HUD bar. */
	public static float getChargeFraction() {
		if (chargeTicks <= HOLD_BUFFER_TICKS) {
			return 0F;
		}

		return (float) (chargeTicks - HOLD_BUFFER_TICKS) / (MAX_CHARGE_TICKS - HOLD_BUFFER_TICKS);
	}

	public static boolean isCharging() {
		return chargeTicks > HOLD_BUFFER_TICKS;
	}
}
