package com.birowo.client.platform.rotation;

/**
 * The rotation the server is told about, which is not always the rotation the player sees.
 *
 * <p>A silent rotation is applied by two pieces of code that cannot see each other: the module
 * that aims at the target writes the rotation here, and the mixin that swaps the rotation into
 * the movement packets reads it. A static holder is the only thing the two can share, because a
 * mixin has no access to the objects of the mod.
 *
 * <p>The rotation is only meaningful while {@link #active()} is true. A module that does not aim
 * at anything clears it, so that the movement packets carry the rotation of the player again.
 *
 * <p>The fields are volatile: the module that aims writes them on the frame thread, while the
 * mixin reads them on the client thread, when the movement packets are sent.
 */
public final class ServerRotation {
	private static volatile float yaw;
	private static volatile float pitch;
	private static volatile boolean active;

	private ServerRotation() {
	}

	/**
	 * Sets the rotation the server is to be told about.
	 *
	 * @param yaw   the horizontal angle, in degrees
	 * @param pitch the vertical angle, in degrees
	 */
	public static void set(float yaw, float pitch) {
		ServerRotation.yaw = yaw;
		ServerRotation.pitch = pitch;
		ServerRotation.active = true;
	}

	/**
	 * Forgets the rotation, so that the server is told about the rotation of the player again.
	 */
	public static void clear() {
		active = false;
	}

	/**
	 * @return whether a rotation is currently being forced on the movement packets
	 */
	public static boolean active() {
		return active;
	}

	/**
	 * @return the horizontal angle the server is to be told about, in degrees
	 */
	public static float yaw() {
		return yaw;
	}

	/**
	 * @return the vertical angle the server is to be told about, in degrees
	 */
	public static float pitch() {
		return pitch;
	}
}
