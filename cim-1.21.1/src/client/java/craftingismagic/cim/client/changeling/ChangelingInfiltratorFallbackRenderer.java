package craftingismagic.cim.client.changeling;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.util.Identifier;

import craftingismagic.cim.changeling.ChangelingInfiltratorEntity;
import craftingismagic.cim.entity.CimEntities;

/**
 * Vanilla-only stand-in for {@link ChangelingInfiltratorEntity} when MineLittlePony isn't
 * installed - same reasoning as {@code RoguePonyFallbackRenderer}: without this, the entity has no
 * renderer at all and crashes the client the moment one enters view.
 */
public final class ChangelingInfiltratorFallbackRenderer extends MobEntityRenderer<ChangelingInfiltratorEntity, PigEntityModel<ChangelingInfiltratorEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/pig/pig.png");

	public ChangelingInfiltratorFallbackRenderer(EntityRendererFactory.Context context) {
		super(context, new PigEntityModel<>(context.getPart(EntityModelLayers.PIG)), 0.7f);
	}

	@Override
	public Identifier getTexture(ChangelingInfiltratorEntity entity) {
		return TEXTURE;
	}

	public static void register() {
		EntityRendererRegistry.register(CimEntities.CHANGELING_INFILTRATOR, ChangelingInfiltratorFallbackRenderer::new);
	}
}
