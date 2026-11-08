package com.birowo.client.platform.rotation;

import java.util.Random;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Keeps the point the aim is pointed at from standing still on one spot of the target.
 *
 * <p>A point that never moves is a point the aim can be read off: the angle it holds is the
 * angle of the middle of the target, every time. The tracker therefore moves the point slowly
 * over the body of the target, and the place it moves to is picked again from time to time, so
 * that the aim does not travel along a fixed path either.
 *
 * <p>The movement stays inside the body. The band the point moves in is derived from how wide the
 * target looks from where the player is standing, so that the point never leaves the target and
 * never stands still on one spot of it.
 *
 * <p>The tracker belongs to one target at a time. Pointing it at another target throws the
 * movement away and starts a new one.
 */
public final class AimPointTracker {
	/**
	 * The widest the band may be when the target is close, in degrees.
	 */
	private static final double BAND_FLOOR_DEGREES = 2.2D;

	/**
	 * The share of the half width of the target the band starts at.
	 */
	private static final double BAND_LOW_FRACTION = 0.55D;

	/**
	 * The share of the half width of the target the band ends at.
	 */
	private static final double BAND_HIGH_FRACTION = 0.85D;

	/**
	 * How far the point may move in one tick while the hit is not about to land, in degrees.
	 */
	private static final double FREE_MAX_STEP_DEGREES = 30.0D;

	/**
	 * How far the point may move in one tick while the hit is about to land, in degrees. The
	 * point has to settle before the hit lands, so it is allowed to move less far then.
	 */
	private static final double WINDOW_MAX_STEP_DEGREES = 4.0D;

	/**
	 * The shortest time the point stays on one side of the target, in ticks.
	 */
	private static final int SIDE_FLIP_TICKS_MIN = 2;

	/**
	 * How much longer the point may stay on one side of the target, in ticks.
	 */
	private static final int SIDE_FLIP_TICKS_SPAN = 3;

	/**
	 * The smallest offset of the point above or below the middle of the target, in degrees.
	 */
	private static final double VERTICAL_OFFSET_MIN = 0.5D;

	/**
	 * How much larger the offset of the point may be, in degrees.
	 */
	private static final double VERTICAL_OFFSET_SPAN = 1.5D;

	/**
	 * The longest the offset of the point stays where it is, in ticks.
	 */
	private static final int VERTICAL_HOLD_TICKS_MIN = 50;

	/**
	 * How much longer the offset of the point may stay where it is, in ticks.
	 */
	private static final int VERTICAL_HOLD_TICKS_SPAN = 50;

	/**
	 * How far the offset of the point may move in one tick, in degrees.
	 */
	private static final double VERTICAL_MAX_STEP_DEGREES = 0.5D;

	/**
	 * How far the edge of the body is kept from the middle of it, in blocks.
	 */
	private static final double INSET_BLOCKS = 0.04D;

	/**
	 * The shortest distance the point may be measured at, in blocks. A target that is standing on
	 * the player has no distance at all, and the angle would be undefined.
	 */
	private static final double MIN_DISTANCE = 0.3D;

	private final Random random = new Random();

	private int targetId = Integer.MIN_VALUE;
	private int side = 1;
	private int sideFlipTicks;
	private double errorDegrees = Double.NaN;
	private double verticalOffsetDegrees;
	private double verticalTargetDegrees;
	private int verticalHoldTicks;

	/**
	 * Moves the point the aim is pointed at.
	 *
	 * @param targetId       the identifier of the entity that is aimed at
	 * @param base           the point the aim would be pointed at without the tracker, in world
	 *                       coordinates
	 * @param box            the body of the target
	 * @param eyes           where the player looks from, in world coordinates
	 * @param inAttackWindow whether the hit is about to land, which slows the point down
	 * @return the point the aim is pointed at, in world coordinates
	 */
	public Vec3d advance(int targetId, Vec3d base, Box box, Vec3d eyes, boolean inAttackWindow) {
		if (this.targetId != targetId) {
			reset(targetId);
		}

		tickVertical();

		double dx = base.x - eyes.x;
		double dz = base.z - eyes.z;
		double distance = Math.max(Math.sqrt(dx * dx + dz * dz), MIN_DISTANCE);

		double lateral = distance * Math.tan(Math.toRadians(error(box, eyes, inAttackWindow)));
		double vertical = distance * Math.tan(Math.toRadians(verticalOffsetDegrees));

		double perpendicularX = -dz / distance;
		double perpendicularZ = dx / distance;

		return new Vec3d(base.x + perpendicularX * lateral, base.y + vertical, base.z + perpendicularZ * lateral);
	}

	/**
	 * Forgets the movement, so that the next target starts from nothing.
	 */
	public void clear() {
		targetId = Integer.MIN_VALUE;
		errorDegrees = Double.NaN;
	}

	/**
	 * Starts the movement of a new target.
	 *
	 * @param targetId the identifier of the entity that is aimed at
	 */
	private void reset(int targetId) {
		this.targetId = targetId;
		side = random.nextBoolean() ? 1 : -1;
		sideFlipTicks = rollSideFlipTicks();
		errorDegrees = Double.NaN;
		verticalOffsetDegrees = rollVerticalOffset();
		verticalTargetDegrees = verticalOffsetDegrees;
		verticalHoldTicks = rollVerticalHoldTicks();
	}

	/**
	 * Moves the offset of the point above and below the middle of the target on.
	 */
	private void tickVertical() {
		if (--verticalHoldTicks <= 0) {
			verticalTargetDegrees = Math.copySign(rollVerticalOffset(), verticalOffsetDegrees);
			verticalHoldTicks = rollVerticalHoldTicks();
		}

		double difference = verticalTargetDegrees - verticalOffsetDegrees;

		if (Math.abs(difference) > VERTICAL_MAX_STEP_DEGREES) {
			verticalOffsetDegrees += Math.copySign(VERTICAL_MAX_STEP_DEGREES, difference);
		} else {
			verticalOffsetDegrees = verticalTargetDegrees;
		}
	}

	/**
	 * The angle the point is off the middle of the target by, in degrees.
	 *
	 * @param box            the body of the target
	 * @param eyes           where the player looks from, in world coordinates
	 * @param inAttackWindow whether the hit is about to land
	 * @return the signed angle, in degrees
	 */
	private double error(Box box, Vec3d eyes, boolean inAttackWindow) {
		double halfWidthAngle = halfWidthAngle(box, eyes);

		if (Double.isNaN(errorDegrees)) {
			errorDegrees = side * rollBand(halfWidthAngle);
			return errorDegrees;
		}

		if (--sideFlipTicks <= 0) {
			side = -side;
			sideFlipTicks = rollSideFlipTicks();
		}

		double goal = side * rollBand(halfWidthAngle);
		double step = inAttackWindow ? WINDOW_MAX_STEP_DEGREES : FREE_MAX_STEP_DEGREES;
		double difference = goal - errorDegrees;

		errorDegrees = Math.abs(difference) > step
				? errorDegrees + Math.copySign(step, difference)
				: goal;

		double low = bandLow(halfWidthAngle);

		if (Math.abs(errorDegrees) < low) {
			errorDegrees = Math.copySign(low, errorDegrees == 0.0D ? side : errorDegrees);
		}

		return errorDegrees;
	}

	/**
	 * @param box  the body of the target
	 * @param eyes where the player looks from, in world coordinates
	 * @return the angle the half width of the target covers from where the player is standing, in
	 *         degrees
	 */
	private static double halfWidthAngle(Box box, Vec3d eyes) {
		Vec3d centre = box.getCenter();
		double dx = centre.x - eyes.x;
		double dz = centre.z - eyes.z;
		double distance = Math.max(Math.sqrt(dx * dx + dz * dz), MIN_DISTANCE);

		double sizeX = box.maxX - box.minX;
		double sizeZ = box.maxZ - box.minZ;
		double halfWidth = Math.min(sizeX, sizeZ) * 0.5D;

		return Math.toDegrees(Math.atan(Math.max(halfWidth - INSET_BLOCKS, 0.01D) / distance));
	}

	/**
	 * @param halfWidthAngle the angle the half width of the target covers, in degrees
	 * @return the smallest angle the point may be off the middle of the target by, in degrees
	 */
	private static double bandLow(double halfWidthAngle) {
		return Math.min(BAND_FLOOR_DEGREES, BAND_LOW_FRACTION * halfWidthAngle);
	}

	/**
	 * @param halfWidthAngle the angle the half width of the target covers, in degrees
	 * @return the largest angle the point may be off the middle of the target by, in degrees
	 */
	private static double bandHigh(double halfWidthAngle) {
		return BAND_HIGH_FRACTION * halfWidthAngle;
	}

	/**
	 * @param halfWidthAngle the angle the half width of the target covers, in degrees
	 * @return an angle inside the band, in degrees
	 */
	private double rollBand(double halfWidthAngle) {
		double low = bandLow(halfWidthAngle);
		double high = bandHigh(halfWidthAngle);

		return low + random.nextDouble() * (high - low);
	}

	/**
	 * @return the number of ticks the point stays on one side of the target
	 */
	private int rollSideFlipTicks() {
		return SIDE_FLIP_TICKS_MIN + random.nextInt(SIDE_FLIP_TICKS_SPAN + 1);
	}

	/**
	 * @return the number of ticks the offset of the point stays where it is
	 */
	private int rollVerticalHoldTicks() {
		return VERTICAL_HOLD_TICKS_MIN + random.nextInt(VERTICAL_HOLD_TICKS_SPAN + 1);
	}

	/**
	 * @return the offset of the point above or below the middle of the target, in degrees
	 */
	private double rollVerticalOffset() {
		double magnitude = VERTICAL_OFFSET_MIN + random.nextDouble() * VERTICAL_OFFSET_SPAN;

		return random.nextBoolean() ? magnitude : -magnitude;
	}
}
