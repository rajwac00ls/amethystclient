package com.birowo.client.platform.rotation;

/**
 * The ways in which the rotation of the player can be applied.
 *
 * <p>The two ways differ in what the player sees. A visible rotation turns the camera, so the
 * player looks at the target. A silent rotation leaves the camera alone and only turns the head
 * of the player, which is what the server is told about in the movement packets.
 *
 * <p>In the protocol of the game the angle of the movement packet is the angle of the head: the
 * server reads it as the head of the player and keeps the body to itself. A silent rotation
 * therefore hands the server the angle of the head while the body of the player keeps facing
 * the way the mouse points.
 */
public enum RotationMode {
	/**
	 * The camera, the head and the body of the player all turn towards the aim.
	 */
	VISIBLE,

	/**
	 * The camera keeps the angle of the mouse; the head of the player turns towards the aim and
	 * the movement packets carry the angle of the aim.
	 */
	SILENT_HEAD
}
