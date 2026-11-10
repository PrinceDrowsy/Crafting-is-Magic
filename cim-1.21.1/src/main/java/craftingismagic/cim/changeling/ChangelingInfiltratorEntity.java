package craftingismagic.cim.changeling;

import java.util.UUID;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import craftingismagic.cim.villager.ChangelingAppearanceAttachment;
import craftingismagic.cim.villager.ChangelingAppearanceAttachment.ChangelingAppearance;

/**
 * A standalone test Changeling (the design doc's Village Changelings are a separate, villager-based
 * system - not touched by this pass, see the Changeling Voice Mimicry plan's note on a future hive/
 * infiltration rework) - proves out shapeshifting into a specific real player plus voice mimicry,
 * inspired by studying the "SkinWalker" mob from SpacePotatoee's MinecraftFoundFootage mod.
 * <p>
 * Unlike that mob, this one doesn't seize the target's own body/camera - it's a separate entity
 * that visually impersonates them (see {@code ChangelingDisguiseApplier}, client-side) while they
 * keep playing normally. Spawned via {@link ChangelingCommand} with an explicit target (mirroring
 * that source mod's own op-only {@code /skinwalker <target>} command) rather than any natural-
 * spawn target-selection heuristic - not built this pass.
 */
public class ChangelingInfiltratorEntity extends HostileEntity {
	/** Fixed test value (30s) - not suspicion-tracked like the source mod, just enough to prove the
	 * disguise/reveal transition out. */
	private static final int REVEAL_TICKS = 600;

	public ChangelingInfiltratorEntity(EntityType<? extends ChangelingInfiltratorEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createChangelingInfiltratorAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.add(2, new WanderAroundFarGoal(this, 1.0));
		this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(4, new LookAroundGoal(this));

		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ChangelingActiveTargetGoal(this));
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData) {
		EntityData result = super.initialize(world, difficulty, spawnReason, entityData);
		ensureAppearance();
		return result;
	}

	/** Rolls a true-form {@link ChangelingAppearance} if this entity doesn't have one yet - a no-op
	 * otherwise. {@link #initialize} only ever fires for a natural-spawn-pipeline creation, which
	 * {@link ChangelingCommand}'s manual create-and-spawn never goes through, so it calls this
	 * directly instead of relying on that callback. */
	public void ensureAppearance() {
		if (!ChangelingAppearanceAttachment.hasAppearance(this)) {
			Random random = this.getRandom();
			ChangelingAppearanceAttachment.setAppearance(this,
					new ChangelingAppearance(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()));
		}
	}

	public boolean isDisguised() {
		return DisguiseTargetAttachment.isDisguised(this);
	}

	public void setDisguiseTarget(UUID targetUuid) {
		DisguiseTargetAttachment.setDisguiseTarget(this, targetUuid);
	}

	/** Ends the disguise and switches to natural (true) form - combat targeting only ever engages
	 * afterward, see {@link ChangelingActiveTargetGoal}. */
	public void reveal() {
		DisguiseTargetAttachment.clearDisguiseTarget(this);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient && isDisguised() && this.age >= REVEAL_TICKS) {
			reveal();
		}
	}

	/** Combat targeting is disabled entirely while disguised - mirrors the source mod's own
	 * {@code SkinWalkerActiveTarget}, which likewise only lets targeting start once revealed. */
	private static final class ChangelingActiveTargetGoal extends ActiveTargetGoal<PlayerEntity> {
		private final ChangelingInfiltratorEntity changeling;

		ChangelingActiveTargetGoal(ChangelingInfiltratorEntity changeling) {
			super(changeling, PlayerEntity.class, true);
			this.changeling = changeling;
		}

		@Override
		public boolean canStart() {
			return !changeling.isDisguised() && super.canStart();
		}
	}
}
