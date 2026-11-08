package com.birowo.client.platform.command;

import com.birowo.client.core.command.Command;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Enables or disables a module by its identifier.
 */
public final class ToggleCommand implements Command {
	private final ModuleManager moduleManager;

	/**
	 * @param moduleManager the registry the module is looked up in
	 */
	public ToggleCommand(ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	@Override
	public String name() {
		return "toggle";
	}

	@Override
	public String description() {
		return "Enables or disables a module";
	}

	@Override
	public String usage() {
		return "<module>";
	}

	@Override
	public boolean execute(String[] args) {
		if (args.length != 1) {
			reply("Usage: .birowo toggle <module>");
			return false;
		}

		String id = args[0].toLowerCase(java.util.Locale.ROOT);
		Module module = moduleManager.byId(id).orElse(null);

		if (module == null) {
			reply("Unknown module: " + args[0]);
			return false;
		}

		module.toggle();
		reply(module.displayName() + (module.enabled() ? " enabled" : " disabled"));
		return true;
	}

	private static void reply(String message) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player != null) {
			client.player.sendMessage(Text.literal("[Birowo] " + message), false);
		}
	}
}
