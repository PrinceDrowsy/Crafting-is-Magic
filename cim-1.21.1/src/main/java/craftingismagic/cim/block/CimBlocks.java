package craftingismagic.cim.block;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/** The mod's blocks - so far, the builders' room markers (docs/building-guide.md). */
public final class CimBlocks {
	public static final List<RoomMarkerBlock> ROOM_MARKERS = new ArrayList<>();

	public static final RoomMarkerBlock BEDROOM_MARKER = marker(RoomType.BEDROOM);
	public static final RoomMarkerBlock KITCHEN_MARKER = marker(RoomType.KITCHEN);
	public static final RoomMarkerBlock LIVING_ROOM_MARKER = marker(RoomType.LIVING_ROOM);
	public static final RoomMarkerBlock WORKROOM_MARKER = marker(RoomType.WORKROOM);
	public static final RoomMarkerBlock NURSERY_MARKER = marker(RoomType.NURSERY);

	public static final KeepsakeSpotBlock KEEPSAKE_SPOT = keepsakeSpot();

	/** Paint on a wall, laid down by the interior generation. */
	public static final WallPaintBlock WALL_PAINT = Registry.register(Registries.BLOCK, Identifier.of(Cim.MOD_ID, "wall_paint"),
			new WallPaintBlock(AbstractBlock.Settings.create().noCollision().nonOpaque().breakInstantly().dropsNothing()
					.replaceable().sounds(BlockSoundGroup.WOOL)));

	/** The Teacher's job site. */
	public static final ChalkboardBlock CHALKBOARD = Registry.register(Registries.BLOCK, Identifier.of(Cim.MOD_ID, "chalkboard"),
			new ChalkboardBlock(AbstractBlock.Settings.create().strength(1.5f).sounds(BlockSoundGroup.WOOD).nonOpaque()));
	static {
		Registry.register(Registries.ITEM, Identifier.of(Cim.MOD_ID, "chalkboard"), new BlockItem(CHALKBOARD, new Item.Settings()));
	}

	public static final BlockEntityType<RoomMarkerBlockEntity> ROOM_MARKER_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Cim.MOD_ID, "room_marker"),
			BlockEntityType.Builder.create(RoomMarkerBlockEntity::new, ROOM_MARKERS.toArray(RoomMarkerBlock[]::new)).build(null));

	private CimBlocks() {
	}

	public static void register() {
		// Builders' tools: alongside the structure blocks, in the operator utilities tab.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(CHALKBOARD));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR).register(entries -> {
			ROOM_MARKERS.forEach(entries::add);
			entries.add(KEEPSAKE_SPOT);
		});
	}

	private static KeepsakeSpotBlock keepsakeSpot() {
		Identifier id = Identifier.of(Cim.MOD_ID, "keepsake_spot");
		KeepsakeSpotBlock block = Registry.register(Registries.BLOCK, id, new KeepsakeSpotBlock(AbstractBlock.Settings.create()
				.noCollision().nonOpaque().breakInstantly().dropsNothing().sounds(BlockSoundGroup.WOOL).replaceable()));
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
		return block;
	}

	private static RoomMarkerBlock marker(RoomType room) {
		Identifier id = Identifier.of(Cim.MOD_ID, room.id() + "_marker");
		RoomMarkerBlock block = Registry.register(Registries.BLOCK, id, new RoomMarkerBlock(room, AbstractBlock.Settings.create()
				.noCollision().nonOpaque().breakInstantly().dropsNothing().sounds(BlockSoundGroup.WOOL).pistonBehavior(PistonBehavior.BLOCK)
				.replaceable()));
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
		ROOM_MARKERS.add(block);
		return block;
	}
}
