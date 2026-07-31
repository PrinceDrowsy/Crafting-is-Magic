package craftingismagic.cim.pegasus;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import craftingismagic.cim.network.SonicRainboomS2CPayload;

/**
 * Shared trigger for the Sonic Rainboom effect (sound + the Veil/Quasar
 * rainbow particle burst), usable at any world position - not just where a
 * dashing player happens to be, so the dev debug stick can fire it at a
 * distance for easier visual critique.
 */
public final class SonicRainboomEffect {
	private SonicRainboomEffect() {
	}

	public static void trigger(ServerWorld world, Vec3d pos) {
		// TEMPORARY placeholder sound until real show sound effects are sourced. Boom + a magical
		// pulse layered underneath - the show's own sonic rainboom sound is a reused "big magic
		// shockwave" sting (also used for Discord's defeat, the changelings' expulsion, etc.), not
		// just a plain sonic boom, but the actual show audio can't be used here (copyright) and
		// hasn't been sourced through any legitimate means yet.
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS,
				4.0F, 1.0F);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS,
				2.0F, 1.2F);

		SonicRainboomS2CPayload payload = new SonicRainboomS2CPayload(pos.x, pos.y, pos.z);
		for (ServerPlayerEntity nearby : PlayerLookup.around(world, pos, 128.0)) {
			ServerPlayNetworking.send(nearby, payload);
		}
	}
}
