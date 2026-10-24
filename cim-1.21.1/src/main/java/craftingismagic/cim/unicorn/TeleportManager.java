package craftingismagic.cim.unicorn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import craftingismagic.cim.network.TeleportChargeEndS2CPayload;
import craftingismagic.cim.network.TeleportChargeStartS2CPayload;
import craftingismagic.cim.species.Species;
import craftingismagic.cim.species.SpeciesAttachment;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.unicorn.TeleportShortcutAttachment.Shortcut;
import craftingismagic.cim.unicorn.TeleportShortcutAttachment.TeleportShortcuts;

/**
 * Unicorn Teleport: holding the activation key while looking at a destination charges up a blink
 * there, mirroring {@code PegasusRainboomCharge}'s own hold-to-charge shape - both the charge time
 * and its Stamina cost scale up with distance (README: "a high-cost spell with a charge time...
 * Both the charge time and the Stamina cost scale up with the distance to the target location").
 * Releasing early, or running out of Stamina before the charge completes, fizzles the spell -
 * whatever Stamina was already spent stays spent, same as an incomplete Rainboom charge.
 * <p>
 * The destination (and its distance, which everything else is derived from) is locked in the
 * instant the charge starts, from a single raycast - not re-aimed every tick, so a charge doesn't
 * change its own required time/cost partway through just because the player looked somewhere else.
 * A shortcut ({@link #useShortcut}) skips that raycast entirely and charges toward the saved
 * position by straight-line distance instead, per the README's own shortcut spec.
 */
public final class TeleportManager {
	private static final double MAX_TELEPORT_RANGE = 100.0;
	/** How far to back off from a hit block's face so the player lands just in front of it, not clipped inside. */
	private static final double SURFACE_MARGIN = 0.5;

	private static final float BASE_REQUIRED_STAMINA = 5.0F;
	private static final double STAMINA_PER_DISTANCE = 0.3;
	private static final double TICKS_PER_DISTANCE = 2.0;
	/** However short/close a teleport is, or however much Stamina progression has compressed the
	 * charge curve, it never drops below this - "gradually becomes instant" per the README, not
	 * literally zero-tick. */
	private static final int MIN_CHARGE_TICKS = 2;

	private static final class Charge {
		final Vec3d destination;
		final int totalTicks;
		final float requiredStamina;
		int elapsedTicks = 0;

		Charge(Vec3d destination, int totalTicks, float requiredStamina) {
			this.destination = destination;
			this.totalTicks = totalTicks;
			this.requiredStamina = requiredStamina;
		}
	}

	private static final Map<UUID, Charge> charges = new HashMap<>();

	private TeleportManager() {
	}

	/** @return whether a charge was started (or one is already in progress) - false if this player can't cast Teleport at all right now. */
	public static boolean startCharge(ServerPlayerEntity player) {
		if (!canCastTeleport(player)) {
			return false;
		}
		if (charges.containsKey(player.getUuid())) {
			return true;
		}

		HitResult hit = player.raycast(MAX_TELEPORT_RANGE, 1.0F, false);
		Vec3d destination = hit.getPos();
		if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			Vec3d normal = new Vec3d(
					blockHit.getSide().getOffsetX(), blockHit.getSide().getOffsetY(), blockHit.getSide().getOffsetZ());
			destination = destination.add(normal.multiply(SURFACE_MARGIN));
		}

		beginCharge(player, destination, player.getEyePos().distanceTo(destination));
		return true;
	}

	/** Called on early release of the activation key - a no-op if the charge already finished/fizzled on its own. */
	public static void cancelCharge(ServerPlayerEntity player) {
		if (charges.remove(player.getUuid()) != null) {
			ServerPlayNetworking.send(player, new TeleportChargeEndS2CPayload());
		}
	}

	/** Also used on disconnect/species-loss cleanup - same effect as a voluntary cancel, just without the now-pointless S2C packet. */
	public static void cancel(ServerPlayerEntity player) {
		charges.remove(player.getUuid());
	}

	public static void saveShortcut(ServerPlayerEntity player, int slot) {
		if (!canCastTeleport(player) || slot < 0 || slot >= TeleportShortcutAttachment.MAX_SHORTCUTS) {
			return;
		}

		TeleportShortcuts current = TeleportShortcutAttachment.get(player);
		TeleportShortcuts updated = current.withSaved(slot, player.getServerWorld().getRegistryKey(), player.getBlockPos());
		TeleportShortcutAttachment.set(player, updated);
	}

	public static void useShortcut(ServerPlayerEntity player, int slot) {
		if (!canCastTeleport(player) || charges.containsKey(player.getUuid())) {
			return;
		}

		Optional<Shortcut> shortcut = TeleportShortcutAttachment.get(player).get(slot);
		if (shortcut.isEmpty()) {
			return;
		}

		RegistryKey<World> dimension = shortcut.get().dimension();
		if (!player.getServerWorld().getRegistryKey().equals(dimension)) {
			player.sendMessage(Text.literal("That shortcut isn't in this dimension"), true);
			return;
		}

		Vec3d destination = Vec3d.ofCenter(shortcut.get().pos());
		beginCharge(player, destination, player.getPos().distanceTo(destination));
	}

	public static boolean isCharging(ServerPlayerEntity player) {
		return charges.containsKey(player.getUuid());
	}

	private static boolean canCastTeleport(ServerPlayerEntity player) {
		return SpeciesAttachment.hasChosenSpecies(player) && SpeciesAttachment.getSpecies(player) == Species.UNICORN;
	}

	private static void beginCharge(ServerPlayerEntity player, Vec3d destination, double distance) {
		float requiredStamina = (float) (BASE_REQUIRED_STAMINA + STAMINA_PER_DISTANCE * distance);
		double progressionFactor = StaminaAttachment.getStamina(player).max() / StaminaAttachment.BASE_MAX_STAMINA;
		int totalTicks = (int) Math.max(MIN_CHARGE_TICKS, Math.round(TICKS_PER_DISTANCE * distance / progressionFactor));

		charges.put(player.getUuid(), new Charge(destination, totalTicks, requiredStamina));
		ServerPlayNetworking.send(player, new TeleportChargeStartS2CPayload(totalTicks));
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Charge>> iterator = charges.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Charge> entry = iterator.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			tickPlayer(player, entry.getValue(), iterator);
		}
	}

	private static void tickPlayer(ServerPlayerEntity player, Charge charge, Iterator<Map.Entry<UUID, Charge>> iterator) {
		// Ramps up linearly tick to tick (small at first, bigger near the end) rather than a flat
		// per-tick rate, so the drain visibly races toward the requirement as the charge nears
		// completion - the same "accelerating toward the threshold" shape as a Rainboom charge. Sums
		// to exactly requiredStamina over totalTicks ticks (the standard arithmetic-sequence identity).
		int tickIndex = charge.elapsedTicks + 1;
		float drainThisTick = (float) (charge.requiredStamina * 2.0 * tickIndex / ((double) charge.totalTicks * (charge.totalTicks + 1)));

		if (!StaminaManager.drain(player, drainThisTick)) {
			iterator.remove();
			fizzle(player, "Not enough Stamina to complete the teleport");
			return;
		}

		charge.elapsedTicks = tickIndex;
		if (charge.elapsedTicks >= charge.totalTicks) {
			iterator.remove();
			complete(player, charge);
		}
	}

	private static void fizzle(ServerPlayerEntity player, String message) {
		ServerPlayNetworking.send(player, new TeleportChargeEndS2CPayload());
		player.sendMessage(Text.literal(message), true);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5F, 0.5F);
	}

	private static void complete(ServerPlayerEntity player, Charge charge) {
		ServerWorld world = player.getServerWorld();
		Vec3d destination = charge.destination;
		Box destinationBox = player.getBoundingBox().offset(destination.subtract(player.getPos()));
		if (!world.isSpaceEmpty(player, destinationBox)) {
			fizzle(player, "Nowhere safe to teleport there");
			return;
		}

		ServerPlayNetworking.send(player, new TeleportChargeEndS2CPayload());
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
