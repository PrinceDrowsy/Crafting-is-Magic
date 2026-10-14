package craftingismagic.cim.debug;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Debug-only movement control for a pony, set from the villager debug editor: hold them in place
 * ({@link Mode#STAY}) or have them walk after you ({@link Mode#FOLLOW}) - handy for staging scenes.
 * Lives only in memory: a restart gives every pony back its own routine.
 */
public final class DebugPonyMovement {
	public enum Mode {
		NORMAL,
		STAY,
		FOLLOW
	}

	private record Order(Mode mode, UUID player) {
	}

	private static final double FOLLOW_SPEED = 0.6;
	/** Follow stops this close, so they don't push into you. */
	private static final int FOLLOW_DISTANCE = 2;

	private static final Map<UUID, Order> orders = new HashMap<>();

	private DebugPonyMovement() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DebugPonyMovement::tick);
	}

	public static void set(ServerPlayerEntity player, Entity pony, Mode mode) {
		if (mode == Mode.NORMAL) {
			orders.remove(pony.getUuid());
			if (pony instanceof MobEntity mob) {
				mob.getBrain().forget(MemoryModuleType.WALK_TARGET);
			}
		} else {
			orders.put(pony.getUuid(), new Order(mode, player.getUuid()));
		}
	}

	public static Mode mode(Entity pony) {
		Order order = orders.get(pony.getUuid());
		return order == null ? Mode.NORMAL : order.mode();
	}

	private static void tick(MinecraftServer server) {
		if (orders.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Order>> it = orders.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Order> entry = it.next();
			MobEntity pony = find(server, entry.getKey());
			if (pony == null || !pony.isAlive()) {
				it.remove();
				continue;
			}
			if (entry.getValue().mode() == Mode.STAY) {
				pony.getBrain().forget(MemoryModuleType.WALK_TARGET);
				pony.getNavigation().stop();
				Vec3d velocity = pony.getVelocity();
				pony.setVelocity(0, velocity.y, 0);
			} else {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getValue().player());
				if (player == null || player.getWorld() != pony.getWorld()) {
					continue;
				}
				if (pony.squaredDistanceTo(player) > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
					if (pony instanceof VillagerEntity) {
						// Villagers walk where their brain's walk target says; setting it each tick wins over their routine.
						pony.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(player, (float) FOLLOW_SPEED, FOLLOW_DISTANCE));
					} else if (pony.age % 10 == 0) {
						pony.getNavigation().startMovingTo(player, 1.0); // goal-driven mobs, like Rogue Ponies
					}
				} else {
					pony.getBrain().forget(MemoryModuleType.WALK_TARGET);
					pony.getNavigation().stop();
				}
			}
		}
	}

	private static MobEntity find(MinecraftServer server, UUID uuid) {
		for (ServerWorld world : server.getWorlds()) {
			if (world.getEntity(uuid) instanceof MobEntity mob) {
				return mob;
			}
		}
		return null;
	}
}
