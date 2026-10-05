package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * True-form coloring for a Changeling - deliberately separate from
 * {@link craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics}, since none of that
 * system's researched hue/vividness curve-fitting (calibrated to real pony coats) applies here:
 * a Changeling's true-form coat is a single fixed color for every individual (see {@code
 * ChangelingTrueFormSkins}), not genetics-driven at all, while the shell and eyes/wings each get
 * one plain uniform-random hue/vividness roll instead. Only meaningful for a Changeling's true
 * form - its disguise form uses ordinary {@link PonyGeneticsAttachment} coloring like any other
 * species, per the rule that typical pony genetic rules apply to the disguise but not the true form.
 */
public final class ChangelingAppearanceAttachment {
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<ChangelingAppearance> APPEARANCE = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "changeling_appearance"),
			builder -> builder.persistent(ChangelingAppearance.CODEC)
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath()
					.syncWith(ChangelingAppearance.PACKET_CODEC, SYNC_TO_TRACKERS));

	private ChangelingAppearanceAttachment() {
	}

	public static boolean hasAppearance(Entity entity) {
		return entity.hasAttached(APPEARANCE);
	}

	public static ChangelingAppearance getAppearance(Entity entity) {
		return entity.getAttached(APPEARANCE);
	}

	public static void setAppearance(Entity entity, ChangelingAppearance appearance) {
		entity.setAttached(APPEARANCE, appearance);
	}

	/** @param eyeWingHue/eyeWingVividness shared by both the eyes and wings on purpose - one texture
	 *  layer covers both regions specifically so they always match. */
	public record ChangelingAppearance(float shellHue, float shellVividness, float eyeWingHue, float eyeWingVividness) {
		public static final Codec<ChangelingAppearance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("shell_hue").forGetter(ChangelingAppearance::shellHue),
				Codec.FLOAT.fieldOf("shell_vividness").forGetter(ChangelingAppearance::shellVividness),
				Codec.FLOAT.fieldOf("eye_wing_hue").forGetter(ChangelingAppearance::eyeWingHue),
				Codec.FLOAT.fieldOf("eye_wing_vividness").forGetter(ChangelingAppearance::eyeWingVividness)
		).apply(instance, ChangelingAppearance::new));

		public static final PacketCodec<ByteBuf, ChangelingAppearance> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.FLOAT, ChangelingAppearance::shellHue,
				PacketCodecs.FLOAT, ChangelingAppearance::shellVividness,
				PacketCodecs.FLOAT, ChangelingAppearance::eyeWingHue,
				PacketCodecs.FLOAT, ChangelingAppearance::eyeWingVividness,
				ChangelingAppearance::new);
	}
}
