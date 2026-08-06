package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Four stages - {@code BABY}, {@code CHILD}, {@code TEENAGER}, {@code ADULT} - replacing vanilla
 * Minecraft's plain baby/adult split (see README, Life Stages). Baby maps onto vanilla's own real
 * baby-villager state; Child and Teenager are new stages this mod layers on top once vanilla's own
 * growth timer would otherwise have made a baby a full adult (see {@link VillagerFamilyHandler} and
 * the growth mixin). Only Adult reads {@link PonyGeneticsAttachment.PonyGenetics#bodyType} for its
 * MineLP model - Baby/Child both use the Foal preset (Baby additionally scaled down further, see
 * {@code CimVillagerPonyRenderer}), Teenager uses Yearling.
 */
public enum LifeStage {
	BABY,
	CHILD,
	TEENAGER,
	ADULT;

	public static final Codec<LifeStage> CODEC = Codec.STRING.xmap(LifeStage::valueOf, LifeStage::name);
	public static final PacketCodec<ByteBuf, LifeStage> PACKET_CODEC =
			PacketCodecs.STRING.xmap(LifeStage::valueOf, LifeStage::name).cast();
}
