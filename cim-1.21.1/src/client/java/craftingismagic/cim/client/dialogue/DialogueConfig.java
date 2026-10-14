package craftingismagic.cim.client.dialogue;

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
 * Client-only conversation settings - how one person likes to read dialogue, never about the world.
 * Same GSON-backed shape as the mod's other configs.
 */
public final class DialogueConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cim-dialogue.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static DialogueConfig instance;

	/** How many inner-voice lines may speak up in one beat (README: quiet/normal/chatty). */
	public enum VoiceLines {
		QUIET(1),
		NORMAL(2),
		CHATTY(4);

		public final int max;

		VoiceLines(int max) {
			this.max = max;
		}
	}

	/** How fast new lines type themselves out, in characters per second; instant shows them whole. */
	public enum RevealSpeed {
		SLOW(20),
		NORMAL(35),
		FAST(70),
		INSTANT(0);

		public final int charsPerSecond;

		RevealSpeed(int charsPerSecond) {
			this.charsPerSecond = charsPerSecond;
		}
	}

	public VoiceLines voiceLines = VoiceLines.NORMAL;
	public RevealSpeed revealSpeed = RevealSpeed.NORMAL;
	/** A soft sound as each line starts. */
	public boolean revealSound = true;

	public RevealSpeed revealSpeed() {
		return revealSpeed == null ? RevealSpeed.NORMAL : revealSpeed;
	}

	public static DialogueConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public int maxVoiceLines() {
		return (voiceLines == null ? VoiceLines.NORMAL : voiceLines).max;
	}

	private static DialogueConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				DialogueConfig loaded = GSON.fromJson(reader, DialogueConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				Cim.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, e);
			}
		}
		DialogueConfig defaults = new DialogueConfig();
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
