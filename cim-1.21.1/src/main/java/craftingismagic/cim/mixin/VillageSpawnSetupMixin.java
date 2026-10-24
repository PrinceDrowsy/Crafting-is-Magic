package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.level.ServerWorldProperties;

import craftingismagic.cim.spawn.VillageSpawnHandler;

/**
 * Moves a brand-new world's spawn into a village right after vanilla picks its own default, and before
 * vanilla generates the spawn area - so that area (and its progress bar) is the village's (see {@link
 * VillageSpawnHandler}).
 */
@Mixin(MinecraftServer.class)
public abstract class VillageSpawnSetupMixin {
	@Inject(method = "setupSpawn", at = @At("TAIL"))
	private static void cim$spawnInVillage(ServerWorld world, ServerWorldProperties worldProperties, boolean bonusChest,
			boolean debugWorld, CallbackInfo ci) {
		VillageSpawnHandler.relocate(world);
	}
}
