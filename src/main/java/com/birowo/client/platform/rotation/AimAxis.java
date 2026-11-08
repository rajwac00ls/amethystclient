package com.birowo.client.platform.rotation;

/**
 * The axes the aim is allowed to move on.
 *
 * <p>An aim that is locked to one axis still turns the head of the player as a whole, but only
 * the angle it is allowed to move on is written. The other one keeps the value it had.
 */
public enum AimAxis {
	/**
	 * The aim moves on both axes.
	 */
	BOTH,

	/**
	 * The aim only turns around the vertical axis; the height of the camera stays where it is.
	 */
	HORIZONTAL,

	/**
	 * The aim only turns around the horizontal axis; the direction of the camera stays where it
	 * is.
	 */
	VERTICAL
}
