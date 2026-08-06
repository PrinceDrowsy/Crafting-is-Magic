package craftingismagic.cim.villager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;

/**
 * Idle Chatter's topic selection and line content (README: "Idle Chatter, in more detail"). Scope
 * call: only {@link IdleChatterTopic#WEATHER} and {@link IdleChatterTopic#HOMELAND_FAMILY} have
 * real backing data today (live weather/biome, and {@link PonyFamilyAttachment}); the other 6
 * topics are flavor-only line pools, personality-weighted at the topic-pick stage but not
 * exhaustively written per personality type at the line level - each pool is topic-level, not a
 * full topic x base-type cross product, to keep the content pass a reasonable size. Village
 * Gossip/Current Event/Friendship & Relationships/Hobbies & Interests/Cutie Mark Story would each
 * need a whole new backing system (a gossip network, holidays, friend groups, mentor/mentee) that's
 * a separate future thread, not this one.
 */
public final class IdleChatterDialogue {
	private static final float TOPIC_BASE_WEIGHT = 1.0f;
	private static final float TOPIC_BIAS_WEIGHT = 3.0f;

	private IdleChatterDialogue() {
	}

	/**
	 * Weighted by base type/quirks per the README's own examples (Gossip quirk favors Village
	 * Gossip, Bookish favors Cutie Mark Story/Hobbies & Interests, Lazy favors Weather). Changelings
	 * never land on Cutie Mark Story - they don't have one, so its weight is zeroed rather than
	 * re-rolled after the fact.
	 */
	public static IdleChatterTopic pickTopic(Species species, Personality personality, Random random) {
		Map<IdleChatterTopic, Float> weights = new EnumMap<>(IdleChatterTopic.class);
		for (IdleChatterTopic topic : IdleChatterTopic.values()) {
			weights.put(topic, TOPIC_BASE_WEIGHT);
		}

		if (personality.quirks().contains(Quirk.GOSSIP)) {
			weights.merge(IdleChatterTopic.VILLAGE_GOSSIP, TOPIC_BIAS_WEIGHT, Float::sum);
		}
		if (personality.baseType() == PersonalityType.BOOKISH) {
			weights.merge(IdleChatterTopic.CUTIE_MARK_STORY, TOPIC_BIAS_WEIGHT, Float::sum);
			weights.merge(IdleChatterTopic.HOBBIES_INTERESTS, TOPIC_BIAS_WEIGHT, Float::sum);
		}
		if (personality.baseType() == PersonalityType.LAZY) {
			weights.merge(IdleChatterTopic.WEATHER, TOPIC_BIAS_WEIGHT, Float::sum);
		}

		if (species == Species.CHANGELING) {
			weights.put(IdleChatterTopic.CUTIE_MARK_STORY, 0f);
		}

		return weightedPick(random, weights);
	}

	private static IdleChatterTopic weightedPick(Random random, Map<IdleChatterTopic, Float> weights) {
		float total = 0f;
		for (float weight : weights.values()) {
			total += weight;
		}
		float roll = random.nextFloat() * total;
		for (Map.Entry<IdleChatterTopic, Float> entry : weights.entrySet()) {
			roll -= entry.getValue();
			if (roll < 0f) {
				return entry.getKey();
			}
		}
		return IdleChatterTopic.WEATHER;
	}

	public static String openingLine(VillagerEntity villager, IdleChatterTopic topic, Species species, ServerWorld world, Random random) {
		return switch (topic) {
			case WEATHER -> weatherLine(villager, world);
			case HOMELAND_FAMILY -> homelandFamilyLine(villager, world);
			case WORK_CRAFT -> pick(random, workCraftLines(villager, species));
			case VILLAGE_GOSSIP -> pick(random, VILLAGE_GOSSIP_LINES);
			case HOBBIES_INTERESTS -> pick(random, HOBBIES_INTERESTS_LINES);
			case CURRENT_EVENT -> pick(random, CURRENT_EVENT_LINES);
			case FRIENDSHIP_RELATIONSHIPS -> pick(random, FRIENDSHIP_LINES);
			case CUTIE_MARK_STORY -> pick(random, CUTIE_MARK_STORY_LINES);
		};
	}

	private static String weatherLine(VillagerEntity villager, ServerWorld world) {
		if (world.isThundering()) {
			return "Some storm out there, huh? I saw a flash light up the whole sky earlier.";
		}
		if (world.isRaining()) {
			return "Bit of a rainy one today. Good for the crops, at least.";
		}
		return biomeFlavor(world, villager);
	}

	private static String biomeFlavor(ServerWorld world, VillagerEntity villager) {
		RegistryEntry<Biome> biome = world.getBiome(villager.getBlockPos());
		if (biome.matchesKey(BiomeKeys.FOREST) || biome.matchesKey(BiomeKeys.DARK_FOREST)
				|| biome.matchesKey(BiomeKeys.BIRCH_FOREST) || biome.matchesKey(BiomeKeys.FLOWER_FOREST)
				|| biome.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST)) {
			return "Clear skies today. The woods nearby have been quiet - almost too quiet, if you ask me.";
		}
		if (biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP)) {
			return "Clear day, for once - the swamp doesn't smell half as strange when the sun's out.";
		}
		if (biome.matchesKey(BiomeKeys.RIVER) || biome.matchesKey(BiomeKeys.BEACH)) {
			return "Beautiful day by the falls. The water's practically sparkling.";
		}
		if (biome.matchesKey(BiomeKeys.SAVANNA) || biome.matchesKey(BiomeKeys.PLAINS) || biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)) {
			return "Good clear day for the orchards. Can't ask for better weather than this.";
		}
		return "Not a cloud in the sky today. Can't complain about that.";
	}

	private static List<String> workCraftLines(VillagerEntity villager, Species species) {
		VillagerProfession profession = villager.getVillagerData().getProfession();
		if (profession != VillagerProfession.NONE) {
			String professionName = profession.id().replace('_', ' ');
			return List.of("Keeping busy with the " + professionName + " work, same as always.",
					"Never a dull moment being the village " + professionName + ".");
		}
		return switch (species) {
			case EARTH_PONY -> List.of("Been out plowing and hauling all morning. Good honest work.");
			case UNICORN -> List.of("Practicing a bit of spellwork when I get the chance. Still got a ways to go.");
			case PEGASUS -> List.of("Been up clearing clouds. Someone's got to keep the sky tidy.");
			case BAT_PONY -> List.of("Mostly keep to the night shift. Suits me fine - less fuss that way.");
			case KIRIN -> List.of("Just keeping to my own quiet routine today. Helps keep the temper in check.");
			case CHANGELING -> List.of("Oh, you know - odd jobs here and there. Whatever needs doing.");
		};
	}

	private static String homelandFamilyLine(VillagerEntity villager, ServerWorld world) {
		if (!PonyFamilyAttachment.hasFamily(villager)) {
			return "Never really knew my family, to be honest. This village is home enough for me now.";
		}
		PonyFamily family = PonyFamilyAttachment.getFamily(villager);
		if (family.orphan()) {
			return "I don't have any family of my own around here. Still, the village looks out for its own.";
		}
		if (!family.parents().isEmpty()) {
			String name = resolveFamilyName(world, family.parents().get(0));
			return name != null
					? "Actually, my family's right here in the village - " + name + " raised me."
					: "My family's around here somewhere, though I haven't seen them in a while.";
		}
		if (!family.partner().isEmpty()) {
			String name = resolveFamilyName(world, family.partner().get(0));
			return name != null
					? name + " and I settled down together right here in the village."
					: "I've got someone special in the village, though they're not around right now.";
		}
		return "No family to speak of just yet - maybe someday.";
	}

	private static String resolveFamilyName(ServerWorld world, UUID uuid) {
		if (world.getEntity(uuid) instanceof VillagerEntity relative) {
			return relative.getName().getString();
		}
		return null;
	}

	public static String reactionLine(Personality personality, ReplyTone tone, boolean matched, Random random) {
		List<String> pool = switch (tone) {
			case FRIENDLY -> FRIENDLY_REACTIONS;
			case CURIOUS -> CURIOUS_REACTIONS;
			case BOLD -> BOLD_REACTIONS;
		};
		String line = pick(random, pool);
		return matched ? line + " " + pick(random, MATCHED_SUFFIXES) : line;
	}

	public static String closingLine(VillagerEntity villager) {
		String favorite = FavoriteGiftAttachment.hasFavorite(villager)
				? FavoriteGiftAttachment.getFavorite(villager).getName().getString()
				: "a surprise";
		return "Anyway, it was nice chatting. Oh, and if you're ever looking for a gift - " + favorite + " would make my day.";
	}

	private static String pick(Random random, List<String> lines) {
		return lines.get(random.nextInt(lines.size()));
	}

	private static final List<String> VILLAGE_GOSSIP_LINES = List.of(
			"Did you hear? Someone's been leaving flowers on doorsteps all over the village. Nopony's owned up to it yet.",
			"Between you and me, I heard two of the neighbors had quite the argument over a fence line last week.",
			"There's a rumor going around that somepony found a whole cave of gems out past the tree line.");

	private static final List<String> HOBBIES_INTERESTS_LINES = List.of(
			"When I get some free time, I like to just sit and watch the clouds roll by.",
			"I've been trying to pick up a new hobby lately. Haven't quite settled on one yet.",
			"Honestly? I could talk about my hobby for hours if you let me.");

	private static final List<String> CURRENT_EVENT_LINES = List.of(
			"Nothing much going on around the village lately - just the usual day to day.",
			"Things have been pretty quiet around here. Suits me just fine, honestly.");

	private static final List<String> FRIENDSHIP_LINES = List.of(
			"It's nice having friends around the village. Makes the place feel like home.",
			"You know, a good friend makes even the dullest chores bearable.");

	private static final List<String> CUTIE_MARK_STORY_LINES = List.of(
			"Want to hear how I got my cutie mark? It's a bit of a story, but I don't mind telling it.",
			"My cutie mark story isn't very exciting, honestly - but it means a lot to me.");

	private static final List<String> FRIENDLY_REACTIONS = List.of(
			"Aw, that's sweet of you to say!",
			"Ha, I like you already.");
	private static final List<String> CURIOUS_REACTIONS = List.of(
			"Ooh, good question - never really thought about it that way.",
			"Huh, that's actually a fair point.");
	private static final List<String> BOLD_REACTIONS = List.of(
			"Ha! Now that's the kind of talk I like to hear.",
			"Bold of you to say - I respect that.");
	private static final List<String> MATCHED_SUFFIXES = List.of(
			"You really get me.",
			"Feels like we're on the same wavelength.");
}
