package craftingismagic.cim.villager;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.Schedule;
import net.minecraft.entity.ai.brain.ScheduleBuilder;
import net.minecraft.entity.passive.VillagerEntity;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * Bat Ponies are nocturnal (see Bat pony villagers in the design doc - "only sleep during the day") -
 * every vanilla schedule anchor shifted by 12000 ticks (half a day), so {@code REST} covers
 * dawn-to-dusk instead of dusk-to-dawn. Only the SCHEDULE (which {@link Activity} is active at which
 * time) changes - the actual task lists behind each {@code Activity} (path to a claimed bed, sleep
 * in it, etc.) are untouched vanilla behavior, just running at the opposite time of day.
 * <p>
 * Applied from two places, deliberately: {@code BatPonyScheduleMixin} (covers fresh spawn, chunk
 * load, and grow-up - everywhere vanilla's own {@code initBrain} runs) and {@link
 * VillagerSpeciesHandler} (covers the one real gap - a genuinely brand-new villager's species hasn't
 * been rolled yet the first time {@code initBrain} runs, since that happens before {@code
 * ENTITY_LOAD} assigns one).
 */
public final class BatPonyScheduleHandler {
	private static final Schedule NOCTURNAL_DEFAULT = new ScheduleBuilder(new Schedule())
			.withActivity(0, Activity.REST)
			.withActivity(12010, Activity.IDLE)
			.withActivity(14000, Activity.WORK)
			.withActivity(21000, Activity.MEET)
			.withActivity(23000, Activity.IDLE)
			.build();

	private static final Schedule NOCTURNAL_BABY = new ScheduleBuilder(new Schedule())
			.withActivity(0, Activity.REST)
			.withActivity(12010, Activity.IDLE)
			.withActivity(15000, Activity.PLAY)
			.withActivity(18000, Activity.IDLE)
			.withActivity(22000, Activity.PLAY)
			.build();

	private BatPonyScheduleHandler() {
	}

	/** Takes {@code brain} explicitly rather than reading {@code villager.getBrain()} internally -
	 * the mixin call site passes vanilla's own {@code initBrain} parameter directly (matching how
	 * vanilla's own body acts on that parameter, not the entity's getter, avoiding any assumption
	 * about whether the field is synced yet at that exact point); the {@code VillagerSpeciesHandler}
	 * call site passes {@code villager.getBrain()} since it runs well after construction. */
	public static void applyIfNocturnal(VillagerEntity villager, Brain<VillagerEntity> brain) {
		if (!SpeciesAttachment.hasChosenSpecies(villager) || SpeciesAttachment.getSpecies(villager) != Species.BAT_PONY) {
			return;
		}
		brain.setSchedule(villager.isBaby() ? NOCTURNAL_BABY : NOCTURNAL_DEFAULT);
		brain.refreshActivities(villager.getWorld().getTimeOfDay(), villager.getWorld().getTime());
	}

	/**
	 * {@code applyIfNocturnal} alone only swaps which {@code Activity} is ALLOWED to start next -
	 * it never stops a task already {@code RUNNING} (confirmed via decompile: {@code Brain#tick}'s
	 * {@code updateTasks} ticks every running task unconditionally regardless of {@code
	 * possibleActivities}, and a {@code MultiTickTask} like a WORK/wander walk can legitimately keep
	 * running for up to 1200 ticks/60 seconds on its own). A villager mid-way through some other
	 * task when it's converted to Bat Pony can sit there untouched for a long time before the
	 * nocturnal schedule actually takes visible effect. This reports whether that's currently true,
	 * so a caller can decide whether the heavier {@code villager.reinitializeBrain(world)} (which
	 * force-stops every running task and rebuilds the brain, but also resets the schedule back to
	 * vanilla as a side effect - always call {@link #applyIfNocturnal} again immediately after it)
	 * is actually warranted, rather than always paying that cost even when nothing's wrong.
	 */
	public static boolean needsNocturnalFix(VillagerEntity villager) {
		if (!SpeciesAttachment.hasChosenSpecies(villager) || SpeciesAttachment.getSpecies(villager) != Species.BAT_PONY) {
			return false;
		}
		Schedule expected = villager.isBaby() ? NOCTURNAL_BABY : NOCTURNAL_DEFAULT;
		return villager.getBrain().getSchedule() != expected;
	}
}
