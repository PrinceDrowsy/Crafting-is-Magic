package craftingismagic.cim.spawn;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import craftingismagic.cim.Cim;

/** Same minimal GSON-backed config shape as {@code LifeStageConfig} (the first config in this
 * codebase) - one toggle, {@link #spawnInVillage}, controlling whether {@link VillageSpawnHandler}
 * relocates a fresh world's spawn point into a nearby village at all. */
public final class VillageSpawnConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-village-spawn.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static VillageSpawnConfig instance;

	public boolean spawnInVillage = true;

	public static VillageSpawnConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static VillageSpawnConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				VillageSpawnConfig loaded = GSON.fromJson(reader, VillageSpawnConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				Cim.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, e);
			}
		}
		VillageSpawnConfig defaults = new VillageSpawnConfig();
		defaults.save();
		return defaults;
	}

	private void save() {
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
