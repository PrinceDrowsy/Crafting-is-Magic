package craftingismagic.cim.villager;

import com.google.common.collect.ImmutableSet;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

import craftingismagic.cim.Cim;
import craftingismagic.cim.block.CimBlocks;

/**
 * The mod's own villager professions (design doc: Village identity - new villager professions). The
 * Teacher works at a chalkboard, at the village Schoolhouse; the tutorial they run comes with the
 * Schoolhouse's thread. More (the Mayor, Guards) join as their buildings do.
 */
public final class CimProfessions {
	public static final RegistryKey<PointOfInterestType> CHALKBOARD_POI = RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE,
			Identifier.of(Cim.MOD_ID, "chalkboard"));

	public static VillagerProfession TEACHER;

	private CimProfessions() {
	}

	public static void register() {
		PointOfInterestHelper.register(CHALKBOARD_POI.getValue(), 1, 1, CimBlocks.CHALKBOARD);
		TEACHER = Registry.register(Registries.VILLAGER_PROFESSION, Identifier.of(Cim.MOD_ID, "teacher"),
				new VillagerProfession("teacher", entry -> entry.matchesKey(CHALKBOARD_POI), entry -> entry.matchesKey(CHALKBOARD_POI),
						ImmutableSet.of(), ImmutableSet.of(), SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN));
		// Placeholder trades: school supplies.
		TradeOfferHelper.registerVillagerOffers(TEACHER, 1, factories -> {
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.PAPER, 12), 16, 2, 0.05f));
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.INK_SAC, 4), 16, 2, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(TEACHER, 2, factories -> {
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 3), new ItemStack(Items.WRITABLE_BOOK), 12, 5, 0.05f));
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.BOOK, 2), new ItemStack(Items.EMERALD), 12, 5, 0.05f));
		});
	}
}
