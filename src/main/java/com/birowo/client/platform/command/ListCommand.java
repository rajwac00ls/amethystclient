package com.birowo.client.platform.command;

import com.birowo.client.core.command.Command;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Lists every registered module and whether it is enabled.
 */
public final class ListCommand implements Command {
	private final ModuleManager moduleManager;

	/**
	 * @param moduleManager the registry the modules are read from
	 */
	public ListCommand(ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	@Override
	public String name() {
		return "list";
	}

	@Override
	public String description() {
		return "Lists every module and its state";
	}

	@Override
	public String usage() {
		return "";
	}

	@Override
	public boolean execute(String[] args) {
		StringBuilder message = new StringBuilder("Modules:");

		for (Module module : moduleManager.modules()) {
			message.append(' ')
					.append(module.id())
					.append('=')
					.append(module.enabled() ? "on" : "off");
		}

		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player != null) {
			client.player.sendMessage(Text.literal("[Birowo] " + message), false);
		}

		return true;
	}
}
