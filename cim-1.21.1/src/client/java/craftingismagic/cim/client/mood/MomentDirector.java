package craftingismagic.cim.client.mood;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.MusicSound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;

import craftingismagic.cim.Cim;
import craftingismagic.cim.sound.ModSounds;

/**
 * The client's scene director (README: The mood system - "set the scene, never label the feeling").
 * A moment is a recipe for the shared mood grade (saturation, tint, contrast, brightness, glow), a dip
 * in the world's sounds, and maybe a change of music, played as an arc: it eases in, holds, then eases
 * back out slowly - color returning reads as healing. Only one moment plays at a time; a new one
 * replaces it. Everything here is client-side and seen only by this player, scaled by
 * {@link MomentConfig}.
 */
public final class MomentDirector {
	public static final Identifier PIPELINE = Identifier.of(Cim.MOD_ID, "mood_grade");
	/** Ends whatever is playing (debug). */
	public static final Identifier STOP = Identifier.of(Cim.MOD_ID, "stop");
	/** World sounds a moment may hush; never music, voices, the interface, or the master volume. */
	private static final Set<SoundCategory> HUSHED = EnumSet.of(SoundCategory.AMBIENT, SoundCategory.WEATHER, SoundCategory.BLOCKS,
			SoundCategory.HOSTILE, SoundCategory.NEUTRAL, SoundCategory.PLAYERS, SoundCategory.RECORDS);

	/**
	 * One moment's look and sound. Grade values are what the moment reaches at full strength;
	 * {@code soundLevel} is how loud the world stays (1 = untouched); {@code muffleHighs} is how much of
	 * the world's treble is left - the muffle (1 = clear, low = dull and far away).
	 */
	public record Recipe(float saturation, float tintR, float tintG, float tintB, float contrast, float brightness, float glow,
			float soundLevel, float muffleHighs, RegistryEntry<SoundEvent> music, int fadeInTicks, int holdTicks, int fadeOutTicks) {
		int length() {
			return fadeInTicks + holdTicks + fadeOutTicks;
		}
	}

	/**
	 * Grief for a close friend (decided 2026-10-14): color drains, as if seen through tears, a touch
	 * cooler; the world's sounds go muffled and far away; the music fades into a gentle track. No vignette, no heartbeat -
	 * sadness never borrows horror cues. Eases in over 5 seconds, holds a minute, and the color seeps
	 * back over 3 minutes.
	 */
	public static final Recipe GRIEF = new Recipe(0.2f, 0.96f, 0.98f, 1.04f, 0.94f, -0.02f, 0f, 0.75f, 0.12f, ModSounds.MUSIC_GRIEF,
			100, 1200, 3600);

	private static final Map<Identifier, Recipe> RECIPES = Map.of(Identifier.of(Cim.MOD_ID, "grief"), GRIEF);

	private static Recipe active;
	private static Identifier activeId;
	private static int age;
	private static boolean pipelineOn;
	private static float lastSoundFactor = 1f;
	private static boolean muffling;
	/** The muffle playing sounds were last set to, so it's only re-sent when it changes. */
	private static float appliedMuffle;

	private MomentDirector() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MomentDirector::tick);
		WorldRenderEvents.START.register(context -> applyGrade(context.tickCounter().getTickDelta(false)));
	}

	/** Starts {@code id}; unknown moments are ignored (the server may know moments this client doesn't). */
	public static void play(Identifier id) {
		if (id.equals(STOP)) {
			stop();
			return;
		}
		Recipe recipe = RECIPES.get(id);
		if (recipe == null) {
			Cim.LOGGER.debug("No client look for moment {}", id);
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (recipe == active) {
			// The same feeling again on top of itself (a second loss while still grieving) deepens it
			// rather than restarting: pick up from where the picture is and hold at full strength anew.
			float current = strength(0f) / Math.max(0.001f, MomentConfig.get().clampedIntensity());
			age = current >= 0.999f ? recipe.fadeInTicks() : Math.round(recipe.fadeInTicks() * current);
		} else {
			active = recipe;
			activeId = id;
			age = 0;
		}
		MusicSound music = recipe.music() == null ? null : new MusicSound(recipe.music(), 20, 600, true);
		if (music != null && MomentConfig.get().enabled && MomentConfig.get().music && !client.getMusicTracker().isPlayingType(music)) {
			client.getMusicTracker().stop();
			client.getMusicTracker().play(music);
		}
	}

	public static void stop() {
		active = null;
		activeId = null;
		age = 0;
	}

	/** What's playing and how far along, for the debug overlay. */
	public static String describe() {
		return active == null ? "none" : activeId + String.format(" %.0f%%", strength(0f) * 100f);
	}

	/** How much treble a sound of this category keeps right now - 1 when it shouldn't be muffled. */
	public static float muffleHighsFor(SoundCategory category) {
		if (!muffling || active == null || !HUSHED.contains(category)) {
			return 1f;
		}
		return MathHelper.lerp(strength(0f), 1f, active.muffleHighs());
	}

	/** How much of the world's volume a hushed category keeps right now (sound mixin). */
	public static float soundFactor(SoundCategory category) {
		if (active == null || !HUSHED.contains(category) || !MomentConfig.get().enabled || !MomentConfig.get().soundDip) {
			return 1f;
		}
		return MathHelper.lerp(strength(0f), 1f, active.soundLevel());
	}

	/** The moment's arc: eases in, holds, eases out (smoothstep at both ends), times the comfort intensity. */
	private static float strength(float tickDelta) {
		if (active == null || !MomentConfig.get().enabled) {
			return 0f;
		}
		float t = age + tickDelta;
		float raw;
		if (t < active.fadeInTicks()) {
			raw = smooth(t / active.fadeInTicks());
		} else if (t < active.fadeInTicks() + active.holdTicks()) {
			raw = 1f;
		} else {
			raw = 1f - smooth((t - active.fadeInTicks() - active.holdTicks()) / active.fadeOutTicks());
		}
		return MathHelper.clamp(raw, 0f, 1f) * MomentConfig.get().clampedIntensity();
	}

	private static float smooth(float x) {
		float c = MathHelper.clamp(x, 0f, 1f);
		return c * c * (3f - 2f * c);
	}

	private static void tick(MinecraftClient client) {
		if (active == null) {
			if (muffling) {
				Muffler.apply(client, HUSHED, 0f, 1f); // the moment is over: clear every filter
				muffling = false;
				appliedMuffle = 0f;
			}
			return;
		}
		if (client.world == null || ++age >= active.length()) {
			stop();
		}
		// The muffle: sounds that start mid-moment are muffled the instant they start
		// (MomentNewSoundMixin), so playing sounds only need updating while the muffle is still
		// changing - fading in or out. During the steady hold this does nothing.
		if (active != null && age % 4 == 0) {
			boolean allowed = MomentConfig.get().enabled && MomentConfig.get().soundDip && active.muffleHighs() < 1f;
			float amount = allowed ? strength(0f) : 0f;
			if ((amount > 0.001f || muffling) && Math.abs(amount - appliedMuffle) > 0.01f) {
				Muffler.apply(client, HUSHED, amount, active.muffleHighs());
				appliedMuffle = amount;
				muffling = amount > 0.001f;
			}
		}
		// Re-apply the dip to sounds already playing, a few times a second while it changes.
		float factor = soundFactor(SoundCategory.AMBIENT);
		if (Math.abs(factor - lastSoundFactor) > 0.01f || (active == null && lastSoundFactor != 1f)) {
			lastSoundFactor = active == null ? 1f : factor;
			for (SoundCategory category : HUSHED) {
				client.getSoundManager().updateSoundVolume(category, client.options.getSoundVolume(category));
			}
		}
	}

	private static void applyGrade(float tickDelta) {
		PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
		float s = strength(tickDelta);
		if (s <= 0.001f) {
			if (pipelineOn) {
				manager.remove(PIPELINE);
				pipelineOn = false;
			}
			return;
		}
		PostPipeline pipeline = manager.getPipeline(PIPELINE);
		if (pipeline == null) {
			return;
		}
		Recipe r = active;
		pipeline.getUniformSafe("Saturation").setFloat(MathHelper.lerp(s, 1f, r.saturation()));
		pipeline.getUniformSafe("Tint").setVector(MathHelper.lerp(s, 1f, r.tintR()), MathHelper.lerp(s, 1f, r.tintG()),
				MathHelper.lerp(s, 1f, r.tintB()));
		pipeline.getUniformSafe("Contrast").setFloat(MathHelper.lerp(s, 1f, r.contrast()));
		pipeline.getUniformSafe("Brightness").setFloat(MathHelper.lerp(s, 0f, r.brightness()));
		pipeline.getUniformSafe("Glow").setFloat(MathHelper.lerp(s, 0f, r.glow()));
		if (!pipelineOn) {
			pipelineOn = manager.add(PIPELINE);
		}
	}
}
