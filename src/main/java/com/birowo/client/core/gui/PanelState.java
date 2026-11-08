package com.birowo.client.core.gui;

/**
 * The state of one panel of the interface.
 *
 * <p>A panel owns everything that must survive the interface being closed and reopened: where it
 * sits on the screen, whether it is collapsed and how far it is scrolled. The animation values are
 * kept here as well so that a panel that is reopened animates from a known state instead of
 * popping into place.
 *
 * <p>The position is kept twice: as the place the panel is heading towards and as the place it
 * is drawn at. The drawn position eases towards the target, which is what makes a dragged panel
 * trail the cursor instead of teleporting with it. The target is the position that is written to
 * the configuration file, so that the panel rests where it was let go.
 */
public final class PanelState {
	/**
	 * The rate at which the open animation settles.
	 */
	private static final float OPEN_RATE = 14.0F;

	/**
	 * The rate at which the collapse animation settles.
	 */
	private static final float COLLAPSE_RATE = 16.0F;

	/**
	 * The rate at which the scroll position settles.
	 */
	private static final float SCROLL_RATE = 20.0F;

	/**
	 * The rate at which the panel moves towards the position it is heading for. The rate is high
	 * enough that the panel keeps up with the cursor but low enough that it does not snap to it.
	 */
	private static final float MOVE_RATE = 26.0F;

	private final String id;
	private final Tween open = new Tween(0.0F);
	private final Tween collapse = new Tween(0.0F);

	private float x;
	private float y;
	private float targetX;
	private float targetY;
	private boolean collapsed;
	private boolean placed;
	private float scroll;
	private float scrollTarget;

	/**
	 * @param id the stable identifier of the panel, used to persist its position
	 * @param x  the initial horizontal position, in scaled GUI pixels
	 * @param y  the initial vertical position, in scaled GUI pixels
	 */
	public PanelState(String id, int x, int y) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.targetX = x;
		this.targetY = y;
	}

	/**
	 * @return the stable identifier of this panel
	 */
	public String id() {
		return id;
	}

	/**
	 * @return the horizontal position this panel is drawn at, in scaled GUI pixels
	 */
	public int x() {
		return Math.round(x);
	}

	/**
	 * @return the vertical position this panel is drawn at, in scaled GUI pixels
	 */
	public int y() {
		return Math.round(y);
	}

	/**
	 * @return the horizontal position this panel rests at, in scaled GUI pixels
	 */
	public int restingX() {
		return Math.round(targetX);
	}

	/**
	 * @return the vertical position this panel rests at, in scaled GUI pixels
	 */
	public int restingY() {
		return Math.round(targetY);
	}

	/**
	 * Moves this panel towards the given position.
	 *
	 * <p>The panel does not arrive there at once: it eases towards the position over the next
	 * frames, which is what keeps a dragged panel from stuttering along with the mouse.
	 *
	 * @param x the new horizontal position, in scaled GUI pixels
	 * @param y the new vertical position, in scaled GUI pixels
	 */
	public void moveTo(int x, int y) {
		this.targetX = x;
		this.targetY = y;
		this.placed = true;
	}

	/**
	 * Puts this panel at the given position without moving it there.
	 *
	 * @param x the new horizontal position, in scaled GUI pixels
	 * @param y the new vertical position, in scaled GUI pixels
	 */
	public void snapTo(int x, int y) {
		this.x = x;
		this.y = y;
		this.targetX = x;
		this.targetY = y;
		this.placed = true;
	}

	/**
	 * Puts this panel at the position it is heading for, ending the movement.
	 */
	public void settle() {
		this.x = targetX;
		this.y = targetY;
	}

	/**
	 * @return whether this panel already has a position of its own
	 */
	public boolean placed() {
		return placed;
	}

	/**
	 * @return whether this panel is collapsed to its header
	 */
	public boolean collapsed() {
		return collapsed;
	}

	/**
	 * Collapses or expands this panel. The height animates towards the new state.
	 *
	 * @param collapsed whether the panel should be collapsed
	 */
	public void setCollapsed(boolean collapsed) {
		this.collapsed = collapsed;
		collapse.target(collapsed ? 1.0F : 0.0F);
	}

	/**
	 * Flips the collapsed state of this panel.
	 */
	public void toggleCollapsed() {
		setCollapsed(!collapsed);
	}

	/**
	 * @return the scroll offset of this panel, in scaled GUI pixels
	 */
	public float scroll() {
		return scroll;
	}

	/**
	 * Requests a scroll offset.
	 *
	 * @param offset the requested offset, in scaled GUI pixels
	 */
	public void scrollTo(float offset) {
		this.scrollTarget = Math.max(0.0F, offset);
	}

	/**
	 * @return the scroll offset this panel is heading towards, in scaled GUI pixels
	 */
	public float scrollTarget() {
		return scrollTarget;
	}

	/**
	 * @return the progress of the open animation, where one is fully open and zero is invisible
	 */
	public float openAmount() {
		return open.value();
	}

	/**
	 * Starts the open animation.
	 */
	public void open() {
		open.target(1.0F);
	}

	/**
	 * Restarts the open animation from the beginning, so that the panel is seen to appear every
	 * time the interface is opened.
	 */
	public void restartOpen() {
		open.set(0.0F);
		open.target(1.0F);
	}

	/**
	 * Starts the close animation.
	 */
	public void close() {
		open.target(0.0F);
	}

	/**
	 * @return whether the open animation has finished
	 */
	public boolean openSettled() {
		return open.settled();
	}

	/**
	 * Advances every animation of this panel.
	 *
	 * @param deltaSeconds the time since the previous update, in seconds
	 */
	public void update(float deltaSeconds) {
		open.update(deltaSeconds, OPEN_RATE);
		collapse.update(deltaSeconds, COLLAPSE_RATE);
		scroll += (scrollTarget - scroll) * (1.0F - (float) Math.exp(-SCROLL_RATE * deltaSeconds));

		float factor = 1.0F - (float) Math.exp(-MOVE_RATE * deltaSeconds);
		x += (targetX - x) * factor;
		y += (targetY - y) * factor;
	}

	/**
	 * @return the progress of the collapse animation, where zero is expanded and one is collapsed
	 */
	public float collapseAmount() {
		return collapse.value();
	}
}
