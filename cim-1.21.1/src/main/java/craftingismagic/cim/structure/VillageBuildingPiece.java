package craftingismagic.cim.structure;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.structure.processor.JigsawReplacementStructureProcessor;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerType;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * One building of a pony village (PonyVillageStructure): a structure placed whole, its jigsaw blocks turned
 * into what they stand for - and where a jigsaw would have brought a villager, cat, golem or farm animal in
 * a vanilla village, it brings one here too (ponies only to the buildings lived in from the start).
 */
public final class VillageBuildingPiece extends SimpleStructurePiece {
	/** Whether ponies live here from the start - most homes stand empty at first and fill over time. */
	private boolean residents = true;

	public VillageBuildingPiece(StructureTemplateManager manager, Identifier template, BlockPos pos, BlockRotation rotation) {
		super(PonyVillageStructure.BUILDING, 0, manager, template, template.toString(), placement(rotation), pos);
	}

	public VillageBuildingPiece(StructureTemplateManager manager, NbtCompound nbt) {
		super(PonyVillageStructure.BUILDING, nbt, manager, id -> placement(BlockRotation.valueOf(nbt.getString("Rot"))));
		this.residents = !nbt.contains("Residents") || nbt.getBoolean("Residents");
	}

	public BlockRotation rotation() {
		return placementData.getRotation();
	}

	void setResidents(boolean residents) {
		this.residents = residents;
	}

	/** How many ponies a vanilla village would have brought to this building. */
	static int villagerSpots(StructureTemplate template) {
		int spots = 0;
		for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(BlockPos.ORIGIN, new StructurePlacementData(), Blocks.JIGSAW)) {
			if (info.nbt() != null && info.nbt().getString("pool").endsWith("/villagers")) spots++;
		}
		return spots;
	}

	static StructurePlacementData placement(BlockRotation rotation) {
		return new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE)
				.addProcessor(JigsawReplacementStructureProcessor.INSTANCE)
				.addProcessor(BlockIgnoreStructureProcessor.IGNORE_STRUCTURE_BLOCKS);
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		super.writeNbt(context, nbt);
		nbt.putString("Rot", placementData.getRotation().name());
		nbt.putBoolean("Residents", residents);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		super.generate(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
		for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(pos, placementData, Blocks.JIGSAW)) {
			if (info.nbt() == null || !chunkBox.contains(info.pos())) continue;
			EntityType<?> type = resident(info.nbt().getString("pool"), random);
			if (type == EntityType.VILLAGER && !residents) continue; // an empty home, for now
			if (type != null) spawn(world, type, info.pos().up());
		}
	}

	/** Who a vanilla village's jigsaw would have brought here, by its pool - or null for decor and the like. */
	private static EntityType<?> resident(String pool, Random random) {
		if (pool.endsWith("/villagers")) return EntityType.VILLAGER;
		if (pool.contains("iron_golem")) return EntityType.IRON_GOLEM;
		if (pool.contains("cats")) return EntityType.CAT;
		if (pool.contains("sheep")) return EntityType.SHEEP;
		if (pool.contains("animals")) {
			return switch (random.nextInt(3)) {
				case 0 -> EntityType.COW;
				case 1 -> EntityType.PIG;
				default -> EntityType.SHEEP;
			};
		}
		return null;
	}

	private static void spawn(StructureWorldAccess world, EntityType<?> type, BlockPos pos) {
		Entity entity = type.create(world.toServerWorld());
		if (entity == null) return;
		entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0f);
		if (entity instanceof MobEntity mob) {
			mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.STRUCTURE, null);
			if (!(mob instanceof VillagerEntity)) mob.setPersistent();
		}
		if (entity instanceof VillagerEntity villager) {
			villager.setVillagerData(villager.getVillagerData().withType(VillagerType.forBiome(world.getBiome(pos))));
		}
		world.spawnEntityAndPassengers(entity);
	}

	@Override
	protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox) {
	}
}
