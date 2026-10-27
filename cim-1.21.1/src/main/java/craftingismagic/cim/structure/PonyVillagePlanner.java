package craftingismagic.cim.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.block.Blocks;
import net.minecraft.block.JigsawBlock;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.Structure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plans a pony village in one go, from the heart out (README: Village identity - the ring layout):
 * <ol>
 * <li>its size, from the seed ({@link PonyVillageSite}) - a hamlet (a smaller plaza ring and one more), or
 * the full core (the plaza ring and two more); anything bigger grows districts round the core, since one
 * structure can only reach 8 chunks from where it starts;</li>
 * <li>its centerpiece, on a wide open plaza green;</li>
 * <li>its roads - a ring road round the plaza and one more per ring, and eight roads out from the plaza
 * through every ring (the four straight ways and the four diagonals), on out of town;</li>
 * <li>the schoolhouse, at the end of one road, past the last ring;</li>
 * <li>a building on every plot along the rings that's free, flat and dry - facing its road, the special
 * buildings round the plaza, homes on the rings beyond, farms and pens out at the edge.</li>
 * </ol>
 * Every plot is checked against everything already planned, so nothing overlaps, whatever its size.
 */
final class PonyVillagePlanner {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/structures");
	/** As far from the heart as anything can go: a structure only reaches 8 chunks round the chunk it starts in. */
	private static final int REACH = 134;
	/** How far a building may stand back from a curving road, with a path to its door. */
	private static final int MAX_SETBACK = 4;
	/** How far along the road a picked building looks for room before another is picked instead. */
	private static final int SEARCH = 30;
	/** How far the roads run on out of town past the last ring. */
	private static final int EXIT = 12;
	/** How far a plot's ground may rise and fall. */
	private static final int MAX_SLOPE = 3;
	/**
	 * Routing (see {@link #route}): how far a road out from the plaza may stray off its straight line for an
	 * easier walk, and what a step costs - more the steeper (sharply past a block a block), over water, and
	 * a little for straying.
	 */
	private static final int CORRIDOR = 6;
	/**
	 * If less than this share of the village's ground is reachable from the plaza (a ravine or a lake cuts
	 * the rest off), it grows one ring further out to make up for it.
	 */
	private static final double CUT_OFF = 0.8;
	/** How far above or below its real ground a building may be set, to sit on the smoothed land. */
	private static final int MAX_SETTLE = 4;
	private static final double SLOPE_COST = 3.0;
	private static final double CLIFF_COST = 15.0;
	private static final double WATER_COST = 3.0;
	private static final double STRAY_COST = 0.04;

	private enum Plot { PLAZA, HOMES, EDGE }

	private record Entrance(int x, int y, int z, Direction facing) {
	}

	/** A planned building, and how many ponies it could bring. */
	private record Planned(VillageBuildingPiece piece, int spots, boolean plaza) {
	}

	private final Structure.Context context;
	private final PonyVillageLayouts.Layout layout;
	private final PonyVillageSite site;
	private final StructureTemplateManager templates;
	private final Random random;
	private final int cx;
	private final int cy;
	private final int cz;
	private final StructurePiecesCollector pieces;

	private final Set<Long> roads = new HashSet<>();
	private final Set<Long> taken = new HashSet<>();
	private VillageTerrain terrain;
	/** The village's ground a pony could walk to from the plaza (VillageTerrain#reachableFrom). */
	private VillageTerrain.Reach reached;
	/** The village's land, smoothed (VillageSmoothingPiece) - what buildings and roads are set on. */
	private VillageSmoothingPiece.Shape shape;
	private final Map<String, Optional<Entrance>> entrances = new HashMap<>();
	private final List<PonyVillageLayouts.Entry> plazaLeft;
	private final List<Planned> buildings = new ArrayList<>();
	private final Map<Identifier, Integer> spots = new HashMap<>();
	private double limit;

	private PonyVillagePlanner(Structure.Context context, PonyVillageLayouts.Layout layout, PonyVillageSite site, BlockPos heart,
			StructurePiecesCollector pieces) {
		this.context = context;
		this.layout = layout;
		this.site = site;
		this.templates = context.structureTemplateManager();
		this.random = context.random();
		this.cx = heart.getX();
		this.cy = heart.getY();
		this.cz = heart.getZ();
		this.pieces = pieces;
		this.plazaLeft = new ArrayList<>(layout.plaza());
	}

	static void plan(Structure.Context context, PonyVillageLayouts.Layout layout, PonyVillageSite site, BlockPos heart,
			StructurePiecesCollector pieces) {
		new PonyVillagePlanner(context, layout, site, heart, pieces).plan();
	}

	private void plan() {
		long started = System.nanoTime();
		centerpiece();
		double[] radii = site.radii();
		double outer = site.outerRing();
		limit = Math.min(outer + 40, REACH);
		terrain = new VillageTerrain(context, cx, cz, (int) Math.ceil(limit));
		reached = terrain.reachableFrom(cx, cz, reachRadius(outer));
		// A village only builds where a pony could walk to from the plaza. If a ravine, a cliff or a lake
		// cuts off much of its ground, it grows a ring further out instead, spreading on its own side.
		if (reached.share(cx, cz, outer) < CUT_OFF && outer + PonyVillageSite.RING_GAP + 40 <= REACH) {
			radii = java.util.Arrays.copyOf(radii, radii.length + 1);
			outer += PonyVillageSite.RING_GAP;
			radii[radii.length - 1] = outer;
			limit = Math.min(outer + 40, REACH);
			terrain = new VillageTerrain(context, cx, cz, (int) Math.ceil(limit));
			reached = terrain.reachableFrom(cx, cz, reachRadius(outer));
		}
		shape = terrain.softened();
		int rings = radii.length;

		// The plaza green stays open.
		int green = (int) radii[0] - 2;
		for (int x = -green; x <= green; x++) {
			for (int z = -green; z <= green; z++) {
				if (x * x + z * z <= green * green) taken.add(VillageRoadPiece.cell(cx + x, cz + z));
			}
		}

		// Roads: the rings, and eight roads out - one of the four straight ones ends at the schoolhouse.
		for (double radius : radii) ring(radius);
		int schoolRoad = layout.schoolhouse() != null ? random.nextInt(4) * 2 : -1;
		for (int k = 0; k < 8; k++) road(k, radii[0], k == schoolRoad ? outer : outer + EXIT); // never across the plaza
		keepReachableRoads();
		taken.addAll(roads);

		if (schoolRoad >= 0) {
			double angle = schoolRoad * Math.PI / 4;
			int fx = cx + (int) Math.round((outer + 1) * Math.cos(angle));
			int fz = cz + (int) Math.round((outer + 1) * Math.sin(angle));
			Direction home = Direction.getFacing(-Math.cos(angle), 0, -Math.sin(angle));
			if (!place(layout.schoolhouse(), fx, fz, home, 0)) {
				// No room past the edge there: that road runs on out of town like the rest, and the
				// schoolhouse takes a plot on a ring instead - the outermost one with room.
				road(schoolRoad, outer, outer + EXIT);
				keepReachableRoads();
				taken.addAll(roads);
				PonyVillageLayouts.Entry school = new PonyVillageLayouts.Entry(layout.schoolhouse(), 1);
				placing:
				for (int i = rings - 1; i >= 0; i--) {
					for (boolean outside : new boolean[] {true, false}) {
						if (i == 0 && !outside) continue; // never on the plaza
						for (double at = 0; at < Math.PI * 2; at += 1.5 / radii[i]) {
							if (placeAt(school, radii[i], outside, at)) break placing;
						}
					}
				}
			}
		}

		for (int i = 0; i < rings; i++) {
			boolean last = i == rings - 1;
			fill(radii[i], true, i == 0 ? Plot.PLAZA : last ? Plot.EDGE : Plot.HOMES);
			if (i > 0) fill(radii[i], false, Plot.HOMES);
		}

		long[] cells = roads.stream().mapToLong(Long::longValue).toArray();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (long cell : cells) {
			int x = (int) (cell >> 32), z = (int) cell;
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		int bottom = context.world().getBottomY() + 1;
		// The land smoothed a little first - everywhere but the building plots and the plaza - so the roads
		// are laid on it.
		long[] plots = taken.stream().filter(cell -> !roads.contains(cell)).mapToLong(Long::longValue).toArray();
		int reach = (int) Math.ceil(limit);
		pieces.addPiece(new VillageSmoothingPiece(shape, cx, cz, reach, plots, cells,
				new BlockBox(cx - reach, bottom, cz - reach, cx + reach, bottom, cz + reach)));
		pieces.addPiece(new VillageRoadPiece(cells, new BlockBox(minX, bottom, minZ, maxX, bottom, maxZ)));
		int ponies = settle();
		LOGGER.info("Planned a pony {} at {} {}: {} rings, {} buildings, {} ponies to start ({} ms, {} terrain readings)",
				site.size().name().toLowerCase(java.util.Locale.ROOT), cx, cz, rings, buildings.size(), ponies,
				(System.nanoTime() - started) / 1_000_000, terrain.samples() + buildings.size());
	}

	/**
	 * Who lives here from the start: up to half the size's starting population in the plaza's workshops, so
	 * the village has its jobs, then homes at random for the rest. The other buildings stand empty, to fill
	 * over time. Returns how many ponies that is.
	 */
	private int settle() {
		List<Planned> plaza = new ArrayList<>();
		List<Planned> homes = new ArrayList<>();
		for (Planned building : buildings) {
			building.piece().setResidents(false);
			(building.plaza() ? plaza : homes).add(building);
		}
		int population = site.size().population;
		int ponies = settle(plaza, 0, population / 2);
		return settle(homes, ponies, population);
	}

	/** Moves ponies into {@code buildings}, in a random order, until there are about {@code upTo}. */
	private int settle(List<Planned> buildings, int ponies, int upTo) {
		for (int i = buildings.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Planned swap = buildings.get(i);
			buildings.set(i, buildings.get(j));
			buildings.set(j, swap);
		}
		for (Planned building : buildings) {
			if (ponies >= upTo) break;
			if (building.spots() == 0) continue;
			building.piece().setResidents(true);
			ponies += building.spots();
		}
		return ponies;
	}

	/** The centerpiece, at the heart: the town hall, unless it's a hamlet. */
	private void centerpiece() {
		PonyVillageLayouts.Entry entry = layout.townHall() != null && site.size() != PonyVillageSite.Size.HAMLET
				? new PonyVillageLayouts.Entry(layout.townHall(), 1)
				: PonyVillageLayouts.pick(layout.centerpieces(), random);
		if (entry == null) return;
		Optional<StructureTemplate> template = templates.getTemplate(entry.structure());
		if (template.isEmpty()) return;
		BlockRotation rotation = BlockRotation.random(random);
		BlockBox atOrigin = template.get().calculateBoundingBox(VillageBuildingPiece.placement(rotation), BlockPos.ORIGIN);
		BlockPos pos = new BlockPos(cx - (atOrigin.getMinX() + atOrigin.getMaxX()) / 2, cy, cz - (atOrigin.getMinZ() + atOrigin.getMaxZ()) / 2);
		add(entry.structure(), template.get(), pos, rotation, true, false);
	}

	/** @param onStep whether it's set down by its front step, at ground level (everything but the centerpiece) */
	private void add(Identifier id, StructureTemplate template, BlockPos pos, BlockRotation rotation, boolean plaza, boolean onStep) {
		VillageBuildingPiece piece = new VillageBuildingPiece(templates, id, pos, rotation, onStep);
		pieces.addPiece(piece);
		buildings.add(new Planned(piece, spots.computeIfAbsent(id, key -> VillageBuildingPiece.villagerSpots(template)), plaza));
	}

	/** Roads stop where the village's reachable ground does - none down into a ravine and out the far side. */
	private void keepReachableRoads() {
		roads.removeIf(cell -> !reached.at((int) (cell >> 32), (int) (long) cell));
	}

	/** How far out reachability's read: past the last ring, as far as its buildings and roads out go. */
	private int reachRadius(double outer) {
		return (int) Math.ceil(Math.min(outer + EXIT + 12, limit));
	}

	/** A ring road, three wide. */
	private void ring(double radius) {
		int reach = (int) radius + 2;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (Math.abs(Math.sqrt(x * x + z * z) - radius) <= 1.25) roads.add(VillageRoadPiece.cell(cx + x, cz + z));
			}
		}
	}

	/**
	 * Road {@code k} of the eight out from the heart (0 = east, then on round by eighths), three wide, from
	 * radius {@code from} to {@code to}: starting and ending on the straight line, and bending a little off
	 * it between where that's an easier walk (see {@link #route}).
	 */
	private void road(int k, double from, double to) {
		double angle = k * Math.PI / 4;
		double dx = Math.cos(angle), dz = Math.sin(angle);
		int sx = cx + (int) Math.round(from * dx), sz = cz + (int) Math.round(from * dz);
		int gx = cx + (int) Math.round(to * dx), gz = cz + (int) Math.round(to * dz);
		for (long[] at : route(sx, sz, gx, gz, dx, dz)) {
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					if (ox * ox + oz * oz <= 1) roads.add(VillageRoadPiece.cell((int) at[0] + ox, (int) at[1] + oz));
				}
			}
		}
	}

	/**
	 * A* over the ground grid for the easiest walk between a road's ends, straying at most {@link #CORRIDOR}
	 * off the straight line. Steep steps, water and straying cost extra, so flat ground keeps it straight.
	 * Falls back to the straight line.
	 */
	private List<long[]> route(int sx, int sz, int gx, int gz, double dx, double dz) {
		double length = Math.hypot(gx - sx, gz - sz);
		long start = VillageRoadPiece.cell(sx, sz), goal = VillageRoadPiece.cell(gx, gz);
		Map<Long, Double> cost = new HashMap<>();
		Map<Long, Long> came = new HashMap<>();
		java.util.PriorityQueue<double[]> open = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		cost.put(start, 0.0);
		open.add(new double[] { length, sx, sz });
		while (!open.isEmpty()) {
			double[] at = open.poll();
			int x = (int) at[1], z = (int) at[2];
			long cell = VillageRoadPiece.cell(x, z);
			if (cell == goal) break;
			double here = cost.get(cell);
			if (at[0] > here + Math.hypot(gx - x, gz - z) + 1.0e-6) continue;
			float height = terrain.smoothGround(x, z);
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					if (ox == 0 && oz == 0) continue;
					int nx = x + ox, nz = z + oz;
					double along = (nx - sx) * dx + (nz - sz) * dz;
					double stray = Math.abs((nx - sx) * -dz + (nz - sz) * dx);
					if (stray > CORRIDOR || along < -2 || along > length + 2) continue;
					long next = VillageRoadPiece.cell(nx, nz);
					if (next != goal && taken.contains(next) && !roads.contains(next)) continue;
					double step = ox != 0 && oz != 0 ? Math.sqrt(2) : 1.0;
					double slope = Math.abs(terrain.smoothGround(nx, nz) - height) / step;
					double price = step * (1 + SLOPE_COST * slope * slope) + (slope > 1 ? CLIFF_COST * (slope - 1) * step : 0)
							+ (terrain.dry(nx, nz) ? 0 : WATER_COST * step) + STRAY_COST * stray;
					double total = here + price;
					Double had = cost.get(next);
					if (had == null || total < had) {
						cost.put(next, total);
						came.put(next, cell);
						open.add(new double[] { total + Math.hypot(gx - nx, gz - nz), nx, nz });
					}
				}
			}
		}
		List<long[]> way = new ArrayList<>();
		if (!came.containsKey(goal)) {
			int steps = (int) Math.ceil(length * 2);
			for (int i = 0; i <= steps; i++) {
				double t = steps == 0 ? 0 : i / (double) steps;
				way.add(new long[] { Math.round(sx + (gx - sx) * t), Math.round(sz + (gz - sz) * t) });
			}
			return way;
		}
		for (long cell = goal; ; cell = came.get(cell)) {
			way.add(0, new long[] { cell >> 32, (int) cell });
			if (cell == start) break;
		}
		return way;
	}

	/**
	 * Buildings all round one side of a ring road, each facing it. The building is picked first, then the
	 * road walked on until there's room for it - so a big house gets its share, instead of every gap going
	 * to whatever small one happens to fit it first.
	 */
	private void fill(double radius, boolean outside, Plot plot) {
		double start = random.nextDouble() * Math.PI * 2;
		double end = start + Math.PI * 2;
		double step = 1.5 / radius;
		double angle = start;
		while (angle < end) {
			boolean plazaPick = plot == Plot.PLAZA;
			PonyVillageLayouts.Entry entry = choose(plot, plazaPick);
			if (entry == null) return;
			double found = -1;
			for (double at = angle; at < Math.min(angle + SEARCH / radius, end) && found < 0; at += step) {
				if (placeAt(entry, radius, outside, at)) found = at;
			}
			if (found >= 0) {
				if (plazaPick) plazaLeft.remove(entry);
				angle = found + step;
			} else {
				angle += step * 2; // no room for that one round here: pick again a little further on
			}
		}
	}

	/** Tries {@code entry} at {@code angle} round the ring, standing as far back as it needs. */
	private boolean placeAt(PonyVillageLayouts.Entry entry, double radius, boolean outside, double angle) {
		double ox = Math.cos(angle), oz = Math.sin(angle);
		double edge = outside ? radius + 1 : radius - 1;
		int fx = cx + (int) Math.round(edge * ox), fz = cz + (int) Math.round(edge * oz);
		if (!roads.contains(VillageRoadPiece.cell(fx, fz))) return false;
		Direction toRoad = outside ? Direction.getFacing(-ox, 0, -oz) : Direction.getFacing(ox, 0, oz);
		for (int setback = 0; setback <= MAX_SETBACK; setback++) {
			if (place(entry.structure(), fx, fz, toRoad, setback)) return true;
		}
		return false;
	}

	private PonyVillageLayouts.Entry choose(Plot plot, boolean plazaPick) {
		if (plazaPick && !plazaLeft.isEmpty()) return PonyVillageLayouts.pick(plazaLeft, random);
		boolean edge = plot == Plot.EDGE && random.nextInt(100) < 30;
		if (edge && !layout.outskirts().isEmpty()) return PonyVillageLayouts.pick(layout.outskirts(), random);
		return PonyVillageLayouts.pick(layout.homes(), random);
	}

	/**
	 * Places a building whose entrance opens onto the road at {@code (fx, fz)}, facing {@code toRoad} - if
	 * it fits there: nothing in the way, inside the village, dry, and not too steep. {@code setback}: how
	 * many blocks back from the road it stands, with a path up to its door (buildings only turn in quarter
	 * turns, so on a curving road a straight front needs a little room).
	 */
	private boolean place(Identifier id, int fx, int fz, Direction toRoad, int setback) {
		Optional<StructureTemplate> template = templates.getTemplate(id);
		if (template.isEmpty()) return false;
		for (BlockRotation rotation : BlockRotation.values()) {
			Optional<Entrance> found = entrance(id, template.get(), rotation);
			if (found.isEmpty()) return false;
			Entrance entrance = found.get();
			if (entrance.facing() != toRoad) continue;
			List<Long> path = new ArrayList<>();
			for (int step = 1; step <= setback; step++) {
				long cell = VillageRoadPiece.cell(fx - toRoad.getOffsetX() * step, fz - toRoad.getOffsetZ() * step);
				if (taken.contains(cell)) return false;
				path.add(cell);
			}
			int jx = fx - toRoad.getOffsetX() * (setback + 1), jz = fz - toRoad.getOffsetZ() * (setback + 1);
			// Checked against the village's ground grid; only a building that fits gets an exact reading to stand on.
			BlockPos pos = new BlockPos(jx - entrance.x(), terrain.ground(jx, jz) - entrance.y(), jz - entrance.z());
			BlockBox box = template.get().calculateBoundingBox(VillageBuildingPiece.placement(rotation), pos);
			if (!free(box) || !flatAndDry(box, jx, jz)) return false;
			// On the village's smoothed land (VillageSmoothingPiece), the same level the ground round it is
			// eased to - but never far from the real ground there.
			int ground = terrain.exactGround(jx, jz);
			int smoothed = MathHelper.clamp(Math.round(shape.at(jx, jz)), ground - MAX_SETTLE, ground + MAX_SETTLE);
			pos = pos.withY(smoothed - entrance.y());
			box = template.get().calculateBoundingBox(VillageBuildingPiece.placement(rotation), pos);
			add(id, template.get(), pos, rotation, layout.plaza().stream().anyMatch(entry -> entry.structure().equals(id)), true);
			for (int x = box.getMinX() - 1; x <= box.getMaxX() + 1; x++) {
				for (int z = box.getMinZ() - 1; z <= box.getMaxZ() + 1; z++) taken.add(VillageRoadPiece.cell(x, z));
			}
			roads.addAll(path);
			taken.addAll(path);
			return true;
		}
		return false;
	}

	/** Where a building's front door jigsaw is, turned this way - empty if it hasn't one. */
	private Optional<Entrance> entrance(Identifier id, StructureTemplate template, BlockRotation rotation) {
		return entrances.computeIfAbsent(id + "/" + rotation, key -> {
			for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(BlockPos.ORIGIN, VillageBuildingPiece.placement(rotation), Blocks.JIGSAW)) {
				if (info.nbt() != null && "minecraft:building_entrance".equals(info.nbt().getString("name"))) {
					return Optional.of(new Entrance(info.pos().getX(), info.pos().getY(), info.pos().getZ(), JigsawBlock.getFacing(info.state())));
				}
			}
			return Optional.empty();
		});
	}

	private boolean free(BlockBox box) {
		for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
			for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
				if (taken.contains(VillageRoadPiece.cell(x, z))) return false;
				double dx = x - cx, dz = z - cz;
				if (dx * dx + dz * dz > limit * limit) return false;
			}
		}
		return true;
	}

	private boolean flatAndDry(BlockBox box, int jx, int jz) {
		int[][] samples = {{box.getMinX(), box.getMinZ()}, {box.getMaxX(), box.getMinZ()}, {box.getMinX(), box.getMaxZ()},
				{box.getMaxX(), box.getMaxZ()}, {box.getCenter().getX(), box.getCenter().getZ()}, {jx, jz}};
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
		for (int[] at : samples) {
			int height = terrain.ground(at[0], at[1]);
			if (!terrain.dry(at[0], at[1])) {
				return false; // water over the ground
			}
			if (!reached.at(at[0], at[1])) {
				return false; // cut off from the plaza
			}
			low = Math.min(low, height);
			high = Math.max(high, height);
		}
		return high - low <= MAX_SLOPE;
	}
}
