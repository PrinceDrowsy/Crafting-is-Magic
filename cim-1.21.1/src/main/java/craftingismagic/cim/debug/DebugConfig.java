package craftingismagic.cim.debug;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import craftingismagic.cim.Cim;

/** Same minimal GSON-backed config shape as {@code LifeStageConfig} - one switch, {@link #enabled},
 * letting a server turn every debug tool off at once (see {@link CimDebug}). On by default: the tools
 * are already gated behind Creative or op level 2. */
public final class DebugConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-debug.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static DebugConfig instance;

	public boolean enabled = true;

	public static DebugConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static DebugConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				DebugConfig loaded = GSON.fromJson(reader, DebugConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				Cim.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, e);
			}
		}
		DebugConfig defaults = new DebugConfig();
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
