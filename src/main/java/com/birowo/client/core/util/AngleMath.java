package com.birowo.client.core.util;

/**
 * The arithmetic of angles, in degrees.
 *
 * <p>The class has no dependency on the game, so that the movement of the aim can be reasoned
 * about — and tested — without a running client.
 */
public final class AngleMath {
	private AngleMath() {
	}

	/**
	 * Brings an angle into the range from minus one hundred and eighty degrees up to one hundred
	 * and eighty degrees.
	 *
	 * <p>Both ends of the range are kept: an angle of one hundred and eighty degrees stays where
	 * it is instead of turning into minus one hundred and eighty degrees. The game wraps it the
	 * other way round; the difference only shows up at exactly half a turn, where both ways round
	 * are the same distance anyway.
	 *
	 * @param degrees the angle, in degrees
	 * @return the same angle, measured the short way round
	 */
	public static float wrapDegrees(float degrees) {
		float wrapped = degrees % 360.0F;

		if (wrapped > 180.0F) {
			wrapped -= 360.0F;
		}

		if (wrapped < -180.0F) {
			wrapped += 360.0F;
		}

		return wrapped;
	}

	/**
	 * Moves an angle towards another one, never further than the given step.
	 *
	 * <p>The difference is measured the short way round, so that an angle of three hundred and
	 * fifty degrees is reached from ten degrees by turning backwards, not forwards.
	 *
	 * @param current the angle the movement starts at, in degrees
	 * @param target  the angle the movement goes to, in degrees
	 * @param maxStep the longest distance the movement may cover, in degrees
	 * @return the angle the movement ends at, in degrees
	 */
	public static float towards(float current, float target, float maxStep) {
		float difference = wrapDegrees(target - current);

		if (Math.abs(difference) <= maxStep) {
			return current + difference;
		}

		return current + Math.signum(difference) * maxStep;
	}

	/**
	 * Moves an angle towards another one, never further than the given step, taking the
	 * difference as it is.
	 *
	 * <p>This is the movement for an angle that cannot run over the end of its range, such as
	 * the angle of the head, which never leaves the range from minus ninety to ninety degrees.
	 *
	 * @param current the angle the movement starts at, in degrees
	 * @param target  the angle the movement goes to, in degrees
	 * @param maxStep the longest distance the movement may cover, in degrees
	 * @return the angle the movement ends at, in degrees
	 */
	public static float towardsDirect(float current, float target, float maxStep) {
		float difference = target - current;

		if (Math.abs(difference) <= maxStep) {
			return target;
		}

		return current + Math.signum(difference) * maxStep;
	}

	/**
	 * @param first  the first angle, in degrees
	 * @param second the second angle, in degrees
	 * @return the distance between the two angles, in degrees, never more than one hundred and
	 *         eighty
	 */
	public static float distance(float first, float second) {
		return Math.abs(wrapDegrees(second - first));
	}

	/**
	 * The angle between two directions, each of which is given by a pair of angles.
	 *
	 * <p>The horizontal difference is measured the short way round, the vertical one is taken as
	 * it is, because the height of the camera never leaves its own range.
	 *
	 * @param yaw        the horizontal angle of the first direction, in degrees
	 * @param pitch      the vertical angle of the first direction, in degrees
	 * @param targetYaw  the horizontal angle of the second direction, in degrees
	 * @param targetPitch the vertical angle of the second direction, in degrees
	 * @return the angle between the two directions, in degrees
	 */
	public static double angleBetween(float yaw, float pitch, float targetYaw, float targetPitch) {
		double yawDifference = wrapDegrees(targetYaw - yaw);
		double pitchDifference = targetPitch - pitch;

		return Math.sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference);
	}
}
