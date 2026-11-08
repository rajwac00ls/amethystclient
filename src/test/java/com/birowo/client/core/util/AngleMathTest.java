package com.birowo.client.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The arithmetic of the aim is the part of the client that has to be right in every case, so it
 * is tested without a game.
 */
class AngleMathTest {
	private static final float TOLERANCE = 0.0001F;

	@Test
	void wrapsAnAngleIntoTheShortWayRound() {
		assertEquals(0.0F, AngleMath.wrapDegrees(360.0F), TOLERANCE);
		assertEquals(-10.0F, AngleMath.wrapDegrees(350.0F), TOLERANCE);
		assertEquals(10.0F, AngleMath.wrapDegrees(370.0F), TOLERANCE);
		assertEquals(-90.0F, AngleMath.wrapDegrees(-90.0F), TOLERANCE);
		assertEquals(180.0F, AngleMath.wrapDegrees(180.0F), TOLERANCE);
	}

	@Test
	void measuresTheDistanceTheShortWayRound() {
		assertEquals(20.0F, AngleMath.distance(350.0F, 10.0F), TOLERANCE);
		assertEquals(90.0F, AngleMath.distance(0.0F, 90.0F), TOLERANCE);
		assertEquals(0.0F, AngleMath.distance(45.0F, 45.0F), TOLERANCE);
	}

	@Test
	void measuresTheAngleBetweenTwoDirections() {
		assertEquals(0.0D, AngleMath.angleBetween(30.0F, 10.0F, 30.0F, 10.0F), TOLERANCE);
		assertEquals(90.0D, AngleMath.angleBetween(0.0F, 0.0F, 90.0F, 0.0F), TOLERANCE);
		assertEquals(20.0D, AngleMath.angleBetween(350.0F, 0.0F, 10.0F, 0.0F), TOLERANCE);
		assertEquals(5.0D, AngleMath.angleBetween(0.0F, 0.0F, 3.0F, 4.0F), TOLERANCE);
	}

	@Test
	void movesTowardsTheTargetWithoutGoingPastIt() {
		assertEquals(15.0F, AngleMath.towards(0.0F, 15.0F, 30.0F), TOLERANCE);
		assertEquals(30.0F, AngleMath.towards(0.0F, 90.0F, 30.0F), TOLERANCE);
		assertEquals(-30.0F, AngleMath.towards(0.0F, -90.0F, 30.0F), TOLERANCE);
	}

	@Test
	void movesTheShortWayRound() {
		// From three hundred and fifty to ten is twenty degrees forwards, and from ten to three
		// hundred and fifty is twenty degrees backwards.
		assertEquals(20.0F, AngleMath.distance(350.0F, AngleMath.towards(350.0F, 10.0F, 30.0F)), TOLERANCE);
		assertEquals(20.0F, AngleMath.distance(10.0F, AngleMath.towards(10.0F, 350.0F, 30.0F)), TOLERANCE);
	}

	@Test
	void movesTheHeadTowardsTheTargetWithoutWrapping() {
		assertEquals(-30.0F, AngleMath.towardsDirect(0.0F, -90.0F, 30.0F), TOLERANCE);
		assertEquals(30.0F, AngleMath.towardsDirect(0.0F, 90.0F, 30.0F), TOLERANCE);
		assertEquals(90.0F, AngleMath.towardsDirect(0.0F, 90.0F, 200.0F), TOLERANCE);
	}

	@Test
	void neverMovesFurtherThanTheStep() {
		for (float current = -180.0F; current <= 180.0F; current += 17.0F) {
			for (float target = -180.0F; target <= 180.0F; target += 23.0F) {
				float moved = AngleMath.towards(current, target, 5.0F);

				assertTrue(Math.abs(moved - current) <= 5.0F + TOLERANCE,
						"the movement from " + current + " to " + target + " was " + (moved - current));
			}
		}
	}
}
