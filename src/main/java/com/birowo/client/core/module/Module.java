package com.birowo.client.core.module;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ModuleConfig;

/**
 * Base class for every feature module.
 *
 * <p>A module is <em>toggleable</em>, <em>configurable</em> and <em>independently disposable</em>:
 * disabling a module must restore vanilla behaviour exactly. Subclasses therefore install their
 * hooks in {@link #onEnable()} and remove them in {@link #onDisable()}, and they must be
 * symmetrical.
 *
 * <p>This class deliberately has no dependency on Minecraft. A module reaches the game through
 * the platform layer, never through a direct reference to a Minecraft class.
 */
public abstract class Module {
	private final String id;
	private final String displayName;
	private final String description;
	private final ModuleCategory category;
	private final ModuleConfig config = new ModuleConfig();
	private final List<Consumer<Boolean>> stateListeners = new CopyOnWriteArrayList<>();

	private boolean enabled;

	/**
	 * @param id          stable machine-readable identifier, unique across all modules; used as
	 *                    the config key and by the command line
	 * @param displayName human-readable name shown in the UI
	 * @param description one-line summary of what the module does, shown as a tooltip
	 * @param category    category this module is listed under
	 */
	protected Module(String id, String displayName, String description, ModuleCategory category) {
		this.id = id;
		this.displayName = displayName;
		this.description = description;
		this.category = category;
	}

	/**
	 * @return the stable identifier of this module
	 */
	public final String id() {
		return id;
	}

	/**
	 * @return the human-readable name of this module
	 */
	public final String displayName() {
		return displayName;
	}

	/**
	 * @return a one-line summary of what this module does
	 */
	public final String description() {
		return description;
	}

	/**
	 * @return the category this module is listed under
	 */
	public final ModuleCategory category() {
		return category;
	}

	/**
	 * @return the mutable configuration of this module
	 */
	public final ModuleConfig config() {
		return config;
	}

	/**
	 * @return whether this module is currently enabled
	 */
	public final boolean enabled() {
		return enabled;
	}

	/**
	 * Enables or disables this module.
	 *
	 * <p>Calling this with the state the module is already in is a no-op, so the enable and disable
	 * hooks always run in matching pairs.
	 *
	 * @param enabled the desired state
	 */
	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;

		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}

		for (Consumer<Boolean> listener : stateListeners) {
			listener.accept(enabled);
		}
	}

	/**
	 * Registers a listener that is notified whenever this module is enabled or disabled.
	 *
	 * <p>This is how state changes stay observable no matter who triggers them — the module
	 * manager, a key binding, a command, or the module itself.
	 *
	 * @param listener the listener to notify
	 */
	public void addStateListener(Consumer<Boolean> listener) {
		stateListeners.add(listener);
	}

	/**
	 * Flips the enabled state of this module.
	 */
	public final void toggle() {
		setEnabled(!enabled);
	}

	/**
	 * Registers a setting on this module's configuration.
	 *
	 * @param value the setting to register
	 * @param <T>   the type of the setting's value
	 * @return the same instance, for chaining
	 */
	protected final <T> ConfigValue<T> setting(ConfigValue<T> value) {
		return config.register(value);
	}

	/**
	 * @return whether the state of this module is written to the configuration file
	 */
	public boolean persistable() {
		return true;
	}

	/**
	 * Called after the module has been enabled. Install hooks here.
	 */
	protected void onEnable() {
	}

	/**
	 * Called after the module has been disabled. Remove hooks here.
	 */
	protected void onDisable() {
	}

	/**
	 * Called once per client tick while the module is enabled.
	 */
	public void onTick() {
	}

	/**
	 * Called once per frame while the module is enabled.
	 *
	 * <p>The hook is for the work that does not need a rendering context, such as advancing an
	 * animation. Work that draws in the world or on the screen is done through the platform
	 * interfaces instead, because the core must not know about Minecraft.
	 */
	public void onRender() {
	}

	/**
	 * @return the settings of this module, in declaration order
	 */
	public final List<ConfigValue<?>> settings() {
		return config.values();
	}
}
