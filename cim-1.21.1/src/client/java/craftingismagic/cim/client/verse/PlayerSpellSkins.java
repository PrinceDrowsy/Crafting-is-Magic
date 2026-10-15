package craftingismagic.cim.client.verse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import craftingismagic.cim.Cim;
import craftingismagic.cim.verse.SpellColors;

/**
 * A player changed by a Change verse ("my mane is now green"): their own skin, recolored. A player's
 * look is their pony skin, not genetics, so this finds the skin's own palette - the coat (the body's
 * main color), the mane (the head's overlay layer) and the eyes (the face) - and repaints each
 * matching pixel in the spell's color, keeping its shading. The skin's top-left corner, where
 * MineLittlePony keeps its settings (race, tail, size...), is never touched.
 * <p>
 * Built once per change, like the villagers' genetics skins - never per frame.
 */
public final class PlayerSpellSkins {
	private record Cached(Identifier source, SpellColors.Colors colors, Identifier result) {
	}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();

	private PlayerSpellSkins() {
	}

	public static void register() {
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof AbstractClientPlayerEntity) forget(entity.getUuid());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			for (UUID uuid : CACHE.keySet().toArray(UUID[]::new)) forget(uuid);
		}));
	}

	/** The recolored skin for {@code player} - or {@code source} itself, if it can't be read yet. */
	public static Identifier resolve(AbstractClientPlayerEntity player, Identifier source, SpellColors.Colors colors) {
		Cached cached = CACHE.get(player.getUuid());
		if (cached != null && cached.source().equals(source) && cached.colors().equals(colors)) {
			return cached.result();
		}
		if (!RenderSystem.isOnRenderThread()) {
			return cached != null ? cached.result() : source;
		}
		forget(player.getUuid());
		NativeImage original = read(source);
		if (original == null) {
			return source; // still downloading - tried again next frame
		}
		NativeImage recolored;
		try (original) {
			recolored = recolor(original, colors);
		}
		Identifier id = Identifier.of(Cim.MOD_ID, "dynamic/spell_skin/" + player.getUuidAsString());
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(recolored));
		CACHE.put(player.getUuid(), new Cached(source, colors, id));
		return id;
	}

	private static void forget(UUID uuid) {
		Cached cached = CACHE.remove(uuid);
		if (cached != null && !cached.result().equals(cached.source())) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(cached.result());
		}
	}

	/** A copy of the skin's pixels, from memory if it's there, otherwise read back from the graphics card. */
	private static NativeImage read(Identifier source) {
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		AbstractTexture texture = textures.getTexture(source);
		if (texture instanceof NativeImageBackedTexture backed && backed.getImage() != null) {
			NativeImage image = backed.getImage();
			NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), true);
			copy.copyFrom(image);
			return copy;
		}
		RenderSystem.bindTexture(texture.getGlId());
		int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
		if (width < 64 || height < 32) return null;
		NativeImage image = new NativeImage(width, height, false);
		image.loadFromTextureImage(0, false);
		return image;
	}

	// ---- The palette swap ----

	private static NativeImage recolor(NativeImage source, SpellColors.Colors colors) {
		int width = source.getWidth();
		int height = source.getHeight();
		float scale = width / 64f;
		NativeImage out = new NativeImage(width, height, true);
		out.copyFrom(source);

		float[] coat = dominant(source, scale, 16, 20, 40, 32, null, null);
		float[] mane = dominant(source, scale, 32, 0, 64, 16, coat, null);
		if (mane == null) mane = dominant(source, scale, 0, 0, 32, 16, coat, null);
		float[] eyes = dominant(source, scale, 8, 12, 16, 16, coat, mane);

		Integer coatTint = colors.rgb(SpellColors.COAT);
		Integer maneTint = colors.rgb(SpellColors.MANE);
		Integer eyeTint = colors.rgb(SpellColors.EYES);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (x < 8 * scale && y < 8 * scale) continue; // MineLittlePony's settings pixels
				int abgr = source.getColor(x, y);
				int alpha = abgr >>> 24;
				if (alpha == 0) continue;
				float[] hsv = hsv(abgr);
				boolean face = x >= 8 * scale && x < 16 * scale && y >= 8 * scale && y < 16 * scale;
				float toCoat = distance(hsv, coat);
				float toMane = distance(hsv, mane);
				float toEyes = face ? distance(hsv, eyes) : Float.MAX_VALUE;
				float best = Math.min(toCoat, Math.min(toMane, toEyes));
				if (best == Float.MAX_VALUE) continue;
				float[] reference;
				Integer tint;
				if (best == toEyes) {
					reference = eyes;
					tint = eyeTint;
				} else if (best == toMane) {
					reference = mane;
					tint = maneTint;
				} else {
					reference = coat;
					tint = coatTint;
				}
				if (tint == null) continue;
				out.setColor(x, y, paint(hsv, reference, tint, alpha));
			}
		}
		return out;
	}

	/**
	 * The most common color in a region of the skin (in 64-wide skin pixels), as {hue, saturation,
	 * value} - leaving out anything close to {@code not} or {@code norEither}, and for the eyes, the
	 * pupils and highlights. Null if the region is empty.
	 */
	private static float[] dominant(NativeImage image, float scale, int x0, int y0, int x1, int y1, float[] not, float[] norEither) {
		Map<Integer, Integer> exact = new HashMap<>();
		Map<Integer, Integer> buckets = new HashMap<>();
		boolean eyes = norEither != null;
		for (int x = Math.round(x0 * scale); x < Math.min(image.getWidth(), Math.round(x1 * scale)); x++) {
			for (int y = Math.round(y0 * scale); y < Math.min(image.getHeight(), Math.round(y1 * scale)); y++) {
				int abgr = image.getColor(x, y);
				if (abgr >>> 24 < 128) continue;
				float[] hsv = hsv(abgr);
				if (not != null && distance(hsv, not) < Float.MAX_VALUE) continue;
				if (norEither != null && distance(hsv, norEither) < Float.MAX_VALUE) continue;
				if (eyes && (hsv[2] < 0.18f || hsv[1] < 0.1f && hsv[2] > 0.85f)) continue;
				int rgb = abgr & 0xFFFFFF;
				exact.merge(rgb, 1, Integer::sum);
				buckets.merge(bucket(hsv), 1, Integer::sum);
			}
		}
		if (buckets.isEmpty()) return null;
		int winner = buckets.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		int best = -1;
		int bestCount = 0;
		for (Map.Entry<Integer, Integer> entry : exact.entrySet()) {
			float[] hsv = hsv(entry.getKey() | 0xFF000000);
			if (bucket(hsv) == winner && entry.getValue() > bestCount) {
				best = entry.getKey();
				bestCount = entry.getValue();
			}
		}
		return hsv(best | 0xFF000000);
	}

	private static int bucket(float[] hsv) {
		return Math.min(23, (int) (hsv[0] * 24)) * 100 + Math.min(4, (int) (hsv[1] * 5)) * 10 + Math.min(4, (int) (hsv[2] * 5));
	}

	/** How far a pixel is from a palette color, allowing for its shading - or MAX_VALUE if it isn't that color at all. */
	private static float distance(float[] pixel, float[] reference) {
		if (reference == null) return Float.MAX_VALUE;
		if (pixel[1] < 0.12f || reference[1] < 0.12f) {
			// Whites, grays and blacks: matched by brightness, not hue.
			if (Math.abs(pixel[1] - reference[1]) > 0.15f || Math.abs(pixel[2] - reference[2]) > 0.35f) return Float.MAX_VALUE;
			return Math.abs(pixel[1] - reference[1]) + Math.abs(pixel[2] - reference[2]) * 0.5f;
		}
		float hue = Math.abs(pixel[0] - reference[0]);
		hue = Math.min(hue, 1 - hue);
		float saturation = Math.abs(pixel[1] - reference[1]);
		float shade = reference[2] <= 0.01f ? 1 : pixel[2] / reference[2];
		if (hue > 0.07f || saturation > 0.3f || shade < 0.45f || shade > 1.6f) return Float.MAX_VALUE;
		return hue * 4 + saturation + Math.abs(1 - shade) * 0.5f;
	}

	/** The spell's color, shaded the way this pixel was shaded relative to its palette color. */
	private static int paint(float[] pixel, float[] reference, int tint, int alpha) {
		float[] target = hsv(tint | 0xFF000000, true);
		float saturation = reference[1] < 0.05f ? target[1] : MathHelper.clamp(target[1] * pixel[1] / reference[1], 0, 1);
		float value = reference[2] <= 0.01f ? target[2] : MathHelper.clamp(target[2] * pixel[2] / reference[2], 0, 1);
		int rgb = MathHelper.hsvToRgb(target[0], saturation, value);
		return alpha << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF;
	}

	/** {hue, saturation, value} of an ABGR pixel. */
	private static float[] hsv(int abgr) {
		return hsv(abgr, false);
	}

	private static float[] hsv(int color, boolean argb) {
		float r = (argb ? color >> 16 & 0xFF : color & 0xFF) / 255f;
		float g = (color >> 8 & 0xFF) / 255f;
		float b = (argb ? color & 0xFF : color >> 16 & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue;
		if (delta < 1e-6f) hue = 0;
		else if (max == r) hue = ((g - b) / delta) / 6f;
		else if (max == g) hue = ((b - r) / delta + 2) / 6f;
		else hue = ((r - g) / delta + 4) / 6f;
		hue -= (float) Math.floor(hue);
		return new float[] {hue, max <= 0 ? 0 : delta / max, max};
	}
}
