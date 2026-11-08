package com.birowo.client.platform.gui;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import com.birowo.client.core.gui.ClickGuiHost;
import com.birowo.client.core.gui.GuiColors;
import com.birowo.client.core.gui.GuiTheme;
import com.birowo.client.core.gui.ModuleVisual;
import com.birowo.client.core.module.Module;

/**
 * Draws the panels of the interface.
 *
 * <p>The renderer holds no state of its own: everything it needs is passed in, so that a frame is
 * a pure function of the state of the client.
 */
public final class PanelRenderer {
	private static final long CARET_BLINK_NANOS = 530_000_000L;
	private static final String NO_MODULES = "No modules";

	private final DrawContext context;
	private final TextRenderer textRenderer;
	private final ClickGuiHost host;
	private final double mouseX;
	private final double mouseY;

	/**
	 * @param context      the draw context of the current frame
	 * @param textRenderer the font of the client
	 * @param host         the source of the modules and of their appearance
	 * @param mouseX       the horizontal position of the cursor, in scaled GUI pixels
	 * @param mouseY       the vertical position of the cursor, in scaled GUI pixels
	 */
	public PanelRenderer(DrawContext context, TextRenderer textRenderer, ClickGuiHost host, double mouseX, double mouseY) {
		this.context = context;
		this.textRenderer = textRenderer;
		this.host = host;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}

	/**
	 * Draws the frame, the header and the body of a category panel.
	 *
	 * @param box the panel to draw
	 */
	public void drawCategoryPanel(PanelBox box) {
		float alpha = box.open();
		int headerHeight = box.sw(GuiTheme.PANEL_HEADER_HEIGHT);

		drawFrame(box, alpha, headerHeight);

		int iconSize = box.sw(GuiTheme.ICON_SIZE);
		int iconY = box.y() + (headerHeight - iconSize) / 2;
		int titleColor = headerColor(box, headerHeight);

		GuiIcon.forCategory(box.category()).draw(context, box.x() + box.sw(GuiTheme.PANEL_PADDING), iconY, titleColor);
		context.drawTextWithShadow(textRenderer, Text.literal(box.category().displayName()),
				box.x() + box.sw(GuiTheme.PANEL_PADDING) + iconSize + box.sw(GuiTheme.ICON_GAP),
				box.y() + (headerHeight - textRenderer.fontHeight) / 2, titleColor);

		GuiIcon chevron = box.state().collapseAmount() > 0.5F ? GuiIcon.COLLAPSED : GuiIcon.EXPANDED;
		chevron.draw(context, box.x() + box.width() - box.sw(GuiTheme.PANEL_PADDING + GuiTheme.ICON_SIZE), iconY,
				titleColor);

		drawSeparator(box, headerHeight, alpha);

		if (box.bodyHeight() <= 0) {
			return;
		}

		int bodyTop = box.y() + headerHeight + 1;
		int bodyBottom = bodyTop + box.sw(box.bodyHeight());
		int rowHeight = box.sw(GuiTheme.ROW_HEIGHT);
		int scroll = Math.round(box.state().scroll() * box.scale());

		context.enableScissor(box.x() + 1, bodyTop, box.x() + box.width() - 1, bodyBottom);

		List<Module> modules = box.modules();

		// A row that is scrolled out of sight is not under the cursor any more, so its highlight
		// has to be taken away.
		for (Module module : modules) {
			host.visualFor(module).setHovered(false);
		}

		for (int index = 0; index < modules.size(); index++) {
			int rowTop = bodyTop + index * rowHeight - scroll;

			if (rowTop + rowHeight <= bodyTop || rowTop >= bodyBottom) {
				continue;
			}

			drawModuleRow(box, modules.get(index), rowTop, rowHeight, alpha);
		}

		context.disableScissor();

		if (modules.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal(NO_MODULES),
					box.x() + (box.width() - textRenderer.getWidth(NO_MODULES)) / 2,
					bodyTop + box.sw(GuiTheme.PANEL_PADDING), GuiColors.withAlpha(GuiTheme.PLACEHOLDER, alpha));
		}
	}

	/**
	 * Draws the frame, the header, the search field and the results of the search panel.
	 *
	 * @param box          the panel to draw
	 * @param query        the text that has been typed into the search field
	 * @param cursor       the position of the caret within the text
	 * @param focused      whether the search field has focus
	 * @param results      the modules that match the query
	 * @param resultsAlpha the animated opacity of the result list
	 */
	public void drawSearchPanel(PanelBox box, String query, int cursor, boolean focused, List<Module> results,
			float resultsAlpha) {
		float alpha = box.open();
		int headerHeight = box.sw(GuiTheme.PANEL_HEADER_HEIGHT);

		drawFrame(box, alpha, headerHeight);

		int iconSize = box.sw(GuiTheme.ICON_SIZE);
		int titleColor = headerColor(box, headerHeight);

		GuiIcon.SEARCH.draw(context, box.x() + box.sw(GuiTheme.PANEL_PADDING),
				box.y() + (headerHeight - iconSize) / 2, titleColor);
		context.drawTextWithShadow(textRenderer, Text.literal("Search"),
				box.x() + box.sw(GuiTheme.PANEL_PADDING) + iconSize + box.sw(GuiTheme.ICON_GAP),
				box.y() + (headerHeight - textRenderer.fontHeight) / 2, titleColor);

		drawSeparator(box, headerHeight, alpha);

		if (box.bodyHeight() <= 0) {
			return;
		}

		int bodyTop = box.y() + headerHeight + 1;
		int bodyBottom = bodyTop + box.sw(box.bodyHeight());
		int padding = box.sw(GuiTheme.PANEL_PADDING);
		int fieldTop = bodyTop + padding;
		int fieldHeight = box.sw(GuiTheme.SEARCH_FIELD_HEIGHT);
		int fieldWidth = box.width() - padding * 2;

		context.enableScissor(box.x() + 1, bodyTop, box.x() + box.width() - 1, bodyBottom);

		drawSearchField(box.x() + padding, fieldTop, fieldWidth, fieldHeight, query, cursor, focused, alpha);

		int listTop = fieldTop + fieldHeight + padding;
		int rowHeight = box.sw(GuiTheme.SEARCH_ROW_HEIGHT);

		for (int index = 0; index < results.size(); index++) {
			int rowTop = listTop + index * rowHeight;

			if (rowTop + rowHeight > bodyBottom) {
				break;
			}

			Module module = results.get(index);
			boolean hovered = box.contains(mouseX, mouseY) && mouseY >= rowTop && mouseY < rowTop + rowHeight;

			if (hovered) {
				context.fill(box.x() + 1, rowTop, box.x() + box.width() - 1, rowTop + rowHeight,
						GuiColors.withAlpha(GuiTheme.ROW_HOVER, resultsAlpha * alpha));
			}

			int textColor = GuiColors.lerp(GuiTheme.SECONDARY_TEXT, GuiTheme.ACCENT,
					host.visualFor(module).toggleAmount());
			textColor = GuiColors.withAlpha(textColor, resultsAlpha * alpha);

			int categoryColor = GuiColors.withAlpha(GuiTheme.PLACEHOLDER, resultsAlpha * alpha);
			int textY = rowTop + (rowHeight - textRenderer.fontHeight) / 2;

			String name = module.displayName();
			String category = module.category().displayName();
			int categoryWidth = textRenderer.getWidth(category);
			int available = Math.max(fieldWidth - categoryWidth - 8, 10);

			context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(name, available)),
					box.x() + padding, textY, textColor);
			context.drawTextWithShadow(textRenderer, Text.literal(category),
					box.x() + box.width() - padding - categoryWidth, textY, categoryColor);
		}

		context.disableScissor();
	}

	private void drawFrame(PanelBox box, float alpha, int headerHeight) {
		GuiShapes.fillRounded(context, box.x() + 1, box.y() + 2, box.width(), box.height(), GuiTheme.CORNER_RADIUS,
				GuiColors.withAlpha(GuiTheme.PANEL_SHADOW, alpha));
		GuiShapes.fillRounded(context, box.x(), box.y(), box.width(), box.height(), GuiTheme.CORNER_RADIUS,
				GuiColors.withAlpha(GuiTheme.PANEL_BACKGROUND, alpha));
		GuiShapes.fillRoundedTop(context, box.x(), box.y(), box.width(), headerHeight, GuiTheme.CORNER_RADIUS,
				GuiColors.withAlpha(GuiTheme.PANEL_HEADER, alpha));
		GuiShapes.strokeRounded(context, box.x(), box.y(), box.width(), box.height(), GuiTheme.CORNER_RADIUS,
				GuiColors.withAlpha(GuiTheme.PANEL_BORDER, alpha));
	}

	private void drawSeparator(PanelBox box, int headerHeight, float alpha) {
		context.fill(box.x() + 1, box.y() + headerHeight, box.x() + box.width() - 1, box.y() + headerHeight + 1,
				GuiColors.withAlpha(GuiTheme.SEPARATOR, alpha));
	}

	private int headerColor(PanelBox box, int headerHeight) {
		boolean hovered = box.contains(mouseX, mouseY) && mouseY < box.y() + headerHeight;
		return GuiColors.withAlpha(hovered ? GuiTheme.PRIMARY_TEXT : GuiTheme.SECONDARY_TEXT, box.open());
	}

	private void drawModuleRow(PanelBox box, Module module, int rowTop, int rowHeight, float alpha) {
		ModuleVisual visual = host.visualFor(module);
		boolean hovered = box.contains(mouseX, mouseY) && mouseY >= rowTop && mouseY < rowTop + rowHeight;

		visual.setHovered(hovered);

		if (hovered && visual.hoverAmount() > 0.01F) {
			context.fill(box.x() + 1, rowTop, box.x() + box.width() - 1, rowTop + rowHeight,
					GuiColors.withAlpha(GuiTheme.ROW_HOVER, visual.hoverAmount() * alpha));
		}

		// The name takes the colour of the switch over the same animation, so that the row does
		// not change colour a frame before the switch does.
		int textColor = GuiColors.lerp(GuiTheme.SECONDARY_TEXT, GuiTheme.ACCENT, visual.toggleAmount());
		textColor = GuiColors.withAlpha(textColor, alpha);

		int toggleWidth = box.sw(GuiTheme.TOGGLE_WIDTH);
		int toggleHeight = box.sw(GuiTheme.TOGGLE_HEIGHT);
		int toggleX = box.x() + box.width() - box.sw(GuiTheme.PANEL_PADDING) - toggleWidth;
		int toggleY = rowTop + (rowHeight - toggleHeight) / 2;

		int textX = box.x() + box.sw(GuiTheme.PANEL_PADDING);
		int textY = rowTop + (rowHeight - textRenderer.fontHeight) / 2;
		int available = Math.max(box.width() - box.sw(GuiTheme.PANEL_PADDING * 2 + GuiTheme.TOGGLE_WIDTH + 6), 10);

		context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(module.displayName(), available)),
				textX, textY, textColor);

		drawToggle(toggleX, toggleY, toggleWidth, toggleHeight, visual.toggleAmount(), alpha);
	}

	private void drawToggle(int x, int y, int width, int height, float amount, float alpha) {
		int track = GuiColors.lerp(GuiTheme.TOGGLE_OFF_TRACK, GuiTheme.TOGGLE_ON_TRACK, amount);
		GuiShapes.fillRounded(context, x, y, width, height, height / 2, GuiColors.withAlpha(track, alpha));

		int knobSize = Math.round(height * (GuiTheme.TOGGLE_KNOB / (float) GuiTheme.TOGGLE_HEIGHT));
		int travel = width - knobSize - 2;
		int knobX = x + 1 + Math.round(travel * amount);
		int knobY = y + (height - knobSize) / 2;
		int knob = GuiColors.lerp(GuiTheme.TOGGLE_OFF, GuiTheme.ACCENT, amount);

		GuiShapes.fillRounded(context, knobX, knobY, knobSize, knobSize, knobSize / 2,
				GuiColors.withAlpha(knob, alpha));
	}

	private void drawSearchField(int x, int y, int width, int height, String query, int cursor, boolean focused,
			float alpha) {
		int borderColor = focused ? GuiTheme.FOCUS_BORDER : GuiTheme.PANEL_BORDER;

		GuiShapes.fillRounded(context, x, y, width, height, GuiTheme.CORNER_RADIUS,
				GuiColors.withAlpha(GuiTheme.TOGGLE_OFF_TRACK, alpha));
		GuiShapes.strokeRounded(context, x, y, width, height, GuiTheme.CORNER_RADIUS,
				GuiColors.withAlpha(borderColor, alpha));

		int textX = x + 5;
		int textY = y + (height - textRenderer.fontHeight) / 2;
		int textColor = GuiColors.withAlpha(focused ? GuiTheme.PRIMARY_TEXT : GuiTheme.SECONDARY_TEXT, alpha);

		if (query.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("Search modules..."), textX, textY,
					GuiColors.withAlpha(GuiTheme.PLACEHOLDER, alpha));
		} else {
			context.drawTextWithShadow(textRenderer, Text.literal(query), textX, textY, textColor);
		}

		if (!focused || !caretVisible()) {
			return;
		}

		int caretX = textX + textRenderer.getWidth(query.substring(0, Math.min(cursor, query.length())));
		context.fill(caretX, textY, caretX + 1, textY + textRenderer.fontHeight, textColor);
	}

	private boolean caretVisible() {
		return (System.nanoTime() / CARET_BLINK_NANOS) % 2L == 0L;
	}
}
