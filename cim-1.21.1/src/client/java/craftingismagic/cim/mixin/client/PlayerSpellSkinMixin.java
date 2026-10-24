package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import craftingismagic.cim.client.verse.PlayerSpellSkins;
import craftingismagic.cim.verse.SpellColors;

/**
 * A player recolored by a Change verse wears their recolored skin - everywhere the game (and
 * MineLittlePony, which reads player skins through here) asks for it.
 * <p>
 * Also, in the development client only: the dev account is offline with a random default skin, and
 * MineLittlePony picks the pony type from the skin - so you might not have a horn to test unicorn magic
 * on. There, your own player on a default skin wears MineLittlePony's unicorn default (Makena) instead.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerSpellSkinMixin {
	private static final Identifier DEV_UNICORN = Identifier.of("minelittlepony", "textures/entity/player/wide/makena_pony.png");

	@Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
	private void cim$spellColors(CallbackInfoReturnable<SkinTextures> cir) {
		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
		if (FabricLoader.getInstance().isDevelopmentEnvironment() && FabricLoader.getInstance().isModLoaded("minelp")
				&& self == MinecraftClient.getInstance().player && cir.getReturnValue().textureUrl() == null) {
			SkinTextures original = cir.getReturnValue();
			cir.setReturnValue(new SkinTextures(DEV_UNICORN, null, original.capeTexture(), original.elytraTexture(),
					SkinTextures.Model.WIDE, original.secure()));
		}
		SpellColors.Colors colors = SpellColors.get(self);
		if (colors == null) return;
		SkinTextures original = cir.getReturnValue();
		Identifier recolored = PlayerSpellSkins.resolve(self, original.texture(), colors);
		if (!recolored.equals(original.texture())) {
			cir.setReturnValue(new SkinTextures(recolored, original.textureUrl(), original.capeTexture(), original.elytraTexture(),
					original.model(), original.secure()));
		}
	}
}
