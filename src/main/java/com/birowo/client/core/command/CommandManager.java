package com.birowo.client.core.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry and dispatcher for client-side commands.
 */
public final class CommandManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("BirowoClient/CommandManager");

	private final Map<String, Command> commands = new LinkedHashMap<>();

	/**
	 * Registers a command.
	 *
	 * @param command the command to register
	 * @throws IllegalArgumentException if a command with the same name is already registered
	 */
	public void register(Command command) {
		String name = command.name().toLowerCase(Locale.ROOT);

		if (commands.putIfAbsent(name, command) != null) {
			throw new IllegalArgumentException("Duplicate command name: " + name);
		}
	}

	/**
	 * @return every registered command, in registration order
	 */
	public Collection<Command> commands() {
		return Collections.unmodifiableCollection(commands.values());
	}

	/**
	 * @param name the command name, case-insensitive
	 * @return the command with that name, if registered
	 */
	public Optional<Command> byName(String name) {
		return Optional.ofNullable(commands.get(name.toLowerCase(Locale.ROOT)));
	}

	/**
	 * Parses and runs a command line.
	 *
	 * @param line the full command line, without the client prefix
	 * @return {@code true} if a command was found and run
	 */
	public boolean execute(String line) {
		String trimmed = line.trim();

		if (trimmed.isEmpty()) {
			return false;
		}

		String[] parts = trimmed.split("\\s+");
		String name = parts[0];
		String[] args = new String[parts.length - 1];
		System.arraycopy(parts, 1, args, 0, args.length);

		Optional<Command> command = byName(name);

		if (command.isEmpty()) {
			return false;
		}

		try {
			return command.get().execute(args);
		} catch (RuntimeException e) {
			LOGGER.error("Command {} failed", name, e);
			return false;
		}
	}
}
