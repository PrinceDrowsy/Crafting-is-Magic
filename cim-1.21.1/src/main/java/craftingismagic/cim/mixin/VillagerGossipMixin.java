package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

import craftingismagic.cim.villager.VillagerGossip;

/**
 * Ponies pass on their stories when they stop to chat - the moment vanilla villagers trade their own
 * gossip, so ours travels at the same natural pace, between ponies who are really together.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerGossipMixin {
	@Inject(method = "talkWithVillager", at = @At("HEAD"))
	private void cim$shareStories(ServerWorld world, VillagerEntity villager, long time, CallbackInfo ci) {
		VillagerGossip.chat((VillagerEntity) (Object) this, villager, world);
	}
}
