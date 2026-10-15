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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
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
import craftingismagic.cim.memory.Mood;
import craftingismagic.cim.network.MomentS2CPayload;
import craftingismagic.cim.villager.VillagerGriefHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import craftingismagic.cim.narrative.TaleAst;
import craftingismagic.cim.narrative.TaleAst.TaleFile;
import craftingismagic.cim.narrative.TaleLibrary;
import craftingismagic.cim.narrative.TaleProblem;
import craftingismagic.cim.narrative.TaleThreads;
import craftingismagic.cim.stamina.StaminaAttachment;
import craftingismagic.cim.stamina.StaminaAttachment.Stamina;
import craftingismagic.cim.stamina.StaminaManager;
import craftingismagic.cim.story.StoryFacts;
import craftingismagic.cim.story.StoryThreads;
import craftingismagic.cim.story.PlayerNews;
import craftingismagic.cim.villager.LifeStage;
import craftingismagic.cim.villager.PonyFamilyAttachment;
import craftingismagic.cim.villager.VillagerDialogue;
import craftingismagic.cim.villager.VillagerFamilyHandler;
import craftingismagic.cim.speech.HumDirector;
import craftingismagic.cim.speech.HumLibrary;
import craftingismagic.cim.speech.PrincessVoices;
import craftingismagic.cim.speech.SpeakingVoice;
import craftingismagic.cim.speech.SpeakingVoices;
import craftingismagic.cim.voice.CimVoices;
import craftingismagic.cim.voice.VoiceLeanings;

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
 * <li>{@code tale validate|list} - the {@code .tale} files from the last data load, and their problems;
 * {@code tale export} - the vocabulary for the {@code .tale} editor.</li>
 * <li>{@code dialogue test <node> <pony>} - play any node as a conversation.</li>
 * <li>{@code thread status|start|advance|reset|fire} - see and move your threads, and fake their events.</li>
 * <li>{@code voice show|set|add|dice} - your inner voices, and forcing rolls.</li>
 * <li>{@code speech show|set|princess|roll <target>} - how a pony or player sounds when they talk.</li>
 * <li>{@code hum <pony> [tune]} - a pony hums right now.</li>
 * <li>{@code mood show [entity]}, {@code moment play|stop} - feelings from memories, and scene-setting moments.</li>
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
						.then(dialogueBranch())
						.then(threadBranch())
						.then(speechBranch())
						.then(humBranch())
						.then(voiceBranch())
						.then(moodBranch())
						.then(momentBranch())
						.then(newsBranch())
						.then(familyBranch())
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
							source.sendFeedback(() -> Text.literal(tales.files().size() + " tale file(s): " + errors + " error(s), " + warnings + " warning(s)"
									+ (tales.leftOut() > 0 ? "; " + tales.leftOut() + " node(s)/thread(s) left out for errors" : ""))
									.formatted(errors > 0 ? Formatting.RED : warnings > 0 ? Formatting.YELLOW : Formatting.GREEN), false);
							tales.problems().stream().limit(TALE_PROBLEMS_SHOWN).forEach(problem -> source.sendFeedback(() -> Text.literal(problem.toString())
									.formatted(problem.severity() == TaleProblem.Severity.ERROR ? Formatting.RED : Formatting.YELLOW), false));
							if (tales.problems().size() > TALE_PROBLEMS_SHOWN) {
								source.sendFeedback(() -> Text.literal("...and " + (tales.problems().size() - TALE_PROBLEMS_SHOWN)
										+ " more - the full list is in the log.").formatted(Formatting.GRAY), false);
							}
							return (int) errors;
						}))
				.then(CommandManager.literal("export")
						.executes(context -> {
							java.nio.file.Path file = exportVocabulary();
							context.getSource().sendFeedback(() -> Text.literal(file == null ? "Export failed - see the log"
									: "Wrote the .tale vocabulary for the editor to " + file), false);
							return file == null ? 0 : 1;
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

	/**
	 * {@code dialogue test <node> <pony>} opens any loaded node as a conversation with that pony,
	 * skipping its {@code start:} and {@code when:} - the way to play a branch without setting up the
	 * world it needs.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> dialogueBranch() {
		return CommandManager.literal("dialogue")
				.then(CommandManager.literal("test")
						.then(CommandManager.argument("node", IdentifierArgumentType.identifier())
								.suggests((context, builder) -> CommandSource.suggestIdentifiers(TaleLibrary.get().nodes().keySet(), builder))
								.then(CommandManager.argument("pony", EntityArgumentType.entity())
										.executes(context -> {
											Identifier id = IdentifierArgumentType.getIdentifier(context, "node");
											TaleAst.Node node = TaleLibrary.get().nodes().get(id);
											if (node == null) {
												throw new SimpleCommandExceptionType(Text.literal("No loaded node " + id + " (see /cim debug tale list)")).create();
											}
											Entity pony = EntityArgumentType.getEntity(context, "pony");
											VillagerDialogue.startAt(context.getSource().getPlayerOrThrow(), pony, node);
											return 1;
										}))));
	}

	/**
	 * {@code thread status} lists your threads; {@code start} (ignores {@code requires:}), {@code
	 * advance}, and {@code reset} move them by hand; {@code fire} fakes an event - {@code sleep},
	 * {@code wake}, {@code day}, {@code night}, {@code enter_biome <biome>}, {@code talk <node|any>},
	 * {@code fact <id>} (the fact itself isn't set) - so a stage can be tested without the world
	 * cooperating.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> threadBranch() {
		LiteralArgumentBuilder<ServerCommandSource> fire = CommandManager.literal("fire");
		for (String bare : List.of("sleep", "wake", "day", "night")) {
			fire.then(CommandManager.literal(bare).executes(context -> forSelf(context, player -> {
				StoryThreads.fire(player, player, TaleThreads.Event.of(Identifier.of("cim", bare)));
				context.getSource().sendFeedback(() -> Text.literal("Fired " + bare), false);
			})));
		}
		fire.then(CommandManager.literal("enter_biome").then(CommandManager.argument("biome", IdentifierArgumentType.identifier())
				.suggests((context, builder) -> CommandSource.suggestIdentifiers(
						context.getSource().getRegistryManager().get(RegistryKeys.BIOME).getIds(), builder))
				.executes(context -> {
					Identifier id = IdentifierArgumentType.getIdentifier(context, "biome");
					ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					RegistryEntry<Biome> biome = context.getSource().getRegistryManager().get(RegistryKeys.BIOME)
							.getEntry(RegistryKey.of(RegistryKeys.BIOME, id))
							.orElseThrow(() -> new SimpleCommandExceptionType(Text.literal("No biome " + id)).create());
					StoryThreads.enteredBiome(player, biome);
					context.getSource().sendFeedback(() -> Text.literal("Fired enter_biome " + id), false);
					return 1;
				})));
		fire.then(CommandManager.literal("talk").then(CommandManager.argument("node", StringArgumentType.greedyString())
				.suggests((context, builder) -> CommandSource.suggestMatching(
						java.util.stream.Stream.concat(java.util.stream.Stream.of("any"),
								TaleLibrary.get().nodes().keySet().stream().map(Identifier::toString)), builder))
				.executes(context -> forSelf(context, player -> {
					String node = StringArgumentType.getString(context, "node").trim();
					boolean any = node.equals("any");
					StoryThreads.talked(player, player, any ? List.of() : List.of(Identifier.of(node)), any);
					context.getSource().sendFeedback(() -> Text.literal("Fired talk " + node), false);
				}))));
		fire.then(CommandManager.literal("fact").then(CommandManager.argument("fact", IdentifierArgumentType.identifier())
				.executes(context -> forSelf(context, player -> {
					Identifier fact = IdentifierArgumentType.getIdentifier(context, "fact");
					StoryThreads.fire(player, player, new TaleThreads.Event(Identifier.of("cim", "fact"),
							args -> args.size() == 1 && Identifier.of(args.get(0).contains(":") ? args.get(0) : "cim:" + args.get(0)).equals(fact)));
					context.getSource().sendFeedback(() -> Text.literal("Fired fact " + fact + " (not set)"), false);
				}))));

		return CommandManager.literal("thread")
				.then(CommandManager.literal("status").executes(context -> forSelf(context, player -> {
					Map<Identifier, TaleThreads.Progress> all = StoryThreads.progress(player);
					context.getSource().sendFeedback(() -> Text.literal(all.size() + " thread(s) for " + player.getName().getString() + ":"), false);
					all.forEach((id, progress) -> context.getSource().sendFeedback(() -> Text.literal("  " + id + " - "
							+ (progress.finished() ? "finished" : "at \"" + progress.stage() + "\", acted " + progress.fired() + "x")
							+ (TaleLibrary.get().threads().containsKey(id) ? "" : " (not loaded)")), false));
					long waiting = TaleLibrary.get().threads().keySet().stream().filter(id -> !all.containsKey(id)).count();
					if (waiting > 0) {
						context.getSource().sendFeedback(() -> Text.literal(waiting + " loaded thread(s) not started for you yet").formatted(Formatting.GRAY), false);
					}
				})))
				.then(CommandManager.literal("start").then(threadArgument()
						.executes(context -> forSelf(context, player -> {
							Identifier id = IdentifierArgumentType.getIdentifier(context, "thread");
							boolean started = StoryThreads.start(player, id, true);
							context.getSource().sendFeedback(() -> Text.literal(started ? "Started " + id
									: "Didn't start " + id + " - not loaded, or already had (reset it first)"), false);
						}))))
				.then(CommandManager.literal("advance").then(threadArgument()
						.then(CommandManager.argument("stage", StringArgumentType.word())
								.suggests((context, builder) -> {
									TaleAst.Thread thread = TaleLibrary.get().threads().get(IdentifierArgumentType.getIdentifier(context, "thread"));
									return CommandSource.suggestMatching(thread == null ? List.of() : java.util.stream.Stream.concat(
											thread.stages().stream().map(TaleAst.Stage::name), java.util.stream.Stream.of("END")).toList(), builder);
								})
								.executes(context -> forSelf(context, player -> {
									Identifier id = IdentifierArgumentType.getIdentifier(context, "thread");
									String stage = StringArgumentType.getString(context, "stage");
									boolean moved = StoryThreads.advance(player, id, stage);
									context.getSource().sendFeedback(() -> Text.literal(moved ? id + " is now at " + stage
											: "No thread " + id + " with a stage " + stage), false);
								})))))
				.then(CommandManager.literal("reset")
						.executes(context -> forSelf(context, player -> {
							int count = StoryThreads.reset(player, Optional.empty());
							context.getSource().sendFeedback(() -> Text.literal("Forgot " + count + " thread(s)"), false);
						}))
						.then(threadArgument().executes(context -> forSelf(context, player -> {
							Identifier id = IdentifierArgumentType.getIdentifier(context, "thread");
							int count = StoryThreads.reset(player, Optional.of(id));
							context.getSource().sendFeedback(() -> Text.literal(count > 0 ? "Forgot " + id : "Didn't have " + id), false);
						}))))
				.then(fire);
	}

	/**
	 * {@code voice show} lists your voices; {@code set}/{@code add} change one (add goes through the
	 * normal growth, top-voice slowdown included); {@code dice success|failure|off} forces your rolls.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> voiceBranch() {
		return CommandManager.literal("voice")
				.then(CommandManager.literal("show").executes(context -> forSelf(context, player -> {
					for (String voice : VoiceLeanings.ALL) {
						context.getSource().sendFeedback(() -> Text.literal(voice + ": " + VoiceLeanings.points(player, voice) + " ("
								+ VoiceLeanings.tierName(VoiceLeanings.tier(player, voice)) + ")"), false);
					}
				})))
				.then(CommandManager.literal("set").then(voiceArgument()
						.then(CommandManager.argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
								.executes(context -> forSelf(context, player -> {
									String voice = StringArgumentType.getString(context, "voice");
									VoiceLeanings.set(player, voice, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "points"));
									context.getSource().sendFeedback(() -> Text.literal(voice + " is now " + VoiceLeanings.points(player, voice)
											+ " (" + VoiceLeanings.tierName(VoiceLeanings.tier(player, voice)) + ")"), false);
								})))))
				.then(CommandManager.literal("add").then(voiceArgument()
						.then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
								.executes(context -> forSelf(context, player -> {
									String voice = StringArgumentType.getString(context, "voice");
									VoiceLeanings.grow(player, voice, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount"));
									context.getSource().sendFeedback(() -> Text.literal(voice + " is now " + VoiceLeanings.points(player, voice)
											+ " (" + VoiceLeanings.tierName(VoiceLeanings.tier(player, voice)) + ")"), false);
								})))))
				.then(CommandManager.literal("dice")
						.then(CommandManager.literal("success").executes(context -> forSelf(context, player -> {
							CimVoices.force(player.getUuid(), true);
							context.getSource().sendFeedback(() -> Text.literal("Every roll succeeds until /cim debug voice dice off"), false);
						})))
						.then(CommandManager.literal("failure").executes(context -> forSelf(context, player -> {
							CimVoices.force(player.getUuid(), false);
							context.getSource().sendFeedback(() -> Text.literal("Every roll fails until /cim debug voice dice off"), false);
						})))
						.then(CommandManager.literal("off").executes(context -> forSelf(context, player -> {
							CimVoices.force(player.getUuid(), null);
							context.getSource().sendFeedback(() -> Text.literal("Rolls are fair again"), false);
						}))));
	}

	/**
	 * Speaking voices (README: Speaking voices) - how a pony or player sounds when they talk.
	 * {@code speech show <target>}; {@code speech set <target> <sound> [pitch]} takes any sound ID
	 * (what the character creator will offer players); {@code speech roll <target>} forgets the voice
	 * so a new one is rolled or inherited.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> speechBranch() {
		return CommandManager.literal("speech")
				.then(CommandManager.literal("show").then(CommandManager.argument("target", EntityArgumentType.entity()).executes(context -> {
					Entity target = EntityArgumentType.getEntity(context, "target");
					SpeakingVoice voice = SpeakingVoices.of(target);
					SpeakingVoice heard = SpeakingVoices.heard(target);
					context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " speaks with " + voice.sound()
							+ " at pitch " + String.format(java.util.Locale.ROOT, "%.2f", voice.pitch())
							+ (heard.pitch() != voice.pitch() ? String.format(java.util.Locale.ROOT, " (%.2f while young)", heard.pitch()) : "")), false);
					return 1;
				})))
				.then(CommandManager.literal("set").then(CommandManager.argument("target", EntityArgumentType.entity())
						.then(CommandManager.argument("sound", net.minecraft.command.argument.IdentifierArgumentType.identifier())
								.suggests(net.minecraft.command.suggestion.SuggestionProviders.AVAILABLE_SOUNDS)
								.executes(context -> setSpeech(context, SpeakingVoices.of(EntityArgumentType.getEntity(context, "target")).pitch()))
								.then(CommandManager.argument("pitch", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(
												SpeakingVoice.MIN_PITCH, SpeakingVoice.MAX_PITCH))
										.executes(context -> setSpeech(context,
												com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "pitch")))))))
				.then(CommandManager.literal("princess").then(CommandManager.argument("target", EntityArgumentType.entity())
						.then(CommandManager.argument("princess", StringArgumentType.word())
								.suggests((context, builder) -> net.minecraft.command.CommandSource.suggestMatching(PrincessVoices.ALL.keySet(), builder))
								.executes(context -> {
									Entity target = EntityArgumentType.getEntity(context, "target");
									String name = StringArgumentType.getString(context, "princess").toLowerCase(java.util.Locale.ROOT);
									SpeakingVoice voice = PrincessVoices.ALL.get(name);
									if (voice == null) {
										context.getSource().sendError(Text.literal("No princess voice \"" + name + "\" - try: "
												+ String.join(", ", new java.util.TreeSet<>(PrincessVoices.ALL.keySet()))));
										return 0;
									}
									SpeakingVoices.set(target, voice);
									context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " now speaks with "
											+ name + "'s voice (provisional)"), false);
									return 1;
								}))))
				.then(CommandManager.literal("roll").then(CommandManager.argument("target", EntityArgumentType.entity()).executes(context -> {
					Entity target = EntityArgumentType.getEntity(context, "target");
					SpeakingVoices.forget(target);
					SpeakingVoice voice = SpeakingVoices.of(target);
					context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " now speaks with " + voice.sound()
							+ String.format(java.util.Locale.ROOT, " at pitch %.2f", voice.pitch())), false);
					return 1;
				})));
	}

	/** {@code hum <pony> [tune]} - the pony hums now (README: Humming), a tune that fits or the one named. */
	private static LiteralArgumentBuilder<ServerCommandSource> humBranch() {
		return CommandManager.literal("hum").then(CommandManager.argument("pony", EntityArgumentType.entity())
				.executes(context -> hum(context, java.util.Optional.empty()))
				.then(CommandManager.argument("tune", net.minecraft.command.argument.IdentifierArgumentType.identifier())
						.suggests((context, builder) -> net.minecraft.command.CommandSource.suggestIdentifiers(
								HumLibrary.all().stream().map(HumLibrary.Hum::id), builder))
						.executes(context -> hum(context, java.util.Optional.of(
								net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "tune"))))));
	}

	private static int hum(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
			java.util.Optional<net.minecraft.util.Identifier> tune) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Entity target = EntityArgumentType.getEntity(context, "pony");
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		if (!(target instanceof net.minecraft.entity.passive.VillagerEntity pony)) {
			context.getSource().sendError(Text.literal("Only ponies hum."));
			return 0;
		}
		java.util.Optional<HumLibrary.Hum> hummed = HumDirector.humNow(player, pony, tune);
		if (hummed.isEmpty()) {
			context.getSource().sendError(Text.literal(tune.isPresent() ? "No hum called " + tune.get() : "No hum fits right now"));
			return 0;
		}
		context.getSource().sendFeedback(() -> Text.literal(pony.getName().getString() + " hums " + hummed.get().id()), false);
		return 1;
	}

	private static int setSpeech(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, float pitch)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Entity target = EntityArgumentType.getEntity(context, "target");
		net.minecraft.util.Identifier sound = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(context, "sound");
		SpeakingVoices.set(target, new SpeakingVoice(sound, pitch));
		context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " now speaks with " + sound
				+ String.format(java.util.Locale.ROOT, " at pitch %.2f", SpeakingVoices.of(target).pitch())), false);
		return 1;
	}

	/**
	 * {@code mood show [entity]} - what they feel now, from their memories; {@code moment play <id>} -
	 * plays a scene-setting moment on your own screen; {@code moment stop}.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> moodBranch() {
		return CommandManager.literal("mood")
				.then(CommandManager.literal("show")
						.executes(context -> forSelf(context, player ->
								context.getSource().sendFeedback(() -> Text.literal("You feel: " + Mood.describe(player)), false)))
						.then(CommandManager.argument("target", EntityArgumentType.entity()).executes(context -> {
							Entity target = EntityArgumentType.getEntity(context, "target");
							context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " feels: " + Mood.describe(target)), false);
							return 1;
						})));
	}

	/** {@code news list|clear|letter} - news you haven't heard yet; {@code letter} delivers the first one by letter now. */
	private static LiteralArgumentBuilder<ServerCommandSource> newsBranch() {
		return CommandManager.literal("news")
				.then(CommandManager.literal("list").executes(context -> forSelf(context, player -> {
					List<PlayerNews.News> pending = PlayerNews.pending(player);
					context.getSource().sendFeedback(() -> Text.literal(pending.size() + " unheard news:"), false);
					long now = player.getWorld().getTime();
					for (PlayerNews.News news : pending) {
						String who = news.anyone() ? "anyone can tell you" : "told by: " + messengerNames(player, news);
						context.getSource().sendFeedback(() -> Text.literal("  " + news.type() + " - " + news.subjectName() + ", " + who + ", "
								+ Math.max(0, (now - news.since()) / Mood.DAY) + " day(s) ago"), false);
					}
				})))
				// Makes the ponies who can break your news glow for 30 seconds, to find them.
				.then(CommandManager.literal("who").executes(context -> forSelf(context, player -> {
					int found = 0;
					for (PlayerNews.News news : PlayerNews.pending(player)) {
						for (java.util.UUID uuid : news.messengers()) {
							if (player.getServerWorld().getEntity(uuid) instanceof net.minecraft.entity.LivingEntity pony) {
								pony.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
										net.minecraft.entity.effect.StatusEffects.GLOWING, 600, 0, false, false));
								found++;
							}
						}
					}
					int glowing = found;
					context.getSource().sendFeedback(() -> Text.literal(glowing > 0 ? glowing + " messenger(s) glowing for 30 seconds"
							: "No messengers loaded nearby (or anyone can tell you)"), false);
				})))
				.then(CommandManager.literal("clear").executes(context -> forSelf(context, player -> {
					int count = PlayerNews.clear(player);
					context.getSource().sendFeedback(() -> Text.literal("Forgot " + count + " unheard news"), false);
				})))
				.then(CommandManager.literal("letter").executes(context -> forSelf(context, player -> {
					List<PlayerNews.News> pending = PlayerNews.pending(player);
					if (pending.isEmpty()) {
						context.getSource().sendError(Text.literal("No unheard news to send"));
					} else {
						PlayerNews.sendLetter(player, pending.get(0));
					}
				})));
	}

	/**
	 * {@code family gather <pony>} - brings a pony and every loaded relative (parents, partner, foals,
	 * siblings) to where you stand, for testing family scenes without hunting the village for them.
	 */
	private static LiteralArgumentBuilder<ServerCommandSource> familyBranch() {
		return CommandManager.literal("family")
				.then(CommandManager.literal("gather").then(CommandManager.argument("pony", EntityArgumentType.entity())
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							Entity pony = EntityArgumentType.getEntity(context, "pony");
							if (!PonyFamilyAttachment.hasFamily(pony)) {
								throw new SimpleCommandExceptionType(Text.literal(pony.getName().getString() + " has no family record")).create();
							}
							java.util.Set<java.util.UUID> family = new java.util.LinkedHashSet<>();
							family.add(pony.getUuid());
							PonyFamilyAttachment.PonyFamily record = PonyFamilyAttachment.getFamily(pony);
							family.addAll(record.parents());
							family.addAll(record.partner());
							family.addAll(PonyFamilyAttachment.children(pony));
							for (java.util.UUID parentId : record.parents()) {
								Entity parent = player.getServerWorld().getEntity(parentId);
								if (parent != null) {
									family.addAll(PonyFamilyAttachment.children(parent)); // siblings
								}
							}
							List<String> gathered = new java.util.ArrayList<>();
							int i = 0;
							for (java.util.UUID uuid : family) {
								Entity relative = player.getServerWorld().getEntity(uuid);
								if (relative == null || !relative.isAlive()) {
									continue;
								}
								double angle = i++ * (Math.PI * 2 / Math.max(1, family.size()));
								relative.requestTeleport(player.getX() + Math.cos(angle) * 1.5, player.getY(), player.getZ() + Math.sin(angle) * 1.5);
								gathered.add(relative.getName().getString());
							}
							context.getSource().sendFeedback(() -> Text.literal("Gathered " + gathered.size() + " of " + family.size() + " (others not loaded): "
									+ String.join(", ", gathered)), false);
							return gathered.size();
						})));
	}

	private static String messengerNames(ServerPlayerEntity player, PlayerNews.News news) {
		List<String> names = new java.util.ArrayList<>();
		for (java.util.UUID uuid : news.messengers()) {
			Entity pony = player.getServerWorld().getEntity(uuid);
			names.add(pony != null ? pony.getName().getString() : "(not loaded)");
		}
		return String.join(", ", names);
	}

	private static LiteralArgumentBuilder<ServerCommandSource> momentBranch() {
		return CommandManager.literal("moment")
				.then(CommandManager.literal("play").then(CommandManager.argument("moment", IdentifierArgumentType.identifier())
						.suggests((context, builder) -> CommandSource.suggestIdentifiers(List.of(VillagerGriefHandler.GRIEF_MOMENT), builder))
						.executes(context -> forSelf(context, player -> {
							Identifier moment = IdentifierArgumentType.getIdentifier(context, "moment");
							ServerPlayNetworking.send(player, new MomentS2CPayload(moment));
							context.getSource().sendFeedback(() -> Text.literal("Playing " + moment + " on your screen"), false);
						}))))
				.then(CommandManager.literal("stop").executes(context -> forSelf(context, player -> {
					ServerPlayNetworking.send(player, new MomentS2CPayload(Identifier.of("cim", "stop")));
					context.getSource().sendFeedback(() -> Text.literal("Stopped the moment"), false);
				})));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, String> voiceArgument() {
		return CommandManager.argument("voice", StringArgumentType.word())
				.suggests((context, builder) -> CommandSource.suggestMatching(VoiceLeanings.ALL, builder));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, Identifier> threadArgument() {
		return CommandManager.argument("thread", IdentifierArgumentType.identifier())
				.suggests((context, builder) -> CommandSource.suggestIdentifiers(TaleLibrary.get().threads().keySet(), builder));
	}

	/**
	 * Writes the .tale vocabulary (every word and its argument rules) to {@code cim-vocabulary.json}
	 * in the game folder, for the editor in {@code tools/tale-editor}. Also runs on its own when a world
	 * opens with debug tools on. Returns where it wrote, or null on failure.
	 */
	public static java.nio.file.Path exportVocabulary() {
		java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("cim-vocabulary.json");
		try {
			craftingismagic.cim.narrative.TaleVocabularyExport.write(file);
			return file;
		} catch (java.io.IOException e) {
			craftingismagic.cim.Cim.LOGGER.error("Couldn't write the .tale vocabulary to {}", file, e);
			return null;
		}
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
