package craftingismagic.cim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.datafixers.util.Either;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.Identifier;

/** Lets {@code VillageAdditions} tell which structure a village pool entry is, to take vanilla's out. */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
	@Accessor("location")
	Either<Identifier, StructureTemplate> cim$location();
}
