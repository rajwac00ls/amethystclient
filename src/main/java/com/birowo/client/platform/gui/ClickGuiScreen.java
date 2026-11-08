package com.birowo.client.platform.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.gui.ClickGuiHost;
import com.birowo.client.core.gui.GuiColors;
import com.birowo.client.core.gui.GuiPanelIds;
import com.birowo.client.core.gui.GuiTheme;
import com.birowo.client.core.gui.ModuleVisual;
import com.birowo.client.core.gui.PanelState;
import com.birowo.client.core.gui.Tween;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;

/**
 * The interface of the client.
 *
 * <p>The screen owns nothing that must outlive it: the panels belong to the client, so their
 * positions survive a session, and the modules are reached through the host. Every animation is
 * driven by the elapsed time, so the interface looks the same at any frame rate.
 */
public final class ClickGuiScreen extends Screen {
	private static final String TITLE = "Birowo";

	/**
	 * The longest frame the animations may be advanced by, in seconds. A long stall must not make
	 * the interface jump.
	 */
	private static final float MAX_FRAME_SECONDS = 0.1F;

	/**
	 * How far the cursor has to travel before a press on a header is treated as a drag rather
	 * than as a click. Without the threshold a shaky click would move the panel by a pixel or two
	 * and would no longer fold it.
	 */
	private static final int DRAG_THRESHOLD = 3;

	/**
	 * The narrowest a category panel may become before the text has to be trimmed.
	 */
	private static final int MIN_PANEL_WIDTH = 118;

	/**
	 * The gap between two panels when the screen is too narrow for the preferred one.
	 */
	private static final int NARROW_GAP = 8;

	/**
	 * The smallest height the body of a panel may have.
	 */
	private static final int MIN_BODY_HEIGHT = 32;

	/**
	 * How close a panel has to be to invisible before the interface is taken down.
	 */
	private static final float CLOSED_THRESHOLD = 0.001F;

	/**
	 * The padding inside the tooltip, in scaled GUI pixels.
	 */
	private static final int TOOLTIP_PADDING = 5;

	private final ClickGuiHost host;
	private final Map<ModuleCategory, PanelBox> categoryBoxes = new EnumMap<>(ModuleCategory.class);
	private final PanelBox searchBox;

	private final StringBuilder query = new StringBuilder();
	private final List<Module> results = new ArrayList<>();
	private final List<Module> matches = new ArrayList<>();
	private final Tween resultsAlpha = new Tween(0.0F);

	private boolean queryFocused;
	private int queryCursor;
	private boolean resultsDirty = true;
	private Module settingsModule;

	private PanelBox draggedBox;
	private double dragOffsetX;
	private double dragOffsetY;
	private double dragStartX;
	private double dragStartY;
	private boolean dragMoved;

	private double mouseX;
	private double mouseY;
	private long previousFrameNanos;
	private boolean closing;
	private boolean opened;

	private int gridColumns;
	private int gridCellWidth;
	private int gridGap;
	private int searchBodyLimit = Integer.MAX_VALUE;

	/**
	 * @param client the client the interface is drawn for
	 * @param host   the source of the modules and of the panel layout
	 */
	public ClickGuiScreen(MinecraftClient client, ClickGuiHost host) {
		super(client, client.textRenderer, Text.literal(TITLE));
		this.host = host;

		for (ModuleCategory category : ModuleCategory.values()) {
			PanelBox box = new PanelBox(panelFor(GuiPanelIds.forCategory(category)), category);
			categoryBoxes.put(category, box);
		}

		searchBox = new PanelBox(panelFor(GuiPanelIds.SEARCH), null);

		for (Module module : host.modules()) {
			PanelBox box = categoryBoxes.get(module.category());

			if (box != null) {
				box.modules().add(module);
			}
		}
	}

	private PanelState panelFor(String id) {
		for (PanelState panel : host.panels()) {
			if (panel.id().equals(id)) {
				return panel;
			}
		}

		throw new IllegalStateException("The interface has no panel with the identifier " + id);
	}

	@Override
	public void init() {
		releaseMovementKeys();
		layout();
	}

	/**
	 * Lets go of the keys that move the player.
	 *
	 * <p>The game keeps running while the interface is open, so a key that is held down would
	 * walk the player away while they are looking at the interface.
	 */
	private void releaseMovementKeys() {
		GameOptions options = client.options;

		options.forwardKey.setPressed(false);
		options.backKey.setPressed(false);
		options.leftKey.setPressed(false);
		options.rightKey.setPressed(false);
		options.jumpKey.setPressed(false);
		options.sneakKey.setPressed(false);
		options.sprintKey.setPressed(false);
	}

	/**
	 * Places every panel that has no position of its own on a grid.
	 *
	 * <p>The panels share the width of the screen evenly and wrap into a second row as soon as
	 * they no longer fit, so that they never overlap each other and never leave the screen. A
	 * panel the player has moved keeps the position it was given, unless that position no longer
	 * fits on the screen.
	 */
	private void layout() {
		List<PanelBox> cells = new ArrayList<>();

		cells.add(searchBox);
		cells.addAll(categoryBoxes.values());

		this.gridGap = width >= 760 ? GuiTheme.PANEL_GAP : NARROW_GAP;

		int available = width - 2 * GuiTheme.EDGE_MARGIN;
		this.gridColumns = Math.max(1, Math.min(cells.size(), available / MIN_PANEL_WIDTH));
		this.gridCellWidth = Math.min(GuiTheme.PANEL_WIDTH,
				(available - (gridColumns - 1) * gridGap) / gridColumns);

		int rowTop = GuiTheme.EDGE_MARGIN;
		int searchRow = -1;
		List<Integer> rowTops = new ArrayList<>();

		for (int index = 0; index < cells.size(); index += gridColumns) {
			rowTops.add(rowTop);

			int rowHeight = 0;

			for (int column = 0; column < gridColumns && index + column < cells.size(); column++) {
				PanelBox box = cells.get(index + column);
				int x = GuiTheme.EDGE_MARGIN + column * (gridCellWidth + gridGap);

				if (!box.state().placed()) {
					box.state().snapTo(x, rowTop);
				} else {
					// A position that was saved on another screen may no longer fit.
					box.state().snapTo(clamp(box.state().restingX(), GuiTheme.EDGE_MARGIN,
							Math.max(GuiTheme.EDGE_MARGIN, width - gridCellWidth - GuiTheme.EDGE_MARGIN)),
							clamp(box.state().restingY(), GuiTheme.EDGE_MARGIN,
									Math.max(GuiTheme.EDGE_MARGIN, height - contentHeight(box) - GuiTheme.EDGE_MARGIN)));
				}

				if (box == searchBox) {
					searchRow = index / gridColumns;
				}

				rowHeight = Math.max(rowHeight, contentHeight(box));
			}

			rowTop = rowTop + rowHeight + gridGap;
		}

		// The result list of the search panel may not grow into the row below it.
		int limit = Integer.MAX_VALUE;

		if (searchRow >= 0 && searchRow + 1 < rowTops.size()) {
			limit = rowTops.get(searchRow + 1) - searchBox.state().y() - GuiTheme.PANEL_HEADER_HEIGHT;
		}

		this.searchBodyLimit = Math.max(MIN_BODY_HEIGHT, limit);

		// The panels are only seen to appear when the interface is opened for the first time. A
		// window that is resized later keeps them where they are.
		if (!opened) {
			opened = true;

			for (PanelBox box : cells) {
				box.state().restartOpen();
			}
		}
	}

	/**
	 * @param box the panel to measure
	 * @return the height of the content of the panel, in scaled GUI pixels
	 */
	private int contentHeight(PanelBox box) {
		if (box == searchBox) {
			return GuiTheme.PANEL_HEADER_HEIGHT + GuiTheme.PANEL_PADDING * 2 + GuiTheme.SEARCH_FIELD_HEIGHT;
		}

		return GuiTheme.PANEL_HEADER_HEIGHT + GuiTheme.PANEL_PADDING * 2 + box.modules().size() * GuiTheme.ROW_HEIGHT;
	}

	/**
	 * @param top the top edge of the panel, in scaled GUI pixels
	 * @return the height the body of that panel may take, in scaled GUI pixels
	 */
	private int maxBodyHeight(int top) {
		return Math.max(MIN_BODY_HEIGHT,
				Math.min(GuiTheme.MAX_BODY_HEIGHT, height - top - GuiTheme.EDGE_MARGIN - GuiTheme.PANEL_HEADER_HEIGHT));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float elapsed = previousFrameNanos == 0L ? 0.0F : (now - previousFrameNanos) / 1_000_000_000.0F;
		previousFrameNanos = now;

		this.mouseX = mouseX;
		this.mouseY = mouseY;

		advance(Math.min(elapsed, MAX_FRAME_SECONDS));

		if (settingsModule != null) {
			drawSettings(context);
		} else {
			drawPanels(context);
		}

		if (closing && allClosed()) {
			host.onGuiClosed();
			super.close();
		}
	}

	private void drawPanels(DrawContext context) {
		GuiShapes.fillRounded(context, 0, 0, width, height, 0, GuiTheme.BACKDROP);

		PanelRenderer renderer = new PanelRenderer(context, textRenderer, host, mouseX, mouseY);

		for (PanelBox box : categoryBoxes.values()) {
			prepareCategory(box);
			renderer.drawCategoryPanel(box);
		}

		prepareSearch();
		renderer.drawSearchPanel(searchBox, query.toString(), queryCursor, queryFocused, results, resultsAlpha.value());

		drawTooltip(context);
	}

	private void drawSettings(DrawContext context) {
		GuiShapes.fillRounded(context, 0, 0, width, height, 0, GuiColors.withAlpha(GuiTheme.BACKDROP, 0.75F));

		int panelHeight = SettingsPanel.height(settingsModule.settings().size(), textRenderer.fontHeight);
		int x = (width - SettingsPanel.WIDTH) / 2;
		int y = Math.max(GuiTheme.EDGE_MARGIN, (this.height - panelHeight) / 2);

		SettingsPanel.draw(context, textRenderer, settingsModule, settingsModule.settings(), x, y, mouseX, mouseY);
	}

	/**
	 * Draws the description of the module the cursor is over, next to the cursor.
	 *
	 * @param context the draw context of the current frame
	 */
	private void drawTooltip(DrawContext context) {
		Module module = hoveredModule();

		if (module == null) {
			return;
		}

		float alpha = host.visualFor(module).hoverAmount();

		if (alpha < 0.05F) {
			return;
		}

		String description = module.description();

		if (description.isEmpty()) {
			return;
		}

		int textWidth = textRenderer.getWidth(description);
		int boxWidth = textWidth + TOOLTIP_PADDING * 2;
		int boxHeight = textRenderer.fontHeight + TOOLTIP_PADDING;
		int x = (int) mouseX + 12;
		int y = (int) mouseY + 12;

		if (x + boxWidth > width - GuiTheme.EDGE_MARGIN) {
			x = (int) mouseX - boxWidth - 8;
		}

		if (y + boxHeight > height - GuiTheme.EDGE_MARGIN) {
			y = (int) mouseY - boxHeight - 8;
		}

		x = Math.max(GuiTheme.EDGE_MARGIN, x);
		y = Math.max(GuiTheme.EDGE_MARGIN, y);

		GuiShapes.fillRounded(context, x, y, boxWidth, boxHeight, 4,
				GuiColors.withAlpha(GuiTheme.TOOLTIP_BACKGROUND, alpha));
		GuiShapes.strokeRounded(context, x, y, boxWidth, boxHeight, 4,
				GuiColors.withAlpha(GuiTheme.TOOLTIP_BORDER, alpha));
		context.drawTextWithShadow(textRenderer, Text.literal(description), x + TOOLTIP_PADDING,
				y + TOOLTIP_PADDING / 2, GuiColors.withAlpha(GuiTheme.SECONDARY_TEXT, alpha));
	}

	/**
	 * @return the module the cursor is over, or {@code null} if the cursor is over nothing
	 */
	private Module hoveredModule() {
		for (PanelBox box : categoryBoxes.values()) {
			Module module = moduleAt(box, mouseX, mouseY);

			if (module != null) {
				return module;
			}
		}

		return searchResultAt(searchBox, mouseX, mouseY);
	}

	private void prepareCategory(PanelBox box) {
		int rows = box.modules().size();
		int contentHeight = GuiTheme.PANEL_PADDING + rows * GuiTheme.ROW_HEIGHT + GuiTheme.PANEL_PADDING;
		int visibleHeight = Math.min(contentHeight, maxBodyHeight(box.state().y()));
		int bodyHeight = Math.round(visibleHeight * (1.0F - box.state().collapseAmount()));

		box.prepare(gridCellWidth, GuiTheme.PANEL_HEADER_HEIGHT + bodyHeight, contentHeight, visibleHeight,
				bodyHeight, box.state().openAmount());
	}

	private void prepareSearch() {
		int resultsHeight = results.isEmpty() ? 0 : results.size() * GuiTheme.SEARCH_ROW_HEIGHT;
		int contentHeight = GuiTheme.PANEL_PADDING + GuiTheme.SEARCH_FIELD_HEIGHT + GuiTheme.PANEL_PADDING
				+ resultsHeight + GuiTheme.PANEL_PADDING;
		int limit = Math.min(maxBodyHeight(searchBox.state().y()), searchBodyLimit);
		int visibleHeight = Math.min(contentHeight, limit);
		int bodyHeight = Math.round(visibleHeight * (1.0F - searchBox.state().collapseAmount()));

		searchBox.prepare(gridCellWidth, GuiTheme.PANEL_HEADER_HEIGHT + bodyHeight, contentHeight,
				visibleHeight, bodyHeight, searchBox.state().openAmount());
	}

	private void advance(float elapsed) {
		for (PanelBox box : categoryBoxes.values()) {
			box.state().update(elapsed);
		}

		searchBox.state().update(elapsed);

		for (Module module : host.modules()) {
			ModuleVisual visual = host.visualFor(module);
			visual.setEnabled(module.enabled());
			visual.update(elapsed);
		}

		resultsAlpha.update(elapsed);

		if (resultsDirty && resultsAlpha.settled()) {
			refreshResults();
		}

		if (resultsAlpha.settled() && resultsAlpha.value() <= 0.0F && !results.isEmpty()) {
			results.clear();
		}
	}

	private boolean allClosed() {
		for (PanelBox box : categoryBoxes.values()) {
			if (!box.state().openSettled() || box.state().openAmount() > CLOSED_THRESHOLD) {
				return false;
			}
		}

		return searchBox.state().openSettled() && searchBox.state().openAmount() <= CLOSED_THRESHOLD;
	}

	private void refreshResults() {
		matches.clear();

		String needle = query.toString().toLowerCase(Locale.ROOT);

		if (!needle.isEmpty()) {
			for (Module module : host.modules()) {
				if (module.displayName().toLowerCase(Locale.ROOT).contains(needle)
						|| module.id().toLowerCase(Locale.ROOT).contains(needle)) {
					matches.add(module);
				}
			}
		}

		resultsDirty = false;

		if (matches.isEmpty()) {
			// The list that is on screen fades away before it is taken down.
			resultsAlpha.target(0.0F);
			return;
		}

		results.clear();
		results.addAll(matches);
		resultsAlpha.set(0.0F);
		resultsAlpha.target(1.0F);
	}

	// Input ------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double x = click.x();
		double y = click.y();

		if (settingsModule != null) {
			return clickSetting(click);
		}

		PanelBox box = boxAt(x, y);

		if (box == null) {
			queryFocused = false;

			// The interface covers the whole screen, so a click that lands on nothing is still
			// a click on the interface and not on the game.
			return true;
		}

		if (box == searchBox) {
			if (inSearchField(box, x, y)) {
				queryFocused = true;
				queryCursor = cursorAt(box, x);
				return true;
			}

			Module result = searchResultAt(box, x, y);

			if (result != null) {
				handleModuleClick(result, click.button(), doubled);
				return true;
			}
		} else {
			Module module = moduleAt(box, x, y);

			if (module != null) {
				handleModuleClick(module, click.button(), doubled);
				return true;
			}
		}

		if (inHeader(box, y)) {
			beginDrag(box, x, y);
			return true;
		}

		queryFocused = false;
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (draggedBox == null) {
			// The press was swallowed, so the release has to be swallowed as well.
			return true;
		}

		// A press that did not move the panel is a click on the header, which folds it.
		if (!dragMoved) {
			draggedBox.state().toggleCollapsed();
		}

		draggedBox = null;
		return true;
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		if (draggedBox == null) {
			// The interface is modal; a drag that does not belong to a panel is swallowed as
			// well.
			return true;
		}

		double travel = Math.hypot(click.x() - dragStartX, click.y() - dragStartY);

		if (!dragMoved && travel < DRAG_THRESHOLD) {
			return true;
		}

		dragMoved = true;

		int x = (int) Math.round(click.x() - dragOffsetX);
		int y = (int) Math.round(click.y() - dragOffsetY);

		draggedBox.state().moveTo(clampPanelX(x), clampPanelY(y, draggedBox));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		PanelBox box = boxAt(mouseX, mouseY);

		if (box == null) {
			// The scroll belongs to the interface; the game must not change the hotbar while
			// it is open.
			return true;
		}

		double limit = Math.max(0, box.contentHeight() - box.visibleHeight());
		float target = box.state().scrollTarget() - (float) vertical * GuiTheme.ROW_HEIGHT;

		box.state().scrollTo(clamp(target, 0.0F, (float) limit));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput key) {
		if (key.isEscape()) {
			if (settingsModule != null) {
				settingsModule = null;
				return true;
			}

			requestClose();
			return true;
		}

		if (!queryFocused) {
			// The keys that move the player are swallowed while the interface is open, so that
			// the player does not walk away while they are looking at it.
			return swallowsMovement(key);
		}

		int code = key.getKeycode();

		if (code == GLFW.GLFW_KEY_BACKSPACE) {
			deleteBeforeCursor();
			return true;
		}

		if (code == GLFW.GLFW_KEY_DELETE) {
			deleteAtCursor();
			return true;
		}

		if (code == GLFW.GLFW_KEY_LEFT) {
			queryCursor = Math.max(0, queryCursor - 1);
			return true;
		}

		if (code == GLFW.GLFW_KEY_RIGHT) {
			queryCursor = Math.min(query.length(), queryCursor + 1);
			return true;
		}

		if (code == GLFW.GLFW_KEY_HOME) {
			queryCursor = 0;
			return true;
		}

		if (code == GLFW.GLFW_KEY_END) {
			queryCursor = query.length();
			return true;
		}

		if (key.isPaste()) {
			paste();
			return true;
		}

		// Every other key belongs to the search field while it has focus.
		return true;
	}

	/**
	 * @param key the key that was pressed
	 * @return whether the key is one of the keys that move the player
	 */
	private boolean swallowsMovement(KeyInput key) {
		GameOptions options = client.options;

		return options.forwardKey.matchesKey(key) || options.backKey.matchesKey(key)
				|| options.leftKey.matchesKey(key) || options.rightKey.matchesKey(key)
				|| options.jumpKey.matchesKey(key) || options.sneakKey.matchesKey(key)
				|| options.sprintKey.matchesKey(key);
	}

	@Override
	public boolean charTyped(CharInput charInput) {
		if (!queryFocused || !charInput.isValidChar()) {
			return false;
		}

		String typed = charInput.asString();

		if (typed.isEmpty()) {
			return false;
		}

		query.insert(queryCursor, typed);
		queryCursor += typed.length();
		resultsDirty = true;
		return true;
	}

	@Override
	public void close() {
		requestClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * Starts the closing animation. The interface is taken down once every panel has faded out.
	 */
	public void requestClose() {
		if (closing) {
			return;
		}

		closing = true;

		for (PanelBox box : categoryBoxes.values()) {
			box.state().close();
		}

		searchBox.state().close();
	}

	// Hit testing ------------------------------------------------------------------------------

	private PanelBox boxAt(double x, double y) {
		if (searchBox.contains(x, y)) {
			return searchBox;
		}

		for (PanelBox box : categoryBoxes.values()) {
			if (box.contains(x, y)) {
				return box;
			}
		}

		return null;
	}

	private boolean inHeader(PanelBox box, double y) {
		return box.localY(y) < GuiTheme.PANEL_HEADER_HEIGHT;
	}

	private boolean inSearchField(PanelBox box, double x, double y) {
		int localY = box.localY(y);
		int fieldTop = GuiTheme.PANEL_HEADER_HEIGHT + 1 + GuiTheme.PANEL_PADDING;

		if (localY < fieldTop || localY > fieldTop + GuiTheme.SEARCH_FIELD_HEIGHT) {
			return false;
		}

		int localX = box.localX(x);
		return localX >= GuiTheme.PANEL_PADDING && localX <= box.width() - GuiTheme.PANEL_PADDING;
	}

	private Module moduleAt(PanelBox box, double x, double y) {
		if (!box.contains(x, y)) {
			return null;
		}

		int localY = box.localY(y);
		int bodyTop = GuiTheme.PANEL_HEADER_HEIGHT + 1;

		if (localY < bodyTop || localY > bodyTop + box.visibleHeight()) {
			return null;
		}

		int index = (localY - bodyTop + Math.round(box.state().scroll())) / GuiTheme.ROW_HEIGHT;

		if (index < 0 || index >= box.modules().size()) {
			return null;
		}

		return box.modules().get(index);
	}

	private Module searchResultAt(PanelBox box, double x, double y) {
		if (!box.contains(x, y)) {
			return null;
		}

		int localY = box.localY(y);
		int bodyTop = GuiTheme.PANEL_HEADER_HEIGHT + 1;
		int listTop = bodyTop + GuiTheme.PANEL_PADDING + GuiTheme.SEARCH_FIELD_HEIGHT + GuiTheme.PANEL_PADDING;

		if (localY < listTop || localY > bodyTop + box.visibleHeight()) {
			return null;
		}

		int index = (localY - listTop) / GuiTheme.SEARCH_ROW_HEIGHT;

		if (index < 0 || index >= results.size()) {
			return null;
		}

		return results.get(index);
	}

	private int cursorAt(PanelBox box, double x) {
		int offset = box.localX(x) - GuiTheme.PANEL_PADDING - 5;

		if (offset <= 0) {
			return 0;
		}

		int index = 0;
		int width = 0;

		while (index < query.length()) {
			int next = width + textRenderer.getWidth(String.valueOf(query.charAt(index)));

			if (next > offset) {
				break;
			}

			width = next;
			index++;
		}

		return index;
	}

	// Actions ----------------------------------------------------------------------------------

	private void handleModuleClick(Module module, int button, boolean doubled) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			settingsModule = module;
			return;
		}

		// The second click of a double click is ignored, so that the module is switched once and
		// not twice.
		if (doubled) {
			return;
		}

		host.toggleModule(module);
	}

	/**
	 * Handles a click while the settings of a module are open.
	 *
	 * @param click the click that was made
	 * @return always {@code true}, because the click belongs to the interface
	 */
	private boolean clickSetting(Click click) {
		int panelHeight = SettingsPanel.height(settingsModule.settings().size(), textRenderer.fontHeight);
		int x = (width - SettingsPanel.WIDTH) / 2;
		int y = Math.max(GuiTheme.EDGE_MARGIN, (this.height - panelHeight) / 2);
		int listTop = SettingsPanel.listTop(y, textRenderer.fontHeight);
		List<ConfigValue<?>> settings = settingsModule.settings();

		for (int index = 0; index < settings.size(); index++) {
			int rowTop = listTop + index * SettingsPanel.ROW_HEIGHT;

			if (click.y() < rowTop || click.y() >= rowTop + SettingsPanel.ROW_HEIGHT) {
				continue;
			}

			if (click.x() < x || click.x() >= x + SettingsPanel.WIDTH) {
				break;
			}

			int direction = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? -1 : 1;
			int steps = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;

			settings.get(index).shift(direction, steps);
			return true;
		}

		// A click outside the rows goes back to the module list.
		settingsModule = null;
		return true;
	}

	/**
	 * Starts a drag of a panel.
	 *
	 * <p>The offset is taken from the place the panel is drawn at, not from the place it rests
	 * at, so that a panel that is still growing out of its opening animation does not jump when
	 * it is picked up.
	 *
	 * @param box the panel that is being dragged
	 * @param x   the horizontal position of the cursor, in scaled GUI pixels
	 * @param y   the vertical position of the cursor, in scaled GUI pixels
	 */
	private void beginDrag(PanelBox box, double x, double y) {
		draggedBox = box;
		dragOffsetX = x - box.x();
		dragOffsetY = y - box.y();
		dragStartX = x;
		dragStartY = y;
		dragMoved = false;
	}

	/**
	 * @param x the horizontal position the panel would take, in scaled GUI pixels
	 * @return the position the panel may take without leaving the screen, in scaled GUI pixels
	 */
	private int clampPanelX(int x) {
		int maximum = Math.max(GuiTheme.EDGE_MARGIN, width - gridCellWidth - GuiTheme.EDGE_MARGIN);
		return clamp(x, GuiTheme.EDGE_MARGIN, maximum);
	}

	/**
	 * @param y    the vertical position the panel would take, in scaled GUI pixels
	 * @param box  the panel that is being dragged
	 * @return the position the panel may take without leaving the screen, in scaled GUI pixels
	 */
	private int clampPanelY(int y, PanelBox box) {
		int panelHeight = GuiTheme.PANEL_HEADER_HEIGHT + box.bodyHeight();
		int maximum = Math.max(GuiTheme.EDGE_MARGIN, height - panelHeight - GuiTheme.EDGE_MARGIN);
		return clamp(y, GuiTheme.EDGE_MARGIN, maximum);
	}

	private void deleteBeforeCursor() {
		if (queryCursor <= 0) {
			return;
		}

		query.deleteCharAt(queryCursor - 1);
		queryCursor--;
		resultsDirty = true;
	}

	private void deleteAtCursor() {
		if (queryCursor >= query.length()) {
			return;
		}

		query.deleteCharAt(queryCursor);
		resultsDirty = true;
	}

	private void paste() {
		String clipboard = client.keyboard.getClipboard();

		if (clipboard == null || clipboard.isEmpty()) {
			return;
		}

		query.insert(queryCursor, clipboard);
		queryCursor += clipboard.length();
		resultsDirty = true;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(value, maximum));
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(value, maximum));
	}
}
