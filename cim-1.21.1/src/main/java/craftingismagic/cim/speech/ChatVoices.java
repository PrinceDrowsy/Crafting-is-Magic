package craftingismagic.cim.speech;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import craftingismagic.cim.network.ChatVoiceS2CPayload;

/**
 * When a player chats, everyone nearby (them included) hears a quiet murmur in their speaking voice,
 * from where they stand (design doc: Speaking voices). Only the "who and how much" crosses the network -
 * each client plays it, so anyone can switch player voices off for themselves.
 */
public final class ChatVoices {
	/** Players this far away (blocks) still hear the murmur, faintly. */
	public static final double RANGE = 24;

	private ChatVoices() {
	}

	public static void register() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> murmur(sender, message.getContent().getString()));
	}

	/** {@code speaker} - a player chatting, or a pony speaking aloud - is heard nearby in their own voice. */
	public static void murmur(Entity speaker, String text) {
		int letters = (int) text.chars().filter(Character::isLetterOrDigit).count();
		if (letters == 0 || !(speaker.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ChatVoiceS2CPayload payload = new ChatVoiceS2CPayload(speaker.getId(), SpeakingVoices.heard(speaker), letters);
		for (ServerPlayerEntity listener : PlayerLookup.around(world, speaker.getPos(), RANGE)) {
			ServerPlayNetworking.send(listener, payload);
		}
	}
}
