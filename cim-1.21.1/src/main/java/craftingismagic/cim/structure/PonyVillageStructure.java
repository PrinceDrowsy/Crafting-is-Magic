package craftingismagic.cim.structure;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import craftingismagic.cim.Cim;

/**
 * A pony village, laid out in rings round its heart (README: Village identity - the ring layout): a
 * centerpiece on an open plaza, ring roads round it, eight roads out through every ring, and buildings on
 * plots along them - planned whole by {@link PonyVillagePlanner} instead of grown street by street like a
 * vanilla village. It takes over {@code minecraft:village_plains} (data/minecraft/worldgen/structure), so
 * {@code /locate}, the village spawn and other mods still find it as a village. What the village is - its
 * size, rings, whether it's abandoned - comes from the seed ({@link PonyVillageSite}), so its districts can
 * work it out too. Abandoned villages are vanilla's own zombie villages, built the vanilla way from
 * {@code abandoned_pool}.
 */
public final class PonyVillageStructure extends Structure {
	private static final Logger LOGGER = LoggerFactory.getLogger("cim/structures");

	public static final MapCodec<PonyVillageStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			configCodecBuilder(instance),
			Identifier.CODEC.fieldOf("layout").forGetter(structure -> structure.layout),
			Identifier.CODEC.optionalFieldOf("abandoned_pool").forGetter(structure -> structure.abandonedPool),
			Codec.floatRange(0, 1).optionalFieldOf("abandoned_chance", 0f).forGetter(structure -> structure.abandonedChance))
			.apply(instance, PonyVillageStructure::new));

	public static final StructureType<PonyVillageStructure> TYPE = Registry.register(Registries.STRUCTURE_TYPE,
			Identifier.of(Cim.MOD_ID, "pony_village"), () -> CODEC);
	public static final StructurePieceType.ManagerAware BUILDING = Registry.register(Registries.STRUCTURE_PIECE,
			Identifier.of(Cim.MOD_ID, "village_building"), (StructurePieceType.ManagerAware) VillageBuildingPiece::new);
	public static final StructurePieceType.Simple ROAD = Registry.register(Registries.STRUCTURE_PIECE,
			Identifier.of(Cim.MOD_ID, "village_road"), (StructurePieceType.Simple) VillageRoadPiece::new);
	public static final StructurePieceType.Simple SMOOTHING = Registry.register(Registries.STRUCTURE_PIECE,
			Identifier.of(Cim.MOD_ID, "village_smoothing"), (StructurePieceType.Simple) VillageSmoothingPiece::new);

	/** The structure set villages are placed by - its grid is how a village finds its neighbours. */
	private static final Identifier VILLAGES = Identifier.ofVanilla("villages");

	private final Identifier layout;
	private final Optional<Identifier> abandonedPool;
	private final float abandonedChance;

	public PonyVillageStructure(Config config, Identifier layout, Optional<Identifier> abandonedPool, float abandonedChance) {
		super(config);
		this.layout = layout;
		this.abandonedPool = abandonedPool;
		this.abandonedChance = abandonedChance;
	}

	@Override
	protected Optional<StructurePosition> getStructurePosition(Context context) {
		ChunkPos chunk = context.chunkPos();
		PonyVillageSite site = PonyVillageSite.of(context.seed(), chunk, abandonedPool.isPresent() ? abandonedChance : 0);
		if (site.abandoned()) {
			var pool = context.dynamicRegistryManager().get(RegistryKeys.TEMPLATE_POOL)
					.getEntry(RegistryKey.of(RegistryKeys.TEMPLATE_POOL, abandonedPool.get()));
			if (pool.isPresent()) {
				return StructurePoolBasedGenerator.generate(context, pool.get(), Optional.empty(), 6, new BlockPos(chunk.getStartX(), 0, chunk.getStartZ()),
						true, Optional.of(Heightmap.Type.WORLD_SURFACE_WG), 80, StructurePoolAliasLookup.EMPTY, DimensionPadding.NONE,
						StructureLiquidSettings.APPLY_WATERLOGGING);
			}
		}
		PonyVillageLayouts.Layout plan = PonyVillageLayouts.get(layout);
		if (plan == null) {
			LOGGER.error("No pony village layout called {} (data/<ns>/pony_village)", layout);
			return Optional.empty();
		}
		int x = site.heartX();
		int z = site.heartZ();
		ChunkGenerator generator = context.chunkGenerator();
		// getHeightOnGround is the same as getHeight - one terrain sample, not two.
		int y = generator.getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
		if (generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig()) < y) {
			return Optional.empty(); // never in the water
		}
		if (crowded(context, site)) return Optional.empty();
		BlockPos heart = new BlockPos(x, y, z);
		return Optional.of(new StructurePosition(heart, pieces -> PonyVillagePlanner.plan(context, plan, site, heart, pieces)));
	}

	/**
	 * Whether a neighbouring village has a claim on this one's ground. Villages can start as little as eight
	 * chunks apart, and pony villages reach much further than vanilla's; where two would overlap, the bigger
	 * one is built (on a tie, the one further north-west), and the other isn't. Both work it out from the
	 * seed alone, so they always agree.
	 */
	private boolean crowded(Context context, PonyVillageSite site) {
		StructureSet villages = context.dynamicRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(VILLAGES);
		if (villages == null || !(villages.placement() instanceof RandomSpreadStructurePlacement spread)) return false;
		int spacing = spread.getSpacing();
		int regionX = Math.floorDiv(site.start().x, spacing), regionZ = Math.floorDiv(site.start().z, spacing);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos other = spread.getStartChunk(context.seed(), (regionX + dx) * spacing, (regionZ + dz) * spacing);
				if (other.equals(site.start())) continue;
				PonyVillageSite neighbour = PonyVillageSite.of(context.seed(), other, abandonedPool.isPresent() ? abandonedChance : 0);
				double distance = Math.hypot(neighbour.heartX() - site.heartX(), neighbour.heartZ() - site.heartZ());
				if (distance >= site.reach() + neighbour.reach() || !neighbour.outranks(site)) continue;
				if (builtAt(context, neighbour)) return true;
			}
		}
		return false;
	}

	/** Whether a pony village really starts at this site: in its biomes, and not in the water. */
	private boolean builtAt(Context context, PonyVillageSite site) {
		ChunkGenerator generator = context.chunkGenerator();
		int x = site.heartX(), z = site.heartZ();
		int y = generator.getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
		if (!context.biomePredicate().test(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y),
				BiomeCoords.fromBlock(z), context.noiseConfig().getMultiNoiseSampler()))) {
			return false;
		}
		return generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig()) >= y;
	}

	@Override
	public StructureType<?> getType() {
		return TYPE;
	}

	/** Registers the structure and its pieces (they have to exist before worlds load). */
	public static void register() {
	}
}
