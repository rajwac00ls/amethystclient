package com.birowo.client.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindMouseTest {
	private static final float TOLERANCE = 0.0001F;

	/**
	 * The speed the aim is allowed to move with, in degrees per second.
	 */
	private static final float SPEED = 120.0F;

	/**
	 * The length of one frame, in seconds.
	 */
	private static final float FRAME = 1.0F / 60.0F;

	@Test
	void movesTowardsTheTarget() {
		WindMouse wind = new WindMouse();

		wind.move(0.0F, 0.0F, 45.0F, 0.0F, SPEED, SPEED, FRAME);

		assertTrue(wind.yaw() > 0.0F, "the aim did not move towards the target");
	}

	@Test
	void neverMovesFurtherThanTheSpeedAllows() {
		WindMouse wind = new WindMouse();
		float limit = SPEED * FRAME;

		for (int frame = 0; frame < 200; frame++) {
			float before = wind.yaw();

			wind.move(wind.yaw(), wind.pitch(), 90.0F, 30.0F, SPEED, SPEED, FRAME);

			assertTrue(Math.abs(wind.yaw() - before) <= limit + TOLERANCE,
					"the aim moved " + (wind.yaw() - before) + " degrees in one frame");
		}
	}

	@Test
	void comesToRestOnTheTarget() {
		WindMouse wind = new WindMouse();

		for (int frame = 0; frame < 600; frame++) {
			wind.move(wind.yaw(), wind.pitch(), 30.0F, 10.0F, SPEED, SPEED, FRAME);
		}

		assertEquals(30.0F, wind.yaw(), 1.0F);
		assertEquals(10.0F, wind.pitch(), 1.0F);
	}

	@Test
	void forgetsItsMovementWhenItIsReset() {
		WindMouse wind = new WindMouse();

		wind.move(0.0F, 0.0F, 45.0F, 0.0F, SPEED, SPEED, FRAME);
		wind.reset();

		assertEquals(0.0F, wind.yaw(), TOLERANCE);
		assertEquals(0.0F, wind.pitch(), TOLERANCE);
	}
}
