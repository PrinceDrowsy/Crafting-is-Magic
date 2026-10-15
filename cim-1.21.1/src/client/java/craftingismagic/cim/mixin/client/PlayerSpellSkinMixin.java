package craftingismagic.cim.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import craftingismagic.cim.client.verse.PlayerSpellSkins;
import craftingismagic.cim.verse.SpellColors;

/**
 * A player recolored by a Change verse wears their recolored skin - everywhere the game (and
 * MineLittlePony, which reads player skins through here) asks for it.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerSpellSkinMixin {
	@Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
	private void cim$spellColors(CallbackInfoReturnable<SkinTextures> cir) {
		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
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
