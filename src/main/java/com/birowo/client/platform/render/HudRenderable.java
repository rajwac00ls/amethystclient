package com.birowo.client.platform.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Implemented by modules that draw on the HUD layer.
 */
public interface HudRenderable {
	/**
	 * Draws on the HUD.
	 *
	 * @param context     the draw context of the current frame
	 * @param tickCounter the tick counter of the current frame
	 */
	void onHudRender(DrawContext context, RenderTickCounter tickCounter);
}
