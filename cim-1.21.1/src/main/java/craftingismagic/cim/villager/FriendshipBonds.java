package craftingismagic.cim.villager;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.item.CimItems;
import craftingismagic.cim.item.FriendshipBraceletItem;
import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;

/**
 * Friend and best friend as real statuses on top of the three feelings (design doc: Becoming friends,
 * concretely; Affection, reworked).
 * <ul>
 * <li><b>Friend:</b> the player gives a Friendship Bracelet, and it's only taken once the pony is close
 * enough - high Fondness and some Trust. The player gets one of their own back, named for the
 * friend and beaded in their mane and coat colors.</li>
 * <li><b>Best friend:</b> all three feelings high <i>and</i> at least one major moment lived through
 * together (a shared hardship) - rare, like the Mane Six. The pony recognizes it themselves.</li>
 * </ul>
 * Thresholds are first-pass, to tune in play.
 */
public final class FriendshipBonds {
	public static final Identifier BECAME_FRIENDS = Identifier.of("cim", "became_friends");
	public static final Identifier BEST_FRIENDS = Identifier.of("cim", "best_friends");

	private static final FeelingBand FRIEND_FONDNESS = FeelingBand.HIGH;
	private static final FeelingBand FRIEND_TRUST = FeelingBand.LOW;
	private static final FeelingBand BEST_FRIEND_ALL = FeelingBand.HIGH;

	private FriendshipBonds() {
	}

	public static boolean isFriend(Entity pony, UUID player) {
		RelationshipType type = FriendshipAttachment.getRelationshipType(pony, player);
		return type == RelationshipType.FRIEND || type == RelationshipType.BEST_FRIEND;
	}

	/** Close enough to take a Friendship Bracelet - and not already friends. */
	public static boolean friendReady(Entity pony, UUID player) {
		return FriendshipAttachment.getRelationshipType(pony, player) == RelationshipType.ACQUAINTANCE
				&& atLeast(pony, player, Feeling.FONDNESS, FRIEND_FONDNESS) && atLeast(pony, player, Feeling.TRUST, FRIEND_TRUST);
	}

	/** Friends with all three feelings high, who've lived through something major together. */
	public static boolean bestFriendReady(Entity pony, UUID player) {
		if (FriendshipAttachment.getRelationshipType(pony, player) != RelationshipType.FRIEND) return false;
		for (Feeling feeling : Feeling.values()) {
			if (!atLeast(pony, player, feeling, BEST_FRIEND_ALL)) return false;
		}
		return MemoryStore.about(pony, player).stream()
				.anyMatch(memory -> memory.knowledge() == MemoryKnowledge.WITNESSED && memory.importance().compareTo(MemoryImportance.MAJOR) >= 0
						&& !memory.type().equals(BECAME_FRIENDS) && !HarmAndForgiveness.isHarm(memory));
	}

	public static boolean isBracelet(ItemStack stack) {
		return stack.isOf(CimItems.FRIENDSHIP_BRACELET) && stack.get(FriendshipBraceletItem.FRIEND) == null;
	}

	/**
	 * The pony takes the bracelet and you're friends: they remember it, and the player gets their own
	 * bracelet back, in the friend's colors. Does nothing if they aren't ready (the conversation checks
	 * {@code friend_ready} first, so the pony can say so).
	 */
	public static void befriend(ServerPlayerEntity player, Entity pony, ItemStack bracelet) {
		if (!isBracelet(bracelet) || !friendReady(pony, player.getUuid())) return;
		ItemStack given = bracelet.copyWithCount(1);
		bracelet.decrement(1);
		FriendshipAttachment.setRelationshipType(pony, player.getUuid(), RelationshipType.FRIEND);
		// The bracelet you gave them is the first keepsake of the friendship.
		if (pony instanceof net.minecraft.entity.passive.VillagerEntity villager) {
			craftingismagic.cim.structure.Keepsakes.offer(villager, player, given, 1f);
		}
		MemoryStore.add(pony, new Memory(BECAME_FRIENDS, Optional.of(player.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.NOTABLE,
				Optional.of(Emotion.JOY), EmotionIntensity.STRONG, player.getWorld().getTime()));
		ItemStack theirs = new ItemStack(CimItems.FRIENDSHIP_BRACELET);
		int[] colors = colors(pony);
		theirs.set(FriendshipBraceletItem.FRIEND, new FriendshipBraceletItem.Friend(pony.getUuid(), pony.getName().getString(), colors[0], colors[1]));
		if (!player.getInventory().insertStack(theirs)) player.dropItem(theirs, false);
	}

	/** The pony names it: you're their best friend. Something they'll never forget. */
	public static void bestFriends(ServerPlayerEntity player, Entity pony) {
		if (!bestFriendReady(pony, player.getUuid())) return;
		FriendshipAttachment.setRelationshipType(pony, player.getUuid(), RelationshipType.BEST_FRIEND);
		MemoryStore.add(pony, new Memory(BEST_FRIENDS, Optional.of(player.getUuid()), MemoryKnowledge.WITNESSED, MemoryImportance.INDELIBLE,
				Optional.of(Emotion.LOVE), EmotionIntensity.STRONG, player.getWorld().getTime()));
	}

	/** {beads, band}: the pony's mane and coat, roughly as their genetics paint them; pink and white otherwise. */
	private static int[] colors(Entity pony) {
		if (!PonyGeneticsAttachment.hasGenetics(pony)) {
			return new int[] {FriendshipBraceletItem.PLAIN_BEADS, FriendshipBraceletItem.PLAIN_BAND};
		}
		PonyGenetics genetics = PonyGeneticsAttachment.getGenetics(pony);
		int mane = MathHelper.hsvToRgb(genetics.mane().huePrimary(), genetics.mane().vividness() * 0.7f, 0.85f);
		int coat = MathHelper.hsvToRgb(genetics.coat().hue(), genetics.coat().vividness() * 0.45f, 0.95f);
		return new int[] {mane, coat};
	}

	private static boolean atLeast(Entity pony, UUID player, Feeling feeling, FeelingBand band) {
		return FriendshipAttachment.getBand(pony, player, feeling).ordinal() >= band.ordinal();
	}
}
