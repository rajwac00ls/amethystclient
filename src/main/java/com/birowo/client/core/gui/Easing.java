package com.birowo.client.core.gui;

/**
 * Easing functions used by the interface animations.
 *
 * <p>All functions take and return a normalized progress in {@code [0, 1]}.
 */
public final class Easing {
	private Easing() {
	}

	/**
	 * @param t the normalized progress
	 * @return the eased progress
	 */
	public static float cubicOut(float t) {
		float inverse = 1.0F - t;
		return 1.0F - inverse * inverse * inverse;
	}

	/**
	 * @param t the normalized progress
	 * @return the eased progress
	 */
	public static float quadOut(float t) {
		float inverse = 1.0F - t;
		return 1.0F - inverse * inverse;
	}

	/**
	 * @param t the normalized progress
	 * @return the eased progress
	 */
	public static float smoothStep(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * @param t the normalized progress
	 * @return the progress, clamped to {@code [0, 1]}
	 */
	public static float clamp01(float t) {
		if (t < 0.0F) {
			return 0.0F;
		}

		return Math.min(t, 1.0F);
	}

	/**
	 * @param from the value at progress zero
	 * @param to   the value at progress one
	 * @param t    the normalized progress
	 * @return the interpolated value
	 */
	public static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}
}
