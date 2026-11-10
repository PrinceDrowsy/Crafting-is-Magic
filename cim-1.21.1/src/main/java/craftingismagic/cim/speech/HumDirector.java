package craftingismagic.cim.speech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Mood;
import craftingismagic.cim.narrative.TaleContext;
import craftingismagic.cim.narrative.TaleRuntime;
import craftingismagic.cim.network.HumS2CPayload;
import craftingismagic.cim.villager.PersonalityAttachment;
import craftingismagic.cim.villager.PersonalityType;
import craftingismagic.cim.villager.VillagerInteractionHandler;

/**
 * Ponies hum as they go about their day (design doc: Humming): a short tune in their own speaking voice,
 * note particles rising from them. Once a second, ponies near players get a chance to start one -
 * <ul>
 * <li>by personality: Peppy ponies hum often, most others now and then, Cranky ones almost never;</li>
 * <li>only on their daily routine - working, walking about, meeting - never asleep, panicking, or
 * mid-conversation;</li>
 * <li>by mood: joy makes it more likely; grief or fear, and they don't hum at all;</li>
 * <li>and sometimes a pony nearby joins in, a third higher, in harmony.</li>
 * </ul>
 * Tunes come from {@link HumLibrary}, picked by weight among those whose {@code when} holds.
 * <p>
 * Chances are set for the Lively setting; about two in three hums are sent as lively-only, so on Soft
 * (each player's choice) only the occasional one is heard.
 */
public final class HumDirector {
	/** Ponies this close to a player can start humming; players this close hear it. */
	private static final double RANGE = 24;
	private static final double HARMONY_RANGE = 6;
	/** Odds of starting a hum, per second, at the Lively rate. */
	private static final float PEPPY_CHANCE = 1f / 25f;
	private static final float USUAL_CHANCE = 1f / 90f;
	private static final float LAZY_CHANCE = 1f / 150f;
	private static final float CRANKY_CHANCE = 1f / 600f;
	private static final float HARMONY_CHANCE = 0.35f;
	private static final float LIVELY_ONLY_SHARE = 0.65f;
	/** Breather after a hum before the same pony hums again, in ticks. */
	private static final int COOLDOWN_TICKS = 20 * 20;
	/** A third above, in a five-note scale. */
	private static final int HARMONY_SHIFT = 2;

	/** Entity id -> the tick they may hum again (also covers the hum still playing). */
	private static final Map<Integer, Long> busyUntil = new HashMap<>();
	private static final Random random = Random.create();

	private HumDirector() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0 || HumLibrary.all().isEmpty()) {
			return;
		}
		long now = server.getTicks();
		busyUntil.values().removeIf(until -> until < now);
		for (ServerWorld world : server.getWorlds()) {
			Set<VillagerEntity> nearby = new HashSet<>();
			for (ServerPlayerEntity player : world.getPlayers()) {
				nearby.addAll(world.getEntitiesByClass(VillagerEntity.class, new Box(player.getBlockPos()).expand(RANGE), pony -> true));
			}
			for (VillagerEntity pony : nearby) {
				if (canHum(pony, now) && random.nextFloat() < chance(pony)) {
					hum(world, pony, now);
				}
			}
		}
	}

	private static boolean canHum(VillagerEntity pony, long now) {
		if (busyUntil.containsKey(pony.getId()) || pony.isSleeping() || pony.isBaby() && random.nextBoolean()
				|| VillagerInteractionHandler.isInConversation(pony)) {
			return false;
		}
		Optional<Activity> activity = pony.getBrain().getFirstPossibleNonCoreActivity();
		boolean onRoutine = activity.map(a -> a == Activity.WORK || a == Activity.IDLE || a == Activity.MEET || a == Activity.PLAY).orElse(false);
		return onRoutine && !Mood.feels(pony, Emotion.GRIEF, EmotionIntensity.FAINT) && !Mood.feels(pony, Emotion.FEAR, EmotionIntensity.FAINT);
	}

	private static float chance(VillagerEntity pony) {
		PersonalityType type = PersonalityAttachment.hasPersonality(pony) ? PersonalityAttachment.getPersonality(pony).baseType() : null;
		float chance = type == PersonalityType.PEPPY ? PEPPY_CHANCE
				: type == PersonalityType.CRANKY ? CRANKY_CHANCE
				: type == PersonalityType.LAZY ? LAZY_CHANCE
				: USUAL_CHANCE;
		return Mood.feels(pony, Emotion.JOY, EmotionIntensity.CLEAR) ? chance * 2 : chance;
	}

	private static void hum(ServerWorld world, VillagerEntity pony, long now) {
		ServerPlayerEntity listener = world.getClosestPlayer(pony, RANGE) instanceof ServerPlayerEntity player ? player : null;
		if (listener == null) {
			return;
		}
		Optional<HumLibrary.Hum> tune = pick(new TaleContext(listener, pony, pony.getRandom()));
		if (tune.isEmpty()) {
			return;
		}
		HumLibrary.Hum hum = tune.get();
		boolean livelyOnly = random.nextFloat() < LIVELY_ONLY_SHARE;
		send(world, pony, hum, 0, livelyOnly);
		busyUntil.put(pony.getId(), now + (long) hum.notes().size() * hum.tempo() + COOLDOWN_TICKS);

		// Sometimes a pony nearby hears it and joins in, a third higher.
		if (random.nextFloat() < HARMONY_CHANCE) {
			for (VillagerEntity other : world.getEntitiesByClass(VillagerEntity.class, pony.getBoundingBox().expand(HARMONY_RANGE),
					other -> other != pony)) {
				if (canHum(other, now) && chance(other) > CRANKY_CHANCE) {
					send(world, other, hum, HARMONY_SHIFT, livelyOnly);
					busyUntil.put(other.getId(), now + (long) hum.notes().size() * hum.tempo() + COOLDOWN_TICKS);
					break;
				}
			}
		}
	}

	/**
	 * Debug: {@code pony} hums right now - {@code tune} if given, otherwise one that fits - heard on every
	 * humming setting but Off. Returns the tune, or empty when there's nothing to hum.
	 */
	public static Optional<HumLibrary.Hum> humNow(ServerPlayerEntity player, VillagerEntity pony, Optional<net.minecraft.util.Identifier> tune) {
		Optional<HumLibrary.Hum> hum = tune.isPresent()
				? HumLibrary.all().stream().filter(candidate -> candidate.id().equals(tune.get())).findFirst()
				: pick(new TaleContext(player, pony, pony.getRandom()));
		hum.ifPresent(chosen -> send((ServerWorld) pony.getWorld(), pony, chosen, 0, false));
		return hum;
	}

	private static Optional<HumLibrary.Hum> pick(TaleContext context) {
		List<HumLibrary.Hum> fitting = new ArrayList<>();
		int total = 0;
		for (HumLibrary.Hum hum : HumLibrary.all()) {
			if (TaleRuntime.allHold(hum.when(), context)) {
				fitting.add(hum);
				total += hum.weight();
			}
		}
		if (fitting.isEmpty()) {
			return Optional.empty();
		}
		int roll = random.nextInt(total);
		for (HumLibrary.Hum hum : fitting) {
			roll -= hum.weight();
			if (roll < 0) {
				return Optional.of(hum);
			}
		}
		return Optional.of(fitting.get(fitting.size() - 1));
	}

	private static void send(ServerWorld world, VillagerEntity pony, HumLibrary.Hum hum, int shift, boolean livelyOnly) {
		HumS2CPayload payload = new HumS2CPayload(pony.getId(), SpeakingVoices.heard(pony), hum.scale(), hum.tempo(), hum.notes(), shift, livelyOnly);
		for (ServerPlayerEntity player : PlayerLookup.around(world, pony.getPos(), RANGE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
