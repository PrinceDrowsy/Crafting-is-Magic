package craftingismagic.cim.item;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

public final class CimItems {
	/** Dev-only: `/give @s cim:sonic_rainboom_debug_stick` - not meant for normal play. */
	public static final Item SONIC_RAINBOOM_DEBUG_STICK = Registry.register(Registries.ITEM,
			Identifier.of(Cim.MOD_ID, "sonic_rainboom_debug_stick"),
			new SonicRainboomDebugStickItem(new Item.Settings().maxCount(1)));

	/** Dev-only: show a teleport's departure, or its arrival, where you look. Not meant for normal play. */
	public static final Item TELEPORT_DEPARTURE_DEBUG_STICK = Registry.register(Registries.ITEM,
			Identifier.of(Cim.MOD_ID, "teleport_departure_debug_stick"),
			new TeleportDebugStickItem(new Item.Settings().maxCount(1), false));
	public static final Item TELEPORT_ARRIVAL_DEBUG_STICK = Registry.register(Registries.ITEM,
			Identifier.of(Cim.MOD_ID, "teleport_arrival_debug_stick"),
			new TeleportDebugStickItem(new Item.Settings().maxCount(1), true));

	/** Dev-only: `/give @s cim:villager_debug_wand` - right-click a villager in Creative to open
	 * the full debug editor (see {@code VillagerDebugEditorHandler}). Not meant for normal play. */
	public static final Item VILLAGER_DEBUG_WAND = Registry.register(Registries.ITEM,
			Identifier.of(Cim.MOD_ID, "villager_debug_wand"),
			new VillagerDebugWandItem(new Item.Settings().maxCount(1)));

	/** Given to a pony you're close to; you get one back in their colors (README: Becoming friends, concretely). */
	public static final Item FRIENDSHIP_BRACELET = Registry.register(Registries.ITEM,
			Identifier.of(Cim.MOD_ID, "friendship_bracelet"),
			new FriendshipBraceletItem(new Item.Settings().maxCount(1)));

	private CimItems() {
	}

	public static void register() {
		// Bracelets sit with the other friendly things, among the tools and utilities.
		net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.TOOLS)
				.register(entries -> entries.add(FRIENDSHIP_BRACELET));
	}
}
