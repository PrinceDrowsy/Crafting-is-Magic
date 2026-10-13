package craftingismagic.cim.debug;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The one gate every debug tool goes through (docs/implementation-plan.md: Debug and testing tools):
 * the server's {@link DebugConfig} switch must be on, and the player must be in Creative or have op
 * level 2. Always checked server-side - a client's own gating is never trusted.
 */
public final class CimDebug {
	private CimDebug() {
	}

	public static boolean allowed(ServerPlayerEntity player) {
		return DebugConfig.get().enabled && (player.isCreative() || player.hasPermissionLevel(2));
	}

	/** Commands use op level 2 alone (the vanilla cheats level), plus the server switch. */
	public static boolean allowed(ServerCommandSource source) {
		return DebugConfig.get().enabled && source.hasPermissionLevel(2);
	}
}
