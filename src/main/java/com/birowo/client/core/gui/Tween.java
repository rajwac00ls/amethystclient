package com.birowo.client.core.gui;

/**
 * A single animated value that eases toward a target.
 *
 * <p>The animation is driven by the elapsed time rather than by the frame count, so it looks the
 * same at 30 and at 240 frames per second. The interpolation is exponential, which is stable for
 * any frame time and never overshoots.
 */
public final class Tween {
	/**
	 * The smoothing rate used when no explicit rate is given. Higher settles faster.
	 */
	public static final float DEFAULT_RATE = 18.0F;

	private float value;
	private float target;

	/**
	 * @param initial the initial value, used for both the current value and the target
	 */
	public Tween(float initial) {
		this.value = initial;
		this.target = initial;
	}

	/**
	 * Sets both the current value and the target, cancelling any animation in flight.
	 *
	 * @param value the new value
	 */
	public void set(float value) {
		this.value = value;
		this.target = value;
	}

	/**
	 * @param target the value to animate toward
	 */
	public void target(float target) {
		this.target = target;
	}

	/**
	 * @return the value the animation is heading toward
	 */
	public float target() {
		return target;
	}

	/**
	 * @return the current animated value
	 */
	public float value() {
		return value;
	}

	/**
	 * @return whether the animation has reached its target
	 */
	public boolean settled() {
		return Math.abs(target - value) < 0.001F;
	}

	/**
	 * Advances the animation.
	 *
	 * @param deltaSeconds the time since the previous update, in seconds
	 * @param rate         the smoothing rate; larger values settle faster
	 * @return the new value
	 */
	public float update(float deltaSeconds, float rate) {
		float factor = 1.0F - (float) Math.exp(-rate * deltaSeconds);
		value += (target - value) * factor;
		return value;
	}

	/**
	 * Advances the animation at the {@link #DEFAULT_RATE}.
	 *
	 * @param deltaSeconds the time since the previous update, in seconds
	 * @return the new value
	 */
	public float update(float deltaSeconds) {
		return update(deltaSeconds, DEFAULT_RATE);
	}
}
