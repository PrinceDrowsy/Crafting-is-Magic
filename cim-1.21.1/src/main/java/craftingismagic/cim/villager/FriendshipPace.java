package craftingismagic.cim.villager;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.villager.PersonalityAttachment.Personality;
import craftingismagic.cim.voice.VoiceLeanings;

/**
 * How friendships unfold (README: Affection, reworked - friendship pacing): there's
 * no fixed timeline - it depends on the pony and on what you go through together. <b>Habits build
 * acquaintance; experiences build friendship.</b>
 * <ul>
 * <li><b>Habits</b> - a chat, a gift - help most at first, then taper as the feeling grows; and within a
 * day the first visit counts fully, later ones less (no farming by clicking through chatter).</li>
 * <li><b>Experiences</b> - memories of something you went through together - are the big jumps, with no
 * taper (see {@link #register}).</li>
 * <li><b>Personality sets each pony's pace</b> for each feeling: Gentle and Peppy ponies warm quickly;
 * Cranky ones are slow to like you but quick to respect you.</li>
 * <li><b>Consistency builds Trust:</b> coming back on a new day is a small, steady step - being someone
 * who's always there.</li>
 * <li><b>First impressions are a nudge:</b> a warm temperament, the player leaning into an Element the
 * pony values, mutual friends, or a good word heard through gossip gives a small head start.</li>
 * <li><b>Gentle fading:</b> after a week apart, Fondness slowly drifts - never below the floor your
 * shared history built, never Respect or Trust - and coming back gives some of it back at once.</li>
 * </ul>
 * Numbers are first-pass, to tune in play.
 */
public final class FriendshipPace {
	/** Habits already counted today -> how much the next one is worth: full, half, a quarter, then a crumb. */
	private static final float[] DAILY_TAPER = {1f, 0.5f, 0.25f, 0.1f};
	/** Habits carry a feeling toward here easily, then ever more slowly (never quite stopping). */
	private static final int HABIT_EASE_UNTIL = FeelingBand.HIGH.threshold();
	private static final float HABIT_FLOOR = 0.15f;
	private static final int CONSISTENCY_TRUST = 2;
	private static final long FADE_AFTER_DAYS = 7;
	private static final long DAY = 24000L;

	private FriendshipPace() {
	}

	/** Experiences are the big jumps: a new memory a pony forms of something they lived through with you. */
	public static void register() {
		MemoryStore.onNewMemory(FriendshipPace::experienced);
	}

	// ---- Habits ----

	/**
	 * An everyday habit warms a feeling by {@code amount} - tapered by how many habits already counted
	 * today, by how far the feeling has grown, and paced by the pony's personality. Returns what landed.
	 * Losses aren't habits; they apply as written.
	 */
	public static int habit(Entity pony, ServerPlayerEntity player, Feeling feeling, int amount) {
		if (amount <= 0) {
			FriendshipAttachment.addFeeling(pony, player.getUuid(), feeling, amount);
			return amount;
		}
		FriendshipAttachment.RelationshipData data = FriendshipAttachment.get(pony, player.getUuid());
		long today = player.getWorld().getTime() / DAY;
		return habit(pony, player, feeling, amount, data.habitDay() == today ? Math.max(0, data.habitsToday() - 1) : 0);
	}

	/** A habit, tapered by {@code counted} - how many of its kind came before it today (gifts count their own). */
	public static int habit(Entity pony, ServerPlayerEntity player, Feeling feeling, int amount, int counted) {
		FriendshipAttachment.RelationshipData data = FriendshipAttachment.get(pony, player.getUuid());
		float taper = DAILY_TAPER[Math.min(counted, DAILY_TAPER.length - 1)];
		float ease = Math.max(HABIT_FLOOR, 1f - (float) data.feeling(feeling) / HABIT_EASE_UNTIL);
		int landed = round(amount * taper * ease * pace(pony, feeling), pony.getRandom());
		FriendshipAttachment.addFeeling(pony, player.getUuid(), feeling, landed);
		return landed;
	}

	private static void countHabit(Entity pony, ServerPlayerEntity player) {
		FriendshipAttachment.takeHabit(pony, player.getUuid(), player.getWorld().getTime() / DAY);
	}

	// ---- Visits ----

	/**
	 * The player came to talk - before {@code recordTalk}, so it still sees when they last did. A first
	 * meeting leaves an impression; the first visit on a new day builds a little Trust; a long absence
	 * has let Fondness drift, and coming back restores some of it.
	 */
	public static void visit(Entity pony, ServerPlayerEntity player) {
		UUID you = player.getUuid();
		FriendshipAttachment.RelationshipData data = FriendshipAttachment.get(pony, you);
		long now = player.getWorld().getTime();
		long today = now / DAY;
		if (data.lastTalked() < 0) {
			firstImpression(pony, player);
		} else if (today > data.lastTalked() / DAY) {
			FriendshipAttachment.addVisitDay(pony, you);
			int trust = data.trust();
			float ease = Math.max(HABIT_FLOOR, 1f - (float) trust / HABIT_EASE_UNTIL);
			FriendshipAttachment.addFeeling(pony, you, Feeling.TRUST, round(CONSISTENCY_TRUST * ease * consistency(pony), pony.getRandom()));
			long daysAway = today - data.lastTalked() / DAY;
			if (daysAway > FADE_AFTER_DAYS) {
				fadeAndReconnect(pony, you, (int) (daysAway - FADE_AFTER_DAYS));
			}
		}
		countHabit(pony, player);
	}

	/** Fondness drifts one point a day past the first week, never below the shared-history floor; half comes back on reconnecting. */
	private static void fadeAndReconnect(Entity pony, UUID you, int days) {
		int fondness = FriendshipAttachment.getFeeling(pony, you, Feeling.FONDNESS);
		int floor = floor(pony, you);
		int faded = Math.max(0, Math.min(days, fondness - floor));
		if (faded > 0) {
			// "I've missed you!" - a neglected friend never turns cold, and catching up is quick.
			FriendshipAttachment.addFeeling(pony, you, Feeling.FONDNESS, -faded + (faded + 1) / 2);
		}
	}

	/** What your shared history guarantees: visits and gifts, and more for the things you lived through together. */
	static int floor(Entity pony, UUID you) {
		int floor = FriendshipAttachment.getSharedMoments(pony, you);
		for (Memory memory : MemoryStore.about(pony, you)) {
			if (memory.knowledge() != MemoryKnowledge.WITNESSED || HarmAndForgiveness.isHarm(memory)) continue;
			floor += switch (memory.importance()) {
				case TRIVIAL -> 0;
				case NOTABLE -> 4;
				case MAJOR -> 10;
				case INDELIBLE -> 20;
			};
		}
		return Math.min(floor, FeelingBand.HIGH.threshold());
	}

	/** A small head start: a warm temperament, and the player leaning into an Element this pony values. */
	private static void firstImpression(Entity pony, ServerPlayerEntity player) {
		UUID you = player.getUuid();
		int warmth = 2;
		List<String> valued = List.of();
		if (PersonalityAttachment.hasPersonality(pony)) {
			Personality personality = PersonalityAttachment.getPersonality(pony);
			warmth = switch (personality.baseType()) {
				case PEPPY, GENTLE -> 6;
				case LAZY -> 3;
				case CRANKY -> 0;
				default -> 2;
			};
			valued = personality.baseType().valuedElements();
		}
		String strongest = null;
		for (String element : VoiceLeanings.ELEMENTS) {
			if (strongest == null || VoiceLeanings.points(player, element) > VoiceLeanings.points(player, strongest)) strongest = element;
		}
		boolean kindred = strongest != null && VoiceLeanings.points(player, strongest) > 0 && valued.contains(strongest);
		FriendshipAttachment.addFeeling(pony, you, Feeling.FONDNESS, warmth + (kindred ? 4 : 0));
		if (kindred) FriendshipAttachment.addFeeling(pony, you, Feeling.RESPECT, 4);
		introductions(pony, player);
	}

	/**
	 * Arriving through someone a pony loves (README: Friendship, deepened - Introductions): "Oh, you're
	 * Petunia's friend!" Their family and friends who are already the player's friends warm them up;
	 * good things they've heard about the player through gossip earn a little Respect.
	 */
	private static void introductions(Entity pony, ServerPlayerEntity player) {
		UUID you = player.getUuid();
		int mutual = 0;
		for (UUID close : closeOnes(pony)) {
			Entity other = player.getServerWorld().getEntity(close);
			if (other != null && FriendshipBonds.isFriend(other, you)) mutual++;
		}
		if (mutual > 0) {
			FriendshipAttachment.addFeeling(pony, you, Feeling.FONDNESS, Math.min(12, 6 + 3 * (mutual - 1)));
			FriendshipAttachment.addFeeling(pony, you, Feeling.TRUST, 3);
		}
		boolean goodWord = MemoryStore.about(pony, you).stream()
				.anyMatch(memory -> memory.knowledge() == MemoryKnowledge.HEARD
						&& memory.emotion().filter(e -> e == Emotion.JOY || e == Emotion.LOVE || e == Emotion.CALM).isPresent());
		if (goodWord) FriendshipAttachment.addFeeling(pony, you, Feeling.RESPECT, 4);
	}

	/** The name of someone close to this pony who's already the player's friend - for "you're Petunia's friend!". */
	public static java.util.Optional<String> mutualFriend(Entity pony, ServerPlayerEntity player) {
		for (UUID close : closeOnes(pony)) {
			Entity other = player.getServerWorld().getEntity(close);
			if (other != null && FriendshipBonds.isFriend(other, player.getUuid())) return java.util.Optional.of(other.getName().getString());
		}
		return java.util.Optional.empty();
	}

	/** Family and friend group, from the pony's own lists. */
	private static List<UUID> closeOnes(Entity pony) {
		List<UUID> close = new java.util.ArrayList<>(PonyFamilyAttachment.children(pony));
		if (PonyFamilyAttachment.hasFamily(pony)) {
			close.addAll(PonyFamilyAttachment.getFamily(pony).parents());
			close.addAll(PonyFamilyAttachment.getFamily(pony).partner());
		}
		if (FriendGroupAttachment.hasGroup(pony)) close.addAll(FriendGroupAttachment.getGroup(pony).members());
		close.remove(pony.getUuid());
		return close;
	}

	// ---- Experiences ----

	/**
	 * A pony formed a new memory of something they went through with the player: the big jumps, no
	 * taper. Fear and anger aren't bonding - that's harm, and Harm & forgiveness will answer it.
	 */
	private static void experienced(Entity holder, Memory memory) {
		if (!(holder instanceof VillagerEntity) || memory.knowledge() != MemoryKnowledge.WITNESSED || memory.subject().isEmpty()) return;
		if (holder.getWorld().getPlayerByUuid(memory.subject().get()) == null) return;
		if (memory.emotion().filter(e -> e == Emotion.FEAR || e == Emotion.ANGER).isPresent()) return;
		if (memory.emotion().isEmpty() && memory.importance().compareTo(MemoryImportance.MAJOR) < 0) return;
		UUID you = memory.subject().get();
		switch (memory.importance()) {
			case NOTABLE -> {
				FriendshipAttachment.addFeeling(holder, you, Feeling.FONDNESS, 8);
				FriendshipAttachment.addFeeling(holder, you, Feeling.TRUST, 4);
			}
			case MAJOR -> {
				FriendshipAttachment.addFeeling(holder, you, Feeling.FONDNESS, 20);
				FriendshipAttachment.addFeeling(holder, you, Feeling.TRUST, 12);
				FriendshipAttachment.addFeeling(holder, you, Feeling.RESPECT, 8);
			}
			case INDELIBLE -> {
				FriendshipAttachment.addFeeling(holder, you, Feeling.FONDNESS, 40);
				FriendshipAttachment.addFeeling(holder, you, Feeling.TRUST, 25);
				FriendshipAttachment.addFeeling(holder, you, Feeling.RESPECT, 15);
			}
			default -> {
			}
		}
	}

	// ---- Personality ----

	/** How quickly this pony's {@code feeling} grows from everyday habits. */
	static float pace(Entity pony, Feeling feeling) {
		if (!PersonalityAttachment.hasPersonality(pony)) return 1f;
		return switch (PersonalityAttachment.getPersonality(pony).baseType()) {
			case PEPPY -> pick(feeling, 1.4f, 0.8f, 1.0f);
			case GENTLE -> pick(feeling, 1.3f, 0.9f, 1.2f);
			case STEADFAST -> pick(feeling, 0.8f, 1.1f, 1.0f);
			case BOOKISH -> pick(feeling, 0.9f, 1.2f, 0.9f);
			case DARING -> pick(feeling, 1.0f, 1.3f, 0.9f);
			case REFINED -> pick(feeling, 1.0f, 1.1f, 0.8f);
			case CRANKY -> pick(feeling, 0.5f, 1.2f, 0.7f);
			case LAZY -> pick(feeling, 1.1f, 0.7f, 1.0f);
		};
	}

	/** How much this pony values someone who keeps coming back. */
	private static float consistency(Entity pony) {
		if (!PersonalityAttachment.hasPersonality(pony)) return 1f;
		return switch (PersonalityAttachment.getPersonality(pony).baseType()) {
			case STEADFAST -> 1.5f;
			case GENTLE -> 1.2f;
			case REFINED -> 0.8f;
			case CRANKY -> 0.7f;
			default -> 1f;
		};
	}

	private static float pick(Feeling feeling, float fondness, float respect, float trust) {
		return switch (feeling) {
			case FONDNESS -> fondness;
			case RESPECT -> respect;
			case TRUST -> trust;
		};
	}

	/** Rounds so small gains still add up on average (0.4 lands as 1 two times in five). */
	private static int round(float value, Random random) {
		int whole = (int) Math.floor(value);
		return whole + (random.nextFloat() < value - whole ? 1 : 0);
	}
}
