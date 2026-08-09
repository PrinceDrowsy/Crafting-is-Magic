package craftingismagic.cim.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Dev-only debug tool (not meant to ship in a real playthrough, same spirit as
 * {@link SonicRainboomDebugStickItem}): right-clicking a villager while holding this in Creative
 * mode opens the full villager debug editor instead of the normal Interact menu. The actual
 * interception happens in {@code VillagerInteractionHandler} (its existing {@code
 * UseEntityCallback} already intercepts every villager right-click unconditionally, so the wand
 * check has to live there rather than in an {@code Item#useOnEntity} override, which would never
 * get a chance to run) - this class only exists for the tooltip/identity, not the logic itself.
 */
public class VillagerDebugWandItem extends Item {
	public VillagerDebugWandItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.cim.villager_debug_wand.tooltip").formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
