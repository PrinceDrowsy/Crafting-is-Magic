package craftingismagic.cim.structure;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;

/**
 * What a pony village is, worked out from the world seed and the chunk it starts in alone (design doc: Village
 * identity - districts): its size, plaza and rings, and whether it's abandoned. Anything can work it out
 * again and get the same answer - the village's own districts, which are structures of their own, use it
 * to find their village and carry on its roads.
 *
 * @param radii heart to each ring road, the plaza's first
 */
public record PonyVillageSite(ChunkPos start, Size size, double[] radii, boolean abandoned) {
	/** Ring road to ring road: room for a row of houses each side. */
	public static final int RING_GAP = 32;

	/**
	 * How big a village grows. Hamlets and villages are a core alone; towns and cities add districts round
	 * it (a town two or three, a city all the way round).
	 */
	public enum Size {
		HAMLET(30, 1, 35, 10),
		VILLAGE(42, 2, 35, 20),
		TOWN(42, 2, 22, 28),
		CITY(42, 2, 8, 36);

		/** Heart to the plaza's ring road. */
		public final int plaza;
		/** Rings of houses beyond the plaza ring, in the core. */
		public final int rings;
		final int weight;
		/**
		 * About how many ponies live there when it's found: the rest of its homes stand empty and fill over
		 * time, as foals grow up and newcomers settle (design doc: Performance - pony population and AI).
		 */
		public final int population;

		Size(int plaza, int rings, int weight, int population) {
			this.plaza = plaza;
			this.rings = rings;
			this.weight = weight;
			this.population = population;
		}
	}

	public static PonyVillageSite of(long worldSeed, ChunkPos start, float abandonedChance) {
		Random random = Random.create(worldSeed ^ start.x * 341873128712L ^ start.z * 132897987541L ^ 0x506F6E7956L);
		boolean abandoned = random.nextFloat() < abandonedChance;
		int total = 0;
		for (Size size : Size.values()) total += size.weight;
		int roll = random.nextInt(total);
		Size size = Size.HAMLET;
		for (Size candidate : Size.values()) {
			roll -= candidate.weight;
			if (roll < 0) {
				size = candidate;
				break;
			}
		}
		double[] radii = new double[size.rings + 1];
		for (int i = 0; i < radii.length; i++) radii[i] = size.plaza + i * RING_GAP;
		return new PonyVillageSite(start, size, radii, abandoned);
	}

	/** The heart: the middle of the chunk it starts in. */
	public int heartX() {
		return start.getCenterX();
	}

	public int heartZ() {
		return start.getCenterZ();
	}

	/** The core's outermost ring road. */
	public double outerRing() {
		return radii[radii.length - 1];
	}

	/** How far the village's ground reaches from its heart: past its last ring, the houses and the roads out. */
	public double reach() {
		return abandoned ? 80 : outerRing() + 30;
	}

	/** Whether this village wins ground both claim: the bigger one, or on a tie the one further north-west. */
	public boolean outranks(PonyVillageSite other) {
		int mine = abandoned ? -1 : size.ordinal(), theirs = other.abandoned ? -1 : other.size.ordinal();
		if (mine != theirs) return mine > theirs;
		return start.z != other.start.z ? start.z < other.start.z : start.x < other.start.x;
	}
}
