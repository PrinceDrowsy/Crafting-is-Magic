package craftingismagic.cim.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.Cim;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.PonyFamilyAttachment.PonyFamily;

/**
 * Who sounds like what (design doc: Speaking voices). Every pony and player gets a voice the first time
 * one is needed, and keeps it for life:
 * <ul>
 * <li>A foal takes after their parents - one parent's instrument, a pitch between theirs, with a
 * little twist of their own - so families sound alike.</li>
 * <li>A pony with no parents to take after rolls one, flavored by species.</li>
 * <li>Players roll one the same way until the character creator lets them choose; any sound ID
 * works for them, so the debug command can set one already.</li>
 * </ul>
 * Ponies only use instruments the inner voices don't, so a pony never sounds like Kindness or Magic.
 * Foals and teens speak a little higher than the voice they'll grow into.
 */
public final class SpeakingVoices {
	public static final AttachmentType<SpeakingVoice> VOICE = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "speaking_voice"),
			// Survives respawn, and a villager's conversion (zombie and back) - it's still them.
			builder -> builder.persistent(SpeakingVoice.CODEC).copyOnDeath());

	private static final Identifier HARP = noteBlock("harp");
	private static final Identifier BASS = noteBlock("bass");
	private static final Identifier BIT = noteBlock("bit");
	private static final Identifier PLING = noteBlock("pling");
	private static final Identifier COW_BELL = noteBlock("cow_bell");
	private static final Identifier IRON_XYLOPHONE = noteBlock("iron_xylophone");
	private static final Identifier DIDGERIDOO = noteBlock("didgeridoo");

	/** Every instrument a pony may speak with: the note block sounds no inner voice uses. */
	public static final List<Identifier> PONY_INSTRUMENTS = List.of(HARP, BASS, BIT, PLING, COW_BELL, IRON_XYLOPHONE, DIDGERIDOO);

	/** The instruments each species leans toward when there's no family to take after (others stay possible). */
	private static final Map<Species, List<Identifier>> FLAVOR = Map.of(
			Species.EARTH_PONY, List.of(HARP, BASS, COW_BELL, DIDGERIDOO),
			Species.UNICORN, List.of(PLING, IRON_XYLOPHONE, HARP),
			Species.PEGASUS, List.of(PLING, HARP, BIT),
			Species.BAT_PONY, List.of(BIT, BASS, DIDGERIDOO),
			Species.KIRIN, List.of(IRON_XYLOPHONE, COW_BELL, PLING),
			Species.CHANGELING, List.of(BIT, DIDGERIDOO, BASS));

	private SpeakingVoices() {
	}

	/** {@code who}'s own voice - rolled or inherited the first time it's asked for, then kept. */
	public static SpeakingVoice of(Entity who) {
		if (who.hasAttached(VOICE)) {
			return who.getAttached(VOICE);
		}
		SpeakingVoice voice = inherited(who).orElseGet(() -> roll(species(who), who.getRandom()));
		who.setAttached(VOICE, voice);
		return voice;
	}

	public static void set(Entity who, SpeakingVoice voice) {
		who.setAttached(VOICE, voice);
	}

	/** Forgets {@code who}'s voice, so the next {@link #of} rolls (or inherits) a new one. */
	public static void forget(Entity who) {
		who.removeAttached(VOICE);
	}

	/** The voice as it sounds right now: foals and teens speak higher than the voice they'll grow into. */
	public static SpeakingVoice heard(Entity who) {
		SpeakingVoice voice = of(who);
		LifeStage stage = PonyFamilyAttachment.hasFamily(who) ? PonyFamilyAttachment.getFamily(who).lifeStage() : LifeStage.ADULT;
		float lift = switch (stage) {
			case ADULT -> 0f;
			case TEENAGER -> 0.1f;
			default -> 0.25f;
		};
		return voice.withPitch(voice.pitch() + lift);
	}

	/** A foal just born: their voice takes after these two parents (rolling theirs first if need be). */
	public static void inheritAtBirth(Entity foal, Entity parent, Entity otherParent) {
		set(foal, blend(List.of(of(parent), of(otherParent)), foal.getRandom()));
	}

	/** From the parents on record, if any are loaded and have a voice. Empty otherwise. */
	private static Optional<SpeakingVoice> inherited(Entity foal) {
		if (!(foal.getWorld() instanceof ServerWorld world) || !PonyFamilyAttachment.hasFamily(foal)) {
			return Optional.empty();
		}
		PonyFamily family = PonyFamilyAttachment.getFamily(foal);
		List<SpeakingVoice> parents = new ArrayList<>();
		for (UUID parentId : family.parents()) {
			Entity parent = world.getEntity(parentId);
			if (parent != null && parent.hasAttached(VOICE)) {
				parents.add(parent.getAttached(VOICE));
			}
		}
		return parents.isEmpty() ? Optional.empty() : Optional.of(blend(parents, foal.getRandom()));
	}

	/** One parent's instrument, a pitch between the parents', and a little twist of the foal's own. */
	private static SpeakingVoice blend(List<SpeakingVoice> parents, Random random) {
		Identifier instrument = parents.get(random.nextInt(parents.size())).sound();
		float pitch = (float) parents.stream().mapToDouble(SpeakingVoice::pitch).average().orElse(1.0);
		return new SpeakingVoice(instrument, pitch + (random.nextFloat() - 0.5f) * 0.2f);
	}

	/** A fresh voice: usually one of the species' instruments, sometimes any pony instrument; any pitch 0.75-1.4. */
	public static SpeakingVoice roll(Species species, Random random) {
		List<Identifier> flavor = FLAVOR.getOrDefault(species, PONY_INSTRUMENTS);
		List<Identifier> pool = random.nextInt(4) == 0 ? PONY_INSTRUMENTS : flavor;
		return new SpeakingVoice(pool.get(random.nextInt(pool.size())), 0.75f + random.nextFloat() * 0.65f);
	}

	private static Species species(Entity who) {
		return SpeciesAttachment.hasChosenSpecies(who) ? SpeciesAttachment.getSpecies(who) : Species.EARTH_PONY;
	}

	private static Identifier noteBlock(String instrument) {
		return Identifier.ofVanilla("block.note_block." + instrument);
	}
}
