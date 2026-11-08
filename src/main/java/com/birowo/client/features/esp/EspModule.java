package com.birowo.client.features.esp;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.platform.entity.EntityQueries;
import com.birowo.client.platform.render.ShapeRenderer;
import com.birowo.client.platform.render.WorldLayers;
import com.birowo.client.platform.render.WorldRenderable;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Draws a wireframe box around nearby entities.
 *
 * <p>The outline is drawn twice when it is asked to shine through walls: once with the layer that
 * ignores the depth of the world, so that the box of an entity behind a wall stays visible, and
 * once with the layer of the game, so that the edges of the box that are actually in sight are
 * drawn over the body of the entity rather than through it. The copy that shines through walls is
 * the fainter of the two, which keeps the body of the entity readable.
 *
 * <p>The entity query runs on the client tick, not on the render thread: world lookups are not
 * safe to perform while rendering. The box itself is measured while rendering, so that it follows
 * an entity that moves instead of trailing it by a tick.
 */
public final class EspModule extends Module implements WorldRenderable {
	/**
	 * How far the outline is kept away from the body of the entity, in blocks. Without the gap
	 * the lines and the surface of the entity share the same depth and fight each other, which
	 * makes the outline flicker.
	 */
	private static final float OUTLINE_EPSILON = 0.02F;

	/**
	 * The opacity of the copy of the outline that shines through walls, between zero and one.
	 */
	private static final float THROUGH_WALLS_ALPHA = 0.45F;

	private final ConfigValue<Double> range = setting(ConfigValues.ofDouble("range", 64.0));
	private final ConfigValue<Boolean> playersOnly = setting(ConfigValues.ofBoolean("playersOnly", true));
	private final ConfigValue<Integer> color = setting(ConfigValues.ofColor("color", 0xFFFF5555));
	private final ConfigValue<Boolean> throughWalls = setting(ConfigValues.ofBoolean("throughWalls", false));

	/**
	 * The entities to outline this frame, published by the tick and read by the render thread.
	 *
	 * <p>Replaced wholesale rather than mutated in place: the client thread and the render thread
	 * are different threads, and an immutable list swapped atomically is the only way for the
	 * renderer to read a consistent set without locking.
	 */
	private volatile List<Entity> targets = List.of();

	/**
	 * @param keyBinds the factory used to register this module's key binding
	 */
	public EspModule(KeyBindFactory keyBinds) {
		super("esp", "ESP", "Draws an outline around nearby entities.", ModuleCategory.RENDER);

		keyBinds.bind("esp", "key.birowo.esp", GLFW.GLFW_KEY_H, this::toggle);
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		if (player == null || world == null) {
			targets = List.of();
			return;
		}

		targets = List.copyOf(EntityQueries.entitiesInRange(world, player, range.value(), playersOnly.value(),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()));
	}

	@Override
	public void onWorldRender(WorldRenderContext context) {
		List<Entity> snapshot = targets;

		if (snapshot.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrices();

		if (matrices == null) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getCameraPos();

		float lineWidth = client.getWindow().getMinimumLineWidth();
		int argb = color.value();

		VertexConsumer throughWallLines = null;

		if (throughWalls.value()) {
			throughWallLines = context.consumers().getBuffer(WorldLayers.linesNoDepth());
		}

		VertexConsumer visible = context.consumers().getBuffer(RenderLayers.lines());
		MatrixStack.Entry entry = matrices.peek();

		for (Entity target : snapshot) {
			double x = target.getX() - cameraPos.x;
			double y = target.getY() - cameraPos.y;
			double z = target.getZ() - cameraPos.z;
			double half = target.getWidth() / 2.0D;

			double minX = x - half - OUTLINE_EPSILON;
			double maxX = x + half + OUTLINE_EPSILON;
			double minY = y - OUTLINE_EPSILON;
			double maxY = y + target.getHeight() + OUTLINE_EPSILON;
			double minZ = z - half - OUTLINE_EPSILON;
			double maxZ = z + half + OUTLINE_EPSILON;

			if (throughWallLines != null) {
				ShapeRenderer.drawBoxEdges(throughWallLines, entry, minX, minY, minZ, maxX, maxY, maxZ,
						fade(argb, THROUGH_WALLS_ALPHA), lineWidth);
			}

			ShapeRenderer.drawBoxEdges(visible, entry, minX, minY, minZ, maxX, maxY, maxZ, argb, lineWidth);
		}
	}

	@Override
	protected void onDisable() {
		targets = List.of();
	}

	/**
	 * @param argb  the colour, with its own opacity
	 * @param alpha the opacity to apply, between zero and one
	 * @return the same colour with the given opacity
	 */
	private static int fade(int argb, float alpha) {
		int opacity = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (opacity << 24) | (argb & 0x00FFFFFF);
	}
}
