package craftingismagic.cim.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The mod's debug-tools creative tab (docs/implementation-plan.md: Debug and testing tools). It's
 * the only tab for now, since none of the mod's real content is an item yet - a separate main tab
 * arrives with the first real items, so debug tools never mix with them. */
public final class CimItemGroups {
	public static final ItemGroup DEBUG = Registry.register(Registries.ITEM_GROUP,
			Identifier.of(Cim.MOD_ID, "debug"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.cim.debug"))
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
