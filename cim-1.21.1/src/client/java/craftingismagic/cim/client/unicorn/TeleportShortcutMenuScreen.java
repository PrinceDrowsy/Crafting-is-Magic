package craftingismagic.cim.client.unicorn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import craftingismagic.cim.client.ability.Ability;
import craftingismagic.cim.client.ability.AbilityActivationKeyBinding;
import craftingismagic.cim.client.ability.RadialMenuScreen;
import craftingismagic.cim.network.SaveTeleportShortcutC2SPayload;
import craftingismagic.cim.network.UseTeleportShortcutC2SPayload;
import craftingismagic.cim.unicorn.TeleportShortcutAttachment;
import craftingismagic.cim.unicorn.TeleportShortcutAttachment.Shortcut;
import craftingismagic.cim.unicorn.TeleportShortcutAttachment.TeleportShortcuts;

/**
 * The Teleport shortcut ring, opened by Shift+activate while Teleport is selected (see {@link
 * TeleportChargeHandler}) - reuses {@link RadialMenuScreen}'s slot-wheel shape, but closes on
 * release of the *activation* key (not the main radial-menu key, since that's not what opened it)
 * and fires its choice immediately instead of just selecting an ability for later. An empty slot
 * saves the player's current position/dimension into it; a filled slot starts a Teleport charge
 * toward it (see {@code TeleportManager#useShortcut}) - skipping the line-of-sight raycast
 * entirely, per the design doc's own shortcut spec.
 */
public final class TeleportShortcutMenuScreen extends RadialMenuScreen {
	public TeleportShortcutMenuScreen() {
		super(buildSlots(), TeleportShortcutMenuScreen::isActivateKeyReleased,
				ability -> ability.onActivate().accept(MinecraftClient.getInstance()));
	}

	private static List<Ability> buildSlots() {
		TeleportShortcuts shortcuts = TeleportShortcutAttachment.get(MinecraftClient.getInstance().player);

		List<Ability> slots = new ArrayList<>();
		for (int i = 0; i < TeleportShortcutAttachment.MAX_SHORTCUTS; i++) {
			int slot = i;
			Optional<Shortcut> saved = shortcuts.get(slot);
			if (saved.isPresent()) {
				slots.add(new Ability(Text.literal("Shortcut " + (slot + 1)), new ItemStack(Items.ENDER_EYE),
						client -> ClientPlayNetworking.send(new UseTeleportShortcutC2SPayload(slot))));
			} else {
				slots.add(new Ability(Text.literal("Save Here"), new ItemStack(Items.ENDER_PEARL),
						client -> ClientPlayNetworking.send(new SaveTeleportShortcutC2SPayload(slot))));
			}
		}
		return slots;
	}

	private static boolean isActivateKeyReleased() {
		InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(AbilityActivationKeyBinding.ACTIVATE_ABILITY);
		return !InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), boundKey.getCode());
	}
}
