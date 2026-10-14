package craftingismagic.cim.narrative;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

/**
 * Who a conversation is between, handed to every condition, effect, and variable. Plain Minecraft
 * types only, so other mods can register vocabulary without depending on this mod's classes.
 *
 * @param player  the player in the conversation
 * @param speaker who they're talking to ({@code {pony}})
 * @param random  the source for every random pick in the conversation
 */
public record TaleContext(ServerPlayerEntity player, Entity speaker, Random random) {
	public ServerWorld world() {
		return player.getServerWorld();
	}
}
