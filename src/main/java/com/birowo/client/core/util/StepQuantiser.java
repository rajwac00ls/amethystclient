package com.birowo.client.core.util;

/**
 * Rounds a movement to the steps of a grid.
 *
 * <p>The mouse of the game moves in steps whose size depends on the sensitivity of the player.
 * A movement that is rounded to the same grid can be produced by the mouse itself.
 *
 * <p>What is left over by one step is kept and added to the next one, so that the sum of many
 * small movements ends up on the grid as well and no movement is lost.
 */
public final class StepQuantiser {
	private float remainder;

	/**
	 * Rounds a movement to the grid.
	 *
	 * @param step  the movement, in degrees
	 * @param grid  the size of one step of the grid, in degrees; a grid of zero lets every
	 *              movement through
	 * @return the rounded movement, in degrees
	 */
	public float quantise(float step, float grid) {
		float accumulated = remainder + step;
		float snapped = grid <= 0.0F ? accumulated : (float) ((int) (accumulated / grid)) * grid;

		remainder = accumulated - snapped;

		return snapped;
	}

	/**
	 * Forgets what was left over by the last movement.
	 */
	public void reset() {
		remainder = 0.0F;
	}
}
