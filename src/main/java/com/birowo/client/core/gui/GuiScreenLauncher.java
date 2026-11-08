package com.birowo.client.core.gui;

/**
 * Opens and closes the interface.
 *
 * <p>The core declares the capability; the platform implements it, because opening a screen is a
 * game concern.
 */
public interface GuiScreenLauncher {
	/**
	 * Opens the interface.
	 *
	 * @param host the source of the modules and of the panel layout
	 */
	void open(ClickGuiHost host);

	/**
	 * Closes the interface, if it is open.
	 */
	void close();
}
