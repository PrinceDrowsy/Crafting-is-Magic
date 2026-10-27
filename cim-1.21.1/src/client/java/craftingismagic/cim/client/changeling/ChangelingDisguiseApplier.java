package craftingismagic.cim.client.changeling;

import java.util.Optional;
import java.util.UUID;

import com.minelittlepony.api.pony.Pony;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Resolves what a Changeling currently shapeshifted into a specific player should actually look
 * like - the "impersonate Player X" dispatch point (see the Changeling Voice Mimicry plan's own
 * note on why this can't just be a skin-texture swap the way the SkinWalker mod this was inspired
 * by does it: every character here renders as a pony, players included).
 * <p>
 * <b>MineLP path (implemented)</b>: {@link Pony#getManager()}'s {@code getPony(PlayerEntity)}
 * already resolves a real player's own currently-active pony appearance - the exact texture and
 * {@code PonyData} (race, tail, size) MineLP itself renders them with, live, every call. Handing
 * that same {@link Pony} straight to the Changeling's own renderer reproduces the target byte-for-
 * byte, the same trick the source mod uses ({@code player.getSkinTexture()}/{@code getModel()}),
 * just going through MineLP's own resolution instead of vanilla's.
 * <p>
 * <b>Figura path (not implemented)</b>: a disguised Changeling should copy a target's
 * actual Figura avatar when that's the rendering backend they use instead of MineLP - flagged as a
 * real follow-up, not done here. It's blocked on a prerequisite that doesn't exist in this codebase
 * yet regardless: the README documents a player choosing MineLP vs. Figura for their own character,
 * but that choice has no attachment/setting anywhere in the project to actually branch on.
 */
public final class ChangelingDisguiseApplier {
	private ChangelingDisguiseApplier() {
	}

	/** @return the target's live pony appearance, or empty if it can't be resolved right now (the
	 * target isn't loaded on this client, etc.) - callers should fall back to the Changeling's own
	 * natural form in that case, not render nothing. */
	public static Optional<Pony> resolveDisguise(UUID targetUuid, World world) {
		if (world == null) {
			return Optional.empty();
		}
		PlayerEntity target = world.getPlayerByUuid(targetUuid);
		if (target == null) {
			return Optional.empty();
		}
		return Optional.of(Pony.getManager().getPony(target));
	}
}
