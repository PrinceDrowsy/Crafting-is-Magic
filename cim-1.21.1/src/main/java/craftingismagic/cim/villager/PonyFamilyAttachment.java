package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * A villager's real family - {@link LifeStage}, how long it's been in that stage, and links to
 * whichever other villagers are its parents/partner (see Village Families in the README). One
 * value per villager, same shape as {@link PonyGeneticsAttachment}.
 * <p>
 * Villages have no "this batch of villagers belongs together" signal anywhere in vanilla or Fabric
 * API (confirmed research) - family membership is decided per-villager, proximity-based, the
 * moment each one loads (see {@link VillagerFamilyHandler}), not as a single batch operation.
 */
public final class PonyFamilyAttachment {
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<PonyFamily> FAMILY = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "pony_family"),
			builder -> builder.persistent(PonyFamily.CODEC)
					// For a mob, "death" also covers conversion (villager -> zombie villager -> cured villager,
					// villager -> witch) - without this, curing a pony would wipe it back to a stranger.
					.copyOnDeath()
					.syncWith(PonyFamily.PACKET_CODEC, SYNC_TO_TRACKERS));

	private PonyFamilyAttachment() {
	}

	public static boolean hasFamily(Entity entity) {
		return entity.hasAttached(FAMILY);
	}

	public static PonyFamily getFamily(Entity entity) {
		return entity.getAttached(FAMILY);
	}

	public static void setFamily(Entity entity, PonyFamily family) {
		entity.setAttached(FAMILY, family);
		// Every family write passes through here, so this is the one place that keeps the growth
		// tick's tracked set of Child/Teenager villagers in step (see VillagerFamilyHandler).
		VillagerFamilyHandler.onFamilyChanged(entity);
	}

	/**
	 * @param growthTicks ticks accumulated in the current stage; meaningless once {@code lifeStage}
	 *                    is {@link LifeStage#ADULT}.
	 * @param parents     0 (orphan or no parent found nearby at spawn), 1 (single parent), or 2
	 *                    (a paired couple) - who raised this villager.
	 * @param partner     0 or 1 - this villager's own paired co-parent, if it's an Adult that got
	 *                    matched up (separate from {@code parents}, which is about its own upbringing).
	 * @param orphan      true only when the explicit orphan roll landed at spawn - distinct from
	 *                    simply not having found a nearby parent to join.
	 */
	public record PonyFamily(LifeStage lifeStage, int growthTicks, List<UUID> parents, List<UUID> partner, boolean orphan) {
		public static final Codec<PonyFamily> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				LifeStage.CODEC.fieldOf("life_stage").forGetter(PonyFamily::lifeStage),
				Codec.INT.fieldOf("growth_ticks").forGetter(PonyFamily::growthTicks),
				Uuids.CODEC.listOf().fieldOf("parents").forGetter(PonyFamily::parents),
				Uuids.CODEC.listOf().fieldOf("partner").forGetter(PonyFamily::partner),
				Codec.BOOL.fieldOf("orphan").forGetter(PonyFamily::orphan)
		).apply(instance, PonyFamily::new));

		public static final PacketCodec<ByteBuf, PonyFamily> PACKET_CODEC = PacketCodec.tuple(
				LifeStage.PACKET_CODEC, PonyFamily::lifeStage,
				PacketCodecs.INTEGER, PonyFamily::growthTicks,
				PacketCodecs.collection(ArrayList::new, Uuids.PACKET_CODEC), PonyFamily::parents,
				PacketCodecs.collection(ArrayList::new, Uuids.PACKET_CODEC), PonyFamily::partner,
				PacketCodecs.BOOL, PonyFamily::orphan,
				PonyFamily::new);
	}
}
