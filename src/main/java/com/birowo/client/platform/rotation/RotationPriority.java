package com.birowo.client.platform.rotation;

/**
 * The order in which the modules compete for the rotation of the player.
 *
 * <p>Only one of them can have it. The module that attacks wins over the module that only turns
 * the camera, because the aim of an attack has to be the one the hit is made with.
 */
public final class RotationPriority {
	/**
	 * The priority of the module that attacks.
	 */
	public static final int KILL_AURA = 30;

	/**
	 * The priority of the module that only turns the camera.
	 */
	public static final int AIM_ASSIST = 10;

	private RotationPriority() {
	}
}
