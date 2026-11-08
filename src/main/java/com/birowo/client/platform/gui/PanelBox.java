package com.birowo.client.platform.gui;

import java.util.ArrayList;
import java.util.List;

import com.birowo.client.core.gui.Easing;
import com.birowo.client.core.gui.GuiTheme;
import com.birowo.client.core.gui.PanelState;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;

/**
 * The geometry of one panel, together with the state it is drawn from.
 *
 * <p>The geometry is recomputed every frame, because the height of a panel depends on how many
 * modules it holds and on how far it is collapsed. The offsets of the content are mapped through
 * the scale of the panel, so that the panel grows out of its own shape while it opens.
 */
public final class PanelBox {
	private final PanelState state;
	private final ModuleCategory category;
	private final List<Module> modules = new ArrayList<>();

	private int x;
	private int y;
	private int width;
	private int height;
	private int contentHeight;
	private int visibleHeight;
	private int bodyHeight;
	private float scale = 1.0F;
	private float open;

	/**
	 * @param state    the state of the panel, which owns its position and its animations
	 * @param category the category the panel lists, or {@code null} for the search panel
	 */
	public PanelBox(PanelState state, ModuleCategory category) {
		this.state = state;
		this.category = category;
	}

	/**
	 * @return the state of this panel
	 */
	public PanelState state() {
		return state;
	}

	/**
	 * @return the category this panel lists, or {@code null} for the search panel
	 */
	public ModuleCategory category() {
		return category;
	}

	/**
	 * @return the modules listed by this panel
	 */
	public List<Module> modules() {
		return modules;
	}

	/**
	 * @return the height of the content of this panel before it is cut short, in scaled GUI pixels
	 */
	public int contentHeight() {
		return contentHeight;
	}

	/**
	 * @return the height of the body that is actually shown, in scaled GUI pixels
	 */
	public int visibleHeight() {
		return visibleHeight;
	}

	/**
	 * @return the animated height of the body, in scaled GUI pixels
	 */
	public int bodyHeight() {
		return bodyHeight;
	}

	/**
	 * @return the horizontal position of the frame of this panel, in scaled GUI pixels
	 */
	public int x() {
		return x;
	}

	/**
	 * @return the vertical position of the frame of this panel, in scaled GUI pixels
	 */
	public int y() {
		return y;
	}

	/**
	 * @return the width of the frame of this panel, in scaled GUI pixels
	 */
	public int width() {
		return width;
	}

	/**
	 * @return the height of the frame of this panel, in scaled GUI pixels
	 */
	public int height() {
		return height;
	}

	/**
	 * @return the progress of the open animation, between zero and one
	 */
	public float open() {
		return open;
	}

	/**
	 * @return the scale the content of this panel is drawn at
	 */
	public float scale() {
		return scale;
	}

	/**
	 * Recomputes the geometry of this panel.
	 *
	 * @param width            the width of the panel, in scaled GUI pixels
	 * @param height           the height of the panel, in scaled GUI pixels
	 * @param contentHeight    the height of the content, in scaled GUI pixels
	 * @param visibleHeight    the height of the body that fits on screen, in scaled GUI pixels
	 * @param bodyHeight       the animated height of the body, in scaled GUI pixels
	 * @param open             the progress of the open animation, between zero and one
	 */
	public void prepare(int width, int height, int contentHeight, int visibleHeight, int bodyHeight, float open) {
		this.width = width;
		this.height = height;
		this.contentHeight = contentHeight;
		this.visibleHeight = visibleHeight;
		this.bodyHeight = bodyHeight;
		this.open = open;

		this.scale = Easing.lerp(GuiTheme.OPEN_MIN_SCALE, 1.0F, Easing.cubicOut(open));
		this.x = state.x() + Math.round(width * (1.0F - scale) / 2.0F);
		this.y = state.y() + Math.round(height * (1.0F - scale) / 2.0F);
	}

	/**
	 * Maps an offset inside the panel onto the screen.
	 *
	 * @param offset the offset from the top left of the panel, in scaled GUI pixels
	 * @return the position on the screen, in scaled GUI pixels
	 */
	public int sx(int offset) {
		return x + Math.round(offset * scale);
	}

	/**
	 * Maps a length inside the panel onto the screen.
	 *
	 * @param size the length inside the panel, in scaled GUI pixels
	 * @return the length on the screen, in scaled GUI pixels
	 */
	public int sw(int size) {
		return Math.round(size * scale);
	}

	/**
	 * @param mouseX the horizontal position of the cursor, in scaled GUI pixels
	 * @param mouseY the vertical position of the cursor, in scaled GUI pixels
	 * @return whether the cursor is over the frame of this panel
	 */
	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/**
	 * @param mouseX the horizontal position of the cursor, in scaled GUI pixels
	 * @param mouseY the vertical position of the cursor, in scaled GUI pixels
	 * @return the offset of the cursor inside this panel, in scaled GUI pixels
	 */
	public int localY(double mouseY) {
		return Math.round((float) (mouseY - y) / scale);
	}

	/**
	 * @param mouseX the horizontal position of the cursor, in scaled GUI pixels
	 * @return the horizontal offset of the cursor inside this panel, in scaled GUI pixels
	 */
	public int localX(double mouseX) {
		return Math.round((float) (mouseX - x) / scale);
	}
}
