package com.birowo.client.core.gui;

import com.birowo.client.core.module.ModuleCategory;

/**
 * The identifiers of the panels of the interface.
 *
 * <p>The interface and the client agree on these names, so that a panel keeps its position across
 * sessions without either side having to know how the other is built.
 */
public final class GuiPanelIds {
	/**
	 * The identifier of the search panel.
	 */
	public static final String SEARCH = "search";

	private GuiPanelIds() {
	}

	/**
	 * @param category the category to name a panel after
	 * @return the identifier of the panel of that category
	 */
	public static String forCategory(ModuleCategory category) {
		return category.name().toLowerCase(java.util.Locale.ROOT);
	}
}
