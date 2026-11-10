package craftingismagic.cim.verse;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * Colors a Change verse has laid over a pony (design doc: Verse, Change): "your mane is now green". Kept
 * apart from their genetics on purpose - the spell changes how they look, not who they are, so their
 * foals still inherit their real colors, and undoing it ("as it was") simply lifts the change.
 * <p>
 * Parts: {@code mane} (mane and tail), {@code coat}, {@code eyes}. Never the aura - that's a reflection
 * of the soul, and no ordinary verse can change it.
 */
public final class SpellColors {
	public static final String MANE = "mane";
	public static final String COAT = "coat";
	public static final String EYES = "eyes";

	/**
	 * @param rgb     0xRRGGBB
	 * @param expires the world time it wears off, or -1: until undone
	 */
	public record Tint(int rgb, long expires) {
		public static final Codec<Tint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("rgb").forGetter(Tint::rgb),
				Codec.LONG.optionalFieldOf("expires", -1L).forGetter(Tint::expires)
		).apply(instance, Tint::new));

		public static final PacketCodec<ByteBuf, Tint> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, Tint::rgb,
				PacketCodecs.VAR_LONG, Tint::expires,
				Tint::new);
	}

	/** Part to tint. Empty is never stored - the attachment is removed instead. */
	public record Colors(Map<String, Tint> parts) {
		public static final Codec<Colors> CODEC = Codec.unboundedMap(Codec.STRING, Tint.CODEC).xmap(Colors::new, Colors::parts);
		public static final PacketCodec<ByteBuf, Colors> PACKET_CODEC = PacketCodecs.<ByteBuf, String, Tint, Map<String, Tint>>map(HashMap::new,
				PacketCodecs.STRING, Tint.PACKET_CODEC).xmap(Colors::new, Colors::parts);

		public Integer rgb(String part) {
			Tint tint = parts.get(part);
			return tint == null ? null : tint.rgb();
		}
	}

	public static final AttachmentType<Colors> COLORS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "spell_colors"),
			builder -> builder.persistent(Colors.CODEC)
					.copyOnDeath()
					.syncWith(Colors.PACKET_CODEC, AttachmentSyncPredicate.all()));

	private SpellColors() {
	}

	/** Changes that wore off while the pony was away (unloaded, or the server was off) are lifted when they come back. */
	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> prune(entity, world.getTime()));
	}

	public static Colors get(Entity entity) {
		return entity.getAttached(COLORS);
	}

	public static void set(Entity entity, String part, int rgb, long expires) {
		Colors current = get(entity);
		Map<String, Tint> parts = current == null ? new HashMap<>() : new HashMap<>(current.parts());
		parts.put(part, new Tint(rgb & 0xFFFFFF, expires));
		entity.setAttached(COLORS, new Colors(Map.copyOf(parts)));
	}

	/** Lifts the change on {@code part}, or on everything when {@code part} is null. Returns whether anything was changed. */
	public static boolean clear(Entity entity, String part) {
		Colors current = get(entity);
		if (current == null) return false;
		if (part == null) {
			entity.removeAttached(COLORS);
			return true;
		}
		if (!current.parts().containsKey(part)) return false;
		Map<String, Tint> parts = new HashMap<>(current.parts());
		parts.remove(part);
		if (parts.isEmpty()) entity.removeAttached(COLORS);
		else entity.setAttached(COLORS, new Colors(Map.copyOf(parts)));
		return true;
	}

	/** Lifts every change whose time is up. */
	public static void prune(Entity entity, long now) {
		Colors current = get(entity);
		if (current == null) return;
		for (Map.Entry<String, Tint> entry : current.parts().entrySet()) {
			if (entry.getValue().expires() >= 0 && entry.getValue().expires() <= now) clear(entity, entry.getKey());
		}
	}
}
