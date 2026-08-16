package craftingismagic.cim.client.rogue;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.util.Identifier;

import craftingismagic.cim.entity.CimEntities;
import craftingismagic.cim.rogue.RoguePonyEntity;

/**
 * Vanilla-only stand-in for {@link RoguePonyEntity} when MineLittlePony isn't installed - registered
 * only in that case (see {@code CimClient}), so a Rogue Pony always has SOME renderer instead of
 * leaving {@link CimEntities#ROGUE_PONY} with none at all, which crashes the client with a
 * null-renderer NPE the moment one enters view (confirmed the hard way: {@link CimRoguePonyRenderer}
 * is the only thing that ever registers a renderer for this entity type, and it's gated behind
 * MineLP being loaded). Not meant to resemble a pony - just a safe placeholder so the "optional
 * dependency" claim in {@code gradle.properties} is actually true.
 */
public final class RoguePonyFallbackRenderer extends MobEntityRenderer<RoguePonyEntity, PigEntityModel<RoguePonyEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/pig/pig.png");

	public RoguePonyFallbackRenderer(EntityRendererFactory.Context context) {
		super(context, new PigEntityModel<>(context.getPart(EntityModelLayers.PIG)), 0.7f);
	}

	@Override
	public Identifier getTexture(RoguePonyEntity entity) {
		return TEXTURE;
	}

	public static void register() {
		EntityRendererRegistry.register(CimEntities.ROGUE_PONY, RoguePonyFallbackRenderer::new);
	}
}
