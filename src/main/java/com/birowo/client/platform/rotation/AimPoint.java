package com.birowo.client.platform.rotation;

/**
 * The part of the target the aim is pointed at.
 */
public enum AimPoint {
	/**
	 * The head of the target, a tenth of a block below the top of its body.
	 */
	HEAD,

	/**
	 * The middle of the body of the target.
	 */
	BODY,

	/**
	 * The feet of the target, a tenth of a block above the bottom of its body.
	 */
	FEET,

	/**
	 * The point of the body of the target that lies closest to the eyes of the player.
	 */
	CLOSEST
}
