package craftingismagic.cim.memory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.entity.Entity;

/**
 * What a pony (or player) is feeling right now, read from the emotions on their memories (design doc:
 * The mood system - "mood is carried as emotional memories ... queried when needed, no new stat, the
 * same for players and NPCs"). Nothing ticks: it's worked out when something asks.
 * <p>
 * A feeling lingers by how strong it was: faint fades within about a day, clear
 * over about three, strong over about two weeks - easing out gradually rather than switching off. The
 * memory itself stays; only the feeling it carries fades.
 */
public final class Mood {
	public static final long DAY = 24000L;

	private Mood() {
	}

	/** How strongly each emotion is felt now, 0-1, taking the strongest memory per emotion. */
	public static Map<Emotion, Float> of(Entity entity) {
		long now = entity.getWorld().getTime();
		Map<Emotion, Float> mood = new EnumMap<>(Emotion.class);
		for (Memory memory : MemoryStore.getAll(entity)) {
			if (memory.emotion().isEmpty()) {
				continue;
			}
			float strength = strength(memory, now);
			if (strength > 0f) {
				mood.merge(memory.emotion().get(), strength, Math::max);
			}
		}
		return mood;
	}

	/** How strongly {@code emotion} is felt now, 0-1. */
	public static float of(Entity entity, Emotion emotion) {
		return of(entity).getOrDefault(emotion, 0f);
	}

	/** The strongest feeling right now, if any is felt at all. */
	public static Optional<Map.Entry<Emotion, Float>> dominant(Entity entity) {
		return of(entity).entrySet().stream().max(Map.Entry.comparingByValue());
	}

	/** Whether {@code emotion} is felt at least at {@code tier} (faint, clear, strong). */
	public static boolean feels(Entity entity, Emotion emotion, EmotionIntensity tier) {
		return of(entity, emotion) >= threshold(tier);
	}

	/** The strength a feeling must still have to count as this tier. */
	public static float threshold(EmotionIntensity tier) {
		return switch (tier) {
			case FAINT -> 0.05f;
			case CLEAR -> 0.4f;
			case STRONG -> 0.75f;
		};
	}

	/** What a memory's feeling is worth right now: its intensity, easing out over its lifetime. */
	public static float strength(Memory memory, long now) {
		float peak = switch (memory.intensity()) {
			case FAINT -> 0.35f;
			case CLEAR -> 0.7f;
			case STRONG -> 1.0f;
		};
		long lasts = lifetime(memory.intensity());
		long age = Math.max(0L, now - memory.time());
		if (age >= lasts) {
			return 0f;
		}
		float left = 1f - age / (float) lasts;
		return peak * left * left * (3f - 2f * left); // smoothstep: lingers, then eases away
	}

	public static long lifetime(EmotionIntensity intensity) {
		return switch (intensity) {
			case FAINT -> DAY;
			case CLEAR -> 3 * DAY;
			case STRONG -> 14 * DAY;
		};
	}

	/** "grief 0.82, joy 0.10" - for debug tools. */
	public static String describe(Entity entity) {
		Map<Emotion, Float> mood = of(entity);
		if (mood.isEmpty()) {
			return "calm (nothing felt)";
		}
		StringBuilder text = new StringBuilder();
		mood.entrySet().stream().sorted(Map.Entry.<Emotion, Float>comparingByValue().reversed()).forEach(entry -> {
			if (!text.isEmpty()) {
				text.append(", ");
			}
			text.append(entry.getKey().name().toLowerCase(java.util.Locale.ROOT)).append(String.format(" %.2f", entry.getValue()));
		});
		return text.toString();
	}
}
