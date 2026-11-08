package com.birowo.client.platform.rotation;

/**
 * The way the aim is moved towards the target.
 *
 * <p>The three ways differ in how the movement of the camera looks, not in where the aim ends
 * up. All of them reach the same angles in the end.
 */
public enum AimMath {
	/**
	 * The aim moves towards the target at a fixed speed, in degrees per second. The movement is
	 * the same whether the game runs at thirty frames or at two hundred.
	 */
	REGULAR,

	/**
	 * The aim jumps straight onto the target. The movement is a single step, which is the reason
	 * this way is not the default.
	 */
	BLATANT,

	/**
	 * The aim is moved by a force that behaves like a hand on a mouse: it is pushed towards the
	 * target, overshoots a little and settles.
	 */
	WIND_MOUSE
}
