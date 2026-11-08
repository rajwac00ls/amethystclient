package com.birowo.client.core.gui;

/**
 * The animated appearance of one module row.
 *
 * <p>These values outlive the interface itself: a toggle that is switched on keeps its colour
 * while the interface is closed and animates from where it was when the interface is opened again.
 */
public final class ModuleVisual {
	private static final float TOGGLE_RATE = 20.0F;
	private static final float HOVER_RATE = 22.0F;

	private final Tween toggle = new Tween(0.0F);
	private final Tween hover = new Tween(0.0F);

	/**
	 * @param enabled whether the module is currently enabled
	 */
	public void setEnabled(boolean enabled) {
		toggle.target(enabled ? 1.0F : 0.0F);
	}

	/**
	 * @param hovered whether the cursor is over the row
	 */
	public void setHovered(boolean hovered) {
		hover.target(hovered ? 1.0F : 0.0F);
	}

	/**
	 * Advances the animations.
	 *
	 * @param deltaSeconds the time since the previous update, in seconds
	 */
	public void update(float deltaSeconds) {
		toggle.update(deltaSeconds, TOGGLE_RATE);
		hover.update(deltaSeconds, HOVER_RATE);
	}

	/**
	 * @return the animated position of the knob of the toggle, where zero is off and one is on
	 */
	public float toggleAmount() {
		return Easing.clamp01(toggle.value());
	}

	/**
	 * @return the animated strength of the row highlight, between zero and one
	 */
	public float hoverAmount() {
		return Easing.clamp01(hover.value());
	}
}
