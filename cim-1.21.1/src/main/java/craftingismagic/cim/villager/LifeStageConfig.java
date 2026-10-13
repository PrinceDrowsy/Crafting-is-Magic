package craftingismagic.cim.villager;

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
 * How long each non-adult {@link LifeStage} lasts. The first config file in this codebase - no
 * config system existed before this, so this is deliberately minimal: a plain GSON-backed POJO,
 * written with defaults on first run if the file doesn't exist yet, loaded once and cached after
 * that (re-read the file and call {@link #reload()} to pick up manual edits without a restart).
 * <p>
 * {@code ticksPerYear} is its own tunable knob, not tied to any real in-mod calendar concept (none
 * exists yet) - a server owner can make "a year" pass faster or slower without touching the
 * per-stage multipliers below it.
 */
public final class LifeStageConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-life-stages.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static LifeStageConfig instance;

	public int ticksPerYear = 24000;
	public float babyDurationYears = 1.0f;
	public float childDurationYears = 1.0f;
	public float teenagerDurationYears = 1.0f;

	public static LifeStageConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	public int babyDurationTicks() {
		return Math.round(babyDurationYears * ticksPerYear);
	}

	public int childDurationTicks() {
		return Math.round(childDurationYears * ticksPerYear);
	}

	public int teenagerDurationTicks() {
		return Math.round(teenagerDurationYears * ticksPerYear);
	}

	private static LifeStageConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				LifeStageConfig loaded = GSON.fromJson(reader, LifeStageConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				Cim.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, e);
			}
		}
		LifeStageConfig defaults = new LifeStageConfig();
		defaults.save();
		return defaults;
	}

	/** Public so the optional in-game config screen (CimConfigScreen) can write edits back. */
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
