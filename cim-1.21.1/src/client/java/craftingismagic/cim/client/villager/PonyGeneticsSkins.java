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
import craftingismagic.cim.rogue.RoguePonyEntity;
import craftingismagic.cim.villager.PonyGeneticsAttachment;
import craftingismagic.cim.villager.PonyGeneticsAttachment.PonyGenetics;

/**
 * Builds a genetics-tinted pony skin texture at runtime, per villager, instead of picking from a
 * small set of fixed named skins - composites {@code coat_base.png} + a mane mask + {@code
 * eye_overlay.png} (all under {@code assets/cim/textures/entity/pony/genetics/}) tinted with that
 * villager's {@link PonyGenetics}, and registers the result as a real dynamic texture MineLP reads
 * directly (see {@link CimVillagerPonyRenderer}'s {@code getEntityPony} override - MineLP's own
 * trigger-pixel parsing reads straight from a registered {@link NativeImageBackedTexture}'s
 * in-memory image, no resource pack round-trip needed, confirmed by tracing MineLP's
 * {@code PonyManagerImpl}/{@code NativeUtil} bytecode).
 * <p>
 * Building the composited image is the expensive part of this class and must only happen once per
 * villager, not per frame - {@link #resolveTexture} is called from the renderer's texture supplier,
 * which runs every frame for every visible villager (same "don't rebuild this every frame" lesson
 * as {@code CimVillagerPonyRenderer}'s own {@code MODELS_BY_RACE} cache).
 */
public final class PonyGeneticsSkins {
	private static final Identifier COAT_BASE = geneticsTexture("coat_base.png");
	private static final Identifier MANE_OVERLAY_ONE_HUE = geneticsTexture("mane_overlay.png");
	private static final Identifier MANE_OVERLAY_TWO_HUE = geneticsTexture("mane_overlay2.png");
	private static final Identifier EYE_OVERLAY = geneticsTexture("eye_overlay.png");

	private static final int IMAGE_SIZE = 64;

	/** Below this mask gray value (of 255), a two-hue mane pixel paints with the secondary hue
	 * instead of the primary - splits {@code mane_overlay2.png}'s finer shading detail into two
	 * strand groups for a two-tone look; also still drives per-pixel shading within each group. */
	private static final int MANE_SECOND_HUE_GRAY_THRESHOLD = 128;

	// Recalibrated 2026-08-05 against real HSV stats, not the ported FCA colormap targets (those
	// were calibrated to an artist-authored palette image's own non-linear axis, not raw HSV, and
	// produced neon-looking coats when plugged straight into saturation - see README/PonyGenetics).
	// Sampled Fill hex (never Outline/Shadow) from 17 canon coats and 13 canon manes via
	// mlpvector.club/cg/pony and converted to real HSV: coats average ~0.95 value / ~0.39 saturation
	// (excluding Rarity/Sweetie-Belle/Svengallop-style near-white coats, which read as the deferred
	// White-coat special trait showing up in the sample rather than the normal case); manes average
	// ~0.84 value / ~0.55 saturation - genuinely more vivid than coats, matching the show's usual
	// "muted coat, punchier mane" look. These caps convert the vividness gene (already biased toward
	// its target, see randomize()) into a saturation that lands near those real averages rather than
	// capping arbitrarily high. Tunable.
	private static final float COAT_SATURATION_CAP = 0.45f;
	private static final float MANE_SATURATION_CAP = 0.70f;
	// The coat mask's own native gray range (~0.76-0.94) is already close to the real ~0.95 value
	// average, so only a mild floor-raise; the mane mask's native range (~0.36-0.50 for the 1-hue
	// mask) is far darker than the real ~0.84 average (which includes bright oranges/pinks alongside
	// darker examples like Twilight's navy), so it gets a bigger stretch.
	private static final float COAT_VALUE_FLOOR = 0.80f;
	private static final float COAT_VALUE_RANGE = 0.20f;
	private static final float MANE_VALUE_FLOOR = 0.35f;
	private static final float MANE_VALUE_RANGE = 0.65f;

	// White/Dark coat special trait (ported FCA research): near-white (Rarity, Sweetie Belle) and
	// near-black (Luna, Thunderlane) coats can't come from the hue/vividness roll above alone. Kept
	// below 1.0 so a tint of the underlying hue always survives - Rarity/Luna aren't pixel-identical
	// to pure white/black, each has a subtle cast. Scales with how far past 0.5 the winning gene
	// lands (see PonyGeneticsAttachment.ColorGenes), covering everything from a faint tint to a
	// full extreme with the same mechanic.
	private static final float MAX_COAT_TONE_BLEND = 0.85f;

	// eye_overlay.png (decoded byte-for-byte 2026-08-05, after the user's own update to the asset)
	// is a grayscale mask like coat/mane, not a pre-colored decal like the original version: a very
	// dark outline/pupil (~26/255), a pure-white highlight/sclera (255/255), and 4 mid-gray levels
	// (45-206/255) forming the actual iris shading. Only the mid-range gets tinted with eye color
	// genetics - the outline and highlight stay fixed regardless of eye color, same as how a real
	// eye's pupil/sclera don't change color while the iris does.
	private static final int EYE_OUTLINE_MAX_GRAY = 30;
	private static final int EYE_HIGHLIGHT_MIN_GRAY = 250;
	// Researched 2026-08-05 (see PonyGeneticsAttachment.EYE_VIVIDNESS_TARGET for the sampling
	// methodology): real canon eyes average ~0.58 saturation. The iris mask's own gray range
	// (~45-206/255, averaging ~0.45) is much darker than the real ~0.84 value average, same kind of
	// gap the mane mask had, so it gets the same floor+range stretch treatment.
	private static final float EYE_SATURATION_CAP = 0.75f;
	private static final float EYE_VALUE_FLOOR = 0.60f;
	private static final float EYE_VALUE_RANGE = 0.55f;
	/** The iris mask's gray value rises top-to-bottom (confirmed by decoding eye_overlay.png's own
	 * pixel rows), same as mane_overlay2.png's shading - so below this threshold is the "top" of the
	 * iris (paints with the secondary hue for a two-hue gradient eye) and above is the "bottom"
	 * (primary hue), matching Apple Bloom's real Gradient Top/Bottom split. */
	private static final int EYE_SECOND_HUE_GRAY_THRESHOLD = 125;

	/** Caches the built texture alongside the exact {@link PonyGenetics} it was built from, so a
	 * change (e.g. from the Villager Debug Editor's Genetics tab sliders) gets noticed and rebuilt -
	 * a plain "build once per UUID, never again" cache silently kept showing the old skin forever
	 * after any live genetics edit, since nothing else ever invalidated it. */
	private record CachedSkin(Identifier textureId, PonyGenetics genetics) {
	}

	private static final Map<UUID, CachedSkin> TEXTURE_BY_VILLAGER = new HashMap<>();

	private PonyGeneticsSkins() {
	}

	public static void register() {
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof VillagerEntity || entity instanceof RoguePonyEntity) {
				CachedSkin cached = TEXTURE_BY_VILLAGER.remove(entity.getUuid());
				if (cached != null) {
					MinecraftClient.getInstance().getTextureManager().destroyTexture(cached.textureId());
				}
			}
		});
	}

	/** Called every frame for every visible villager, so the genetics-equality check (cheap - no
	 * image work) has to stay in the fast path; only a real change pays for a full rebuild. */
	public static Identifier resolveTexture(Entity entity) {
		PonyGenetics genetics = PonyGeneticsAttachment.getGenetics(entity);
		CachedSkin cached = TEXTURE_BY_VILLAGER.get(entity.getUuid());
		if (cached != null && cached.genetics().equals(genetics)) {
			return cached.textureId();
		}
		if (cached != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(cached.textureId());
		}
		Identifier id = buildAndRegister(entity, genetics);
		TEXTURE_BY_VILLAGER.put(entity.getUuid(), new CachedSkin(id, genetics));
		return id;
	}

	private static Identifier buildAndRegister(Entity entity, PonyGenetics genetics) {
		NativeImage composited = composite(genetics);
		Identifier id = Identifier.of(Cim.MOD_ID, "dynamic/pony_genetics/" + entity.getUuidAsString());
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(composited));
		return id;
	}

	private static NativeImage composite(PonyGenetics genetics) {
		NativeImage output = new NativeImage(IMAGE_SIZE, IMAGE_SIZE, true);

		try (NativeImage coatMask = load(COAT_BASE)) {
			paintCoatLayer(output, coatMask, genetics);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load pony genetics coat texture: " + COAT_BASE, e);
		}

		Identifier maneMaskId = genetics.mane().hueCount() >= 2 ? MANE_OVERLAY_TWO_HUE : MANE_OVERLAY_ONE_HUE;
		try (NativeImage maneMask = load(maneMaskId)) {
			paintManeLayer(output, maneMask, genetics);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load pony genetics mane texture: " + maneMaskId, e);
		}

		try (NativeImage eye = load(EYE_OVERLAY)) {
			paintEyeLayer(output, eye, genetics);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load pony genetics eye texture: " + EYE_OVERLAY, e);
		}

		return output;
	}

	private static void paintCoatLayer(NativeImage output, NativeImage mask, PonyGenetics genetics) {
		float white = genetics.coat().whiteCoat();
		float dark = genetics.coat().darkCoat();
		for (int x = 0; x < IMAGE_SIZE; x++) {
			for (int y = 0; y < IMAGE_SIZE; y++) {
				int alpha = mask.getOpacity(x, y) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				float grayFraction = (mask.getRed(x, y) & 0xFF) / 255f;
				float saturation = genetics.coat().vividness() * COAT_SATURATION_CAP;
				float value = COAT_VALUE_FLOOR + grayFraction * COAT_VALUE_RANGE;
				int color = hsvToRgb(genetics.coat().hue(), saturation, value);
				// Both can independently exceed 0.5 for a bred child of a white parent and a dark
				// parent (a natural spawn can't roll both at once, see randomCoatTone()) - whoever is
				// further past 0.5 wins outright rather than blending toward both, so the result still
				// reads as one clear coat instead of a muddy in-between.
				if (white > 0.5f && white >= dark) {
					color = blendToward(color, 255, 255, 255, MAX_COAT_TONE_BLEND * (white - 0.5f) * 2.0f);
				} else if (dark > 0.5f) {
					color = blendToward(color, 0, 0, 0, MAX_COAT_TONE_BLEND * (dark - 0.5f) * 2.0f);
				}
				output.setColor(x, y, color);
			}
		}
	}

	private static void paintManeLayer(NativeImage output, NativeImage mask, PonyGenetics genetics) {
		boolean twoHues = genetics.mane().hueCount() >= 2;
		for (int x = 0; x < IMAGE_SIZE; x++) {
			for (int y = 0; y < IMAGE_SIZE; y++) {
				int alpha = mask.getOpacity(x, y) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				int gray = mask.getRed(x, y) & 0xFF;
				float hue = twoHues && gray < MANE_SECOND_HUE_GRAY_THRESHOLD
						? genetics.mane().hueSecondary()
						: genetics.mane().huePrimary();
				float saturation = genetics.mane().vividness() * MANE_SATURATION_CAP;
				float value = MANE_VALUE_FLOOR + (gray / 255f) * MANE_VALUE_RANGE;
				output.setColor(x, y, hsvToRgb(hue, saturation, value));
			}
		}
	}

	private static void paintEyeLayer(NativeImage output, NativeImage eye, PonyGenetics genetics) {
		// The outline/pupil (very dark) and highlight/sclera (near-white) stay fixed regardless of
		// eye color, same as a real eye's pupil/sclera don't change color while the iris does - only
		// the mid-gray band (the iris shading) gets tinted with eye color genetics.
		boolean twoHues = genetics.eye().hueCount() >= 2;
		for (int x = 0; x < IMAGE_SIZE; x++) {
			for (int y = 0; y < IMAGE_SIZE; y++) {
				int alpha = eye.getOpacity(x, y) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				int gray = eye.getRed(x, y) & 0xFF;
				if (gray <= EYE_OUTLINE_MAX_GRAY || gray >= EYE_HIGHLIGHT_MIN_GRAY) {
					output.setColor(x, y, eye.getColor(x, y));
					continue;
				}
				float hue = twoHues && gray < EYE_SECOND_HUE_GRAY_THRESHOLD
						? genetics.eye().hueSecondary()
						: genetics.eye().huePrimary();
				float saturation = genetics.eye().vividness() * EYE_SATURATION_CAP;
				float value = EYE_VALUE_FLOOR + (gray / 255f) * EYE_VALUE_RANGE;
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

	private static int blendToward(int abgr, int targetR, int targetG, int targetB, float blend) {
		int r = ColorHelper.Abgr.getRed(abgr);
		int g = ColorHelper.Abgr.getGreen(abgr);
		int b = ColorHelper.Abgr.getBlue(abgr);
		int newR = Math.round(r + (targetR - r) * blend);
		int newG = Math.round(g + (targetG - g) * blend);
		int newB = Math.round(b + (targetB - b) * blend);
		return ColorHelper.Abgr.getAbgr(255, newB, newG, newR);
	}

	private static NativeImage load(Identifier id) throws IOException {
		try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(id)) {
			return NativeImage.read(stream);
		}
	}

	private static Identifier geneticsTexture(String name) {
		return Identifier.of(Cim.MOD_ID, "textures/entity/pony/genetics/" + name);
	}
}
