package craftingismagic.cim.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The mod's own creative-inventory tab - currently just the dev-only debug items, since none of
 * the mod's other content (abilities, genetics, etc.) is obtained as a placeable/holdable item. */
public final class CimItemGroups {
	public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP,
			Identifier.of(Cim.MOD_ID, "main"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.cim.main"))
					.icon(() -> new ItemStack(CimItems.VILLAGER_DEBUG_WAND))
					.entries((displayContext, entries) -> {
						entries.add(CimItems.VILLAGER_DEBUG_WAND);
						entries.add(CimItems.SONIC_RAINBOOM_DEBUG_STICK);
					})
					.build());

	private CimItemGroups() {
	}

	public static void register() {
	}
}
