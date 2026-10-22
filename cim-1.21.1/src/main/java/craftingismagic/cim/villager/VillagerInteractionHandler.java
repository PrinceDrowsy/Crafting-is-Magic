package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.item.CimItems;
import craftingismagic.cim.rogue.RoguePonyEntity;

/**
 * Right-clicking a villager starts a conversation directly, instead of vanilla's trade screen - the
 * pony greets you, and gifts and trades are brought up from inside the conversation (README:
 * Conversations replace the interact menu). Only intercepts {@code Hand.MAIN_HAND} since Minecraft
 * dispatches this callback for both hands on a single right-click, which would otherwise start it twice.
 * <p>
 * {@code frozenVillagers} tracks every entity-id/player-uuid pair currently "in conversation" -
 * {@code MobEntity}-typed (not {@code VillagerEntity}) so the same freeze also covers a Rogue Pony
 * held still by the debug editor (see {@link VillagerDebugEditorHandler#open}); a {@code
 * ServerTickEvents} listener actively re-stops navigation and zeroes horizontal velocity on them
 * every tick, unconditionally effective regardless of which internal AI mechanism would otherwise
 * move it (a single {@code setAiDisabled}/{@code hasCustomer()} flag alone wasn't sufficient -
 * disabling AI stops new goals being picked but doesn't cancel an in-progress path). {@code
 * setCustomer} is set too on a {@code VillagerEntity} specifically (that method doesn't exist on a
 * Rogue Pony), both because it's harmless and because "Offer Trade" needs it for the hand-off into
 * vanilla's own trade-session lifecycle. The actual root cause of several failed attempts here
 * turned out to be unrelated to the freeze mechanism at all: {@code VillagerMenuScreen} originally
 * released the freeze from {@code Screen#removed()}, which also fires on routine screen
 * re-initialization (e.g. a window resize/focus event) - releasing the villager seconds into what
 * still looked like an open menu. It now releases from {@code close()} instead (every screen that
 * freezes something follows this same rule - see {@code VillagerDebugEditorScreen} too), which
 * only fires on an intentional close.
 */
public final class VillagerInteractionHandler {
	// Idle Chatter's own Fondness (+5, +3 for the pony's favorite tone) now lives in
	// data/cim/tales/chatter/idle_chatter.tale, run by VillagerDialogue.
	private static final int FAVORITE_GIFT_FONDNESS_BOOST = 20;
	/** A favorite gift answers something the pony told you in conversation (Idle Chatter's closing
	 * line reveals it) - a gift that shows you listened builds Trust, not just Fondness (README:
	 * Friendship, deepened - "Gifts reward thoughtfulness over value"). */
	private static final int FAVORITE_GIFT_TRUST_BOOST = 3;
	private static final int GENERIC_GIFT_FONDNESS_BOOST = 5;
	/** Sanity check on the client-supplied entity id - the player must actually be near it. */
	static final double MAX_INTERACT_DISTANCE_SQUARED = 8.0 * 8.0;

	/** Frozen entity id -> the player currently interacting with it (villager menu/idle chatter, or
	 * the debug editor - see the class doc comment). */
	private static final Map<Integer, UUID> frozenVillagers = new HashMap<>();

	private VillagerInteractionHandler() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (hand != Hand.MAIN_HAND || world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			// One right-click on an entity arrives as two packets: "interact at" (with a hit result) and then
			// "interact". Answering both started every conversation twice - so only the second one counts.
			if (hitResult != null) {
				return ActionResult.PASS;
			}

			// Creative-only debug wand takes over the interaction entirely, bypassing the normal
			// Interact menu/freeze - see VillagerDebugWandItem for why this has to be checked here
			// rather than in an Item#useOnEntity override (this callback would otherwise always
			// intercept first regardless of held item). Widened to RoguePonyEntity too, since the
			// debug editor's Genetics/Personality/Relationship tabs work on it just as well - a
			// normal (non-Creative) right-click on a Rogue Pony intentionally falls through to
			// ActionResult.PASS below instead, since it should never open a peaceful menu.
			if ((entity instanceof VillagerEntity || entity instanceof RoguePonyEntity)
					&& serverPlayer.isCreative() && serverPlayer.getStackInHand(hand).isOf(CimItems.VILLAGER_DEBUG_WAND)) {
				VillagerDebugEditorHandler.open(serverPlayer, entity.getId());
				return ActionResult.SUCCESS;
			}

			if (entity instanceof VillagerEntity villager) {
				// No menu: the pony greets you and the conversation starts; gifts and trades are
				// brought up from there (README: Conversations replace the interact menu).
				freeze(villager, serverPlayer);
				VillagerDialogue.startChatter(serverPlayer, villager.getId());
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
	}

	/** Whether {@code entity} is held still in a conversation (or the debug editor) right now. */
	public static boolean isInConversation(Entity entity) {
		return frozenVillagers.containsKey(entity.getId());
	}

	public static void tick(MinecraftServer server) {
		if (frozenVillagers.isEmpty()) {
			return;
		}
		frozenVillagers.entrySet().removeIf(entry -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getValue());
			if (player == null) {
				return true;
			}
			if (player.getServerWorld().getEntityById(entry.getKey()) instanceof MobEntity mob) {
				Vec3d velocity = mob.getVelocity();
				mob.getNavigation().stop();
				mob.setVelocity(0, velocity.y, 0);
				return false;
			}
			return true;
		});
	}

	public static void offerTrade(ServerPlayerEntity player, int villagerEntityId) {
		VillagerEntity villager = resolveNearbyVillager(player, villagerEntityId);
		if (villager != null) {
			// Stop actively freezing it (vanilla trading manages its own stillness via the
			// customer field, no need to keep re-stopping navigation during a real trade), but
			// leave the customer set - interactMob relies on it, and it'll reset the AI flag itself.
			frozenVillagers.remove(villager.getId());
			villager.setAiDisabled(false);
			villager.interactMob(player, Hand.MAIN_HAND);
		}
	}

	/** Whether {@code pony} will take another gift from {@code player} today (there's a daily limit). */
	public static boolean canTakeGift(ServerPlayerEntity player, Entity pony) {
		return FriendshipAttachment.canGiftToday(pony, player.getUuid(), player.getServerWorld().getTime() / 24000L);
	}

	public static boolean isFavoriteGift(Entity pony, ItemStack gift) {
		return FavoriteGiftAttachment.hasFavorite(pony) && gift.getItem() == FavoriteGiftAttachment.getFavorite(pony);
	}

	/**
	 * {@code pony} takes one of {@code gift} from {@code player} - the {@code ~ take_gift} effect, once
	 * the gift conversation has had the pony react. Fondness grows, more for their favorite (which
	 * also earns some Trust). Does nothing past the daily limit; the conversation checks
	 * {@code can_take_gift} first so the pony can say so in their own words.
	 */
	public static void takeGift(ServerPlayerEntity player, Entity pony, ItemStack gift) {
		long today = player.getServerWorld().getTime() / 24000L;
		if (gift.isEmpty() || !FriendshipAttachment.canGiftToday(pony, player.getUuid(), today)) {
			return;
		}
		boolean isFavorite = isFavoriteGift(pony, gift);
		ItemStack keepsake = gift.copyWithCount(1);
		gift.decrement(1);
		// Sometimes a friend keeps it somewhere at home - a keepsake you might notice later.
		if (pony instanceof VillagerEntity villager) {
			craftingismagic.cim.structure.Keepsakes.offer(villager, player, keepsake, isFavorite ? 0.8f : 0.5f);
		}
		// A habit, tapered by the gifts already given today (README: friendship pacing).
		FriendshipAttachment.RelationshipData data = FriendshipAttachment.get(pony, player.getUuid());
		int giftsBefore = data.lastGiftDay() == today ? data.giftsGivenThatDay() : 0;
		FriendshipPace.habit(pony, player, Feeling.FONDNESS, isFavorite ? FAVORITE_GIFT_FONDNESS_BOOST : GENERIC_GIFT_FONDNESS_BOOST, giftsBefore);
		if (isFavorite) {
			FriendshipPace.habit(pony, player, Feeling.TRUST, FAVORITE_GIFT_TRUST_BOOST, giftsBefore);
		}
		FriendshipAttachment.recordGift(pony, player.getUuid(), today);
		FriendshipAttachment.addSharedMoments(pony, player.getUuid(), 3);
	}

	/**
	 * Deliberately not distance-gated like the other actions: this only releases a hold the entity
	 * is already under, so it needs to work even if the player moved far away in the meantime (e.g.
	 * via Teleport) before closing the menu/editor - otherwise it would be stuck facing them
	 * forever. Checks {@code frozenVillagers} itself (who's actually holding it) rather than {@code
	 * VillagerEntity#getCustomer()}, since that method doesn't exist on a Rogue Pony at all.
	 */
	public static void stopInteracting(ServerPlayerEntity player, int villagerEntityId) {
		VillagerDialogue.end(player);
		if (player.getUuid().equals(frozenVillagers.get(villagerEntityId))
				&& player.getServerWorld().getEntityById(villagerEntityId) instanceof MobEntity mob) {
			unfreeze(mob);
		}
	}

	/** Releases every entity a disconnecting player was holding still, so none stay frozen forever. */
	public static void cancel(ServerPlayerEntity player) {
		VillagerDialogue.end(player);
		frozenVillagers.entrySet().removeIf(entry -> {
			if (!entry.getValue().equals(player.getUuid())) {
				return false;
			}
			if (player.getServerWorld().getEntityById(entry.getKey()) instanceof MobEntity mob) {
				if (mob instanceof VillagerEntity villager) {
					villager.setCustomer(null);
				}
				mob.setAiDisabled(false);
			}
			return true;
		});
	}

	/** Package-visible so {@link VillagerDebugEditorHandler#open} can hold a Rogue Pony/villager
	 * still the same way the normal Interact menu does. */
	static void freeze(MobEntity mob, ServerPlayerEntity player) {
		if (mob instanceof VillagerEntity villager) {
			villager.setCustomer(player);
		}
		mob.setAiDisabled(true);
		frozenVillagers.put(mob.getId(), player.getUuid());
	}

	private static void unfreeze(MobEntity mob) {
		if (mob instanceof VillagerEntity villager) {
			villager.setCustomer(null);
		}
		mob.setAiDisabled(false);
		frozenVillagers.remove(mob.getId());
	}

	static VillagerEntity resolveNearbyVillager(ServerPlayerEntity player, int villagerEntityId) {
		Entity entity = player.getServerWorld().getEntityById(villagerEntityId);
		if (entity instanceof VillagerEntity villager && villager.squaredDistanceTo(player) <= MAX_INTERACT_DISTANCE_SQUARED) {
			return villager;
		}
		return null;
	}
}
