package craftingismagic.cim.client.villager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import craftingismagic.cim.Cim;
import craftingismagic.cim.villager.ChangelingAppearanceAttachment;
import craftingismagic.cim.villager.ChangelingAppearanceAttachment.ChangelingAppearance;

/**
 * Builds a Changeling's TRUE-form skin texture at runtime - deliberately separate from {@link
 * PonyGeneticsSkins}, since none of that system's genetics-driven coloring applies to a Changeling's
 * true form (only to its disguise, which keeps using {@link PonyGeneticsSkins} unchanged). Composites
 * {@code changeling_coat_base.png} (copied through as-is, no tinting - the base chitin color is fixed
 * for every Changeling, not an individual trait), {@code shell.png} (tinted with a per-Changeling
 * shell hue), and {@code wing_eye_overlay.png} (tinted with a per-Changeling hue shared by both the
 * eyes and wings, since one mask covers both regions specifically so they always match) - all under
 * {@code assets/cim/textures/entity/pony/changeling/}. All three source PNGs are genuine grayscale
 * masks, read the same way {@link PonyGeneticsSkins}' own
 * masks are - no special-case outline/highlight preservation needed here, since neither `shell.png`
 * nor `wing_eye_overlay.png` has a near-white region that would need protecting from tinting.
 */
public final class ChangelingTrueFormSkins {
	private static final Identifier COAT_BASE = changelingTexture("changeling_coat_base.png");
	private static final Identifier SHELL_OVERLAY = changelingTexture("shell.png");
	private static final Identifier WING_EYE_OVERLAY = changelingTexture("wing_eye_overlay.png");

	private static final int IMAGE_SIZE = 64;

	private static final float SHELL_SATURATION_CAP = 0.55f;
	private static final float SHELL_VALUE_FLOOR = 0.35f;
	private static final float SHELL_VALUE_RANGE = 0.55f;

	private static final float EYE_WING_SATURATION_CAP = 0.65f;
	private static final float EYE_WING_VALUE_FLOOR = 0.30f;
	private static final float EYE_WING_VALUE_RANGE = 0.65f;

	/** Same cache-correctness pattern {@link PonyGeneticsSkins} uses - a plain "build once per UUID"
	 * cache would silently keep showing a stale texture after a live appearance change. */
	private record CachedSkin(Identifier textureId, ChangelingAppearance appearance) {
	}

	private static final Map<UUID, CachedSkin> TEXTURE_BY_CHANGELING = new HashMap<>();

	private ChangelingTrueFormSkins() {
	}

	public static void register() {
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity) {
				CachedSkin cached = TEXTURE_BY_CHANGELING.remove(entity.getUuid());
				if (cached != null) {
					MinecraftClient.getInstance().getTextureManager().destroyTexture(cached.textureId());
				}
			}
		});
	}

	public static Identifier resolveTexture(Entity entity) {
		ChangelingAppearance appearance = ChangelingAppearanceAttachment.getAppearance(entity);
		CachedSkin cached = TEXTURE_BY_CHANGELING.get(entity.getUuid());
		if (cached != null && cached.appearance().equals(appearance)) {
			return cached.textureId();
		}
		if (cached != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(cached.textureId());
		}
		Identifier id = buildAndRegister(entity, appearance);
		TEXTURE_BY_CHANGELING.put(entity.getUuid(), new CachedSkin(id, appearance));
		return id;
	}

	private static Identifier buildAndRegister(Entity entity, ChangelingAppearance appearance) {
		NativeImage composited = composite(appearance);
		Identifier id = Identifier.of(Cim.MOD_ID, "dynamic/changeling_true_form/" + entity.getUuidAsString());
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(composited));
		return id;
	}

	private static NativeImage composite(ChangelingAppearance appearance) {
		NativeImage output = new NativeImage(IMAGE_SIZE, IMAGE_SIZE, true);

		try (NativeImage coat = load(COAT_BASE)) {
			paintBaseLayer(output, coat);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load changeling coat texture: " + COAT_BASE, e);
		}

		try (NativeImage shell = load(SHELL_OVERLAY)) {
			paintTintedLayer(output, shell, appearance.shellHue(), appearance.shellVividness(),
					SHELL_SATURATION_CAP, SHELL_VALUE_FLOOR, SHELL_VALUE_RANGE);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load changeling shell texture: " + SHELL_OVERLAY, e);
		}

		try (NativeImage wingEye = load(WING_EYE_OVERLAY)) {
			paintTintedLayer(output, wingEye, appearance.eyeWingHue(), appearance.eyeWingVividness(),
					EYE_WING_SATURATION_CAP, EYE_WING_VALUE_FLOOR, EYE_WING_VALUE_RANGE);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load changeling wing/eye texture: " + WING_EYE_OVERLAY, e);
		}

		return output;
	}

	/** No genetics involved - every Changeling's base chitin color is the same fixed asset, copied
	 * through untouched rather than run through any hue/vividness tint. */
	private static void paintBaseLayer(NativeImage output, NativeImage mask) {
		for (int x = 0; x < IMAGE_SIZE; x++) {
			for (int y = 0; y < IMAGE_SIZE; y++) {
				if ((mask.getOpacity(x, y) & 0xFF) == 0) {
					continue;
				}
				output.setColor(x, y, mask.getColor(x, y));
			}
		}
	}

	private static void paintTintedLayer(NativeImage output, NativeImage mask, float hue, float vividness,
			float saturationCap, float valueFloor, float valueRange) {
		float saturation = vividness * saturationCap;
		for (int x = 0; x < IMAGE_SIZE; x++) {
			for (int y = 0; y < IMAGE_SIZE; y++) {
				if ((mask.getOpacity(x, y) & 0xFF) == 0) {
					continue;
				}
				float grayFraction = (mask.getRed(x, y) & 0xFF) / 255f;
				float value = valueFloor + grayFraction * valueRange;
				output.setColor(x, y, hsvToRgb(hue, saturation, value));
			}
		}
	}

	private static int hsvToRgb(float hue, float saturation, float value) {
		float normalizedHue = hue - (float) Math.floor(hue);
		float h = normalizedHue * 6.0f;
		int sector = (int) h % 6;
		float fractional = h - (float) Math.floor(h);
		float p = value * (1 - saturation);
		float q = value * (1 - fractional * saturation);
		float t = value * (1 - (1 - fractional) * saturation);

		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> {
				r = value;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = value;
				b = p;
			}
			case 2 -> {
				r = p;
				g = value;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = value;
			}
			case 4 -> {
				r = t;
				g = p;
				b = value;
			}
			default -> {
				r = value;
				g = p;
				b = q;
			}
		}

		int red = Math.round(clamp01(r) * 255);
		int green = Math.round(clamp01(g) * 255);
		int blue = Math.round(clamp01(b) * 255);
		return ColorHelper.Abgr.getAbgr(255, blue, green, red);
	}

	private static float clamp01(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private static NativeImage load(Identifier id) throws IOException {
		try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(id)) {
			return NativeImage.read(stream);
		}
	}

	private static Identifier changelingTexture(String name) {
		return Identifier.of(Cim.MOD_ID, "textures/entity/pony/changeling/" + name);
	}
}
