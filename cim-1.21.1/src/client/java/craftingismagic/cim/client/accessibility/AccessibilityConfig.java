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
 * Client-only accessibility settings, stored per player's game since they're about how one person reads
 * and sees, never about the world or server. Each is grounded in what people with the disability say
 * helps (design doc: Accessibility); the descriptions say exactly what changes and promise no outcome.
 */
public final class AccessibilityConfig {
	public static final float MIN_TEXT_SCALE = 0.75f;
	public static final float MAX_TEXT_SCALE = 2.0f;
	public static final float MAX_LINE_SPACING = 2.0f;
	public static final float MAX_LETTER_SPACING = 2.0f;

	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-accessibility.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AccessibilityConfig instance;

	/** The font for the mod's text. Dyslexic readers differ - one font helps some and hinders others - so it's a choice. */
	public enum ReadingFont {
		MINECRAFT, EQUESTRIA_READER, ATKINSON_HYPERLEGIBLE
	}

	public ReadingFont readingFont = ReadingFont.MINECRAFT;
	/** Size multiplier for the mod's reading text (dialogue, lore, the Journal). */
	public float textScale = 1.0f;
	/** Lines of reading text this many times their usual distance apart. */
	public float lineSpacing = 1.0f;
	/** Extra space between letters of reading text, in pixels at normal size. */
	public float letterSpacing = 0f;
	/** How solid a dark backdrop behind reading text is (0 for none). */
	public float textBackdrop = 0f;
	/** Whether the reading options have been offered yet - they're shown the first time a reading screen opens. */
	public boolean readingSetupSeen = false;

	/** How bright and big flashes are (0-1): a spell's release, a teleport, Verse's lights. */
	public float flashIntensity = 1.0f;
	/** Flashes in the magic's own color instead of white. */
	public boolean colorFlashes = false;
	/** Holds still what flickers or ripples: glow edges, a teleport's spikes, twinkling lights, a charge's sparks. */
	public boolean steadyEffects = false;
	/** How many sparkles, glints, dust and shards magic gives off (0-1). */
	public float particleAmount = 1.0f;

	/** The old on/off dyslexia-font setting, read once from older files and turned into {@link #readingFont}. */
	private Boolean dyslexicFont;

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
					if (loaded.dyslexicFont != null) {
						if (loaded.dyslexicFont) {
							loaded.readingFont = ReadingFont.EQUESTRIA_READER;
						}
						loaded.dyslexicFont = null;
						loaded.save();
					}
					if (loaded.readingFont == null) {
						loaded.readingFont = ReadingFont.MINECRAFT;
					}
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
