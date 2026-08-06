package craftingismagic.cim.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;

import craftingismagic.cim.network.IdleChatterReactionS2CPayload;
import craftingismagic.cim.network.OpenIdleChatterS2CPayload;
import craftingismagic.cim.network.OpenVillagerMenuS2CPayload;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;

/**
 * Right-clicking a villager opens our own Interact menu directly, instead of vanilla's trade
 * screen - the menu itself offers "Offer Trade" as one of its options to reach vanilla trading.
 * Only intercepts {@code Hand.MAIN_HAND} since Minecraft dispatches this callback for both hands
 * on a single right-click, which would otherwise open the menu twice.
 * <p>
 * {@code frozenVillagers} tracks every villager-id/player-uuid pair currently "in conversation";
 * a {@code ServerTickEvents} listener actively re-stops navigation and zeroes horizontal velocity
 * on them every tick, unconditionally effective regardless of which internal AI mechanism would
 * otherwise move it (a single {@code setAiDisabled}/{@code hasCustomer()} flag alone wasn't
 * sufficient - disabling AI stops new goals being picked but doesn't cancel an in-progress path).
 * {@code setCustomer} is set too, both because it's harmless and because "Offer Trade" needs it for
 * the hand-off into vanilla's own trade-session lifecycle. The actual root cause of several failed
 * attempts here turned out to be unrelated to the freeze mechanism at all: {@code
 * VillagerMenuScreen} originally released the freeze from {@code Screen#removed()}, which also
 * fires on routine screen re-initialization (e.g. a window resize/focus event) - releasing the
 * villager seconds into what still looked like an open menu. It now releases from {@code close()}
 * instead, which only fires on an intentional close.
 */
public final class VillagerInteractionHandler {
	private static final int IDLE_CHATTER_AFFECTION_BOOST = 5;
	private static final int IDLE_CHATTER_TONE_MATCH_BONUS = 3;
	private static final int FAVORITE_GIFT_AFFECTION_BOOST = 20;
	private static final int GENERIC_GIFT_AFFECTION_BOOST = 5;
	/** Sanity check on the client-supplied entity id - the player must actually be near it. */
	private static final double MAX_INTERACT_DISTANCE_SQUARED = 8.0 * 8.0;

	/** Villager entity id -> the player currently interacting with it. */
	private static final Map<Integer, UUID> frozenVillagers = new HashMap<>();

	private VillagerInteractionHandler() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (hand == Hand.MAIN_HAND && !world.isClient && entity instanceof VillagerEntity villager
					&& player instanceof ServerPlayerEntity serverPlayer) {
				int affection = FriendshipAttachment.getAffection(villager, player.getUuid());
				boolean canTrade = villager.getVillagerData().getProfession() != VillagerProfession.NONE;
				freeze(villager, serverPlayer);
				ServerPlayNetworking.send(serverPlayer,
						new OpenVillagerMenuS2CPayload(villager.getId(), affection, canTrade, villager.getName().getString()));
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
			if (player.getServerWorld().getEntityById(entry.getKey()) instanceof VillagerEntity villager) {
				Vec3d velocity = villager.getVelocity();
				villager.getNavigation().stop();
				villager.setVelocity(0, velocity.y, 0);
				return false;
			}
			return true;
		});
	}

	/**
	 * Opens the conversation with a topic pick + opening line only - no affection change yet, that
	 * moves to {@link #replyToIdleChatter} once the player actually picks a reply tone, so no pick is
	 * ever penalized (README: "No pick is ever penalized; a mismatch just doesn't earn the bonus.").
	 */
	public static void idleChatter(ServerPlayerEntity player, int villagerEntityId) {
		VillagerEntity villager = resolveNearbyVillager(player, villagerEntityId);
		if (villager == null) {
			return;
		}

		Species species = SpeciesAttachment.hasChosenSpecies(villager) ? SpeciesAttachment.getSpecies(villager) : Species.EARTH_PONY;
		Personality personality = resolvePersonality(villager);
		Random random = villager.getRandom();

		IdleChatterTopic topic = IdleChatterDialogue.pickTopic(species, personality, random);
		String openingLine = IdleChatterDialogue.openingLine(villager, topic, species, player.getServerWorld(), random);
		ServerPlayNetworking.send(player,
				new OpenIdleChatterS2CPayload(villagerEntityId, villager.getName().getString(), openingLine));
	}

	/**
	 * Grants the base affection boost, plus a match bonus if the picked tone lines up with the
	 * pony's {@link PersonalityType#toneAffinity()} - the concrete mechanical hook behind the
	 * Personality system (README: Personality system, Mechanical hook).
	 */
	public static void replyToIdleChatter(ServerPlayerEntity player, int villagerEntityId, ReplyTone tone) {
		VillagerEntity villager = resolveNearbyVillager(player, villagerEntityId);
		if (villager == null) {
			return;
		}

		Personality personality = resolvePersonality(villager);
		boolean matched = personality.baseType().toneAffinity().map(affinity -> affinity == tone).orElse(false);
		int boost = IDLE_CHATTER_AFFECTION_BOOST + (matched ? IDLE_CHATTER_TONE_MATCH_BONUS : 0);
		FriendshipAttachment.addAffection(villager, player.getUuid(), boost);

		Random random = villager.getRandom();
		String reactionLine = IdleChatterDialogue.reactionLine(personality, tone, matched, random);
		String closingLine = IdleChatterDialogue.closingLine(villager);
		ServerPlayNetworking.send(player, new IdleChatterReactionS2CPayload(reactionLine, closingLine));
	}

	/** Falls back to a fresh uniform roll for a pre-existing villager that predates this system,
	 * same "needs a fresh villager to see the real thing" caveat as genetics - not persisted here,
	 * just keeps a stale/missing attachment from throwing. */
	private static Personality resolvePersonality(VillagerEntity villager) {
		return PersonalityAttachment.hasPersonality(villager)
				? PersonalityAttachment.getPersonality(villager)
				: PersonalityAttachment.randomize(villager.getRandom());
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
		FriendshipAttachment.addAffection(villager, player.getUuid(), isFavorite ? FAVORITE_GIFT_AFFECTION_BOOST : GENERIC_GIFT_AFFECTION_BOOST);
		FriendshipAttachment.recordGift(villager, player.getUuid(), today);

		player.sendMessage(Text.translatable(isFavorite ? "message.cim.gift_favorite" : "message.cim.gift_generic"), true);
	}

	/**
	 * Deliberately not distance-gated like the other actions: this only releases a hold the
	 * villager is already under, so it needs to work even if the player moved far away in the
	 * meantime (e.g. via Teleport) before closing the menu - otherwise the villager would be stuck
	 * facing them forever.
	 */
	public static void stopInteracting(ServerPlayerEntity player, int villagerEntityId) {
		Entity entity = player.getServerWorld().getEntityById(villagerEntityId);
		if (entity instanceof VillagerEntity villager && villager.getCustomer() == player) {
			unfreeze(villager);
		}
	}

	/** Releases every villager a disconnecting player was holding still, so none stay frozen forever. */
	public static void cancel(ServerPlayerEntity player) {
		frozenVillagers.entrySet().removeIf(entry -> {
			if (!entry.getValue().equals(player.getUuid())) {
				return false;
			}
			if (player.getServerWorld().getEntityById(entry.getKey()) instanceof VillagerEntity villager) {
				villager.setCustomer(null);
				villager.setAiDisabled(false);
			}
			return true;
		});
	}

	private static void freeze(VillagerEntity villager, ServerPlayerEntity player) {
		villager.setCustomer(player);
		villager.setAiDisabled(true);
		frozenVillagers.put(villager.getId(), player.getUuid());
	}

	private static void unfreeze(VillagerEntity villager) {
		villager.setCustomer(null);
		villager.setAiDisabled(false);
		frozenVillagers.remove(villager.getId());
	}

	private static VillagerEntity resolveNearbyVillager(ServerPlayerEntity player, int villagerEntityId) {
		Entity entity = player.getServerWorld().getEntityById(villagerEntityId);
		if (entity instanceof VillagerEntity villager && villager.squaredDistanceTo(player) <= MAX_INTERACT_DISTANCE_SQUARED) {
			return villager;
		}
		return null;
	}
}
