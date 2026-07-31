package craftingismagic.cim.client.ability;

import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Something the radial menu can offer and activate. First-pass, generic
 * placeholder shape — species-gating and real ability logic (rather than
 * an icon and a callback) can layer on once there's more than one real
 * ability to hang off it.
 */
public record Ability(Text displayName, ItemStack icon, Consumer<MinecraftClient> onSelect) {
	public void activate(MinecraftClient client) {
		onSelect.accept(client);
	}
}
