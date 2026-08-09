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

	/** Dev-only: `/give @s cim:villager_debug_wand` - right-click a villager in Creative to open
	 * the full debug editor (see {@code VillagerDebugEditorHandler}). Not meant for normal play. */
	public static final Item VILLAGER_DEBUG_WAND = Registry.register(Registries.ITEM,
			Identifier.of(Cim.MOD_ID, "villager_debug_wand"),
			new VillagerDebugWandItem(new Item.Settings().maxCount(1)));

	private CimItems() {
	}

	public static void register() {
	}
}
