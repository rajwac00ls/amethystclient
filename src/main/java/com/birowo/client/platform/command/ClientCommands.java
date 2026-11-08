package com.birowo.client.platform.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.birowo.client.core.command.CommandManager;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Exposes the client's commands through the Brigadier client command tree.
 */
public final class ClientCommands {
	private static final String ROOT = "birowo";

	private ClientCommands() {
	}

	/**
	 * Registers the {@code .birowo} command tree.
	 *
	 * <p>The tree is built inside {@link ClientCommandRegistrationCallback#EVENT} rather than
	 * against {@link ClientCommandManager#getActiveDispatcher()} directly: the active dispatcher
	 * only exists once the client has joined a server, and it is rebuilt on every connection, so
	 * registering eagerly at startup yields a {@code null} dispatcher and aborts the mod.
	 *
	 * @param commandManager the manager the commands are dispatched to
	 */
	public static void register(CommandManager commandManager) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(buildTree(commandManager)));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildTree(CommandManager commandManager) {
		return ClientCommandManager.literal(ROOT)
				.executes(context -> {
					commandManager.execute("help");
					return 1;
				})
				.then(ClientCommandManager.literal("toggle")
						.then(ClientCommandManager.argument("module", StringArgumentType.string())
								.executes(context -> {
									commandManager.execute("toggle " + StringArgumentType.getString(context, "module"));
									return 1;
								})))
				.then(ClientCommandManager.literal("list")
						.executes(context -> {
							commandManager.execute("list");
							return 1;
						}))
				.then(ClientCommandManager.literal("pvp")
						.then(ClientCommandManager.argument("version", StringArgumentType.string())
								.executes(context -> {
									commandManager.execute("pvp " + StringArgumentType.getString(context, "version"));
									return 1;
								})));
	}
}
