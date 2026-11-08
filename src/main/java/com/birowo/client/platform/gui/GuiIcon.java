package com.birowo.client.platform.gui;

import net.minecraft.client.gui.DrawContext;

import com.birowo.client.core.gui.GuiTheme;
import com.birowo.client.core.module.ModuleCategory;

/**
 * The glyph drawn in the header of a panel.
 *
 * <p>Every glyph is a bitmap drawn as one pixel per set bit, so the icons share a single visual
 * weight and none of them depends on a font or on an emoji.
 */
public enum GuiIcon {
	/**
	 * A blade, for the combat category.
	 */
	COMBAT(7, new int[] {
			0b0000100,
			0b0000110,
			0b0001100,
			0b0011000,
			0b0101000,
			0b1100000,
			0b1000000
	}),

	/**
	 * An arrow, for the movement category.
	 */
	MOVEMENT(7, new int[] {
			0b0011100,
			0b0111110,
			0b1111111,
			0b0011100,
			0b0011100,
			0b0011100,
			0b0011100
	}),

	/**
	 * An eye, for the render category.
	 */
	RENDER(7, new int[] {
			0b0000000,
			0b0011100,
			0b0110110,
			0b1101011,
			0b0110110,
			0b0011100,
			0b0000000
	}),

	/**
	 * A window, for the client category.
	 */
	CLIENT(7, new int[] {
			0b0000000,
			0b1111111,
			0b1000001,
			0b1011101,
			0b1000001,
			0b1111111,
			0b0000000
	}),

	/**
	 * Three dots, for the miscellaneous category.
	 */
	MISC(7, new int[] {
			0b0000000,
			0b0100010,
			0b0000000,
			0b0100010,
			0b0000000,
			0b0100010,
			0b0000000
	}),

	/**
	 * A magnifier, for the search panel.
	 */
	SEARCH(7, new int[] {
			0b0111110,
			0b1100011,
			0b1100011,
			0b1100011,
			0b0111110,
			0b0000110,
			0b0000011
	}),

	/**
	 * A chevron pointing down, shown on a panel that is expanded.
	 */
	EXPANDED(7, new int[] {
			0b0000000,
			0b0011100,
			0b0111110,
			0b1101011,
			0b0000000,
			0b0000000,
			0b0000000
	}),

	/**
	 * A chevron pointing up, shown on a panel that is collapsed.
	 */
	COLLAPSED(7, new int[] {
			0b0000000,
			0b0000000,
			0b1101011,
			0b0111110,
			0b0011100,
			0b0000000,
			0b0000000
	});

	private final int width;
	private final int[] mask;

	GuiIcon(int width, int[] mask) {
		this.width = width;
		this.mask = mask;
	}

	/**
	 * @param category the category to draw an icon for
	 * @return the icon of that category
	 */
	public static GuiIcon forCategory(ModuleCategory category) {
		return switch (category) {
			case COMBAT -> COMBAT;
			case MOVEMENT -> MOVEMENT;
			case RENDER -> RENDER;
			case CLIENT -> CLIENT;
			case MISC -> MISC;
		};
	}

	/**
	 * Draws this glyph.
	 *
	 * @param context the draw context of the current frame
	 * @param x       the left edge, in scaled GUI pixels
	 * @param y       the top edge, in scaled GUI pixels
	 * @param color   the ARGB colour to draw with
	 */
	public void draw(DrawContext context, int x, int y, int color) {
		GuiShapes.drawMask(context, mask, width, GuiTheme.ICON_SIZE, x, y, color);
	}
}
