package craftingismagic.cim.villager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

/**
 * The one kinship check every pairing system goes through - the mod has a hard rule that nobody,
 * NPC or player, can pair romantically or breed with a relative. Two ponies count as related if
 * their lineages (self, parents, grandparents) overlap at all, which covers parent/child,
 * (half-)siblings, grandparent/grandchild, aunt-or-uncle/niece-or-nephew, and first cousins in one
 * rule.
 * <p>
 * Best-effort past the first generation: parents are stored on each pony directly, but
 * grandparents are read off the parents' own records, so a parent who's unloaded right now can't
 * contribute theirs. Any future off-screen ancestry data should feed into {@link #lineage} too, so
 * the check covers generated relatives, not just loaded entities.
 */
public final class PonyKinship {
	private PonyKinship() {
	}

	public static boolean areRelated(ServerWorld world, Entity a, Entity b) {
		Set<UUID> lineageA = lineage(world, a);
		for (UUID ancestor : lineage(world, b)) {
			if (lineageA.contains(ancestor)) {
				return true;
			}
		}
		return false;
	}

	private static Set<UUID> lineage(ServerWorld world, Entity pony) {
		Set<UUID> lineage = new HashSet<>();
		lineage.add(pony.getUuid());
		if (!PonyFamilyAttachment.hasFamily(pony)) {
			return lineage;
		}
		for (UUID parentUuid : PonyFamilyAttachment.getFamily(pony).parents()) {
			lineage.add(parentUuid);
			Entity parent = world.getEntity(parentUuid);
			if (parent != null && PonyFamilyAttachment.hasFamily(parent)) {
				lineage.addAll(PonyFamilyAttachment.getFamily(parent).parents());
			}
		}
		return lineage;
	}
}
