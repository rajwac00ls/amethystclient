package com.birowo.client.core.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The movement of a hand on a mouse.
 *
 * <p>The aim is pushed towards the target by a force that grows with the distance to it, and the
 * force is damped when the target is close. The result is a movement that overshoots a little
 * and settles, instead of the even slide of a fixed speed.
 *
 * <p>The class keeps the state of the movement between two frames, so that the force has
 * something to build on.
 */
public final class WindMouse {
	/**
	 * The distance in degrees within which the wind settles, instead of pushing.
	 */
	private static final double SETTLE_DISTANCE = 8.0D;

	/**
	 * The distance in degrees within which the wind is damped.
	 */
	private static final double DAMPING_DISTANCE = 0.5D;

	/**
	 * The distance in degrees within which the wind is stopped altogether.
	 */
	private static final double STOP_DISTANCE = 0.1D;

	private double windYaw;
	private double windPitch;
	private double yawVelocity;
	private double pitchVelocity;

	private float yaw;
	private float pitch;

	/**
	 * Moves the aim one step towards the target.
	 *
	 * @param currentYaw   the horizontal angle the aim is at, in degrees
	 * @param currentPitch the vertical angle the aim is at, in degrees
	 * @param targetYaw    the horizontal angle of the target, in degrees
	 * @param targetPitch  the vertical angle of the target, in degrees
	 * @param yawSpeed     how fast the aim may turn around the vertical axis, in degrees per second
	 * @param pitchSpeed   how fast the aim may turn around the horizontal axis, in degrees per second
	 * @param delta        the time since the previous step, in seconds
	 */
	public void move(float currentYaw, float currentPitch, float targetYaw, float targetPitch,
			float yawSpeed, float pitchSpeed, float delta) {
		double yawStep = yawSpeed * delta;
		double pitchStep = pitchSpeed * delta;
		double baseStep = Math.max(yawStep, pitchStep);

		double gravityMax = baseStep * 0.6D;
		double windMax = baseStep * 0.2D;

		double yawDifference = AngleMath.wrapDegrees(targetYaw - currentYaw);
		double pitchDifference = targetPitch - currentPitch;
		double distance = Math.hypot(yawDifference, pitchDifference);

		if (distance >= SETTLE_DISTANCE) {
			windYaw = windYaw / Math.sqrt(3.0D)
					+ (ThreadLocalRandom.current().nextDouble() * 2.0D - 1.0D) * Math.min(windMax, distance)
							/ Math.sqrt(5.0D);
			windPitch = windPitch / Math.sqrt(3.0D)
					+ (ThreadLocalRandom.current().nextDouble() * 2.0D - 1.0D) * Math.min(windMax, distance)
							/ Math.sqrt(5.0D);
		} else {
			windYaw /= Math.sqrt(3.0D);
			windPitch /= Math.sqrt(3.0D);
		}

		double yawLimit = yawStep;
		double pitchLimit = pitchStep;

		if (distance < SETTLE_DISTANCE) {
			yawLimit /= Math.sqrt(5.0D);
			pitchLimit /= Math.sqrt(5.0D);
		}

		double gravity = Math.min(gravityMax, distance);

		yawVelocity += windYaw + (distance == 0.0D ? 0.0D : gravity * yawDifference / distance);
		pitchVelocity += windPitch + (distance == 0.0D ? 0.0D : gravity * pitchDifference / distance);

		if (distance < DAMPING_DISTANCE) {
			yawVelocity *= 0.7D;
			pitchVelocity *= 0.7D;
		}

		if (distance < STOP_DISTANCE) {
			yawVelocity = 0.0D;
			pitchVelocity = 0.0D;
		}

		yawVelocity = Math.max(-yawLimit, Math.min(yawVelocity, yawLimit));
		pitchVelocity = Math.max(-pitchLimit, Math.min(pitchVelocity, pitchLimit));

		this.yaw = (float) (currentYaw + yawVelocity);
		this.pitch = (float) (currentPitch + pitchVelocity);
	}

	/**
	 * @return the horizontal angle the aim ended up at, in degrees
	 */
	public float yaw() {
		return yaw;
	}

	/**
	 * @return the vertical angle the aim ended up at, in degrees
	 */
	public float pitch() {
		return pitch;
	}

	/**
	 * Forgets the movement of the wind, so that the next movement starts from nothing.
	 */
	public void reset() {
		windYaw = 0.0D;
		windPitch = 0.0D;
		yawVelocity = 0.0D;
		pitchVelocity = 0.0D;
		yaw = 0.0F;
		pitch = 0.0F;
	}
}
