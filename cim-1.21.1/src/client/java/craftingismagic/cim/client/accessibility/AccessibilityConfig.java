package craftingismagic.cim.client.accessibility;

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
 * Client-only reading settings (README: Further rules, under Verse - the dyslexia-friendly font,
 * text size, and highlighting that never relies on color alone apply to the whole mod). Same
 * minimal GSON-backed shape as the mod's other configs, but stored per player's game, since these
 * are about how one person reads, never about the world or server.
 */
public final class AccessibilityConfig {
	public static final float MIN_TEXT_SCALE = 0.75f;
	public static final float MAX_TEXT_SCALE = 2.0f;

	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-accessibility.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AccessibilityConfig instance;

	/** Draw the mod's text in Equestria Reader (the dyslexia-friendly font) instead of Minecraft's. */
	public boolean dyslexicFont = false;
	/** Size multiplier for the mod's reading text (dialogue, lore, the Journal). */
	public float textScale = 1.0f;

	public static AccessibilityConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public float clampedTextScale() {
		return Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, textScale));
	}

	private static AccessibilityConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				AccessibilityConfig loaded = GSON.fromJson(reader, AccessibilityConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				Cim.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, e);
			}
		}
		AccessibilityConfig defaults = new AccessibilityConfig();
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
