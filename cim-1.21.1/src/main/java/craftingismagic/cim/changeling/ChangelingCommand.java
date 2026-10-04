package craftingismagic.cim.changeling;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import craftingismagic.cim.entity.CimEntities;

/**
 * Test-only, op-only spawn command for {@link ChangelingInfiltratorEntity} - mirrors the source
 * mod's own op-only {@code /skinwalker <target>} shape. Explicitly command-only for this pass, no
 * natural world-spawn tuning yet (see the Changeling Voice Mimicry plan).
 */
public final class ChangelingCommand {
	private static final SimpleCommandExceptionType TOO_MANY_TARGETS = new SimpleCommandExceptionType(new LiteralMessage("Can only apply to 1 target"));

	private ChangelingCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
			CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("changeling")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("target", EntityArgumentType.players())
						.executes(context -> execute(context.getSource(), EntityArgumentType.getPlayers(context, "target")))));
	}

	private static int execute(ServerCommandSource source, Collection<ServerPlayerEntity> targets) throws CommandSyntaxException {
		if (targets.size() > 1) {
			throw TOO_MANY_TARGETS.create();
		}

		for (ServerPlayerEntity target : targets) {
			ChangelingInfiltratorEntity changeling = CimEntities.CHANGELING_INFILTRATOR.create(source.getWorld());
			if (changeling == null) {
				continue;
			}

			changeling.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
			changeling.ensureAppearance();
			changeling.setDisguiseTarget(target.getUuid());
			source.getWorld().spawnEntity(changeling);
		}
		return 1;
	}
}
