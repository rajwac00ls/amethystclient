package com.birowo.client.core.gui;

import java.util.Collection;
import java.util.List;

import com.birowo.client.core.module.Module;

/**
 * What the interface needs from the client in order to draw itself.
 *
 * <p>The interface is implemented by the platform layer and consumed by the screen, which lives in
 * the platform layer as well. Keeping the contract in the core means the interface never has to
 * know which module it is displaying.
 */
public interface ClickGuiHost {
	/**
	 * @return every module that may be displayed, in registration order
	 */
	Collection<Module> modules();

	/**
	 * @return the state of every panel, in display order
	 */
	List<PanelState> panels();

	/**
	 * @param module the module to look up
	 * @return the animated appearance of that module
	 */
	ModuleVisual visualFor(Module module);

	/**
	 * Enables or disables a module.
	 *
	 * @param module the module to toggle
	 */
	void toggleModule(Module module);

	/**
	 * Called once the interface has finished closing.
	 */
	void onGuiClosed();
}
