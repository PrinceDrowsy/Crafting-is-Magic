package craftingismagic.cim.villager;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * A discrete label for the player's relationship with a villager (or, since this attachment is
 * {@code Entity}-typed, a hostile NPC too - see {@code RoguePonyEntity}), layered on top of the
 * continuous {@code affection} number in {@link FriendshipAttachment} the same way a real
 * relationship has both a "how close are we" feeling and a nameable status. {@code PARENT}/{@code
 * CHILD} are traditional/literal family from the player's own perspective (genetic or adopted) -
 * distinct from {@link PonyFamilyAttachment}, which is strictly NPC-to-NPC lineage and never
 * involves the player, but meant to read as real kinship, not a metaphor. {@code MENTOR}/{@code
 * MENTEE} is the "found family" pairing instead (the Rainbow Dash/Scootaloo dynamic already
 * described in the README's Mentor/mentee section) - deliberately the opposite framing from
 * Parent/Child, not a synonym for it. {@code FRENEMIES} is for villain-leaning players who've
 * befriended a hostile NPC instead of fighting it - a direct reference to the MLP Season 9 episode
 * of the same name, and the mechanical hook that makes a befriended {@code RoguePonyEntity} stop
 * targeting that player.
 * <p>
 * Not yet auto-assigned by any real gameplay trigger for villagers - today {@code FRIEND}/etc. on
 * a villager is only ever set directly through the Creative-only villager debug editor. The README
 * already has real, still-tentative candidates for wiring some of these up for real (the
 * Friendship Bracelet for {@code FRIEND}, an affection threshold for {@code MENTOR}/{@code
 * MENTEE}) - this enum exists ahead of that automatic-assignment work, not instead of it.
 * {@code ENEMY}/{@code FRENEMIES} on a {@code RoguePonyEntity} specifically ARE auto-assigned for
 * real (see {@code RoguePonyEntity}'s target-selection logic), the one case where this field is
 * more than manual-only today.
 */
public enum RelationshipType {
	ACQUAINTANCE,
	FRIEND,
	BEST_FRIEND,
	PARENT,
	CHILD,
	MENTOR,
	MENTEE,
	ROMANTIC,
	ENEMY,
	FRENEMIES;

	public static final Codec<RelationshipType> CODEC = Codec.STRING.xmap(RelationshipType::valueOf, RelationshipType::name);
	public static final PacketCodec<ByteBuf, RelationshipType> PACKET_CODEC =
			PacketCodecs.STRING.xmap(RelationshipType::valueOf, RelationshipType::name).cast();
}
