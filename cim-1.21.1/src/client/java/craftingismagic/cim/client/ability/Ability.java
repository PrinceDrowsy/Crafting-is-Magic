package craftingismagic.cim.client.ability;

import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Something the radial menu can offer. Choosing it in the radial menu
 * only *selects* it (see {@link SelectedAbility}) - the actual effect
 * fires separately, on pressing {@link AbilityActivationKeyBinding},
 * whichever ability is currently selected. For a plain instant-effect
 * ability, {@code onActivate} does the whole thing; for something that
 * needs to know about holding/releasing the activation key (like the
 * Earth Pony's charge jump), {@code onActivate} can be a no-op and the
 * ability's own handler can poll {@link SelectedAbility#isSelected} and
 * {@link AbilityActivationKeyBinding#ACTIVATE_ABILITY} directly instead.
 */
public record Ability(Text displayName, ItemStack icon, Consumer<MinecraftClient> onActivate) {
}
