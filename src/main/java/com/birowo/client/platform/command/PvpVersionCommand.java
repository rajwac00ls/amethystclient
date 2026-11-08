package com.birowo.client.platform.command;

import java.util.Locale;

import com.birowo.client.core.command.Command;
import com.birowo.client.platform.combat.PvpVersion;
import com.birowo.client.platform.combat.PvpVersionService;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Sets the PvP rules the client assumes the server runs with.
 *
 * <p>The rules are read from the version the server announces, but a proxy may hide the version
 * of the server behind it, so the version may also be set by hand.
 */
public final class PvpVersionCommand implements Command {
	private final PvpVersionService pvpVersion;

	/**
	 * @param pvpVersion the service the version is set on
	 */
	public PvpVersionCommand(PvpVersionService pvpVersion) {
		this.pvpVersion = pvpVersion;
	}

	@Override
	public String name() {
		return "pvp";
	}

	@Override
	public String description() {
		return "Sets the PvP version of the server";
	}

	@Override
	public String usage() {
		return "<1.8.9|1.21|auto>";
	}

	@Override
	public boolean execute(String[] args) {
		if (args.length != 1) {
			reply("Usage: .birowo pvp <1.8.9|1.21|auto>");
			return false;
		}

		String argument = args[0].toLowerCase(Locale.ROOT);

		if (argument.equals("auto")) {
			pvpVersion.configure(null);
			reply("PvP version follows the server: " + pvpVersion.current().label());
			return true;
		}

		PvpVersion chosen = parse(argument);

		if (chosen == null) {
			reply("Unknown PvP version: " + args[0]);
			return false;
		}

		pvpVersion.configure(chosen);
		reply("PvP version set to " + chosen.label());
		return true;
	}

	/**
	 * @param argument the version the player wrote, in lower case
	 * @return the version it names, or {@code null} when it names none
	 */
	private static PvpVersion parse(String argument) {
		if (argument.equals("1.8") || argument.equals("1.8.9") || argument.equals("legacy")) {
			return PvpVersion.LEGACY_1_8_9;
		}

		if (argument.equals("1.21") || argument.equals("modern")) {
			return PvpVersion.MODERN_1_21;
		}

		return null;
	}

	private static void reply(String message) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player != null) {
			client.player.sendMessage(Text.literal("[Birowo] " + message), false);
		}
	}
}
