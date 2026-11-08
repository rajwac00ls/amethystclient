package com.birowo.client.core.gui;

/**
 * The visual language of the interface: palette and metrics.
 *
 * <p>Every dimension is expressed in scaled GUI pixels, the same space the game's own screens use,
 * so the interface scales with the player's GUI scale setting.
 */
public final class GuiTheme {
	private GuiTheme() {
	}

	// Palette ---------------------------------------------------------------------------------

	/**
	 * The colour the screen is dimmed with, behind the panels.
	 */
	public static final int BACKDROP = 0xE6171B22;

	/**
	 * The fill colour of a panel body.
	 */
	public static final int PANEL_BACKGROUND = 0xF0202630;

	/**
	 * The fill colour of a panel header.
	 */
	public static final int PANEL_HEADER = 0xF2252C38;

	/**
	 * The colour of a panel border.
	 */
	public static final int PANEL_BORDER = 0xFF303946;

	/**
	 * The colour of the soft shadow drawn under a panel.
	 */
	public static final int PANEL_SHADOW = 0x50000000;

	/**
	 * The colour of the text of an enabled module and of the category names.
	 */
	public static final int PRIMARY_TEXT = 0xFFD8DEE9;

	/**
	 * The colour of secondary text, such as a module that is turned off.
	 */
	public static final int SECONDARY_TEXT = 0xFF8995A5;

	/**
	 * The accent colour, used for the active state of a toggle and of a module.
	 */
	public static final int ACCENT = 0xFF79E6B3;

	/**
	 * The colour of a toggle that is off.
	 */
	public static final int TOGGLE_OFF = 0xFF566170;

	/**
	 * The colour of the track of a toggle that is off.
	 */
	public static final int TOGGLE_OFF_TRACK = 0xFF2C333F;

	/**
	 * The colour of the track of a toggle that is on.
	 */
	public static final int TOGGLE_ON_TRACK = 0xFF3E6B58;

	/**
	 * The colour of the row highlight drawn under the cursor.
	 */
	public static final int ROW_HOVER = 0x14FFFFFF;

	/**
	 * The colour of the separator drawn between the header and the body of a panel.
	 */
	public static final int SEPARATOR = 0xFF2B323E;

	/**
	 * The colour of the text shown in the search field when it is empty.
	 */
	public static final int PLACEHOLDER = 0xFF5C6675;

	/**
	 * The colour of the border of the search field when it has focus.
	 */
	public static final int FOCUS_BORDER = 0xFF79E6B3;

	/**
	 * The fill colour of the tooltip that describes a module.
	 */
	public static final int TOOLTIP_BACKGROUND = 0xF00F1319;

	/**
	 * The colour of the border of the tooltip that describes a module.
	 */
	public static final int TOOLTIP_BORDER = 0xFF3A4453;

	// Metrics ---------------------------------------------------------------------------------

	/**
	 * The width of a category panel, in scaled GUI pixels.
	 */
	public static final int PANEL_WIDTH = 150;

	/**
	 * The height of a panel header, in scaled GUI pixels.
	 */
	public static final int PANEL_HEADER_HEIGHT = 26;

	/**
	 * The height of a module row, in scaled GUI pixels.
	 */
	public static final int ROW_HEIGHT = 22;

	/**
	 * The padding between the edge of a panel and its content.
	 */
	public static final int PANEL_PADDING = 6;

	/**
	 * The gap between two panels of the default row.
	 */
	public static final int PANEL_GAP = 12;

	/**
	 * The corner radius of a panel, a toggle and the search field.
	 */
	public static final int CORNER_RADIUS = 5;

	/**
	 * The width of a toggle, in scaled GUI pixels.
	 */
	public static final int TOGGLE_WIDTH = 22;

	/**
	 * The height of a toggle, in scaled GUI pixels.
	 */
	public static final int TOGGLE_HEIGHT = 12;

	/**
	 * The diameter of the knob of a toggle, in scaled GUI pixels.
	 */
	public static final int TOGGLE_KNOB = 10;

	/**
	 * The height of the search field, in scaled GUI pixels.
	 */
	public static final int SEARCH_FIELD_HEIGHT = 20;

	/**
	 * The height of a row in the search result list, in scaled GUI pixels.
	 */
	public static final int SEARCH_ROW_HEIGHT = 19;

	/**
	 * The maximum height of a panel body before it starts to scroll, in scaled GUI pixels.
	 */
	public static final int MAX_BODY_HEIGHT = 220;

	/**
	 * The size of a category icon, in scaled GUI pixels. The glyphs are seven pixels wide.
	 */
	public static final int ICON_SIZE = 7;

	/**
	 * The gap between the icon of a header and its title, in scaled GUI pixels.
	 */
	public static final int ICON_GAP = 6;

	/**
	 * The scale a panel grows from while it opens.
	 */
	public static final float OPEN_MIN_SCALE = 0.96F;

	/**
	 * The maximum width of a panel that may be visible on screen, in scaled GUI pixels.
	 */
	public static final int EDGE_MARGIN = 4;
}
