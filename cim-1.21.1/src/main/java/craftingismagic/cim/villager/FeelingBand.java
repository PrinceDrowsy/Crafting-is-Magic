package craftingismagic.cim.villager;

/**
 * The named bands a feeling falls into - what dialogue checks against ({@code trust >= medium} in
 * docs/narrative-reference.md) and what players ever see described, since the friendship system never
 * shows numbers. {@code DEVOTED} is the rare, ride-or-die top tier.
 * <p>
 * Thresholds are first-pass values to tune once more ways of earning each feeling exist.
 */
public enum FeelingBand {
	NONE(Integer.MIN_VALUE),
	LOW(10),
	MEDIUM(40),
	HIGH(100),
	DEVOTED(200);

	private final int threshold;

	FeelingBand(int threshold) {
		this.threshold = threshold;
	}

	public int threshold() {
		return threshold;
	}

	public static FeelingBand of(int value) {
		FeelingBand result = NONE;
		for (FeelingBand band : values()) {
			if (value >= band.threshold) {
				result = band;
			}
		}
		return result;
	}
}
