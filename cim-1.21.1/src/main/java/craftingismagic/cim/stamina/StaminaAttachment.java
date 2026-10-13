package craftingismagic.cim.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Stamina - "the cross-species resource shared by all species' abilities" (see the README's Magic
 * System overview). {@code Entity}-typed like {@link craftingismagic.cim.species.SpeciesAttachment},
 * but not gated behind an explicit "has chosen" flag the way species is - every player simply has
 * Stamina by default, no opt-in step. {@link #getStamina} returns a full default value when nothing's
 * been set yet rather than requiring initialization, the same "missing means the sensible default"
 * shape {@link craftingismagic.cim.villager.FriendshipAttachment.RelationshipData#EMPTY} already uses.
 * <p>
 * Server-side storage only - not synced through the attachment, because regen and continuous drains
 * write it every tick. {@link StaminaSync} sends it to the owning player on meaningful changes
 * instead; the client reads {@code ClientStaminaState}, never this attachment.
 */
public final class StaminaAttachment {
	/** Public so other systems (e.g. Levitation's Stamina-scaled throw power) have a stable
	 * reference point for "a fresh, un-grown pool" rather than duplicating the magic number. */
	public static final float BASE_MAX_STAMINA = 100.0f;

	public static final AttachmentType<Stamina> STAMINA = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "stamina"),
			builder -> builder.persistent(Stamina.CODEC)
					// Survives respawn - otherwise dying would wipe this player state (and reset Stamina, a free
					// way out of Exhaustion).
					.copyOnDeath());

	private StaminaAttachment() {
	}

	public static Stamina getStamina(Entity entity) {
		return entity.hasAttached(STAMINA) ? entity.getAttached(STAMINA) : new Stamina(BASE_MAX_STAMINA, BASE_MAX_STAMINA, false);
	}

	public static void setStamina(Entity entity, Stamina stamina) {
		entity.setAttached(STAMINA, stamina);
	}

	/**
	 * @param awaitingSleep True from the moment {@code current} hits exactly 0 until the player next
	 * wakes from a full night's sleep (see {@code StaminaManager}) - regen stays locked at 0 and the
	 * max-growth earned by the depletion is held back the whole time, both released together on
	 * waking. A new codec field on an existing record needs {@code optionalFieldOf} with a default,
	 * not {@code fieldOf}, or worlds saved before this field existed fail to parse on load.
	 */
	public record Stamina(float current, float max, boolean awaitingSleep) {
		public static final Codec<Stamina> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("current").forGetter(Stamina::current),
				Codec.FLOAT.fieldOf("max").forGetter(Stamina::max),
				Codec.BOOL.optionalFieldOf("awaiting_sleep", false).forGetter(Stamina::awaitingSleep)
		).apply(instance, Stamina::new));

		public static final PacketCodec<RegistryByteBuf, Stamina> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.FLOAT.cast(), Stamina::current,
				PacketCodecs.FLOAT.cast(), Stamina::max,
				PacketCodecs.BOOL.cast(), Stamina::awaitingSleep,
				Stamina::new);
	}
}
