package craftingismagic.cim.debug;

import java.util.Collection;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.VillagerFamilyHandler;

/**
 * The {@code /cim debug ...} root every debug command hangs off (docs/implementation-plan.md: Debug
 * and testing tools). Gated by {@link CimDebug#allowed(ServerCommandSource)} - op level 2 plus the
 * server's debug switch. Each later phase adds its own branch here.
 * <ul>
 * <li>{@code stamina show|set|max|exhaust|wake} - exercise Stamina without waiting on regen or a
 * night's sleep.</li>
 * <li>{@code grow <villagers>} - push villagers to their next life stage.</li>
 * </ul>
 */
public final class DebugCommand {
	private DebugCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
			CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("cim")
				.then(CommandManager.literal("debug")
						.requires(CimDebug::allowed)
						.then(staminaBranch())
						.then(CommandManager.literal("grow")
								.then(CommandManager.argument("villagers", EntityArgumentType.entities())
										.executes(context -> grow(context.getSource(), EntityArgumentType.getEntities(context, "villagers")))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> staminaBranch() {
		return CommandManager.literal("stamina")
				.then(CommandManager.literal("show")
						.executes(context -> forSelf(context, player -> context.getSource().sendFeedback(() -> describe(player), false))))
				.then(CommandManager.literal("set")
						.then(CommandManager.argument("value", FloatArgumentType.floatArg(0f))
								.executes(context -> forSelf(context, player -> {
									Stamina stamina = StaminaAttachment.getStamina(player);
									float value = Math.min(FloatArgumentType.getFloat(context, "value"), stamina.max());
									StaminaAttachment.setStamina(player, new Stamina(value, stamina.max(), stamina.awaitingSleep()));
									context.getSource().sendFeedback(() -> describe(player), false);
								}))))
				.then(CommandManager.literal("max")
						.then(CommandManager.argument("value", FloatArgumentType.floatArg(1f))
								.executes(context -> forSelf(context, player -> {
									Stamina stamina = StaminaAttachment.getStamina(player);
									float max = FloatArgumentType.getFloat(context, "value");
									StaminaAttachment.setStamina(player, new Stamina(Math.min(stamina.current(), max), max, stamina.awaitingSleep()));
									context.getSource().sendFeedback(() -> describe(player), false);
								}))))
				// Drains to exactly 0 through the real drain path, so Exhaustion applies the same way
				// gameplay would trigger it.
				.then(CommandManager.literal("exhaust")
						.executes(context -> forSelf(context, player -> {
							StaminaManager.drain(player, StaminaAttachment.getStamina(player).current());
							context.getSource().sendFeedback(() -> describe(player), false);
						})))
				// Acts as if the player just woke from a full night's sleep - releases Exhaustion and
				// grants any growth it was holding back.
				.then(CommandManager.literal("wake")
						.executes(context -> forSelf(context, player -> {
							StaminaManager.onWokeUp(player);
							context.getSource().sendFeedback(() -> describe(player), false);
						})));
	}

	private static int forSelf(CommandContext<ServerCommandSource> context, Consumer<ServerPlayerEntity> action) throws CommandSyntaxException {
		action.accept(context.getSource().getPlayerOrThrow());
		return 1;
	}

	private static Text describe(ServerPlayerEntity player) {
		Stamina stamina = StaminaAttachment.getStamina(player);
		return Text.literal(String.format("Stamina %.1f / %.1f%s", stamina.current(), stamina.max(),
				stamina.awaitingSleep() ? " (Exhausted - awaiting sleep)" : ""));
	}

	private static int grow(ServerCommandSource source, Collection<? extends Entity> entities) {
		int grown = 0;
		for (Entity entity : entities) {
			if (!(entity instanceof VillagerEntity villager)) {
				continue;
			}
			LifeStage next = VillagerFamilyHandler.advanceLifeStage(villager);
			if (next != null) {
				grown++;
				source.sendFeedback(() -> Text.literal(villager.getName().getString() + " is now " + next), false);
			}
		}
		if (grown == 0) {
			source.sendError(Text.literal("No villagers in a growing stage were selected."));
		}
		return grown;
	}
}
