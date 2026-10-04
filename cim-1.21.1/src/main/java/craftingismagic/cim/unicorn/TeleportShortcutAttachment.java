package craftingismagic.cim.unicorn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import craftingismagic.cim.Cim;

/**
 * A player's saved Teleport shortcut locations (README: "mark a handful of previously-visited
 * locations as shortcuts, letting them teleport to those without needing direct line of sight").
 * Owner-only sync, same shape as {@link craftingismagic.cim.stamina.StaminaAttachment} - only the
 * owning player's own client ever needs to see their shortcut list, to render the shortcut ring.
 * <p>
 * A slot is represented by presence, not a null/empty placeholder entry - {@link #MAX_SHORTCUTS}
 * caps how many slot indices are valid, but an unset slot simply has no {@link Shortcut} in the
 * list at all, rather than needing a codec-awkward "optional list element."
 */
public final class TeleportShortcutAttachment {
	/** "A handful" per the README's own wording. */
	public static final int MAX_SHORTCUTS = 5;

	public static final AttachmentType<TeleportShortcuts> TELEPORT_SHORTCUTS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "teleport_shortcuts"),
			builder -> builder.persistent(TeleportShortcuts.CODEC)
					.syncWith(TeleportShortcuts.PACKET_CODEC, (target, player) -> target == player));

	private TeleportShortcutAttachment() {
	}

	public static TeleportShortcuts get(Entity entity) {
		return entity.hasAttached(TELEPORT_SHORTCUTS) ? entity.getAttached(TELEPORT_SHORTCUTS) : TeleportShortcuts.EMPTY;
	}

	public static void set(Entity entity, TeleportShortcuts shortcuts) {
		entity.setAttached(TELEPORT_SHORTCUTS, shortcuts);
	}

	public record Shortcut(int slot, RegistryKey<World> dimension, BlockPos pos) {
		public static final Codec<Shortcut> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("slot").forGetter(Shortcut::slot),
				World.CODEC.fieldOf("dimension").forGetter(Shortcut::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Shortcut::pos)
		).apply(instance, Shortcut::new));

		public static final PacketCodec<RegistryByteBuf, Shortcut> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, Shortcut::slot,
				RegistryKey.createPacketCodec(RegistryKeys.WORLD).cast(), Shortcut::dimension,
				BlockPos.PACKET_CODEC.cast(), Shortcut::pos,
				Shortcut::new);
	}

	public record TeleportShortcuts(List<Shortcut> shortcuts) {
		public static final TeleportShortcuts EMPTY = new TeleportShortcuts(List.of());

		public static final Codec<TeleportShortcuts> CODEC = Shortcut.CODEC.listOf()
				.xmap(TeleportShortcuts::new, TeleportShortcuts::shortcuts);

		public static final PacketCodec<RegistryByteBuf, TeleportShortcuts> PACKET_CODEC =
				PacketCodecs.collection(ArrayList::new, Shortcut.PACKET_CODEC)
						.xmap(TeleportShortcuts::new, shortcuts -> new ArrayList<>(shortcuts.shortcuts()));

		public Optional<Shortcut> get(int slot) {
			return shortcuts.stream().filter(shortcut -> shortcut.slot() == slot).findFirst();
		}

		/** Replaces whichever entry (if any) already occupies this slot with a new one at the given location. */
		public TeleportShortcuts withSaved(int slot, RegistryKey<World> dimension, BlockPos pos) {
			List<Shortcut> updated = new ArrayList<>(shortcuts.stream().filter(shortcut -> shortcut.slot() != slot).toList());
			updated.add(new Shortcut(slot, dimension, pos));
			return new TeleportShortcuts(updated);
		}
	}
}
