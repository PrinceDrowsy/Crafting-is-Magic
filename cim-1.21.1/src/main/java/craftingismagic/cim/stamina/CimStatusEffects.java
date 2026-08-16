package craftingismagic.cim.stamina;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import craftingismagic.cim.Cim;

/**
 * This mod's custom {@code StatusEffect}s - just {@link #EXHAUSTION} so far, applied purely for its
 * real vanilla HUD/inventory-panel icon while a player's Stamina regen is locked (see
 * {@code StaminaManager}) - a plain {@code StatusEffect} subclass with no overrides does nothing
 * mechanically on its own (no attribute modifiers, no periodic effect), so this is UI-only by
 * construction, not something that needs a separate "don't actually apply a debuff" guard.
 */
public final class CimStatusEffects {
	public static final StatusEffect EXHAUSTION = Registry.register(Registries.STATUS_EFFECT,
			Identifier.of(Cim.MOD_ID, "exhaustion"),
			new StatusEffect(StatusEffectCategory.HARMFUL, 0x3C4A6B) {
			});

	private CimStatusEffects() {
	}

	public static void register() {
	}
}
