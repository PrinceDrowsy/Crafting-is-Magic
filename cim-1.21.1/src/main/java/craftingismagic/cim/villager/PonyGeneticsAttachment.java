package craftingismagic.cim.villager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import craftingismagic.cim.Cim;

/**
 * A villager's pony genetics - coat/mane color and adult body type. One value per villager (unlike
 * affection), same overall shape as {@link craftingismagic.cim.species.SpeciesAttachment}.
 * <p>
 * v1: random generation only ({@link PonyGenetics#randomize}). {@link PonyGenetics#combine} is
 * written as a documented capability for a future "Village Families" system to call once real
 * parent/child NPC linking exists - nothing in this codebase calls it yet.
 */
public final class PonyGeneticsAttachment {
	private static final AttachmentSyncPredicate SYNC_TO_TRACKERS = (target, player) ->
			target instanceof Entity targetEntity && PlayerLookup.tracking(targetEntity).contains(player);

	public static final AttachmentType<PonyGenetics> GENETICS = AttachmentRegistry.create(
			Identifier.of(Cim.MOD_ID, "pony_genetics"),
			builder -> builder.persistent(PonyGenetics.CODEC)
					.copyOnDeath()
					.syncWith(PonyGenetics.PACKET_CODEC, SYNC_TO_TRACKERS));

	private PonyGeneticsAttachment() {
	}

	public static boolean hasGenetics(Entity entity) {
		return entity.hasAttached(GENETICS);
	}

	public static PonyGenetics getGenetics(Entity entity) {
		return entity.getAttached(GENETICS);
	}

	public static void setGenetics(Entity entity, PonyGenetics genetics) {
		entity.setAttached(GENETICS, genetics);
	}

	public record PonyGenetics(ColorGenes coat, ManeGenes mane, EyeGenes eye, PonyTailLength tailLength,
			PonyTailShape tailShape, PonyBodyType bodyType) {
		/** {@code tail_length}/{@code tail_shape} use {@code optionalFieldOf} with a default, not
		 * {@code fieldOf} - villagers saved to disk before those genes existed have persisted NBT
		 * missing those keys entirely, and a mandatory field fails the whole record's decode, which
		 * silently drops the attachment and falls back to a plain per-species skin (looked like
		 * genetics "reverting to default" on pre-existing villagers only - new ones were unaffected). */
		public static final Codec<PonyGenetics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ColorGenes.CODEC.fieldOf("coat").forGetter(PonyGenetics::coat),
				ManeGenes.CODEC.fieldOf("mane").forGetter(PonyGenetics::mane),
				EyeGenes.CODEC.fieldOf("eye").forGetter(PonyGenetics::eye),
				PonyTailLength.CODEC.optionalFieldOf("tail_length", PonyTailLength.FULL).forGetter(PonyGenetics::tailLength),
				PonyTailShape.CODEC.optionalFieldOf("tail_shape", PonyTailShape.STRAIGHT).forGetter(PonyGenetics::tailShape),
				PonyBodyType.CODEC.fieldOf("body_type").forGetter(PonyGenetics::bodyType)
		).apply(instance, PonyGenetics::new));

		public static final PacketCodec<ByteBuf, PonyGenetics> PACKET_CODEC = PacketCodec.tuple(
				ColorGenes.PACKET_CODEC, PonyGenetics::coat,
				ManeGenes.PACKET_CODEC, PonyGenetics::mane,
				EyeGenes.PACKET_CODEC, PonyGenetics::eye,
				PonyTailLength.PACKET_CODEC, PonyGenetics::tailLength,
				PonyTailShape.PACKET_CODEC, PonyGenetics::tailShape,
				PonyBodyType.PACKET_CODEC, PonyGenetics::bodyType,
				PonyGenetics::new);

		private static final int MIN_PARENTS = 2;
		private static final int MAX_PARENTS = 5;

		// --- Tunable starting constants - see the Pony Genetics section of the design doc for the research
		// these translate (canon color cross-referencing, mane-hue-count/analogous-hue sampling). ---
		private static final float COAT_VIVIDNESS_TARGET = 1.00f;
		private static final float MANE_VIVIDNESS_TARGET = 0.74f;
		private static final float VIVIDNESS_BIAS_SPREAD = 0.6f;
		/** Pulls mane hue toward coat hue - matches the researched ~44 degree median gap. */
		private static final float MANE_COAT_HUE_PAIRING_SPREAD = 0.65f;
		/** 1-2 mane hues only this pass - no 3+, no Rainbow (needs different mask assets, deferred). */
		private static final float MANE_TWO_HUE_CHANCE = 0.20f;
		private static final float MANE_SECOND_HUE_MIN_OFFSET = 20f / 360f;
		private static final float MANE_SECOND_HUE_MAX_OFFSET = 60f / 360f;
		/** Soft bias toward Normal - the other 4 body types are each equally, slightly less common. */
		private static final float NORMAL_BODY_TYPE_CHANCE = 0.40f;
		private static final PonyBodyType[] NON_NORMAL_BODY_TYPES =
				{PonyBodyType.BULKY, PonyBodyType.LANKY, PonyBodyType.STOCKY, PonyBodyType.SQUAT};
		/** Rolled as one 3-way mutually-exclusive split (white / dark / neither) on a natural spawn,
		 * so a pony can't land in both bands at once - a bred child of a white parent x dark parent
		 * *can* get both genes past 0.5 (see combine()), which {@link PonyGeneticsSkins} resolves by
		 * having whichever is further past 0.5 win outright rather than blending muddy. */
		private static final float WHITE_COAT_CHANCE = 0.2f;
		private static final float DARK_COAT_CHANCE = 0.2f;
		// Sampled "Gradient Bottom" (the lighter, dominant iris shade - the
		// eye-color equivalent of coat/mane's "Fill", avoiding the darker "Gradient Top" shadow ring
		// and the separate highlight gradient) from 16 canon characters via mlpvector.club/cg/pony.
		// Real mean ~0.58 saturation - more vivid than coats (~0.39) and slightly more than manes
		// (~0.55), matching the show's usual vivid/expressive eyes. Eye hue still rolls fully
		// independent of coat/mane (no researched pairing for eyes specifically).
		private static final float EYE_VIVIDNESS_TARGET = 0.75f;
		/** ~1/6 of characters sampled had a genuine two-hue "gradient eye" (see EyeGenes) - 20%
		 * matches that rate closely enough, same shape/offset range as the mane's two-hue trait. */
		private static final float EYE_TWO_HUE_CHANCE = 0.20f;

		public static PonyGenetics randomize(Random random) {
			float coatHue = random.nextFloat();
			float coatVividness = biasedRandom(random, COAT_VIVIDNESS_TARGET, VIVIDNESS_BIAS_SPREAD);
			float[] coatTone = randomCoatTone(random);

			float manePrimaryHue = biasedHueWrap(random, coatHue, MANE_COAT_HUE_PAIRING_SPREAD);
			int hueCount = random.nextFloat() < MANE_TWO_HUE_CHANCE ? 2 : 1;
			float maneSecondaryHue = wrap(manePrimaryHue + secondHueOffset(random));
			float maneVividness = biasedRandom(random, MANE_VIVIDNESS_TARGET, VIVIDNESS_BIAS_SPREAD);

			float eyeHue = random.nextFloat();
			int eyeHueCount = random.nextFloat() < EYE_TWO_HUE_CHANCE ? 2 : 1;
			float eyeSecondaryHue = wrap(eyeHue + secondHueOffset(random));
			float eyeVividness = biasedRandom(random, EYE_VIVIDNESS_TARGET, VIVIDNESS_BIAS_SPREAD);

			PonyBodyType bodyType = randomBodyType(random);
			PonyTailLength tailLength = randomTailLength(random);
			PonyTailShape tailShape = randomTailShape(random);

			return new PonyGenetics(
					new ColorGenes(coatHue, coatVividness, coatTone[0], coatTone[1]),
					new ManeGenes(hueCount, manePrimaryHue, maneSecondaryHue, maneVividness),
					new EyeGenes(eyeHueCount, eyeHue, eyeSecondaryHue, eyeVividness),
					tailLength, tailShape, bodyType);
		}

		// Ported directly from the FCA research (see design doc: Pony Genetics) - this is real color-theory
		// data (hue position 0-1 for common color words), not show-specific research, so it doesn't
		// need re-deriving. Deliberately excludes achromatic words (silver, ivory, ebony, etc.) since
		// those don't map to a meaningful hue - White/Dark coat (above) already covers that territory.
		private static final Map<String, Float> NAME_COLOR_HUES = buildNameColorHues();
		/** How far the actual hue is allowed to drift from the named color's target - keeps some
		 * individuality rather than every same-named pony being identical. */
		private static final float NAME_COLOR_HUE_VARIANCE = 0.06f;

		private static Map<String, Float> buildNameColorHues() {
			Map<String, Float> hues = new LinkedHashMap<>();
			hues.put("scarlet", 0.00f);
			hues.put("crimson", 0.00f);
			hues.put("ruby", 0.00f);
			hues.put("cherry", 0.00f);
			hues.put("red", 0.00f);
			hues.put("copper", 0.06f);
			hues.put("chestnut", 0.05f);
			hues.put("coral", 0.05f);
			hues.put("peach", 0.08f);
			hues.put("orange", 0.08f);
			hues.put("amber", 0.11f);
			hues.put("bronze", 0.09f);
			hues.put("honey", 0.13f);
			hues.put("golden", 0.14f);
			hues.put("gold", 0.14f);
			hues.put("lemon", 0.17f);
			hues.put("yellow", 0.17f);
			hues.put("chartreuse", 0.22f);
			hues.put("lime", 0.25f);
			hues.put("emerald", 0.35f);
			hues.put("jade", 0.38f);
			hues.put("green", 0.33f);
			hues.put("mint", 0.42f);
			hues.put("turquoise", 0.48f);
			hues.put("teal", 0.50f);
			hues.put("cyan", 0.50f);
			hues.put("sky", 0.55f);
			hues.put("azure", 0.58f);
			hues.put("cobalt", 0.63f);
			hues.put("sapphire", 0.65f);
			hues.put("blue", 0.667f);
			hues.put("indigo", 0.72f);
			hues.put("lavender", 0.75f);
			hues.put("violet", 0.78f);
			hues.put("purple", 0.78f);
			hues.put("plum", 0.83f);
			hues.put("magenta", 0.83f);
			hues.put("fuchsia", 0.87f);
			hues.put("lilac", 0.80f);
			hues.put("rose", 0.97f);
			hues.put("pink", 0.92f);
			return hues;
		}

		/**
		 * If the pony's name contains a recognizable color word, nudges coat and mane hue toward that
		 * color - a nudge, not a forced value (see {@link #NAME_COLOR_HUE_VARIANCE}), and only ever
		 * the first color word found in the name (matching the ported research exactly). No-op if no
		 * color word is found. Must be called after {@link #randomize}, once the pony's name is
		 * actually known - safe to call unconditionally any time a name is set/confirmed.
		 */
		public static PonyGenetics applyNameColorBias(PonyGenetics genetics, Random random, String name) {
			if (name == null || name.isBlank()) {
				return genetics;
			}
			String lowerName = name.toLowerCase(Locale.ROOT);
			for (Map.Entry<String, Float> entry : NAME_COLOR_HUES.entrySet()) {
				if (!lowerName.contains(entry.getKey())) {
					continue;
				}
				float targetHue = entry.getValue();
				float coatHue = biasedHueWrap(random, targetHue, NAME_COLOR_HUE_VARIANCE);
				float maneHue = biasedHueWrap(random, targetHue, NAME_COLOR_HUE_VARIANCE);
				float maneSecondaryHue = wrap(maneHue + secondHueOffset(random));
				return new PonyGenetics(
						new ColorGenes(coatHue, genetics.coat().vividness(), genetics.coat().whiteCoat(), genetics.coat().darkCoat()),
						new ManeGenes(genetics.mane().hueCount(), maneHue, maneSecondaryHue, genetics.mane().vividness()),
						genetics.eye(),
						genetics.tailLength(), genetics.tailShape(), genetics.bodyType());
			}
			return genetics;
		}

		/** Returns {whiteCoat, darkCoat}. Below-0.5 values on the "losing" side of the 3-way split
		 * are still randomized (not zeroed) so a bred child mixing two natural-spawn parents doesn't
		 * get suspiciously round numbers - see combine()'s own independent blend of these genes. */
		private static float[] randomCoatTone(Random random) {
			float coatToneRoll = random.nextFloat();
			if (coatToneRoll < WHITE_COAT_CHANCE) {
				return new float[] {0.5f + random.nextFloat() * 0.5f, random.nextFloat() * 0.5f};
			} else if (coatToneRoll < WHITE_COAT_CHANCE + DARK_COAT_CHANCE) {
				return new float[] {random.nextFloat() * 0.5f, 0.5f + random.nextFloat() * 0.5f};
			}
			return new float[] {random.nextFloat() * 0.5f, random.nextFloat() * 0.5f};
		}

		/**
		 * Blends 2-5 parents' genetics into a child's. Gender-neutral and family-size-neutral by
		 * design - not "mother"/"father", just an ordered list of contributing parents, so a child in
		 * a polyamorous household can inherit from any of them rather than being limited to a pair.
		 * Not called anywhere in this codebase yet - written now purely as a documented hook for a
		 * future "Village Families" system, once real parent/child NPC linking exists.
		 * <p>
		 * Each continuous gene independently picks 2 distinct parents at random from the full list and
		 * blends between just those two (random-ratio interpolation + small mutation jitter; hue
		 * blending takes the shortest arc around the wheel rather than a naive linear blend, avoiding
		 * wrongly averaging through the opposite side near the 0/1 seam) - different genes can trace
		 * back to different pairs within the same family. Discrete traits ({@code hueCount},
		 * {@code bodyType}) instead inherit directly from one parent, picked uniformly at random.
		 */
		public static PonyGenetics combine(List<PonyGenetics> parents, Random random) {
			if (parents.size() < MIN_PARENTS || parents.size() > MAX_PARENTS) {
				throw new IllegalArgumentException("Expected between " + MIN_PARENTS + " and " + MAX_PARENTS
						+ " parents, got " + parents.size());
			}

			float coatHue = blendHue(random, pickTwo(parents, random), g -> g.coat().hue());
			float coatVividness = blendLinear(random, pickTwo(parents, random), g -> g.coat().vividness());
			float whiteCoat = blendLinear(random, pickTwo(parents, random), g -> g.coat().whiteCoat());
			float darkCoat = blendLinear(random, pickTwo(parents, random), g -> g.coat().darkCoat());
			float manePrimaryHue = blendHue(random, pickTwo(parents, random), g -> g.mane().huePrimary());
			float maneVividness = blendLinear(random, pickTwo(parents, random), g -> g.mane().vividness());
			int hueCount = pickOne(parents, random).mane().hueCount();
			float maneSecondaryHue = wrap(manePrimaryHue + secondHueOffset(random));
			float eyeHue = blendHue(random, pickTwo(parents, random), g -> g.eye().huePrimary());
			float eyeVividness = blendLinear(random, pickTwo(parents, random), g -> g.eye().vividness());
			int eyeHueCount = pickOne(parents, random).eye().hueCount();
			float eyeSecondaryHue = wrap(eyeHue + secondHueOffset(random));
			PonyBodyType bodyType = pickOne(parents, random).bodyType();
			PonyTailLength tailLength = pickOne(parents, random).tailLength();
			PonyTailShape tailShape = pickOne(parents, random).tailShape();

			return new PonyGenetics(
					new ColorGenes(coatHue, coatVividness, whiteCoat, darkCoat),
					new ManeGenes(hueCount, manePrimaryHue, maneSecondaryHue, maneVividness),
					new EyeGenes(eyeHueCount, eyeHue, eyeSecondaryHue, eyeVividness),
					tailLength, tailShape, bodyType);
		}

		private static PonyBodyType randomBodyType(Random random) {
			if (random.nextFloat() < NORMAL_BODY_TYPE_CHANCE) {
				return PonyBodyType.NORMAL;
			}
			return NON_NORMAL_BODY_TYPES[random.nextInt(NON_NORMAL_BODY_TYPES.length)];
		}

		/** Soft bias toward Full - the show's default, most other lengths are visibly a deliberate choice. */
		private static final float FULL_TAIL_LENGTH_CHANCE = 0.45f;
		private static final PonyTailLength[] NON_FULL_TAIL_LENGTHS =
				{PonyTailLength.STUB, PonyTailLength.QUARTER, PonyTailLength.HALF, PonyTailLength.THREE_QUARTERS};

		private static PonyTailLength randomTailLength(Random random) {
			if (random.nextFloat() < FULL_TAIL_LENGTH_CHANCE) {
				return PonyTailLength.FULL;
			}
			return NON_FULL_TAIL_LENGTHS[random.nextInt(NON_FULL_TAIL_LENGTHS.length)];
		}

		/** Soft bias toward Straight - same shape as the body-type/tail-length majority bias above. */
		private static final float STRAIGHT_TAIL_SHAPE_CHANCE = 0.40f;
		private static final PonyTailShape[] NON_STRAIGHT_TAIL_SHAPES =
				{PonyTailShape.BUMPY, PonyTailShape.SWIRLY, PonyTailShape.SPIKY};

		private static PonyTailShape randomTailShape(Random random) {
			if (random.nextFloat() < STRAIGHT_TAIL_SHAPE_CHANCE) {
				return PonyTailShape.STRAIGHT;
			}
			return NON_STRAIGHT_TAIL_SHAPES[random.nextInt(NON_STRAIGHT_TAIL_SHAPES.length)];
		}

		private static float secondHueOffset(Random random) {
			return MANE_SECOND_HUE_MIN_OFFSET
					+ random.nextFloat() * (MANE_SECOND_HUE_MAX_OFFSET - MANE_SECOND_HUE_MIN_OFFSET);
		}

		private static PonyGenetics[] pickTwo(List<PonyGenetics> parents, Random random) {
			if (parents.size() == MIN_PARENTS) {
				return new PonyGenetics[] {parents.get(0), parents.get(1)};
			}
			List<PonyGenetics> pool = new ArrayList<>(parents);
			PonyGenetics first = pool.remove(random.nextInt(pool.size()));
			PonyGenetics second = pool.remove(random.nextInt(pool.size()));
			return new PonyGenetics[] {first, second};
		}

		private static PonyGenetics pickOne(List<PonyGenetics> parents, Random random) {
			return parents.get(random.nextInt(parents.size()));
		}

		private static float blendLinear(Random random, PonyGenetics[] pair, java.util.function.ToDoubleFunction<PonyGenetics> gene) {
			float m = (float) gene.applyAsDouble(pair[0]);
			float f = (float) gene.applyAsDouble(pair[1]);
			float t = random.nextFloat();
			float mutation = (random.nextFloat() - 0.5f) * 0.2f;
			return MathHelper.clamp(m * t + f * (1 - t) + mutation, 0f, 1f);
		}

		private static float blendHue(Random random, PonyGenetics[] pair, java.util.function.ToDoubleFunction<PonyGenetics> gene) {
			float m = (float) gene.applyAsDouble(pair[0]);
			float f = (float) gene.applyAsDouble(pair[1]);
			// Shortest signed distance from m to f around the wheel, so blending never wrongly
			// averages "through" the opposite side near the 0/1 seam.
			float diff = wrap(f - m + 1.5f) - 0.5f;
			float t = random.nextFloat();
			return biasedHueWrap(random, wrap(m + diff * t), 0.05f);
		}

		private static float biasedRandom(Random random, float target, float spread) {
			float jitter = (random.nextFloat() - 0.5f) * (random.nextFloat() - 0.5f) * 4.0f * spread;
			return MathHelper.clamp(target + jitter, 0f, 1f);
		}

		private static float biasedHueWrap(Random random, float anchor, float spread) {
			float jitter = (random.nextFloat() - 0.5f) * (random.nextFloat() - 0.5f) * 4.0f * spread;
			return wrap(anchor + jitter);
		}

		private static float wrap(float value) {
			return value - MathHelper.floor(value);
		}
	}

	/**
	 * {@code whiteCoat}/{@code darkCoat}: a separate, mutually-exclusive-on-natural-spawn special
	 * trait layered on top of {@code hue}/{@code vividness} - real near-white (Rarity, Sweetie Belle)
	 * and near-black (Luna, Thunderlane) coats can't come from the normal hue/vividness roll alone
	 * (several sampled canon "Fill" hex values read
	 * as near-zero saturation, well outside the normal ~0.39-average range). Only takes effect past
	 * a 0.5 threshold (see {@link PonyGeneticsSkins} for the blend), so most rolls land below that
	 * and never visibly apply.
	 */
	public record ColorGenes(float hue, float vividness, float whiteCoat, float darkCoat) {
		public static final Codec<ColorGenes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("hue").forGetter(ColorGenes::hue),
				Codec.FLOAT.fieldOf("vividness").forGetter(ColorGenes::vividness),
				Codec.FLOAT.fieldOf("white_coat").forGetter(ColorGenes::whiteCoat),
				Codec.FLOAT.fieldOf("dark_coat").forGetter(ColorGenes::darkCoat)
		).apply(instance, ColorGenes::new));

		public static final PacketCodec<ByteBuf, ColorGenes> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.FLOAT, ColorGenes::hue,
				PacketCodecs.FLOAT, ColorGenes::vividness,
				PacketCodecs.FLOAT, ColorGenes::whiteCoat,
				PacketCodecs.FLOAT, ColorGenes::darkCoat,
				ColorGenes::new);
	}

	/**
	 * {@code hueCount}: most eyes are a single hue with ordinary light/dark shading (Twilight, Rainbow Dash, Applejack, Rarity, and Fluttershy's canon
	 * "Gradient Top"/"Gradient Bottom" hex values are within ~2 degrees of each other, i.e. the same
	 * hue, just shaded). A minority genuinely transition between two different hues, top to bottom -
	 * Apple Bloom is the confirmed example (~359 degree red-pink top to ~34 degree orange-gold
	 * bottom, a real ~35 degree gap) - same shape and rarity as the mane's two-hue trait.
	 */
	public record EyeGenes(int hueCount, float huePrimary, float hueSecondary, float vividness) {
		public static final Codec<EyeGenes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("hue_count").forGetter(EyeGenes::hueCount),
				Codec.FLOAT.fieldOf("hue_primary").forGetter(EyeGenes::huePrimary),
				Codec.FLOAT.fieldOf("hue_secondary").forGetter(EyeGenes::hueSecondary),
				Codec.FLOAT.fieldOf("vividness").forGetter(EyeGenes::vividness)
		).apply(instance, EyeGenes::new));

		public static final PacketCodec<ByteBuf, EyeGenes> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, EyeGenes::hueCount,
				PacketCodecs.FLOAT, EyeGenes::huePrimary,
				PacketCodecs.FLOAT, EyeGenes::hueSecondary,
				PacketCodecs.FLOAT, EyeGenes::vividness,
				EyeGenes::new);
	}

	public record ManeGenes(int hueCount, float huePrimary, float hueSecondary, float vividness) {
		public static final Codec<ManeGenes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("hue_count").forGetter(ManeGenes::hueCount),
				Codec.FLOAT.fieldOf("hue_primary").forGetter(ManeGenes::huePrimary),
				Codec.FLOAT.fieldOf("hue_secondary").forGetter(ManeGenes::hueSecondary),
				Codec.FLOAT.fieldOf("vividness").forGetter(ManeGenes::vividness)
		).apply(instance, ManeGenes::new));

		public static final PacketCodec<ByteBuf, ManeGenes> PACKET_CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, ManeGenes::hueCount,
				PacketCodecs.FLOAT, ManeGenes::huePrimary,
				PacketCodecs.FLOAT, ManeGenes::hueSecondary,
				PacketCodecs.FLOAT, ManeGenes::vividness,
				ManeGenes::new);
	}
}
