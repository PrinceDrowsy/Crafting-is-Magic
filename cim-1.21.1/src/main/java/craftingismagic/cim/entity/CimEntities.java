package craftingismagic.cim.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;
import craftingismagic.cim.rogue.RoguePonyEntity;

/** This mod's custom {@code EntityType}s - just {@link #ROGUE_PONY} so far. Same shape as {@code
 * CimItems}/{@code ModSounds}: a no-op {@link #register()} exists purely to force this class's
 * static initializer (and therefore the {@code Registry.register} call below) to run early and
 * explicitly from {@code Cim.onInitialize()}, rather than whenever something else first happens to
 * touch this class. */
public final class CimEntities {
	/** {@code FabricEntityTypeBuilder} itself is flagged deprecated in this Fabric API version
	 * (in favor of builder methods mixed directly onto vanilla's own {@code EntityType.Builder} at
	 * runtime, per the module jar's {@code EntityTypeBuilderMixin}) but is still fully functional
	 * and compiles clean otherwise - kept as-is rather than chasing an API surface that isn't
	 * statically inspectable via javap (mixin-injected members don't exist in the unmodified
	 * vanilla jar). Revisit if/when this actually breaks. */
	public static final EntityType<RoguePonyEntity> ROGUE_PONY = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(Cim.MOD_ID, "rogue_pony"),
			FabricEntityTypeBuilder.createMob()
					.entityFactory(RoguePonyEntity::new)
					.spawnGroup(SpawnGroup.MONSTER)
					.dimensions(EntityDimensions.fixed(0.6f, 1.95f))
					.defaultAttributes(RoguePonyEntity::createRoguePonyAttributes)
					.build());

	private CimEntities() {
	}

	public static void register() {
	}
}
