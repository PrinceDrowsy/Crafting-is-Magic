package craftingismagic.cim.item;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import craftingismagic.cim.Cim;

/**
 * A Friendship Bracelet (README: Becoming friends, concretely). Crafted plain, it's given as a gift -
 * and only taken once the pony feels close enough. Then the player gets one of their own back,
 * named for the friend and beaded in their colors ({@link Friend}).
 */
public class FriendshipBraceletItem extends Item {
	/**
	 * Who a bracelet is from - and its beads, in their colors.
	 *
	 * @param beads 0xRRGGBB, the friend's mane
	 * @param band  0xRRGGBB, the friend's coat
	 */
	public record Friend(UUID friend, String name, int beads, int band) {
		public static final Codec<Friend> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Uuids.CODEC.fieldOf("friend").forGetter(Friend::friend),
				Codec.STRING.fieldOf("name").forGetter(Friend::name),
				Codec.INT.optionalFieldOf("beads", 0xF59AC8).forGetter(Friend::beads),
				Codec.INT.optionalFieldOf("band", 0xF7F5F2).forGetter(Friend::band)
		).apply(instance, Friend::new));

		public static final PacketCodec<ByteBuf, Friend> PACKET_CODEC = PacketCodec.tuple(
				Uuids.PACKET_CODEC, Friend::friend,
				PacketCodecs.STRING, Friend::name,
				PacketCodecs.INTEGER, Friend::beads,
				PacketCodecs.INTEGER, Friend::band,
				Friend::new);
	}

	public static final ComponentType<Friend> FRIEND = Registry.register(Registries.DATA_COMPONENT_TYPE,
			Identifier.of(Cim.MOD_ID, "friend"),
			ComponentType.<Friend>builder().codec(Friend.CODEC).packetCodec(Friend.PACKET_CODEC).build());

	/** An unbound bracelet's beads: pink and white. */
	public static final int PLAIN_BEADS = 0xF59AC8;
	public static final int PLAIN_BAND = 0xF7F5F2;

	public FriendshipBraceletItem(Settings settings) {
		super(settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		Friend friend = stack.get(FRIEND);
		return friend != null ? Text.translatable("item.cim.friendship_bracelet.of", friend.name()) : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		if (stack.get(FRIEND) == null) {
			tooltip.add(Text.translatable("item.cim.friendship_bracelet.hint").formatted(Formatting.GRAY));
		}
	}
}
