package com.birowo.client.core.command;

/**
 * A client-side command.
 */
public interface Command {
	/**
	 * @return the name the command is invoked by, without the client prefix
	 */
	String name();

	/**
	 * @return a one-line description shown in the command list
	 */
	String description();

	/**
	 * @return the argument syntax, or an empty string if the command takes no arguments
	 */
	String usage();

	/**
	 * Runs the command.
	 *
	 * @param args the arguments that followed the command name
	 * @return {@code true} if the command was understood
	 */
	boolean execute(String[] args);
}
