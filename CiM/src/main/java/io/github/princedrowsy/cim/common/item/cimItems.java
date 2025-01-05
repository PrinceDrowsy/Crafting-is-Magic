package io.github.princedrowsy.cim.common.item;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import io.github.princedrowsy.cim.cim;

public class cimItems {
	public static final Item TESTSTICK = registerItem("teststick", new Item(new FabricItemSettings()));

	private static void addItemsToIngredientItemGroup(FabricItemGroupEntries entries) {
		entries.addItem(TESTSTICK);
	}

	private static Item registerItem(String name, Item item) {
		return Registry.register(Registries.ITEM, new Identifier(cim.MOD_ID, name), item);
	}

	public static void registerModItems() {
		cim.LOGGER.info("Registering Mod Items for " + cim.MOD_ID);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(cimItems::addItemsToIngredientItemGroup);
	}
}
