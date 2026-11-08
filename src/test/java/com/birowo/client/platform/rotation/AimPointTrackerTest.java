package com.birowo.client.platform.rotation;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The point the aim is pointed at has to stay on the body of the target, and it has to move.
 */
class AimPointTrackerTest {
	/**
	 * The body of the target, in world coordinates.
	 */
	private static final Box BODY = new Box(-0.3D, 64.0D, -0.3D, 0.3D, 65.8D, 0.3D);

	/**
	 * Where the player looks from, in world coordinates.
	 */
	private static final Vec3d EYES = new Vec3d(0.0D, 65.0D, -3.0D);

	/**
	 * The identifier of the target that is aimed at.
	 */
	private static final int TARGET = 7;

	@Test
	void keepsThePointOnTheBodyOfTheTarget() {
		AimPointTracker tracker = new AimPointTracker();
		Vec3d centre = BODY.getCenter();

		for (int tick = 0; tick < 500; tick++) {
			Vec3d point = tracker.advance(TARGET, centre, BODY, EYES, false);

			assertTrue(point.x >= BODY.minX && point.x <= BODY.maxX, "the point left the body sideways");
			assertTrue(point.y >= BODY.minY && point.y <= BODY.maxY, "the point left the body upwards");
			assertTrue(point.z >= BODY.minZ && point.z <= BODY.maxZ, "the point left the body backwards");
		}
	}

	@Test
	void movesThePointOverTheBody() {
		AimPointTracker tracker = new AimPointTracker();
		Vec3d centre = BODY.getCenter();
		Vec3d first = tracker.advance(TARGET, centre, BODY, EYES, false);
		Vec3d last = first;

		for (int tick = 0; tick < 200; tick++) {
			last = tracker.advance(TARGET, centre, BODY, EYES, false);
		}

		assertNotEquals(first, last, "the point stood still on the same spot of the body");
	}

	@Test
	void startsTheNewTargetInsideTheBandOfTheBody() {
		AimPointTracker tracker = new AimPointTracker();
		Vec3d centre = BODY.getCenter();

		for (int tick = 0; tick < 50; tick++) {
			tracker.advance(TARGET, centre, BODY, EYES, false);
		}

		Vec3d point = tracker.advance(TARGET + 1, centre, BODY, EYES, false);
		double distance = Math.hypot(point.x - centre.x, point.z - centre.z);

		// The band ends at the point that is four fifths of the way to the edge of the body.
		double halfWidthAngle = Math.toDegrees(Math.atan((0.3D - 0.04D) / 3.0D));
		double edge = 3.0D * Math.tan(Math.toRadians(0.85D * halfWidthAngle));

		assertTrue(distance <= edge + 1.0E-6D, "the point started outside the band of the new target");
	}
}
