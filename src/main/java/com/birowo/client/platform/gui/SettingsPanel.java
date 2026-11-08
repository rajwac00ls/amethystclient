package com.birowo.client.platform.gui;

import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.gui.GuiColors;
import com.birowo.client.core.gui.GuiTheme;
import com.birowo.client.core.module.Module;

/**
 * The settings of a module, drawn over the interface.
 *
 * <p>The panel is part of the interface rather than a screen of its own, so that opening it never
 * takes the interface down and the position of the panels is not disturbed.
 */
public final class SettingsPanel {
	/**
	 * The width of the panel, in scaled GUI pixels.
	 */
	public static final int WIDTH = 240;

	/**
	 * The height of the header of the panel, in scaled GUI pixels.
	 */
	public static final int HEADER_HEIGHT = 26;

	/**
	 * The height of a setting row, in scaled GUI pixels.
	 */
	public static final int ROW_HEIGHT = 20;

	/**
	 * The height reserved for the hint at the bottom of the panel, in scaled GUI pixels.
	 */
	public static final int FOOTER_HEIGHT = 26;

	/**
	 * The padding inside the panel, in scaled GUI pixels.
	 */
	public static final int PADDING = 8;

	private static final String HINT = "Click to change, right click to reverse";

	private SettingsPanel() {
	}

	/**
	 * @param rows       the number of settings the panel shows
	 * @param fontHeight the height of the font of the client
	 * @return the height of the panel, in scaled GUI pixels
	 */
	public static int height(int rows, int fontHeight) {
		return HEADER_HEIGHT + PADDING + fontHeight + PADDING + rows * ROW_HEIGHT + FOOTER_HEIGHT;
	}

	/**
	 * @param y          the top edge of the panel, in scaled GUI pixels
	 * @param fontHeight the height of the font of the client
	 * @return the top edge of the first setting row, in scaled GUI pixels
	 */
	public static int listTop(int y, int fontHeight) {
		return y + HEADER_HEIGHT + PADDING + fontHeight + PADDING;
	}

	/**
	 * Draws the panel.
	 *
	 * @param context      the draw context of the current frame
	 * @param textRenderer the font of the client
	 * @param module       the module whose settings are shown
	 * @param settings     the settings of that module
	 * @param x            the left edge of the panel, in scaled GUI pixels
	 * @param y            the top edge of the panel, in scaled GUI pixels
	 * @param mouseX       the horizontal position of the cursor, in scaled GUI pixels
	 * @param mouseY       the vertical position of the cursor, in scaled GUI pixels
	 */
	public static void draw(DrawContext context, TextRenderer textRenderer, Module module,
			List<ConfigValue<?>> settings, int x, int y, double mouseX, double mouseY) {
		GuiShapes.fillRounded(context, x + 1, y + 2, WIDTH, height(settings.size(), textRenderer.fontHeight),
				GuiTheme.CORNER_RADIUS, GuiColors.withAlpha(GuiTheme.PANEL_SHADOW, 1.0F));
		GuiShapes.fillRounded(context, x, y, WIDTH, height(settings.size(), textRenderer.fontHeight),
				GuiTheme.CORNER_RADIUS, GuiTheme.PANEL_BACKGROUND);
		GuiShapes.fillRoundedTop(context, x, y, WIDTH, HEADER_HEIGHT, GuiTheme.CORNER_RADIUS, GuiTheme.PANEL_HEADER);
		GuiShapes.strokeRounded(context, x, y, WIDTH, height(settings.size(), textRenderer.fontHeight),
				GuiTheme.CORNER_RADIUS, GuiTheme.PANEL_BORDER);

		context.drawTextWithShadow(textRenderer, Text.literal(module.displayName()), x + PADDING,
				y + (HEADER_HEIGHT - textRenderer.fontHeight) / 2, GuiTheme.PRIMARY_TEXT);

		int descriptionY = y + HEADER_HEIGHT + PADDING;
		String description = textRenderer.trimToWidth(module.description(), WIDTH - PADDING * 2);
		context.drawTextWithShadow(textRenderer, Text.literal(description), x + PADDING, descriptionY,
				GuiTheme.SECONDARY_TEXT);

		int listTop = listTop(y, textRenderer.fontHeight);

		for (int index = 0; index < settings.size(); index++) {
			ConfigValue<?> setting = settings.get(index);
			int rowTop = listTop + index * ROW_HEIGHT;

			if (mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT && mouseX >= x && mouseX < x + WIDTH) {
				context.fill(x + 1, rowTop, x + WIDTH - 1, rowTop + ROW_HEIGHT, GuiTheme.ROW_HOVER);
			}

			int textY = rowTop + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
			String value = setting.display();

			context.drawTextWithShadow(textRenderer, Text.literal(setting.key()), x + PADDING, textY,
					GuiTheme.SECONDARY_TEXT);
			context.drawTextWithShadow(textRenderer, Text.literal(value),
					x + WIDTH - PADDING - textRenderer.getWidth(value), textY, GuiTheme.ACCENT);
		}

		int footerY = y + height(settings.size(), textRenderer.fontHeight) - FOOTER_HEIGHT + 6;
		context.drawTextWithShadow(textRenderer, Text.literal(HINT), x + PADDING, footerY, GuiTheme.PLACEHOLDER);
	}
}
