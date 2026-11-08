package com.birowo.client.core.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The panel of the interface is dragged with the mouse, and the position it is drawn at has to
 * follow the cursor without a jump and without a stutter.
 */
class PanelStateTest {
	@Test
	void aPanelThatWasMovedEasesTowardsItsNewPlace() {
		PanelState panel = new PanelState("test", 0, 0);

		panel.moveTo(100, 0);
		panel.update(1.0F / 60.0F);

		assertTrue(panel.x() > 0, "the panel did not move at all");
		assertTrue(panel.x() < 100, "the panel jumped to its new place instead of easing there");
	}

	@Test
	void aPanelArrivesAtItsNewPlaceAfterAWhile() {
		PanelState panel = new PanelState("test", 0, 0);

		panel.moveTo(100, 40);

		for (int frame = 0; frame < 200; frame++) {
			panel.update(1.0F / 60.0F);
		}

		assertEquals(100, panel.x());
		assertEquals(40, panel.y());
	}

	@Test
	void aPanelThatIsPickedUpWhileItMovesDoesNotJump() {
		PanelState panel = new PanelState("test", 0, 0);

		panel.moveTo(100, 0);

		for (int frame = 0; frame < 5; frame++) {
			panel.update(1.0F / 60.0F);
		}

		int drawn = panel.x();

		// The cursor grabs the panel where it is drawn, so the offset of the drag is the
		// distance between the cursor and the place the panel is drawn at.
		panel.moveTo(drawn + 30, 0);

		assertEquals(drawn, panel.x(), "the panel jumped when it was picked up");
	}

	@Test
	void aPanelThatIsSnappedIsThereAtOnce() {
		PanelState panel = new PanelState("test", 0, 0);

		panel.snapTo(50, 60);

		assertEquals(50, panel.x());
		assertEquals(60, panel.y());
		assertEquals(50, panel.restingX());
		assertEquals(60, panel.restingY());
		assertTrue(panel.placed());
	}

	@Test
	void aPanelThatIsSettledIsAtItsRestingPlace() {
		PanelState panel = new PanelState("test", 0, 0);

		panel.moveTo(70, 80);
		panel.settle();

		assertEquals(70, panel.x());
		assertEquals(80, panel.y());
	}

	@Test
	void aPanelIsCollapsedAndExpandedAgain() {
		PanelState panel = new PanelState("test", 0, 0);

		assertFalse(panel.collapsed());

		panel.toggleCollapsed();
		assertTrue(panel.collapsed());

		for (int frame = 0; frame < 200; frame++) {
			panel.update(1.0F / 60.0F);
		}

		assertEquals(1.0F, panel.collapseAmount(), 0.01F);

		panel.toggleCollapsed();

		for (int frame = 0; frame < 200; frame++) {
			panel.update(1.0F / 60.0F);
		}

		assertEquals(0.0F, panel.collapseAmount(), 0.01F);
	}

	@Test
	void aPanelThatIsScrolledNeverScrollsBackwards() {
		PanelState panel = new PanelState("test", 0, 0);

		panel.scrollTo(-50);

		assertEquals(0.0F, panel.scrollTarget(), 0.01F);

		panel.scrollTo(120);

		for (int frame = 0; frame < 200; frame++) {
			panel.update(1.0F / 60.0F);
		}

		assertEquals(120.0F, panel.scroll(), 0.5F);
	}
}
