package com.birowo.client.platform.keybind;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

import com.birowo.client.core.keybind.KeyBind;
import com.birowo.client.core.keybind.KeyBindFactory;

/**
 * Creates Minecraft key bindings and dispatches their press events.
 *
 * <p>The manager is the single owner of every binding it creates. Presses are consumed here, once
 * per tick, so a module can never steal a press from the action it registered.
 */
public final class KeyBindManager implements KeyBindFactory {
	private final KeyBinding.Category category;
	private final List<PlatformKeyBind> bindings = new ArrayList<>();

	/**
	 * @param categoryPath the path of the key bind category, relative to the {@code birowo}
	 *                     namespace
	 */
	public KeyBindManager(String categoryPath) {
		this.category = KeyBinding.Category.create(Identifier.of("birowo", categoryPath));
	}

	@Override
	public KeyBind bind(String id, String translationKey, int defaultKeyCode, Runnable onPressed) {
		KeyBinding binding = new KeyBinding(translationKey, defaultKeyCode, category);
		KeyBindingHelper.registerKeyBinding(binding);

		PlatformKeyBind keyBind = new PlatformKeyBind(id, binding, onPressed);
		bindings.add(keyBind);
		return keyBind;
	}

	/**
	 * Dispatches the press of every binding that was pressed since the last tick.
	 */
	public void onTick() {
		for (PlatformKeyBind binding : bindings) {
			if (binding.wasPressed()) {
				binding.press();
			}
		}
	}
}
