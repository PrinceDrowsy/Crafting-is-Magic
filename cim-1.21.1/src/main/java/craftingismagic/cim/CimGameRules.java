package craftingismagic.cim;

import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.gamerule.v1.rule.EnumRule;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

/**
 * Per-world story settings, as vanilla gamerules (README: Performance, configurability, and
 * compatibility) - these belong to a world rather than a player or a server, so they're saved with
 * the world and changed with {@code /gamerule} like any vanilla setting. Scaffolding for now: the
 * systems that read them (threads, the Mane Six, the Crystal Empire Rescue) aren't built yet.
 */
public final class CimGameRules {
	private static final CustomGameRuleCategory CATEGORY = new CustomGameRuleCategory(
			Identifier.of(Cim.MOD_ID, "story"), Text.literal("Crafting is Magic: Story").formatted(Formatting.YELLOW, Formatting.BOLD));

	/** How story villains move when no player has engaged them (README: Progression, world-wait). */
	public static final GameRules.Key<EnumRule<WorldWait>> WORLD_WAIT = GameRuleRegistry.register(
			"cimWorldWait", CATEGORY, GameRuleFactory.createEnumRule(WorldWait.MIDDLE_PATH));

	/** Whether the show's Mane Six exist in this world - opt-in, so off by default (README: The Mane
	 * Six are opt-in). */
	public static final GameRules.Key<GameRules.BooleanRule> MANE_SIX = GameRuleRegistry.register(
			"cimManeSix", CATEGORY, GameRuleFactory.createBooleanRule(false));

	/** Lets crystal ponies and the Crystal Empire exist without first completing the Crystal Empire
	 * Rescue (README: Crystal heritage, the proposed config option). */
	public static final GameRules.Key<GameRules.BooleanRule> SKIP_CRYSTAL_LOCK = GameRuleRegistry.register(
			"cimSkipCrystalLock", CATEGORY, GameRuleFactory.createBooleanRule(false));

	private CimGameRules() {
	}

	/** Forces the static registrations above to run during mod init. */
	public static void register() {
		Cim.LOGGER.debug("Registered gamerules {}, {}, {}", WORLD_WAIT.getName(), MANE_SIX.getName(), SKIP_CRYSTAL_LOCK.getName());
	}

	public enum WorldWait {
		/** No villain ever moves without the player. */
		EVERYTHING_WAITS,
		/** Story villains wait until found; ignored threats grow on a soft clock (the default). */
		MIDDLE_PATH,
		/** Villains act on their own timelines. */
		LIVING_WORLD
	}
}
