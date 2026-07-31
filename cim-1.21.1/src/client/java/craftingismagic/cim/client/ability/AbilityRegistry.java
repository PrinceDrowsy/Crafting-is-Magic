package craftingismagic.cim.client.ability;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import craftingismagic.cim.client.levitation.LevitationClientState;
import craftingismagic.cim.network.PlaceLevitatedBlockC2SPayload;
import craftingismagic.cim.network.StartLevitationC2SPayload;
import craftingismagic.cim.species.Species;

/**
 * The abilities the radial menu offers, per species. Species without any
 * real ability content yet fall back to a shared placeholder set, just to
 * prove out the menu with more than one wedge — real per-species lists
 * replace these as abilities get built.
 */
public final class AbilityRegistry {
	/**
	 * onActivate is a no-op here on purpose: the charge jump needs to know
	 * about the activation key being *held*, not just pressed, so its
	 * whole behavior lives in EarthPonyJumpHandler's own tick-based poll
	 * of {@link SelectedAbility#isSelected} and
	 * {@link AbilityActivationKeyBinding#ACTIVATE_ABILITY} instead.
	 */
	public static final Ability CHARGED_JUMP = new Ability(Text.literal("Charged Jump"), new ItemStack(Items.FEATHER),
			client -> {
			});

	/**
	 * Press while empty-handed to start a block selection (hold and scroll
	 * to grow it, release to lock it in - handled by
	 * LevitationSelectionHandler, not here, since that also cares about
	 * key *release*). Press again while holding to place or throw it (the
	 * server decides which based on leftover scroll momentum, unless
	 * Shift is held too, which always forces a gentle placement). No-op
	 * while actively selecting, since releasing is what matters there.
	 */
	public static final Ability LEVITATION = new Ability(Text.literal("Levitation"), new ItemStack(Items.ENDER_PEARL),
			client -> {
				switch (LevitationClientState.get()) {
					case NONE -> ClientPlayNetworking.send(new StartLevitationC2SPayload());
					case HOLDING -> ClientPlayNetworking.send(
							new PlaceLevitatedBlockC2SPayload(client.options.sneakKey.isPressed()));
					case SELECTING -> {
					}
				}
			});

	private static final List<Ability> PLACEHOLDER_ABILITIES = List.of(
			new Ability(Text.literal("Test Ability A"), new ItemStack(Items.DIAMOND),
					client -> client.player.sendMessage(Text.literal("Test Ability A activated"), true)),
			new Ability(Text.literal("Test Ability B"), new ItemStack(Items.EMERALD),
					client -> client.player.sendMessage(Text.literal("Test Ability B activated"), true)));

	private AbilityRegistry() {
	}

	public static List<Ability> abilitiesFor(Species species) {
		List<Ability> abilities = new ArrayList<>();
		if (species == Species.EARTH_PONY) {
			abilities.add(CHARGED_JUMP);
		}
		if (species == Species.UNICORN) {
			abilities.add(LEVITATION);
		}
		abilities.addAll(PLACEHOLDER_ABILITIES);
		return abilities;
	}
}
