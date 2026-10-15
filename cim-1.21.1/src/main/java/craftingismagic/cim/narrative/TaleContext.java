package craftingismagic.cim.narrative;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
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
 * @param item    the item the conversation is about - a gift being offered - or empty. It's the real
 *                stack in the player's inventory, so an effect that takes it takes it from them.
 */
public record TaleContext(ServerPlayerEntity player, Entity speaker, Random random, ItemStack item) {
	public TaleContext(ServerPlayerEntity player, Entity speaker, Random random) {
		this(player, speaker, random, ItemStack.EMPTY);
	}

	public ServerWorld world() {
		return player.getServerWorld();
	}
}
