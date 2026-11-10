package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

import craftingismagic.cim.speech.SpeakingVoices;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyKinship;
import craftingismagic.cim.villager.VillagerFamilyHandler;

/**
 * Vanilla villager breeding ({@code VillagerBreedTask}) builds the baby via {@code createChild},
 * then sets its breeding age, then spawns it - so hooking {@code createChild}'s return lands before
 * the baby's own {@code ENTITY_LOAD}, letting {@link VillagerFamilyHandler#assignBredChild} give it
 * its real parents instead of the proximity guesswork a freshly-generated villager gets. Targets
 * the {@code VillagerEntity}-returning overload explicitly, not the synthetic {@code
 * PassiveEntity}-returning bridge method.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerBreedingMixin {
	/** Vanilla's own post-breeding cooldown for each parent - see {@code VillagerBreedTask}. */
	private static final int BREEDING_COOLDOWN_TICKS = 6000;

	/**
	 * Hard rule: related ponies never breed (see {@link PonyKinship}). Returning null is vanilla's
	 * own "no baby" path - {@code VillagerBreedTask} just skips spawning - but that path also skips
	 * the parents' cooldown, which would leave the pair re-attempting every time they meet, so the
	 * cooldown is applied here instead.
	 */
	@Inject(method = "createChild(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/PassiveEntity;)Lnet/minecraft/entity/passive/VillagerEntity;",
			at = @At("HEAD"), cancellable = true)
	private void cim$refuseRelatives(ServerWorld world, PassiveEntity partner, CallbackInfoReturnable<VillagerEntity> cir) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		// Hard rules: only Adults ever pair, and related ponies never do. (A safety net - non-adults
		// never become ready to breed in the first place; see cim$adultsOnly.)
		if (partner instanceof VillagerEntity other
				&& (!isAdult(self) || !isAdult(other) || PonyKinship.areRelated(world, self, other))) {
			self.setBreedingAge(BREEDING_COOLDOWN_TICKS);
			other.setBreedingAge(BREEDING_COOLDOWN_TICKS);
			cir.setReturnValue(null);
		}
	}

	/**
	 * Hard rule: only Adults pair (see the content rules). The mod's Child and
	 * Teenager stages are vanilla adults (only Baby is a vanilla baby), so without this vanilla would
	 * let them breed. A non-Adult pony is simply never ready to breed - it won't seek a partner or take
	 * breeding food.
	 */
	@Inject(method = "isReadyToBreed", at = @At("RETURN"), cancellable = true)
	private void cim$adultsOnly(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !isAdult((VillagerEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/** A pony with no family record yet is treated as an Adult, as vanilla villagers always were. */
	private static boolean isAdult(VillagerEntity pony) {
		return !PonyFamilyAttachment.hasFamily(pony) || PonyFamilyAttachment.getFamily(pony).lifeStage() == LifeStage.ADULT;
	}

	@Inject(method = "createChild(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/PassiveEntity;)Lnet/minecraft/entity/passive/VillagerEntity;",
			at = @At("RETURN"))
	private void cim$inheritFromParents(ServerWorld world, PassiveEntity partner, CallbackInfoReturnable<VillagerEntity> cir) {
		VillagerEntity child = cir.getReturnValue();
		if (child != null) {
			VillagerFamilyHandler.assignBredChild(child, (VillagerEntity) (Object) this, partner);
			// Both parents are right here, so the foal's voice takes after them now (design doc: Speaking voices).
			SpeakingVoices.inheritAtBirth(child, (VillagerEntity) (Object) this, partner);
		}
	}
}
