package craftingismagic.cim.memory;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * One memory (README: The memory system). Every memory answers the same questions - what happened,
 * who it's about, how the holder knows it, how much it matters, how it felt, and when - so one shape
 * serves Shared Memories, gossip, heritage, mood, and Changeling suspicion alike.
 *
 * @param type      what happened, as a namespaced ID (e.g. {@code cim:shared_grief}) so datapacks and
 *                  other mods can add their own kinds
 * @param subject   who it's about, if anyone - usually the player or another pony
 * @param knowledge witnessed, heard, or legend
 * @param importance how much it matters, which decides what gets forgotten
 * @param emotion   how it felt, if it carried a feeling - what the mood system reads
 * @param intensity how strongly; only meaningful with an emotion
 * @param time      the world time (total game ticks) it was formed, for "last spring" and for fading
 */
public record Memory(Identifier type, Optional<UUID> subject, MemoryKnowledge knowledge, MemoryImportance importance,
		Optional<Emotion> emotion, EmotionIntensity intensity, long time) {

	/** Every field past {@code type} is optional with a default, so records saved by an earlier
	 * version never fail to load (see [[project_fabric_attachment_gotchas]]). */
	public static final Codec<Memory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("type").forGetter(Memory::type),
			Uuids.CODEC.optionalFieldOf("subject").forGetter(Memory::subject),
			MemoryKnowledge.CODEC.optionalFieldOf("knowledge", MemoryKnowledge.WITNESSED).forGetter(Memory::knowledge),
			MemoryImportance.CODEC.optionalFieldOf("importance", MemoryImportance.NOTABLE).forGetter(Memory::importance),
			Emotion.CODEC.optionalFieldOf("emotion").forGetter(Memory::emotion),
			EmotionIntensity.CODEC.optionalFieldOf("intensity", EmotionIntensity.CLEAR).forGetter(Memory::intensity),
			Codec.LONG.optionalFieldOf("time", 0L).forGetter(Memory::time)
	).apply(instance, Memory::new));

	public boolean isAbout(UUID who) {
		return subject.isPresent() && subject.get().equals(who);
	}
}
