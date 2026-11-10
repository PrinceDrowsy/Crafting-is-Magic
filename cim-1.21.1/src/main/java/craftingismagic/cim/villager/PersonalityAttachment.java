package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;

/**
 * A villager's {@link PersonalityType} base type plus 2-3 {@link Quirk}s (design doc: Personality
 * system) - one value per villager, same shape as {@link PonyGeneticsAttachment}. Assigned
 * alongside genetics in {@link VillagerFamilyHandler}, since like genetics it doesn't depend on
 * family/life-stage at all, just species (and, best-effort, profession).
 */
public final class PersonalityAttachment {
	private static final int MIN_QUIRKS = 2;
	private static final int MAX_QUIRKS = 3;

	/** Soft bias, not an exclusive pick - a biased type is just more likely, per the design doc. */
	private static final float SPECIES_BIAS_WEIGHT = 2.5f;
	/** Stacks multiplicatively on top of any species bias already applied to that same type. */
	private static final float PROFESSION_BIAS_MULTIPLIER = 1.5f;

	/**
	 * design doc: "Pegasi skew Daring/Lazy, Earth Ponies skew Steadfast/Peppy, Unicorns skew
	 * Bookish/Refined, guards skew Steadfast/Cranky, librarians skew Bookish." No {@code GUARD}
	 * profession exists anywhere in vanilla or this mod - only the Librarian half of that sentence
	 * is actually implementable today (see {@link #applyProfessionBias}). Bat Pony/Kirin get no
	 * species bias (not mentioned in the design doc's examples).
	 */
	private static final Map<Species, PersonalityType[]> SPECIES_BIAS = Map.of(
			Species.PEGASUS, new PersonalityType[] {PersonalityType.DARING, PersonalityType.LAZY},
			Species.EARTH_PONY, new PersonalityType[] {PersonalityType.STEADFAST, PersonalityType.PEPPY},
			Species.UNICORN, new PersonalityType[] {PersonalityType.BOOKISH, PersonalityType.REFINED});

	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<Personality> PERSONALITY = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "personality"),
			builder -> builder.persistent(Personality.CODEC)
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath()
					.syncWith(Personality.PACKET_CODEC, SYNC_TO_TRACKERS));

	private PersonalityAttachment() {
	}

	public static boolean hasPersonality(Entity entity) {
		return entity.hasAttached(PERSONALITY);
	}

	public static Personality getPersonality(Entity entity) {
		return entity.getAttached(PERSONALITY);
	}

	public static void setPersonality(Entity entity, Personality personality) {
		entity.setAttached(PERSONALITY, personality);
	}

	/** Changelings roll fully uniform - their outward persona is a performed mask, not innate. */
	public static Personality randomize(Random random) {
		return new Personality(uniformBaseType(random), randomQuirks(random));
	}

	public static Personality randomizeBiased(Random random, Species species, VillagerProfession profession) {
		Map<PersonalityType, Float> weights = new EnumMap<>(PersonalityType.class);
		for (PersonalityType type : PersonalityType.values()) {
			weights.put(type, 1.0f);
		}
		for (PersonalityType biased : SPECIES_BIAS.getOrDefault(species, new PersonalityType[0])) {
			weights.put(biased, SPECIES_BIAS_WEIGHT);
		}
		applyProfessionBias(weights, profession);

		return new Personality(weightedBaseType(random, weights), randomQuirks(random));
	}

	private static void applyProfessionBias(Map<PersonalityType, Float> weights, VillagerProfession profession) {
		if (profession == VillagerProfession.LIBRARIAN) {
			weights.merge(PersonalityType.BOOKISH, PROFESSION_BIAS_MULTIPLIER, (base, multiplier) -> base * multiplier);
		}
	}

	private static PersonalityType uniformBaseType(Random random) {
		PersonalityType[] types = PersonalityType.values();
		return types[random.nextInt(types.length)];
	}

	private static PersonalityType weightedBaseType(Random random, Map<PersonalityType, Float> weights) {
		float totalWeight = 0f;
		for (float weight : weights.values()) {
			totalWeight += weight;
		}
		float roll = random.nextFloat() * totalWeight;
		for (Map.Entry<PersonalityType, Float> entry : weights.entrySet()) {
			roll -= entry.getValue();
			if (roll < 0f) {
				return entry.getKey();
			}
		}
		return PersonalityType.LAZY;
	}

	private static List<Quirk> randomQuirks(Random random) {
		List<Quirk> pool = new ArrayList<>(List.of(Quirk.values()));
		int count = MIN_QUIRKS + random.nextInt(MAX_QUIRKS - MIN_QUIRKS + 1);
		List<Quirk> chosen = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			chosen.add(pool.remove(random.nextInt(pool.size())));
		}
		return chosen;
	}

	public record Personality(PersonalityType baseType, List<Quirk> quirks) {
		public static final Codec<Personality> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				PersonalityType.CODEC.fieldOf("base_type").forGetter(Personality::baseType),
				Quirk.CODEC.listOf().fieldOf("quirks").forGetter(Personality::quirks)
		).apply(instance, Personality::new));

		public static final PacketCodec<ByteBuf, Personality> PACKET_CODEC = PacketCodec.tuple(
				PersonalityType.PACKET_CODEC, Personality::baseType,
				PacketCodecs.collection(ArrayList::new, Quirk.PACKET_CODEC), Personality::quirks,
				Personality::new);
	}
}
