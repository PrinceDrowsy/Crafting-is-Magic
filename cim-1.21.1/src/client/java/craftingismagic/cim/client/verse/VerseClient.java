package craftingismagic.cim.client.verse;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import craftingismagic.cim.network.VerseStrikeC2SPayload;
import craftingismagic.cim.network.VerseWatchS2CPayload;

/**
 * The client's part in Verse's "when I strike" (design doc: Verse, caster inputs): while the server says a
 * verse is waiting for a strike, pressing the attack key reports one - only then, never every swing.
 */
public final class VerseClient {
	private static boolean strikeWatched;
	private static boolean attackWasDown;

	private VerseClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VerseWatchS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> strikeWatched = payload.strike()));
		ClientTickEvents.END_CLIENT_TICK.register(VerseClient::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null) {
			strikeWatched = false;
			return;
		}
		boolean down = client.options.attackKey.isPressed() && client.currentScreen == null;
		if (strikeWatched && down && !attackWasDown) {
			ClientPlayNetworking.send(new VerseStrikeC2SPayload());
		}
		attackWasDown = down;
	}
}
