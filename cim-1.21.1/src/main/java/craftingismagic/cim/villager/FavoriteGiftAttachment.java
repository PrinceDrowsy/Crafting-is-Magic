package craftingismagic.cim.villager;

import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.Cim;

/**
 * A villager's favorite gift item - one value per villager (unlike affection, this isn't
 * per-player), assigned once from a small curated pool. Revealed to the player via Idle Chatter;
 * giving it grants a bigger affection boost than a generic gift (see
 * {@link VillagerInteractionHandler}).
 */
public final class FavoriteGiftAttachment {
	/** A deliberately small, clearly pony-flavored pool rather than "any item in the game". */
	private static final Item[] GIFT_POOL = {
			Items.APPLE, Items.GOLDEN_APPLE, Items.WHEAT, Items.HAY_BLOCK,
			Items.POPPY, Items.SUNFLOWER, Items.EMERALD, Items.DIAMOND,
			Items.AMETHYST_SHARD, Items.CAKE, Items.HONEY_BOTTLE, Items.SWEET_BERRIES
	};

	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<Item> FAVORITE_GIFT = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "favorite_gift"),
			builder -> builder.persistent(Registries.ITEM.getCodec())
					.syncWith(PacketCodecs.registryValue(RegistryKeys.ITEM), SYNC_TO_TRACKERS));

	private FavoriteGiftAttachment() {
	}

	public static Item randomGift(Random random) {
		return GIFT_POOL[random.nextInt(GIFT_POOL.length)];
	}

	/** Exposed for the Creative-only villager debug editor's Favorite Gift cycling control -
	 * normal gameplay only ever needs {@link #randomGift}. */
	public static List<Item> giftPool() {
		return List.of(GIFT_POOL);
	}

	public static boolean hasFavorite(Entity entity) {
		return entity.hasAttached(FAVORITE_GIFT);
	}

	public static Item getFavorite(Entity entity) {
		return entity.getAttached(FAVORITE_GIFT);
	}

	public static void setFavorite(Entity entity, Item item) {
		entity.setAttached(FAVORITE_GIFT, item);
	}
}
