package craftingismagic.cim.client.pegasus;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * The rainbow trail's looping shimmer sound. Originally this was a
 * server-broadcast positional sound retriggered every few ticks, but a
 * positional sound anchored to a stale, periodically-refreshed point can't
 * keep up with the slingshot speed a Sonic Rainboom launches at - by the
 * time it plays, the player has already outrun it. Tracking the entity's
 * position every client tick (not just every retrigger) keeps this sound
 * glued to the player instead, and its volume fades out over the trail's
 * duration to match the visual ribbon fade.
 */
public final class RainboomTrailShimmerSound extends MovingSoundInstance {
	/**
	 * The Rainboom's own boom (thunder at volume 4.0 + a totem chime at volume 2.0, see
	 * {@code SonicRainboomEffect}) fires at the same moment this starts and its rumble tail can
	 * span most of the trail's duration, so this needs to be at least as loud to cut through
	 * rather than being masked underneath it.
	 */
	private static final float PEAK_VOLUME = 4.0F;

	private final Entity target;
	private final int durationTicks;
	private int age;

	public RainboomTrailShimmerSound(Entity target, int durationTicks) {
		// TEMPORARY placeholder sound - see the Sonic Rainboom sound memory note.
		super(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, SoundInstance.createRandom());
		this.target = target;
		this.durationTicks = durationTicks;
		this.repeat = true;
		this.repeatDelay = 5;
		this.volume = PEAK_VOLUME;
		this.pitch = 0.9F + this.random.nextFloat() * 0.5F;
		this.x = target.getX();
		this.y = target.getY();
		this.z = target.getZ();
	}

	@Override
	public void tick() {
		if (this.target.isRemoved() || this.age >= this.durationTicks) {
			this.setDone();
			return;
		}

		this.x = this.target.getX();
		this.y = this.target.getY();
		this.z = this.target.getZ();
		this.volume = PEAK_VOLUME * Math.max(0.0F, 1.0F - (float) this.age / this.durationTicks);
		this.age++;
	}
}
