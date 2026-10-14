package craftingismagic.cim.debug;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import craftingismagic.cim.memory.Emotion;
import craftingismagic.cim.memory.EmotionIntensity;
import craftingismagic.cim.memory.Memory;
import craftingismagic.cim.memory.MemoryDescriptions;
import craftingismagic.cim.memory.MemoryImportance;
import craftingismagic.cim.memory.MemoryKnowledge;
import craftingismagic.cim.memory.MemoryStore;
import craftingismagic.cim.narrative.TaleAst.TaleFile;
import craftingismagic.cim.narrative.TaleLibrary;
import craftingismagic.cim.narrative.TaleProblem;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.story.StoryFacts;
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
 * <li>{@code memory add|list|clear <target>} - write, read, and wipe memories.</li>
 * <li>{@code fact world|player|relationship ... set|clear|has|list} - story facts at each scope.</li>
 * <li>{@code tale validate|list} - the {@code .tale} files from the last data load, and their problems.</li>
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
						.then(memoryBranch())
						.then(factBranch())
						.then(taleBranch())
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

	/**
	 * {@code memory add <target> <type> [importance] [emotion] [intensity]}, {@code memory list <target>},
	 * {@code memory clear <target>}. A memory added here is about whoever ran the command (if a player),
	 * and witnessed - the shape of a Shared Memory between them.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> memoryBranch() {
		return CommandManager.literal("memory")
				.then(CommandManager.literal("add")
						.then(CommandManager.argument("target", EntityArgumentType.entity())
								.then(CommandManager.argument("type", IdentifierArgumentType.identifier())
										.executes(context -> addMemory(context, MemoryImportance.NOTABLE, null, EmotionIntensity.CLEAR))
										.then(enumArgument("importance", MemoryImportance.values())
												.executes(context -> addMemory(context, enumValue(context, "importance", MemoryImportance.class), null, EmotionIntensity.CLEAR))
												.then(enumArgument("emotion", Emotion.values())
														.executes(context -> addMemory(context, enumValue(context, "importance", MemoryImportance.class),
																enumValue(context, "emotion", Emotion.class), EmotionIntensity.CLEAR))
														.then(enumArgument("intensity", EmotionIntensity.values())
																.executes(context -> addMemory(context, enumValue(context, "importance", MemoryImportance.class),
																		enumValue(context, "emotion", Emotion.class),
																		enumValue(context, "intensity", EmotionIntensity.class)))))))))
				.then(CommandManager.literal("list")
						.then(CommandManager.argument("target", EntityArgumentType.entity())
								.executes(DebugCommand::listMemories)))
				.then(CommandManager.literal("clear")
						.then(CommandManager.argument("target", EntityArgumentType.entity())
								.executes(context -> {
									Entity target = EntityArgumentType.getEntity(context, "target");
									int count = MemoryStore.getAll(target).size();
									MemoryStore.clear(target);
									context.getSource().sendFeedback(() -> Text.literal("Cleared " + count + " memories from " + target.getName().getString()), false);
									return count;
								})));
	}

	/** One scope of story facts, resolved from the command's arguments. */
	private interface FactScope {
		void set(Identifier fact) throws CommandSyntaxException;

		boolean clear(Identifier fact) throws CommandSyntaxException;

		boolean has(Identifier fact) throws CommandSyntaxException;

		Map<Identifier, Long> all() throws CommandSyntaxException;

		String describe() throws CommandSyntaxException;
	}

	@FunctionalInterface
	private interface ScopeResolver {
		FactScope resolve(CommandContext<ServerCommandSource> context) throws CommandSyntaxException;
	}

	/**
	 * {@code fact world <set|clear|has> <id>}, {@code fact player <player> ...}, {@code fact relationship
	 * <pony> ...} (between whoever runs it and that pony), each also with {@code list}.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> factBranch() {
		ScopeResolver world = context -> {
			MinecraftServer server = context.getSource().getServer();
			return new FactScope() {
				public void set(Identifier fact) {
					StoryFacts.setWorld(server, fact);
				}

				public boolean clear(Identifier fact) {
					return StoryFacts.clearWorld(server, fact);
				}

				public boolean has(Identifier fact) {
					return StoryFacts.hasWorld(server, fact);
				}

				public Map<Identifier, Long> all() {
					return StoryFacts.worldFacts(server);
				}

				public String describe() {
					return "the world";
				}
			};
		};
		ScopeResolver personal = context -> {
			ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
			return new FactScope() {
				public void set(Identifier fact) {
					StoryFacts.setPersonal(player, fact);
				}

				public boolean clear(Identifier fact) {
					return StoryFacts.clearPersonal(player, fact);
				}

				public boolean has(Identifier fact) {
					return StoryFacts.hasPersonal(player, fact);
				}

				public Map<Identifier, Long> all() {
					return StoryFacts.personalFacts(player);
				}

				public String describe() {
					return player.getName().getString();
				}
			};
		};
		ScopeResolver relationship = context -> {
			Entity pony = EntityArgumentType.getEntity(context, "pony");
			ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
			return new FactScope() {
				public void set(Identifier fact) {
					StoryFacts.setRelationship(pony, player.getUuid(), fact);
				}

				public boolean clear(Identifier fact) {
					return StoryFacts.clearRelationship(pony, player.getUuid(), fact);
				}

				public boolean has(Identifier fact) {
					return StoryFacts.hasRelationship(pony, player.getUuid(), fact);
				}

				public Map<Identifier, Long> all() {
					return StoryFacts.relationshipFacts(pony, player.getUuid());
				}

				public String describe() {
					return player.getName().getString() + " and " + pony.getName().getString();
				}
			};
		};

		return CommandManager.literal("fact")
				.then(factActions(CommandManager.literal("world"), world))
				.then(CommandManager.literal("player")
						.then(factActions(CommandManager.argument("player", EntityArgumentType.player()), personal)))
				.then(CommandManager.literal("relationship")
						.then(factActions(CommandManager.argument("pony", EntityArgumentType.entity()), relationship)));
	}

	private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, T>> T factActions(T node, ScopeResolver resolver) {
		return node
				.then(CommandManager.literal("set").then(CommandManager.argument("fact", IdentifierArgumentType.identifier())
						.executes(context -> {
							FactScope scope = resolver.resolve(context);
							Identifier fact = IdentifierArgumentType.getIdentifier(context, "fact");
							scope.set(fact);
							String who = scope.describe();
							context.getSource().sendFeedback(() -> Text.literal("Set " + fact + " for " + who), false);
							return 1;
						})))
				.then(CommandManager.literal("clear").then(CommandManager.argument("fact", IdentifierArgumentType.identifier())
						.executes(context -> {
							FactScope scope = resolver.resolve(context);
							Identifier fact = IdentifierArgumentType.getIdentifier(context, "fact");
							boolean removed = scope.clear(fact);
							String who = scope.describe();
							context.getSource().sendFeedback(() -> Text.literal((removed ? "Cleared " : "Wasn't set: ") + fact + " for " + who), false);
							return removed ? 1 : 0;
						})))
				.then(CommandManager.literal("has").then(CommandManager.argument("fact", IdentifierArgumentType.identifier())
						.executes(context -> {
							FactScope scope = resolver.resolve(context);
							Identifier fact = IdentifierArgumentType.getIdentifier(context, "fact");
							boolean has = scope.has(fact);
							String who = scope.describe();
							context.getSource().sendFeedback(() -> Text.literal(fact + (has ? " is set" : " is not set") + " for " + who), false);
							return has ? 1 : 0;
						})))
				.then(CommandManager.literal("list")
						.executes(context -> {
							FactScope scope = resolver.resolve(context);
							Map<Identifier, Long> facts = scope.all();
							String who = scope.describe();
							long now = context.getSource().getWorld().getTime();
							context.getSource().sendFeedback(() -> Text.literal(facts.size() + " facts for " + who + ":"), false);
							facts.forEach((fact, time) -> context.getSource().sendFeedback(
									() -> Text.literal("  " + fact + " (" + Math.max(0, (now - time) / 24000L) + " days ago)"), false));
							return facts.size();
						}));
	}

	/** How many problems {@code tale validate} prints in chat before pointing at the log. */
	private static final int TALE_PROBLEMS_SHOWN = 20;

	/**
	 * {@code tale validate} prints every problem from the last load (run {@code /reload} after editing a
	 * file); {@code tale list} shows each loaded file with its nodes and threads.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> taleBranch() {
		return CommandManager.literal("tale")
				.then(CommandManager.literal("validate")
						.executes(context -> {
							ServerCommandSource source = context.getSource();
							TaleLibrary.Snapshot tales = TaleLibrary.get();
							long errors = tales.errorCount();
							long warnings = tales.warningCount();
							source.sendFeedback(() -> Text.literal(tales.files().size() + " tale file(s): " + errors + " error(s), " + warnings + " warning(s)")
									.formatted(errors > 0 ? Formatting.RED : warnings > 0 ? Formatting.YELLOW : Formatting.GREEN), false);
							tales.problems().stream().limit(TALE_PROBLEMS_SHOWN).forEach(problem -> source.sendFeedback(() -> Text.literal(problem.toString())
									.formatted(problem.severity() == TaleProblem.Severity.ERROR ? Formatting.RED : Formatting.YELLOW), false));
							if (tales.problems().size() > TALE_PROBLEMS_SHOWN) {
								source.sendFeedback(() -> Text.literal("...and " + (tales.problems().size() - TALE_PROBLEMS_SHOWN)
										+ " more - the full list is in the log.").formatted(Formatting.GRAY), false);
							}
							return (int) errors;
						}))
				.then(CommandManager.literal("list")
						.executes(context -> {
							ServerCommandSource source = context.getSource();
							Collection<TaleFile> files = TaleLibrary.get().files().values();
							source.sendFeedback(() -> Text.literal(files.size() + " tale file(s):"), false);
							for (TaleFile file : files) {
								source.sendFeedback(() -> Text.literal("  " + file.id() + " - " + file.nodes().size() + " node(s), "
										+ file.threads().size() + " thread(s)"), false);
							}
							return files.size();
						}));
	}

	private static int addMemory(CommandContext<ServerCommandSource> context, MemoryImportance importance, Emotion emotion,
			EmotionIntensity intensity) throws CommandSyntaxException {
		Entity target = EntityArgumentType.getEntity(context, "target");
		Identifier type = IdentifierArgumentType.getIdentifier(context, "type");
		ServerPlayerEntity player = context.getSource().getPlayer();
		Memory memory = new Memory(type, Optional.ofNullable(player).map(Entity::getUuid), MemoryKnowledge.WITNESSED,
				importance, Optional.ofNullable(emotion), intensity, target.getWorld().getTime());
		MemoryStore.add(target, memory);
		context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " now remembers "
				+ MemoryDescriptions.describe(memory, target.getWorld().getTime())), false);
		return 1;
	}

	private static int listMemories(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Entity target = EntityArgumentType.getEntity(context, "target");
		List<Memory> memories = MemoryStore.getAll(target);
		context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " holds " + memories.size() + " memories:"), false);
		long now = target.getWorld().getTime();
		for (Memory memory : memories) {
			context.getSource().sendFeedback(() -> Text.literal("  " + MemoryDescriptions.describe(memory, now)), false);
		}
		return memories.size();
	}

	private static String lower(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> RequiredArgumentBuilder<ServerCommandSource, String> enumArgument(String name, E[] values) {
		return CommandManager.argument(name, StringArgumentType.word())
				.suggests((context, builder) -> CommandSource.suggestMatching(
						Arrays.stream(values).map(DebugCommand::lower), builder));
	}

	private static <E extends Enum<E>> E enumValue(CommandContext<ServerCommandSource> context, String name, Class<E> type)
			throws CommandSyntaxException {
		String raw = StringArgumentType.getString(context, name);
		try {
			return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Text.literal("Unknown " + name + ": " + raw)).create();
		}
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
