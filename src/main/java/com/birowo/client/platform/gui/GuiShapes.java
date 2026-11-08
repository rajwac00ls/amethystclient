package com.birowo.client.platform.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * The drawing primitives the interface is built from.
 *
 * <p>The shapes are assembled from rectangles rather than from textures so that the interface has
 * no assets of its own and stays sharp at every GUI scale. The corner geometry is precomputed, so
 * drawing a rounded rectangle costs a handful of rectangles and no allocation.
 */
public final class GuiShapes {
	private static final int MAX_CORNER_RADIUS = 12;

	/**
	 * The horizontal inset of each row of a rounded corner, for every supported radius.
	 *
	 * <p>{@code CORNER_INSETS[radius][row]} is the number of pixels cut from the left of the row
	 * {@code row} counted from the top of the corner.
	 */
	private static final int[][] CORNER_INSETS = new int[MAX_CORNER_RADIUS + 1][];

	static {
		for (int radius = 0; radius <= MAX_CORNER_RADIUS; radius++) {
			int[] insets = new int[radius];

			for (int row = 0; row < radius; row++) {
				double distance = radius - row - 0.5;
				double reach = Math.sqrt(radius * radius - distance * distance);
				insets[row] = radius - (int) Math.round(reach);
			}

			CORNER_INSETS[radius] = insets;
		}
	}

	private GuiShapes() {
	}

	/**
	 * Fills a rectangle with rounded corners.
	 *
	 * @param context the draw context of the current frame
	 * @param x       the left edge, in scaled GUI pixels
	 * @param y       the top edge, in scaled GUI pixels
	 * @param width   the width, in scaled GUI pixels
	 * @param height  the height, in scaled GUI pixels
	 * @param radius  the corner radius, in scaled GUI pixels
	 * @param color   the ARGB colour to fill with
	 */
	public static void fillRounded(DrawContext context, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int clamped = Math.min(Math.min(radius, width / 2), Math.min(height / 2, MAX_CORNER_RADIUS));

		if (clamped <= 0) {
			context.fill(x, y, x + width, y + height, color);
			return;
		}

		// The straight parts of the shape.
		context.fill(x + clamped, y, x + width - clamped, y + height, color);
		context.fill(x, y + clamped, x + clamped, y + height - clamped, color);
		context.fill(x + width - clamped, y + clamped, x + width, y + height - clamped, color);

		int[] insets = CORNER_INSETS[clamped];

		for (int row = 0; row < clamped; row++) {
			int inset = insets[row];
			int top = y + row;
			int bottom = y + height - clamped + row;

			context.fill(x + inset, top, x + clamped, top + 1, color);
			context.fill(x + width - clamped, top, x + width - inset, top + 1, color);
			context.fill(x + inset, bottom, x + clamped, bottom + 1, color);
			context.fill(x + width - clamped, bottom, x + width - inset, bottom + 1, color);
		}
	}

	/**
	 * Draws a one pixel outline around a rectangle with rounded corners.
	 *
	 * <p>The outline is drawn as a filled shape with the inside left transparent, which is cheaper
	 * than tracing the perimeter and produces the same result at this thickness.
	 *
	 * @param context the draw context of the current frame
	 * @param x       the left edge, in scaled GUI pixels
	 * @param y       the top edge, in scaled GUI pixels
	 * @param width   the width, in scaled GUI pixels
	 * @param height  the height, in scaled GUI pixels
	 * @param radius  the corner radius, in scaled GUI pixels
	 * @param color   the ARGB colour of the outline
	 */
	public static void strokeRounded(DrawContext context, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int clamped = Math.min(Math.min(radius, width / 2), Math.min(height / 2, MAX_CORNER_RADIUS));

		if (clamped <= 0) {
			context.fill(x, y, x + width, y + height, color);
			return;
		}

		context.fill(x + clamped, y, x + width - clamped, y + 1, color);
		context.fill(x + clamped, y + height - 1, x + width - clamped, y + height, color);
		context.fill(x, y + clamped, x + 1, y + height - clamped, color);
		context.fill(x + width - 1, y + clamped, x + width, y + height - clamped, color);

		int[] insets = CORNER_INSETS[clamped];

		for (int row = 0; row < clamped; row++) {
			int inset = insets[row];
			int outer = insets[clamped - 1 - row];
			int top = y + row;
			int bottom = y + height - clamped + row;

			context.fill(x + outer, top, x + inset, top + 1, color);
			context.fill(x + width - inset, top, x + width - outer, top + 1, color);
			context.fill(x + outer, bottom, x + inset, bottom + 1, color);
			context.fill(x + width - inset, bottom, x + width - outer, bottom + 1, color);
		}
	}

	/**
	 * Fills a rectangle whose bottom corners are square and whose top corners are rounded.
	 *
	 * @param context the draw context of the current frame
	 * @param x       the left edge, in scaled GUI pixels
	 * @param y       the top edge, in scaled GUI pixels
	 * @param width   the width, in scaled GUI pixels
	 * @param height  the height, in scaled GUI pixels
	 * @param radius  the corner radius of the top corners, in scaled GUI pixels
	 * @param color   the ARGB colour to fill with
	 */
	public static void fillRoundedTop(DrawContext context, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int clamped = Math.min(Math.min(radius, width / 2), Math.min(height, MAX_CORNER_RADIUS));

		if (clamped <= 0) {
			context.fill(x, y, x + width, y + height, color);
			return;
		}

		context.fill(x, y + clamped, x + width, y + height, color);

		int[] insets = CORNER_INSETS[clamped];

		for (int row = 0; row < clamped; row++) {
			int inset = insets[row];
			context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
		}
	}

	/**
	 * Draws a bitmap glyph.
	 *
	 * <p>Each bit of a row marks one pixel, with the least significant bit on the left, so a mask
	 * reads the same way it is drawn.
	 *
	 * @param context the draw context of the current frame
	 * @param mask    the rows of the glyph, one integer per row
	 * @param width   the width of the glyph, in pixels
	 * @param height  the height of the glyph, in pixels
	 * @param x       the left edge, in scaled GUI pixels
	 * @param y       the top edge, in scaled GUI pixels
	 * @param color   the ARGB colour to draw with
	 */
	public static void drawMask(DrawContext context, int[] mask, int width, int height, int x, int y, int color) {
		for (int row = 0; row < height; row++) {
			int bits = mask[row];

			for (int column = 0; column < width; column++) {
				if ((bits & (1 << column)) == 0) {
					continue;
				}

				context.fill(x + column, y + row, x + column + 1, y + row + 1, color);
			}
		}
	}
}
