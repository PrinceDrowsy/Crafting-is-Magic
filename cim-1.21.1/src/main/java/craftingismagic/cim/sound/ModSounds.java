package craftingismagic.cim.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** Custom (non-vanilla) sound events used by the mod's abilities. */
public final class ModSounds {
	public static final SoundEvent LEVITATION_PICKUP = register("levitation.pickup");
	public static final SoundEvent LEVITATION_LOOP_1 = register("levitation.loop_1");
	public static final SoundEvent LEVITATION_LOOP_2 = register("levitation.loop_2");
	public static final SoundEvent LEVITATION_RELEASE = register("levitation.release");
	/** The grief moment's music: gentle vanilla tracks (Comforting Memories, Mice on Venus, Sweden) - no new audio. */
	public static final RegistryEntry<SoundEvent> MUSIC_GRIEF = Registries.SOUND_EVENT.getEntry(register("music.grief"));

	private ModSounds() {
	}

	/** No-op call that forces this class's static initializers (and thus registration) to run. */
	public static void bootstrap() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.of(Cim.MOD_ID, name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
