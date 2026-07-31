package craftingismagic.cim.pegasus;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Plain {@code Entity#setVelocity} never reaches a real player's own
 * client: the controlling client runs its own local movement/physics
 * simulation and only reports position back to the server, so a server-
 * side velocity change is silently discarded unless explicitly pushed
 * down. (Vanilla hits this same requirement for knockback, explosions,
 * etc. - {@code Entity#addVelocity} and damage code flag it for sync,
 * but a bare {@code setVelocity} call does not.) Anything here that
 * forces a real player's velocity - the dash lock/taper, the sprint-
 * triggered active-flight launch - needs to call this right after.
 */
public final class PegasusVelocitySync {
	private PegasusVelocitySync() {
	}

	public static void push(ServerPlayerEntity player) {
		player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
	}
}
