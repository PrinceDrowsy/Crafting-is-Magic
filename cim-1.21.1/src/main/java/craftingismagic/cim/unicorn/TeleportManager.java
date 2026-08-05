package craftingismagic.cim.unicorn;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;

/**
 * Unicorn Teleport: a single tap instantly blinks the player to wherever they're currently
 * looking, up to {@link #MAX_TELEPORT_RANGE} - no aim/confirm gesture (unlike Levitation), since
 * the server re-derives the player's current look direction itself at the moment the tap arrives.
 * No cooldown - the only throttle is however fast the player can tap.
 */
public final class TeleportManager {
	private static final double MAX_TELEPORT_RANGE = 100.0;
	/** How far to back off from a hit block's face so the player lands just in front of it, not clipped inside. */
	private static final double SURFACE_MARGIN = 0.5;

	private TeleportManager() {
	}

	public static void tryTeleport(ServerPlayerEntity player) {
		if (!SpeciesAttachment.hasChosenSpecies(player) || SpeciesAttachment.getSpecies(player) != Species.UNICORN) {
			return;
		}

		HitResult hit = player.raycast(MAX_TELEPORT_RANGE, 1.0F, false);
		Vec3d destination = hit.getPos();
		if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			Vec3d normal = new Vec3d(
					blockHit.getSide().getOffsetX(), blockHit.getSide().getOffsetY(), blockHit.getSide().getOffsetZ());
			destination = destination.add(normal.multiply(SURFACE_MARGIN));
		}

		ServerWorld world = player.getServerWorld();
		Box destinationBox = player.getBoundingBox().offset(destination.subtract(player.getPos()));
		if (!world.isSpaceEmpty(player, destinationBox)) {
			player.sendMessage(Text.literal("Nowhere safe to teleport there"), true);
			return;
		}

		Vec3d origin = player.getPos();
		world.spawnParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1.0, origin.z, 30, 0.3, 0.5, 0.3, 0.05);
		player.requestTeleport(destination.x, destination.y, destination.z);
		world.spawnParticles(ParticleTypes.PORTAL, destination.x, destination.y + 1.0, destination.z, 30, 0.3, 0.5, 0.3, 0.05);
		// Placeholder sound - not the real Unicorn teleport SFX yet, just something in the right
		// spirit (matches how the Sonic Rainboom effect started with a placeholder sound too).
		world.playSound(null, destination.x, destination.y, destination.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
}
