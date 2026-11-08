package com.birowo.client.platform.command;

import com.birowo.client.core.command.Command;
import com.birowo.client.core.command.CommandManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Lists the available commands.
 */
public final class HelpCommand implements Command {
	private final CommandManager commandManager;

	/**
	 * @param commandManager the registry the commands are read from
	 */
	public HelpCommand(CommandManager commandManager) {
		this.commandManager = commandManager;
	}

	@Override
	public String name() {
		return "help";
	}

	@Override
	public String description() {
		return "Lists the available commands";
	}

	@Override
	public String usage() {
		return "";
	}

	@Override
	public boolean execute(String[] args) {
		StringBuilder message = new StringBuilder("Commands:");

		for (Command command : commandManager.commands()) {
			message.append(" .birowo ")
					.append(command.name());

			if (!command.usage().isEmpty()) {
				message.append(' ').append(command.usage());
			}

			message.append(" - ").append(command.description());
		}

		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player != null) {
			client.player.sendMessage(Text.literal("[Birowo] " + message), false);
		}

		return true;
	}
}
