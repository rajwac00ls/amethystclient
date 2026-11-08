package com.birowo.client.platform.keybind;

import net.minecraft.client.option.KeyBinding;

import com.birowo.client.core.keybind.KeyBind;

/**
 * A {@link KeyBind} backed by a Minecraft key binding.
 */
public final class PlatformKeyBind implements KeyBind {
	private final String id;
	private final KeyBinding binding;
	private final Runnable action;

	PlatformKeyBind(String id, KeyBinding binding, Runnable action) {
		this.id = id;
		this.binding = binding;
		this.action = action;
	}

	@Override
	public String id() {
		return id;
	}

	/**
	 * @return whether the key was pressed since the last call, consuming the press
	 */
	boolean wasPressed() {
		return binding.wasPressed();
	}

	/**
	 * Runs the action bound to this key.
	 */
	void press() {
		action.run();
	}
}
