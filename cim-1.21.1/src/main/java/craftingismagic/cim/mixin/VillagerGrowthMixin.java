package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.passive.VillagerEntity;

import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;

/**
 * Vanilla's own baby-villager growth timer fires {@code onGrowUp} once a baby's breeding age
 * reaches 0, turning it into a full vanilla adult. Rather than block that (this mod deliberately
 * lets Child/Teenager stay full vanilla adults underneath - profession/trading gating is a
 * separate, deferred concern, see README), this lets vanilla's own transition happen normally and
 * then layers our own Child stage on top of it, per the README's "Child and Teenager are new
 * stages layered on top once vanilla's own growth timer would otherwise have made them an adult."
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerGrowthMixin {
	@Inject(method = "onGrowUp", at = @At("TAIL"))
	private void cim$promoteBabyToChild(CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		if (!PonyFamilyAttachment.hasFamily(self)) {
			return;
		}
		PonyFamily family = PonyFamilyAttachment.getFamily(self);
		if (family.lifeStage() != LifeStage.BABY) {
			return;
		}
		PonyFamilyAttachment.setFamily(self, new PonyFamily(LifeStage.CHILD, 0, family.parents(), family.partner(), family.orphan()));
	}
}
