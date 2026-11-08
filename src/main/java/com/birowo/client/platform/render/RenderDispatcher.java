package com.birowo.client.platform.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleManager;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Routes the game's render passes to the modules that implement them.
 *
 * <p>A module that throws while rendering is logged and skipped: a single broken overlay must not
 * take the whole frame — or the game — down with it.
 */
public final class RenderDispatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger("BirowoClient/RenderDispatcher");

	private final ModuleManager moduleManager;

	/**
	 * @param moduleManager the manager holding the modules to dispatch to
	 */
	public RenderDispatcher(ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	/**
	 * Draws the HUD layer of every module that implements {@link HudRenderable}.
	 *
	 * <p>Every enabled module is given its frame hook first, so that a module can advance its
	 * animations before anything is drawn.
	 *
	 * @param context     the draw context of the current frame
	 * @param tickCounter the tick counter of the current frame
	 */
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		for (Module module : moduleManager.modules()) {
			if (!module.enabled()) {
				continue;
			}

			try {
				module.onRender();
			} catch (Throwable t) {
				LOGGER.error("Module {} failed to render", module.id(), t);
			}

			if (!(module instanceof HudRenderable renderable)) {
				continue;
			}

			try {
				renderable.onHudRender(context, tickCounter);
			} catch (Throwable t) {
				LOGGER.error("Module {} failed to render on the HUD", module.id(), t);
			}
		}
	}

	/**
	 * Draws the world layer of every module that implements {@link WorldRenderable}.
	 *
	 * @param context the render context of the current frame
	 */
	public void onWorldRender(WorldRenderContext context) {
		for (Module module : moduleManager.modules()) {
			if (!module.enabled() || !(module instanceof WorldRenderable renderable)) {
				continue;
			}

			try {
				renderable.onWorldRender(context);
			} catch (Throwable t) {
				LOGGER.error("Module {} failed to render in the world", module.id(), t);
			}
		}
	}
}
