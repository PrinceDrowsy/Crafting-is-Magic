package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;

import craftingismagic.cim.item.CimItems;
import craftingismagic.cim.network.OpenVillagerMenuS2CPayload;
import craftingismagic.cim.rogue.RoguePonyEntity;

/**
 * Right-clicking a villager opens our own Interact menu directly, instead of vanilla's trade
 * screen - the menu itself offers "Offer Trade" as one of its options to reach vanilla trading.
 * Only intercepts {@code Hand.MAIN_HAND} since Minecraft dispatches this callback for both hands
 * on a single right-click, which would otherwise open the menu twice.
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
				boolean canTrade = villager.getVillagerData().getProfession() != VillagerProfession.NONE;
				freeze(villager, serverPlayer);
				ServerPlayNetworking.send(serverPlayer,
						new OpenVillagerMenuS2CPayload(villager.getId(), canTrade, villager.getName().getString()));
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
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

	public static void giveGift(ServerPlayerEntity player, int villagerEntityId) {
		VillagerEntity villager = resolveNearbyVillager(player, villagerEntityId);
		if (villager == null) {
			return;
		}

		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			player.sendMessage(Text.translatable("message.cim.gift_empty_hand"), true);
			return;
		}

		long today = player.getServerWorld().getTime() / 24000L;
		if (!FriendshipAttachment.canGiftToday(villager, player.getUuid(), today)) {
			player.sendMessage(Text.translatable("message.cim.gift_cap_reached"), true);
			return;
		}

		boolean isFavorite = FavoriteGiftAttachment.hasFavorite(villager)
				&& held.getItem() == FavoriteGiftAttachment.getFavorite(villager);

		held.decrement(1);
		FriendshipAttachment.addFeeling(villager, player.getUuid(), Feeling.FONDNESS,
				isFavorite ? FAVORITE_GIFT_FONDNESS_BOOST : GENERIC_GIFT_FONDNESS_BOOST);
		if (isFavorite) {
			FriendshipAttachment.addFeeling(villager, player.getUuid(), Feeling.TRUST, FAVORITE_GIFT_TRUST_BOOST);
		}
		FriendshipAttachment.recordGift(villager, player.getUuid(), today);

		player.sendMessage(Text.translatable(isFavorite ? "message.cim.gift_favorite" : "message.cim.gift_generic"), true);
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
