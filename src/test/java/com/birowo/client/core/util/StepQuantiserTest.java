package com.birowo.client.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StepQuantiserTest {
	private static final float TOLERANCE = 0.0001F;

	@Test
	void roundsEveryMovementToTheGrid() {
		StepQuantiser quantiser = new StepQuantiser();

		for (int step = 0; step < 50; step++) {
			float movement = quantiser.quantise(1.4F, 1.0F);

			assertEquals(Math.round(movement), movement, TOLERANCE);
			assertTrue(Math.abs(movement - 1.4F) <= 1.0F + TOLERANCE);
		}
	}

	@Test
	void keepsWhatIsLeftOverForTheNextMovement() {
		StepQuantiser quantiser = new StepQuantiser();

		float first = quantiser.quantise(0.6F, 1.0F);
		float second = quantiser.quantise(0.6F, 1.0F);

		assertEquals(0.0F, first, TOLERANCE);
		assertEquals(1.0F, second, TOLERANCE);
	}

	@Test
	void addsUpToTheMovementThatWasAskedFor() {
		StepQuantiser quantiser = new StepQuantiser();
		float total = 0.0F;

		for (int step = 0; step < 100; step++) {
			total += quantiser.quantise(0.37F, 1.0F);
		}

		assertEquals(37.0F, total, TOLERANCE);
	}

	@Test
	void letsEveryMovementThroughWhenThereIsNoGrid() {
		StepQuantiser quantiser = new StepQuantiser();

		assertEquals(0.37F, quantiser.quantise(0.37F, 0.0F), TOLERANCE);
		assertEquals(0.37F, quantiser.quantise(0.37F, 0.0F), TOLERANCE);
	}

	@Test
	void forgetsTheLeftoverWhenItIsReset() {
		StepQuantiser quantiser = new StepQuantiser();

		quantiser.quantise(0.6F, 1.0F);
		quantiser.reset();

		assertEquals(0.0F, quantiser.quantise(0.6F, 1.0F), TOLERANCE);
	}

	@Test
	void neverMovesFurtherThanTheMovementItWasGiven() {
		StepQuantiser quantiser = new StepQuantiser();

		for (int step = 0; step < 100; step++) {
			float movement = quantiser.quantise(0.9F, 1.0F);

			assertTrue(Math.abs(movement) <= 1.0F + TOLERANCE);
		}
	}
}
