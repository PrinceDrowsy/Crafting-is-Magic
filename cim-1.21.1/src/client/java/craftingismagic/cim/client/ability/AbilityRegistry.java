package craftingismagic.cim.client.ability;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * The abilities currently offered by the radial menu. Flat placeholder
 * list for this first pass, proving out the open/hover/select/activate
 * flow — species-gating and real per-species ability lists come once
 * there's enough real ability content to justify it.
 */
public final class AbilityRegistry {
	public static final List<Ability> ABILITIES = List.of(
			new Ability(Text.literal("Charged Jump"), new ItemStack(Items.FEATHER),
					client -> client.player.sendMessage(Text.literal("Charged Jump selected"), true)),
			new Ability(Text.literal("Test Ability A"), new ItemStack(Items.DIAMOND),
					client -> client.player.sendMessage(Text.literal("Test Ability A selected"), true)),
			new Ability(Text.literal("Test Ability B"), new ItemStack(Items.EMERALD),
					client -> client.player.sendMessage(Text.literal("Test Ability B selected"), true)),
			new Ability(Text.literal("Test Ability C"), new ItemStack(Items.AMETHYST_SHARD),
					client -> client.player.sendMessage(Text.literal("Test Ability C selected"), true)));

	private AbilityRegistry() {
	}
}
