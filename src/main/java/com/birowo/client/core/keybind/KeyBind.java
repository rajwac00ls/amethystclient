package com.birowo.client.core.keybind;

/**
 * A key binding a module can be bound to.
 *
 * <p>The interface is deliberately free of Minecraft types; the platform layer supplies the
 * implementation.
 *
 * <p>Only the identity of the binding is exposed. Press events are dispatched by the platform's
 * key bind manager, which owns the underlying binding — a module that also polled for presses
 * would consume them before the manager saw them.
 */
public interface KeyBind {
	/**
	 * @return the identifier of this binding, unique within the client
	 */
	String id();
}
