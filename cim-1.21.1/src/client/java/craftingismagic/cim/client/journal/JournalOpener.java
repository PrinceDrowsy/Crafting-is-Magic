package craftingismagic.cim.client.journal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;

import craftingismagic.cim.client.gui.JournalScreen;
import craftingismagic.cim.species.SpeciesAttachment;

/** Opens {@link JournalScreen} on a fresh press of {@link JournalKeyBinding#OPEN_JOURNAL}. */
public final class JournalOpener {
	private JournalOpener() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientPlayerEntity player = client.player;
			while (player != null && client.currentScreen == null
					&& SpeciesAttachment.hasChosenSpecies(player) && JournalKeyBinding.OPEN_JOURNAL.wasPressed()) {
				client.setScreen(new JournalScreen(JournalScreen.Tab.ABILITIES));
			}
		});
	}
}
