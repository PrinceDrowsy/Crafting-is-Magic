package craftingismagic.cim.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * A player's chosen Stamina bar colors - the fill color and the background/empty-notch color are
 * independently customizable (full RGB, not a curated palette) so a player can pick a combination
 * that doesn't clash, rather than being stuck with one fixed accent color. The first piece of a
 * general "player personalization color" concept - a future pass could let a Unicorn's magic aura
 * (MineLP's {@code PonyData} already has an unused {@code DEFAULT_MAGIC_COLOR} slot, confirmed in
 * {@code CimVillagerPonyRenderer.buildPonyData}) read the same kind of stored preference, though that
 * tie-in isn't built yet.
 * <p>
 * {@code Entity}-typed and owner-only synced, matching {@link StaminaAttachment}'s own shape exactly.
 */
public final class StaminaColorAttachment {
	private static final int DEFAULT_FILL_COLOR = 0xFFE8A33D;
	private static final int DEFAULT_BACKGROUND_COLOR = 0xFF3B4252;

	private static final AttachmentSyncPredicate SYNC_TO_OWNER = (target, player) -> target == player;

	public static final AttachmentType<StaminaColors> STAMINA_COLORS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "stamina_colors"),
			builder -> builder.persistent(StaminaColors.CODEC)
					.syncWith(StaminaColors.PACKET_CODEC, SYNC_TO_OWNER));

	private StaminaColorAttachment() {
	}

	public static StaminaColors getColors(Entity entity) {
		return entity.hasAttached(STAMINA_COLORS) ? entity.getAttached(STAMINA_COLORS)
				: new StaminaColors(DEFAULT_FILL_COLOR, DEFAULT_BACKGROUND_COLOR);
	}

	public static void setColors(Entity entity, StaminaColors colors) {
		entity.setAttached(STAMINA_COLORS, colors);
	}

	/** Both colors are opaque ARGB ints (alpha always {@code 0xFF} - the player only ever chooses an
	 * RRGGBB hex value, alpha is filled in when parsing it client-side). */
	public record StaminaColors(int fillColor, int backgroundColor) {
		public static final Codec<StaminaColors> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("fill_color").forGetter(StaminaColors::fillColor),
				Codec.INT.fieldOf("background_color").forGetter(StaminaColors::backgroundColor)
		).apply(instance, StaminaColors::new));

		public static final PacketCodec<RegistryByteBuf, StaminaColors> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER.cast(), StaminaColors::fillColor,
				PacketCodecs.INTEGER.cast(), StaminaColors::backgroundColor,
				StaminaColors::new);
	}
}
