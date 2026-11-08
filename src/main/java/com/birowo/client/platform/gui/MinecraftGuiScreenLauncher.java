package com.birowo.client.platform.gui;

import net.minecraft.client.MinecraftClient;

import com.birowo.client.core.gui.ClickGuiHost;
import com.birowo.client.core.gui.GuiScreenLauncher;

/**
 * Opens the interface on the screen of the game.
 *
 * <p>The launcher owns the screen that is currently open, so that the interface can be closed
 * again without the caller having to know how it was opened.
 */
public final class MinecraftGuiScreenLauncher implements GuiScreenLauncher {
	private ClickGuiScreen screen;

	@Override
	public void open(ClickGuiHost host) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClickGuiScreen opened = new ClickGuiScreen(client, host);

		screen = opened;
		client.setScreen(opened);
	}

	@Override
	public void close() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (screen != null && client.currentScreen == screen) {
			screen.requestClose();
		}
	}
}
