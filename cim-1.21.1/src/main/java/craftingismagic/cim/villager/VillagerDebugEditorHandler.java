package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.network.OpenVillagerDebugEditorS2CPayload;
import craftingismagic.cim.rogue.RoguePonyEntity;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;

/**
 * Server side of the Creative-only villager debug editor (see {@link
 * craftingismagic.cim.item.VillagerDebugWandItem}) - lets a developer directly edit a villager's
 * (or, since {@link #resolveEntity} also accepts {@link RoguePonyEntity}, a Rogue Pony's) name,
 * species, life stage, full genetics, personality, the requesting player's own affection/
 * relationship type, and favorite gift. Life stage/family are villager-only concepts - a Rogue
 * Pony is always treated as a fixed Adult with no {@link PonyFamilyAttachment}, guarded by
 * {@code instanceof VillagerEntity} checks below rather than pretending it applies. Deliberately
 * out of scope: vanilla profession/trades (a vanilla concern, not this mod's own identity systems)
 * and editing family *links* to other villagers (would need a villager-picker UI, a bigger lift
 * than this pass). The "Villager" naming across this class/its payloads predates Rogue Pony
 * support and is kept as-is rather than a disruptive rename - it now covers both.
 * <p>
 * Every handler here re-checks {@link ServerPlayerEntity#isCreative()} independently rather than
 * trusting the client to only send these packets from Creative mode - the item/screen gating is a
 * convenience, not the actual security boundary.
 */
public final class VillagerDebugEditorHandler {
	private static final double MAX_EDIT_DISTANCE_SQUARED = 8.0 * 8.0;

	private VillagerDebugEditorHandler() {
	}

	public static void open(ServerPlayerEntity player, int villagerEntityId) {
		MobEntity entity = resolveEntity(player, villagerEntityId);
		if (entity == null) {
			return;
		}

		// Same hold-still mechanism the normal Interact menu uses - see VillagerInteractionHandler's
		// class doc comment for why a tick-based re-stop is needed instead of just setAiDisabled.
		// Released by VillagerDebugEditorScreen#close() sending the same StopVillagerInteractionC2SPayload
		// the normal menu already uses, handled by the same VillagerInteractionHandler#stopInteracting.
		VillagerInteractionHandler.freeze(entity, player);

		Random random = entity.getRandom();
		if (!PonyGeneticsAttachment.hasGenetics(entity)) {
			PonyGenetics genetics = PonyGenetics.applyNameColorBias(PonyGenetics.randomize(random), random, entity.getName().getString());
			PonyGeneticsAttachment.setGenetics(entity, genetics);
		}
		if (!PersonalityAttachment.hasPersonality(entity)) {
			PersonalityAttachment.setPersonality(entity, PersonalityAttachment.randomize(random));
		}
		if (entity instanceof VillagerEntity && !FavoriteGiftAttachment.hasFavorite(entity)) {
			FavoriteGiftAttachment.setFavorite(entity, FavoriteGiftAttachment.randomGift(random));
		}

		ServerWorld world = player.getServerWorld();
		Species species = SpeciesAttachment.hasChosenSpecies(entity) ? SpeciesAttachment.getSpecies(entity) : Species.EARTH_PONY;
		OpenVillagerDebugEditorS2CPayload.Identity identity = new OpenVillagerDebugEditorS2CPayload.Identity(
				entity.getName().getString(), species, lifeStageOf(entity), growthTicksOf(entity));
		OpenVillagerDebugEditorS2CPayload.Relationship relationship = new OpenVillagerDebugEditorS2CPayload.Relationship(
				FriendshipAttachment.getAffection(entity, player.getUuid()),
				FriendshipAttachment.getRelationshipType(entity, player.getUuid()),
				FavoriteGiftAttachment.hasFavorite(entity) ? FavoriteGiftAttachment.getFavorite(entity) : Items.APPLE,
				buildFriendGroupSummary(entity, world));

		ServerPlayNetworking.send(player, new OpenVillagerDebugEditorS2CPayload(
				villagerEntityId, identity,
				PonyGeneticsAttachment.getGenetics(entity),
				PersonalityAttachment.getPersonality(entity),
				relationship,
				buildFamilySummary(entity, world)));
	}

	/** {@code FAMILY_VIEW_RADIUS} is deliberately wider than {@code
	 * VillagerFamilyHandler#FAMILY_SEARCH_RADIUS} (28.0) - this is a debug/inspection tool, not a
	 * gameplay mechanic, so casting a wider net to actually find wandered-off relatives is worth
	 * the extra search cost in a way it wouldn't be for real spawn-time family assignment. */
	private static final double FAMILY_VIEW_RADIUS = 64.0;

	private static OpenVillagerDebugEditorS2CPayload.Family buildFamilySummary(Entity entity, ServerWorld world) {
		if (!(entity instanceof VillagerEntity villager) || !PonyFamilyAttachment.hasFamily(villager)) {
			return new OpenVillagerDebugEditorS2CPayload.Family(List.of(), "", List.of(), List.of(), false);
		}

		PonyFamily family = PonyFamilyAttachment.getFamily(villager);
		List<String> parentNames = new ArrayList<>();
		for (UUID parentId : family.parents()) {
			parentNames.add(resolveRelativeName(world, parentId));
		}
		String partnerName = family.partner().isEmpty() ? "" : resolveRelativeName(world, family.partner().get(0));

		UUID selfId = villager.getUuid();
		Box searchBox = new Box(villager.getBlockPos()).expand(FAMILY_VIEW_RADIUS);
		List<String> childrenNames = new ArrayList<>();
		List<String> siblingNames = new ArrayList<>();
		for (VillagerEntity other : world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox,
				candidate -> candidate != villager && PonyFamilyAttachment.hasFamily(candidate))) {
			PonyFamily otherFamily = PonyFamilyAttachment.getFamily(other);
			if (otherFamily.parents().contains(selfId)) {
				childrenNames.add(other.getName().getString());
			} else if (!family.parents().isEmpty() && otherFamily.parents().stream().anyMatch(family.parents()::contains)) {
				siblingNames.add(other.getName().getString());
			}
		}

		return new OpenVillagerDebugEditorS2CPayload.Family(parentNames, partnerName, childrenNames, siblingNames, family.orphan());
	}

	/** Same "not around right now" fallback {@code IdleChatterDialogue} already uses for this exact
	 * situation (a real linked relative whose chunk just isn't loaded) - distinct from genuinely
	 * having no parent/partner at all, which shows as an empty list/string instead. */
	private static String resolveRelativeName(ServerWorld world, UUID uuid) {
		return world.getEntity(uuid) instanceof VillagerEntity relative ? relative.getName().getString() : "(not around right now)";
	}

	/** Unlike {@link #buildFamilySummary}, no proximity scan is needed - {@link FriendGroupAttachment}
	 * stores membership bidirectionally by construction (see {@link VillagerFriendGroupHandler}), so
	 * this just resolves the already-stored member UUIDs to names. */
	private static List<String> buildFriendGroupSummary(Entity entity, ServerWorld world) {
		if (!(entity instanceof VillagerEntity villager) || !FriendGroupAttachment.hasGroup(villager)) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (UUID memberId : FriendGroupAttachment.getGroup(villager).members()) {
			names.add(resolveRelativeName(world, memberId));
		}
		return names;
	}

	/** Villagers auto-assign a real family/life stage on load ({@code VillagerFamilyHandler}), so
	 * this only ever creates one on demand here for a Rogue Pony-adjacent edge case that shouldn't
	 * normally happen; Rogue Ponies never get one at all - always reported as a fixed {@code ADULT}. */
	private static LifeStage lifeStageOf(Entity entity) {
		if (entity instanceof VillagerEntity villager) {
			if (!PonyFamilyAttachment.hasFamily(villager)) {
				PonyFamilyAttachment.setFamily(villager, new PonyFamily(LifeStage.ADULT, 0, List.of(), List.of(), false));
			}
			return PonyFamilyAttachment.getFamily(villager).lifeStage();
		}
		return LifeStage.ADULT;
	}

	private static int growthTicksOf(Entity entity) {
		if (entity instanceof VillagerEntity villager && PonyFamilyAttachment.hasFamily(villager)) {
			return PonyFamilyAttachment.getFamily(villager).growthTicks();
		}
		return 0;
	}

	public static void setName(ServerPlayerEntity player, int villagerEntityId, String name) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity != null && !name.isBlank()) {
			entity.setCustomName(Text.literal(name));
			entity.setCustomNameVisible(true);
		}
	}

	public static void setSpecies(ServerPlayerEntity player, int villagerEntityId, Species species) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity != null) {
			SpeciesAttachment.setSpecies(entity, species);
		}
	}

	/** No-op on a Rogue Pony - it has no {@link PonyFamilyAttachment}/life stage concept at all. */
	public static void setLifeStage(ServerPlayerEntity player, int villagerEntityId, LifeStage lifeStage, int growthTicks) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (!(entity instanceof VillagerEntity villager)) {
			return;
		}
		PonyFamily existing = PonyFamilyAttachment.hasFamily(villager)
				? PonyFamilyAttachment.getFamily(villager)
				: new PonyFamily(LifeStage.ADULT, 0, List.of(), List.of(), false);
		PonyFamilyAttachment.setFamily(villager,
				new PonyFamily(lifeStage, growthTicks, existing.parents(), existing.partner(), existing.orphan()));
		// Keeps vanilla's own baby/adult flag consistent with the mod's life stage - the growth
		// mixin only re-fires onGrowUp() while breedingAge is negative (see VillagerGrowthMixin),
		// same convention VillagerFamilyHandler#tryJoinFamily already uses for a real Baby.
		villager.setBreedingAge(lifeStage == LifeStage.BABY ? -LifeStageConfig.get().babyDurationTicks() : 0);
	}

	public static void setGenetics(ServerPlayerEntity player, int villagerEntityId, PonyGenetics genetics) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity != null) {
			PonyGeneticsAttachment.setGenetics(entity, genetics);
		}
	}

	public static void setPersonality(ServerPlayerEntity player, int villagerEntityId, Personality personality) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity != null) {
			PersonalityAttachment.setPersonality(entity, personality);
		}
	}

	public static void setAffection(ServerPlayerEntity player, int villagerEntityId, int affection) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity != null) {
			FriendshipAttachment.setAffection(entity, player.getUuid(), affection);
		}
	}

	public static void setRelationshipType(ServerPlayerEntity player, int villagerEntityId, RelationshipType relationshipType) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity != null) {
			FriendshipAttachment.setRelationshipType(entity, player.getUuid(), relationshipType);
		}
	}

	/** No-op on a Rogue Pony - {@link FavoriteGiftAttachment} stays a villager-only, Give-Gift-flavored concept. */
	public static void setFavoriteGift(ServerPlayerEntity player, int villagerEntityId, Item favoriteGift) {
		Entity entity = resolveEntity(player, villagerEntityId);
		if (entity instanceof VillagerEntity villager) {
			FavoriteGiftAttachment.setFavorite(villager, favoriteGift);
		}
	}

	private static MobEntity resolveEntity(ServerPlayerEntity player, int entityId) {
		if (!player.isCreative()) {
			return null;
		}
		Entity entity = player.getServerWorld().getEntityById(entityId);
		if ((entity instanceof VillagerEntity || entity instanceof RoguePonyEntity)
				&& entity.squaredDistanceTo(player) <= MAX_EDIT_DISTANCE_SQUARED) {
			return (MobEntity) entity;
		}
		return null;
	}
}
