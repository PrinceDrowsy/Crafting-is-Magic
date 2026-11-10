package craftingismagic.cim.client.mood;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import craftingismagic.cim.Cim;

/**
 * Comfort settings for scene-setting moments (design doc: The mood system - "perception effects get
 * intensity sliders and toggles"). Client-only: how one person wants big moments to feel.
 */
public final class MomentConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-moments.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static MomentConfig instance;

	/** Big moments change the picture and sound at all. */
	public boolean enabled = true;
	/** How strongly they do, 0-1. */
	public float intensity = 1.0f;
	/** A moment may change the music. */
	public boolean music = true;
	/** A moment may dip and hush the world's sounds. */
	public boolean soundDip = true;

	public static MomentConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public float clampedIntensity() {
		return Math.max(0f, Math.min(1f, intensity));
	}

	private static MomentConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				MomentConfig loaded = GSON.fromJson(reader, MomentConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				Cim.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, e);
			}
		}
		MomentConfig defaults = new MomentConfig();
		defaults.save();
		return defaults;
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Cim.LOGGER.warn("Failed to save {}", CONFIG_PATH, e);
		}
	}
}
