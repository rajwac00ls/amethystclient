package com.birowo.client.features.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.birowo.client.core.gui.ClickGuiHost;
import com.birowo.client.core.gui.GuiLayoutStore;
import com.birowo.client.core.gui.GuiPanelIds;
import com.birowo.client.core.gui.GuiScreenLauncher;
import com.birowo.client.core.gui.ModuleVisual;
import com.birowo.client.core.gui.PanelState;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.core.module.ModuleManager;

/**
 * The module that owns the interface of the client.
 *
 * <p>It is enabled exactly while the interface is on screen, so the row that shows it in the
 * client category reflects what the player sees. Its state is not written to the configuration
 * file: an interface that opens by itself when the game starts is never what the player wants.
 */
public final class ClickGuiModule extends Module implements ClickGuiHost {
	private final ModuleManager moduleManager;
	private final GuiScreenLauncher launcher;
	private final GuiLayoutStore layoutStore;
	private final List<PanelState> panels = new ArrayList<>();
	private final Map<Module, ModuleVisual> visuals = new HashMap<>();

	/**
	 * @param moduleManager the registry the interface lists
	 * @param launcher      the platform service that opens the interface
	 * @param layoutStore   the store the positions of the panels are kept in
	 * @param keyBinds      the factory used to register this module's key binding
	 */
	public ClickGuiModule(ModuleManager moduleManager, GuiScreenLauncher launcher, GuiLayoutStore layoutStore,
			KeyBindFactory keyBinds) {
		super("clickgui", "ClickGUI", "Opens the interface of the client.", ModuleCategory.CLIENT);

		this.moduleManager = moduleManager;
		this.launcher = launcher;
		this.layoutStore = layoutStore;

		for (ModuleCategory category : ModuleCategory.values()) {
			panels.add(new PanelState(GuiPanelIds.forCategory(category), 0, 0));
		}

		panels.add(new PanelState(GuiPanelIds.SEARCH, 0, 0));
		layoutStore.load(panels);

		keyBinds.bind("clickgui", "key.birowo.clickgui", GLFW.GLFW_KEY_R, this::toggle);
	}

	@Override
	public boolean persistable() {
		return false;
	}

	@Override
	protected void onEnable() {
		launcher.open(this);
	}

	@Override
	protected void onDisable() {
		launcher.close();

		// A panel that was still moving when the interface closed rests where it was let go.
		for (PanelState panel : panels) {
			panel.settle();
		}

		layoutStore.save(panels);
	}

	/**
	 * Writes the layout of the interface to disk.
	 */
	public void saveLayout() {
		layoutStore.save(panels);
	}

	@Override
	public Collection<Module> modules() {
		return moduleManager.modules();
	}

	@Override
	public List<PanelState> panels() {
		return panels;
	}

	@Override
	public ModuleVisual visualFor(Module module) {
		return visuals.computeIfAbsent(module, key -> new ModuleVisual());
	}

	@Override
	public void toggleModule(Module module) {
		module.toggle();
	}

	@Override
	public void onGuiClosed() {
		setEnabled(false);
	}
}
