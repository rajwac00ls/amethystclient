package com.birowo.client.platform.rotation;

/**
 * A claim on the rotation of the player.
 *
 * <p>Several modules may want to aim at the same time. Each of them owns a request, and the
 * engine gives the rotation to the request with the highest priority; the others wait their
 * turn. A module without an aim does not take part in the contest.
 *
 * <p>The request is written by the module on the client thread and read by the engine on the
 * render thread, so the aim it holds is swapped as a whole.
 */
public final class RotationRequest {
	private final int priority;
	private volatile RotationAim aim;

	/**
	 * @param priority how strongly the request competes for the rotation; the higher the number,
	 *                 the sooner the request wins
	 */
	public RotationRequest(int priority) {
		this.priority = priority;
	}

	/**
	 * @return how strongly the request competes for the rotation
	 */
	public int priority() {
		return priority;
	}

	/**
	 * Sets what the request wants to aim at.
	 *
	 * @param aim what to aim at, or {@code null} when the request wants no rotation at all
	 */
	public void setAim(RotationAim aim) {
		this.aim = aim;
	}

	/**
	 * @return what the request wants to aim at, or {@code null} when it wants no rotation
	 */
	public RotationAim aim() {
		return aim;
	}
}
