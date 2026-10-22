package craftingismagic.cim.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.datafixers.util.Pair;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;

/** Lets {@code VillageAdditions} add the mod's buildings to vanilla's village pools. */
@Mixin(StructurePool.class)
public interface StructurePoolAccessor {
	@Accessor("elements")
	ObjectArrayList<StructurePoolElement> cim$elements();

	@Accessor("elementCounts")
	List<Pair<StructurePoolElement, Integer>> cim$elementCounts();

	@Mutable
	@Accessor("elementCounts")
	void cim$setElementCounts(List<Pair<StructurePoolElement, Integer>> counts);
}
