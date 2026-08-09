package craftingismagic.cim.rogue;

import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.FriendshipAttachment;
import craftingismagic.cim.villager.PersonalityAttachment;
import craftingismagic.cim.villager.PonyGeneticsAttachment;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;
import craftingismagic.cim.villager.RelationshipType;

/**
 * A wandering hostile pony (README: Mobs, "Rogue Ponies") - villain-flavored, not a vanilla
 * Pillager reskin, and deliberately not boss-tier: the intended base for a future redemption
 * system, not the whole system. Reuses the same {@code Species}/{@code PonyGenetics}/{@code
 * PersonalityAttachment} data model villagers use (all confirmed {@code Entity}-typed, not
 * villager-locked) so it renders as a real ponified mob via {@link
 * craftingismagic.cim.client.rogue.CimRoguePonyRenderer} - but deliberately does NOT get a
 * {@code VillagerNames} name (that pool is friendly/cheerful, "Cherry Cordial" reads as a joke on
 * a hostile mob) or a {@code PonyFamilyAttachment} (life stage/family are villager-only concepts;
 * a Rogue Pony is always effectively an adult).
 * <p>
 * {@link #getTargetPredicate()} is where {@link RelationshipType#FRENEMIES} actually does
 * something: a befriended player is never targeted. Every other player defaults to hostile - the
 * first time a specific player is actually considered as a target, their relationship is recorded
 * as {@link RelationshipType#ENEMY} (if nothing more specific was already set), which is what
 * makes "spawns with the Enemy type" true from first contact without needing to pre-populate a
 * relationship map for players who haven't joined the world yet.
 */
public class RoguePonyEntity extends HostileEntity {
	public RoguePonyEntity(EntityType<? extends RoguePonyEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createRoguePonyAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
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
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true, getTargetPredicate()));
	}

	/** Excludes {@code FRENEMIES}-flagged players from targeting; every other player (including one
	 * never seen before) is fair game, and gets recorded as {@code ENEMY} the moment they're
	 * actually considered here - see the class doc comment. */
	private Predicate<LivingEntity> getTargetPredicate() {
		return candidate -> {
			if (!(candidate instanceof ServerPlayerEntity player)) {
				return true;
			}
			UUID playerId = player.getUuid();
			RelationshipType current = FriendshipAttachment.getRelationshipType(this, playerId);
			if (current == RelationshipType.FRENEMIES) {
				return false;
			}
			if (current == RelationshipType.ACQUAINTANCE) {
				FriendshipAttachment.setRelationshipType(this, playerId, RelationshipType.ENEMY);
			}
			return true;
		};
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData) {
		EntityData result = super.initialize(world, difficulty, spawnReason, entityData);
		rollAppearance();
		return result;
	}

	/** Same randomize calls villagers use ({@code VillagerSpeciesHandler}/{@code
	 * VillagerFamilyHandler}), minus the name-color bias step (no name is assigned here) and minus
	 * species/profession-weighted personality (no profession exists for a hostile mob - uniform
	 * {@link PersonalityAttachment#randomize} is the same shape Changelings already use). */
	private void rollAppearance() {
		Random random = this.getRandom();
		if (!SpeciesAttachment.hasChosenSpecies(this)) {
			Species[] species = Species.values();
			SpeciesAttachment.setSpecies(this, species[random.nextInt(species.length)]);
		}
		if (!PonyGeneticsAttachment.hasGenetics(this)) {
			PonyGenetics genetics = PonyGenetics.randomize(random);
			PonyGeneticsAttachment.setGenetics(this, genetics);
		}
		if (!PersonalityAttachment.hasPersonality(this)) {
			PersonalityAttachment.setPersonality(this, PersonalityAttachment.randomize(random));
		}
	}
}
