package com.birowo.client.core.keybind;

/**
 * Creates key bindings on behalf of modules.
 *
 * <p>This is the seam between the Minecraft-free core and the platform: a module asks for a
 * binding, the platform creates one backed by a Minecraft key binding, and the module never
 * learns the difference.
 */
public interface KeyBindFactory {
	/**
	 * Creates and registers a key binding.
	 *
	 * @param id             the identifier of the binding, unique within the client
	 * @param translationKey the translation key of the binding's display name
	 * @param defaultKeyCode the GLFW key code the binding starts out on
	 * @param onPressed      the action to run when the binding is pressed
	 * @return the created binding
	 */
	KeyBind bind(String id, String translationKey, int defaultKeyCode, Runnable onPressed);
}
