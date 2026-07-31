package craftingismagic.cim.pegasus;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.network.PegasusChargeEndS2CPayload;
import craftingismagic.cim.network.PegasusChargeStartS2CPayload;

/**
 * The real Sonic Rainboom trigger: once already flying fast, double-tapping
 * forward again (the same trigger a normal dash uses) starts a charge-up
 * instead of an instant boost - hold forward for the full charge duration
 * and it releases into a slingshot burst of speed that breaks the sound
 * barrier, triggering {@link SonicRainboomEffect}. Releasing forward
 * early, dropping below flying speed, or landing cancels the charge with
 * no effect. A plain dash (not already fast enough to be charge-eligible)
 * still falls through to {@link PegasusDashManager} unaffected.
 */
public final class PegasusRainboomCharge {
	/** Speed a player already has to be moving at (from a prior dash/dive) to start a charge at all. */
	private static final double CHARGE_ELIGIBLE_SPEED = 1.0;
	/** Charge cancels if speed drops below this while charging - held is not enough, they have to stay fast. */
	private static final double CHARGE_CANCEL_SPEED = 0.6;
	private static final int CHARGE_DURATION_TICKS = 60;
	/** Speed of the slingshot burst released on a completed charge. */
	private static final double SLINGSHOT_SPEED = 2.6;
	/** How often (in ticks) the looping charge-up sound retriggers - a placeholder "static crackle" until real SFX are sourced. */
	private static final int CRACKLE_SOUND_INTERVAL_TICKS = 3;

	private static final Map<UUID, Integer> chargeTicks = new HashMap<>();

	private PegasusRainboomCharge() {
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> iterator = chargeTicks.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			tickPlayer(player, entry, iterator);
		}
	}

	/** @return whether a charge was started (or is already in progress) - if false, the caller should fall through to a normal dash. */
	public static boolean tryStartCharge(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (chargeTicks.containsKey(id)) {
			return true;
		}

		if (!PegasusFlightManager.isActivelyFlying(player) || player.getVelocity().length() < CHARGE_ELIGIBLE_SPEED) {
			return false;
		}

		chargeTicks.put(id, 0);
		broadcast(player, new PegasusChargeStartS2CPayload(player.getId(), CHARGE_DURATION_TICKS));
		return true;
	}

	public static void cancelFromRelease(ServerPlayerEntity player) {
		if (chargeTicks.remove(player.getUuid()) != null) {
			broadcast(player, new PegasusChargeEndS2CPayload(player.getId()));
		}
	}

	public static void cancel(ServerPlayerEntity player) {
		chargeTicks.remove(player.getUuid());
	}

	private static void tickPlayer(ServerPlayerEntity player, Map.Entry<UUID, Integer> entry,
			Iterator<Map.Entry<UUID, Integer>> iterator) {
		if (!PegasusFlightManager.isActivelyFlying(player) || player.getVelocity().length() < CHARGE_CANCEL_SPEED) {
			iterator.remove();
			broadcast(player, new PegasusChargeEndS2CPayload(player.getId()));
			return;
		}

		int ticks = entry.getValue() + 1;
		if (ticks % CRACKLE_SOUND_INTERVAL_TICKS == 0) {
			// TEMPORARY placeholder sound (same as the Rainboom's own sound - see memory note on
			// that) - vanilla has no dedicated "electric crackle" sound, this is the closest
			// "building energy" texture available, retriggered to build a continuous swell.
			Vec3d pos = player.getPos();
			player.getServerWorld().playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
					SoundCategory.PLAYERS, 1.0F, 1.3F);
		}

		if (ticks >= CHARGE_DURATION_TICKS) {
			iterator.remove();
			release(player);
			return;
		}

		entry.setValue(ticks);
	}

	private static void release(ServerPlayerEntity player) {
		Vec3d launch = player.getRotationVec(1.0F).multiply(SLINGSHOT_SPEED);
		player.setVelocity(launch);
		PegasusVelocitySync.push(player);

		broadcast(player, new PegasusChargeEndS2CPayload(player.getId()));
		SonicRainboomEffect.trigger(player.getServerWorld(), player.getPos());
	}

	private static void broadcast(ServerPlayerEntity player, CustomPayload payload) {
		ServerWorld world = player.getServerWorld();
		for (ServerPlayerEntity nearby : PlayerLookup.around(world, player.getPos(), 128.0)) {
			ServerPlayNetworking.send(nearby, payload);
		}
	}
}
