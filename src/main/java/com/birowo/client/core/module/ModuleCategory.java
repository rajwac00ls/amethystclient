package com.birowo.client.core.module;

/**
 * Categories group modules in the module list and in the settings interface.
 *
 * <p>The order of the constants is the order they are presented to the user.
 */
public enum ModuleCategory {
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	CLIENT("Client"),
	MISC("Misc");

	private final String displayName;

	ModuleCategory(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * @return the human-readable label shown in the module list
	 */
	public String displayName() {
		return displayName;
	}
}
