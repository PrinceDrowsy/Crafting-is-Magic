package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.FriendGroupAttachment.FriendGroup;

/**
 * Assigns each freshly-loaded villager a place in a real friend circle (see Friend groups in the
 * design doc - "a pony can belong to an existing circle of friends, mirroring the show's Mane
 * Six-style groups"), the same proximity-based, per-villager, no-batch-signal approach
 * {@link VillagerFamilyHandler} already uses for families (see that class's own doc comment for why
 * there's no "these villagers spawned together" event to hook instead).
 * <p>
 * Unlike Family, a friend group has no asymmetric roles (no parent/child, no partner) - it's a flat
 * clique, so joining one means writing the new member into every existing member's own list, not
 * just one other entity's. That fan-out only ever touches whichever members happen to be loaded
 * right now; both directions of every link are derived from that same loaded set (see
 * {@link #tryJoinFriendGroup}), so the symmetric invariant (A friends B implies B friends A) can
 * never be left half-written even though a full family-style repair pass isn't attempted here - a
 * clique member who's off wandering at the exact moment someone else joins just isn't linked to the
 * newcomer, the same "wandered off, won't show up" honesty {@link PonyFamilyAttachment} already
 * accepts for children/siblings.
 */
public final class VillagerFriendGroupHandler {
	/** Mane Six - the show's own reference point for "a friend circle", named directly in the
	 * design doc's Friend groups bullet. */
	private static final int MAX_GROUP_SIZE = 6;
	private static final double FRIEND_GROUP_SEARCH_RADIUS = 28.0;

	/** Below this size, joining a candidate circle is unconditional. Once a candidate already has
	 * at least this many members, a newcomer only joins it with {@link #JOIN_BEYOND_PREFERRED_CHANCE}
	 * probability - otherwise it founds its own circle instead. This compounds per join (reaching
	 * five members needs the roll to succeed twice, six needs it three times), so three-pony circles
	 * end up the most common landing size and five/six-pony circles end up only slightly rarer, not
	 * a hard cutoff. */
	private static final int PREFERRED_GROUP_SIZE = 3;
	private static final float JOIN_BEYOND_PREFERRED_CHANCE = 0.85f;

	private VillagerFriendGroupHandler() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity villager && !FriendGroupAttachment.hasGroup(villager)) {
				assignFriendGroup(villager, world);
			}
		});
	}

	/** Changelings always found their own solo group rather than attempting to join one - same
	 * shape as {@link VillagerFamilyHandler#assignChangeling}, which never calls its own
	 * try-join-family method either. Left joinable BY others without a species check, matching the
	 * same depth of handling Family already has (its own open-parent/open-partner searches are
	 * species-blind too) - not a new inconsistency introduced here. */
	private static void assignFriendGroup(VillagerEntity villager, ServerWorld world) {
		Species species = SpeciesAttachment.hasChosenSpecies(villager) ? SpeciesAttachment.getSpecies(villager) : Species.EARTH_PONY;
		if (species == Species.CHANGELING || !tryJoinFriendGroup(villager, world)) {
			FriendGroupAttachment.setGroup(villager, new FriendGroup(List.of()));
		}
	}

	/**
	 * Both directions of the new link are derived from the exact same loaded-only membership set -
	 * seeding the joiner's own list from the anchor's full STORED membership (which can include
	 * unloaded ponies) while only writing the reverse link to currently-loaded members would produce
	 * a silently asymmetric, unrepairable friend list. Restricting both sides to what's loaded right
	 * now keeps "A friends B iff B friends A" true by construction.
	 */
	private static boolean tryJoinFriendGroup(VillagerEntity self, ServerWorld world) {
		VillagerEntity anchor = findOpenGroupMember(self, world);
		if (anchor == null) {
			return false;
		}

		int anchorSize = FriendGroupAttachment.getGroup(anchor).members().size() + 1;
		if (anchorSize >= PREFERRED_GROUP_SIZE && self.getRandom().nextFloat() > JOIN_BEYOND_PREFERRED_CHANCE) {
			return false;
		}

		List<UUID> loadedMembership = new ArrayList<>();
		loadedMembership.add(anchor.getUuid());
		for (UUID memberId : FriendGroupAttachment.getGroup(anchor).members()) {
			if (world.getEntity(memberId) instanceof VillagerEntity) {
				loadedMembership.add(memberId);
			}
		}
		FriendGroupAttachment.setGroup(self, new FriendGroup(loadedMembership));

		for (UUID memberId : loadedMembership) {
			VillagerEntity member = (VillagerEntity) world.getEntity(memberId);
			List<UUID> updated = new ArrayList<>(FriendGroupAttachment.getGroup(member).members());
			updated.add(self.getUuid());
			FriendGroupAttachment.setGroup(member, new FriendGroup(updated));
		}
		return true;
	}

	private static VillagerEntity findOpenGroupMember(VillagerEntity self, ServerWorld world) {
		Box searchBox = new Box(self.getBlockPos()).expand(FRIEND_GROUP_SEARCH_RADIUS);
		List<VillagerEntity> candidates = world.getEntitiesByType(TypeFilter.instanceOf(VillagerEntity.class), searchBox,
				other -> other != self && FriendGroupAttachment.hasGroup(other)
						&& FriendGroupAttachment.getGroup(other).members().size() + 1 < MAX_GROUP_SIZE);
		return candidates.isEmpty() ? null : candidates.get(0);
	}
}
