package com.birowo.client.core.gui;

/**
 * Colour arithmetic for the interface.
 */
public final class GuiColors {
	private GuiColors() {
	}

	/**
	 * Scales the opacity of a colour.
	 *
	 * @param color  the ARGB colour
	 * @param factor the factor to scale the opacity by, between zero and one
	 * @return the colour with the scaled opacity
	 */
	public static int withAlpha(int color, float factor) {
		int alpha = (int) (((color >>> 24) & 0xFF) * Easing.clamp01(factor));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	/**
	 * Interpolates between two colours.
	 *
	 * @param from the colour at progress zero
	 * @param to   the colour at progress one
	 * @param t    the progress, between zero and one
	 * @return the interpolated colour
	 */
	public static int lerp(int from, int to, float t) {
		float progress = Easing.clamp01(t);

		int alpha = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * progress);
		int red = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * progress);
		int green = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * progress);
		int blue = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);

		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}
}
