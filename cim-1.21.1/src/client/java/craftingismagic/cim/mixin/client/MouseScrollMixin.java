package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Mouse;

import craftingismagic.cim.client.levitation.LevitationClientState;
import craftingismagic.cim.network.AdjustLevitationDistanceC2SPayload;
import craftingismagic.cim.network.AdjustLevitationSelectionCountC2SPayload;

/**
 * Redirects scroll input to Levitation while it's active, instead of the
 * normal hotbar-slot scroll: growing/shrinking the block selection while
 * selecting, or adjusting hold distance (and building throw momentum)
 * while holding. There's no generic in-world mouse-scroll event in
 * Fabric API to hook for this, so this intercepts vanilla's own scroll
 * handler directly - same approach vanilla mods generally have to take
 * for this.
 */
@Mixin(Mouse.class)
public class MouseScrollMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void cim$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (vertical == 0) {
			return;
		}

		if (LevitationClientState.isSelecting()) {
			ClientPlayNetworking.send(new AdjustLevitationSelectionCountC2SPayload((float) vertical));
			ci.cancel();
		} else if (LevitationClientState.isHolding()) {
			ClientPlayNetworking.send(new AdjustLevitationDistanceC2SPayload((float) vertical));
			ci.cancel();
		}
	}
}
