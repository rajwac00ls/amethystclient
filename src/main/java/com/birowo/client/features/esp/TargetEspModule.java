package com.birowo.client.features.esp;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.platform.event.TargetChangedEvent;
import com.birowo.client.platform.render.ShapeRenderer;
import com.birowo.client.platform.render.WorldLayers;
import com.birowo.client.platform.render.WorldRenderable;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Marks the entity that is being attacked.
 *
 * <p>Three things are drawn around the target: a ring that floats up and down around it, a
 * translucent fill of its body that fades from the feet to the head, and a cone that stands on
 * its head. The fill is faint on purpose — it is there to tell where the target is, not to light
 * the scene up.
 *
 * <p>Everything is drawn at the place the target is drawn at, which is between two ticks. A
 * target that runs therefore does not make the shapes jump once per tick.
 *
 * <p>The ring follows the target with a delay of its own, so that it glides after the target
 * instead of sticking to it, which keeps the movement smooth when the target runs.
 *
 * <p>The target is announced by the combat module through the event bus, so that the two modules
 * do not have to know each other.
 */
public final class TargetEspModule extends Module implements WorldRenderable {
	/**
	 * The number of straight lines the ring is made of.
	 */
	private static final int SEGMENTS = 48;

	/**
	 * The number of triangles the cone is made of.
	 */
	private static final int CONE_SEGMENTS = 24;

	/**
	 * The rate at which the ring catches up with the target, per second.
	 */
	private static final float FOLLOW_RATE = 16.0F;

	/**
	 * The longest frame the animation may be advanced by, in seconds.
	 */
	private static final float MAX_FRAME_SECONDS = 0.1F;

	/**
	 * The number of radians in a full turn.
	 */
	private static final float TAU = (float) (Math.PI * 2.0D);

	/**
	 * The number of radians in a degree.
	 */
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0D);

	/**
	 * The gap between the head of the target and the bottom of the cone, in blocks. It keeps the
	 * cone from flickering against the head.
	 */
	private static final double CONE_GAP = 0.06D;

	private final ConfigValue<Double> radius = setting(ConfigValues.ofDouble("radius", 0.7));
	private final ConfigValue<Double> amplitude = setting(ConfigValues.ofDouble("amplitude", 0.12));
	private final ConfigValue<Double> speed = setting(ConfigValues.ofDouble("speed", 2.0));
	private final ConfigValue<Integer> color = setting(ConfigValues.ofColor("color", 0xFFFFFFFF));
	private final ConfigValue<Integer> fillColor = setting(ConfigValues.ofColor("fillColor", 0x30A0A0A0));
	private final ConfigValue<Boolean> throughWalls = setting(ConfigValues.ofBoolean("throughWalls", false));
	private final ConfigValue<Boolean> coneHat = setting(ConfigValues.ofBoolean("coneHat", true));
	private final ConfigValue<Integer> coneColor = setting(ConfigValues.ofColor("coneColor", 0xFFE8E8E8));
	private final ConfigValue<Double> coneHeight = setting(ConfigValues.ofDouble("coneHeight", 0.55));
	private final ConfigValue<Double> coneRadius = setting(ConfigValues.ofDouble("coneRadius", 0.42));
	private final ConfigValue<Double> coneOpacity = setting(ConfigValues.ofDouble("coneOpacity", 0.9));
	private final ConfigValue<Double> coneRotation = setting(ConfigValues.ofDouble("coneRotation", 20.0));

	private volatile Entity target;
	private Entity followed;

	private double ringX;
	private double ringY;
	private double ringZ;
	private float phase;
	private float conePhase;
	private long previousFrameNanos;

	/**
	 * @param eventBus the bus the change of the target is announced on
	 * @param keyBinds the factory used to register this module's key binding
	 */
	public TargetEspModule(EventBus eventBus, KeyBindFactory keyBinds) {
		super("targetesp", "TargetESP", "Marks the entity that is attacked.", ModuleCategory.RENDER);

		eventBus.subscribe(TargetChangedEvent.class, event -> this.target = event.target());

		keyBinds.bind("targetesp", "key.birowo.targetesp", GLFW.GLFW_KEY_Y, this::toggle);
	}

	@Override
	public void onWorldRender(WorldRenderContext context) {
		Entity entity = target;

		if (entity == null || !entity.isAlive()) {
			return;
		}

		MatrixStack matrices = context.matrices();

		if (matrices == null) {
			return;
		}

		float delta = frameDelta();

		phase += delta * speed.value().floatValue() * TAU;

		if (phase > TAU) {
			phase -= TAU;
		}

		conePhase += delta * coneRotation.value().floatValue() * DEGREES_TO_RADIANS;

		if (conePhase > TAU) {
			conePhase -= TAU;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getCameraPos();

		// The place the target is drawn at, which is between two ticks.
		float tickDelta = client.getRenderTickCounter().getTickProgress(false);
		Vec3d position = entity.getLerpedPos(tickDelta);

		if (entity != followed) {
			followed = entity;
			ringX = position.x;
			ringY = position.y;
			ringZ = position.z;
		} else {
			float factor = 1.0F - (float) Math.exp(-FOLLOW_RATE * delta);

			ringX += (position.x - ringX) * factor;
			ringY += (position.y - ringY) * factor;
			ringZ += (position.z - ringZ) * factor;
		}

		float lineWidth = client.getWindow().getMinimumLineWidth();
		int argb = color.value();
		int fill = fillColor.value();

		MatrixStack.Entry entry = matrices.peek();

		boolean throughWalls = this.throughWalls.value();

		VertexConsumer fillConsumer = context.consumers()
				.getBuffer(throughWalls ? WorldLayers.filledBoxNoDepth() : WorldLayers.filledBox());
		VertexConsumer lineConsumer = context.consumers()
				.getBuffer(throughWalls ? WorldLayers.linesNoDepth() : RenderLayers.lines());

		double half = entity.getWidth() / 2.0D;
		double height = entity.getHeight();

		double minX = position.x - half - cameraPos.x;
		double minY = position.y - cameraPos.y;
		double minZ = position.z - half - cameraPos.z;
		double maxX = position.x + half - cameraPos.x;
		double maxY = position.y + height - cameraPos.y;
		double maxZ = position.z + half - cameraPos.z;

		// The fill of the body fades out towards the head, so that the head stays readable.
		ShapeRenderer.drawBoxFill(fillConsumer, entry, minX, minY, minZ, maxX, maxY, maxZ, fill, fade(fill, 0.0F));
		ShapeRenderer.drawBoxEdges(lineConsumer, entry, minX, minY, minZ, maxX, maxY, maxZ, argb, lineWidth);

		// The ring floats around the middle of the target.
		float bob = (float) Math.sin(phase) * amplitude.value().floatValue();
		double ringHeight = ringY - cameraPos.y + height * 0.5D + bob;

		ShapeRenderer.drawRing(lineConsumer, entry, ringX - cameraPos.x, ringHeight, ringZ - cameraPos.z,
				radius.value(), argb, lineWidth, SEGMENTS);

		if (coneHat.value()) {
			double coneBase = position.y + height + CONE_GAP - cameraPos.y;
			int cone = fade(coneColor.value(), coneOpacity.value().floatValue());

			ShapeRenderer.drawCone(fillConsumer, entry, position.x - cameraPos.x, coneBase,
					position.z - cameraPos.z, coneRadius.value(), coneHeight.value(), cone, conePhase,
					CONE_SEGMENTS);
			ShapeRenderer.drawRing(lineConsumer, entry, position.x - cameraPos.x, coneBase,
					position.z - cameraPos.z, coneRadius.value(), argb, lineWidth, SEGMENTS);
		}
	}

	@Override
	protected void onDisable() {
		followed = null;
		phase = 0.0F;
		conePhase = 0.0F;
		previousFrameNanos = 0L;
	}

	/**
	 * @return the time since the previous frame, in seconds
	 */
	private float frameDelta() {
		long now = System.nanoTime();
		float elapsed = previousFrameNanos == 0L ? 0.0F : (now - previousFrameNanos) / 1_000_000_000.0F;
		previousFrameNanos = now;

		return Math.min(elapsed, MAX_FRAME_SECONDS);
	}

	/**
	 * @param argb  the colour, with its own opacity
	 * @param alpha the factor to apply to the opacity, between zero and one
	 * @return the same colour with the scaled opacity
	 */
	private static int fade(int argb, float alpha) {
		float factor = Math.max(0.0F, Math.min(1.0F, alpha));
		int opacity = Math.round(((argb >>> 24) & 0xFF) * factor);
		return (opacity << 24) | (argb & 0x00FFFFFF);
	}
}
