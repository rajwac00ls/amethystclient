package com.birowo.client.platform.rotation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.birowo.client.core.util.AngleMath;
import com.birowo.client.core.util.StepQuantiser;
import com.birowo.client.core.util.WindMouse;

/**
 * The rotation of the player, and the only rotation the client owns.
 *
 * <p>Modules do not turn the player themselves: they tell the engine what they want to aim at,
 * and the engine decides how the aim is moved. That keeps two modules from fighting over the
 * camera, and it keeps the movement of the aim in one place, where the frame time is known.
 *
 * <p>The engine is stepped once per frame, before the camera of the game is updated, so that the
 * rotation the player sees is the rotation of this frame and not the one of the last.
 *
 * <p>An aim is either visible or silent. A visible aim turns the camera of the player towards the
 * target and leaves the mouse in charge of the finer movement: the aim pulls the camera, it does
 * not replace it. A silent aim leaves the camera alone and turns only the head of the player;
 * the angle of the head is what the movement packets carry, so the server is told about an angle
 * that the player does not look with.
 *
 * <p>Every movement the engine makes is rounded to the step of the mouse. The mouse of the game
 * moves in steps whose size depends on the sensitivity of the player, and the server checks the
 * angles it receives against that step: a rotation that is not a multiple of the step of the
 * mouse cannot have been made by the mouse. Rounding the movement rather than the angle keeps
 * the sum of many small movements on the step as well, so no movement is lost along the way.
 *
 * <p>The state of the engine is static, because the mixin that steps it has no access to the
 * objects of the mod. Only one aim is active at a time; the requests of the modules compete for
 * it.
 */
public final class RotationEngine {
	/**
	 * The factor the speed is multiplied with when the aim is silent. A silent aim has to reach
	 * the target before the next hit, so it is allowed to move faster than a visible one.
	 */
	private static final float SILENT_SPEED_FACTOR = 2.5F;

	/**
	 * The speed the aim returns with when there is no target any more, in degrees per second.
	 */
	private static final float RETURN_SPEED = 150.0F;

	/**
	 * The angle below which the aim counts as returned, in degrees.
	 */
	private static final float RETURN_THRESHOLD = 1.0F;

	/**
	 * How fast the camera is eased back to the angle it had before the aim took it over, in
	 * degrees per second.
	 */
	private static final float WIND_DOWN_SPEED = 240.0F;

	/**
	 * How long the camera is eased back to the angle it had before the aim took it over, in
	 * milliseconds.
	 */
	private static final long WIND_DOWN_MILLIS = 250L;

	/**
	 * How far the mouse may move in one frame without the wind-down giving up, in degrees. One
	 * step of the mouse is a tenth of a degree at the lowest sensitivity, so a movement of the
	 * mouse is always read as one.
	 */
	private static final float MOUSE_MOVE_DEGREES = 0.1F;

	/**
	 * The longest frame the aim may be advanced by, in seconds. A stall must not make the aim
	 * jump.
	 */
	private static final float MAX_FRAME_SECONDS = 0.1F;

	private static final List<RotationRequest> REQUESTS = new ArrayList<>();

	private static float aimYaw;
	private static float aimPitch;
	private static volatile Entity aimTarget;
	private static boolean tracking;
	private static RotationMode mode = RotationMode.VISIBLE;

	/**
	 * The angle the camera had before the aim took it over. The aim returns to it when it is let
	 * go, so that the camera does not stay where the target was.
	 */
	private static float originYaw;
	private static float originPitch;

	/**
	 * The angle the engine wrote last. The difference between it and the angle of the player is
	 * the movement of the mouse.
	 */
	private static float writtenYaw;
	private static float writtenPitch;

	/**
	 * The moment the wind-down ends, in nanoseconds since the start of the machine. Zero while
	 * there is no wind-down.
	 */
	private static long windDownUntilNanos;

	private static final WindMouse WIND = new WindMouse();
	private static final StepQuantiser YAW_QUANTISER = new StepQuantiser();
	private static final StepQuantiser PITCH_QUANTISER = new StepQuantiser();

	private static long previousFrameNanos;

	private RotationEngine() {
	}

	/**
	 * Adds a claim on the rotation of the player.
	 *
	 * @param request the request to add
	 */
	public static void register(RotationRequest request) {
		if (!REQUESTS.contains(request)) {
			REQUESTS.add(request);
		}
	}

	/**
	 * @return the horizontal angle the aim is at, in degrees
	 */
	public static float aimYaw() {
		return aimYaw;
	}

	/**
	 * @return the vertical angle the aim is at, in degrees
	 */
	public static float aimPitch() {
		return aimPitch;
	}

	/**
	 * @return whether an aim is active at the moment
	 */
	public static boolean tracking() {
		return tracking;
	}

	/**
	 * @return the entity the aim is pointed at, or {@code null} when there is no aim
	 */
	public static Entity target() {
		return aimTarget;
	}

	/**
	 * The horizontal angle a player has to look at to see a point.
	 *
	 * @param from where the player looks from
	 * @param to   the point that is looked at
	 * @return the angle around the vertical axis, in degrees
	 */
	public static float yawTo(Vec3d from, Vec3d to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
	}

	/**
	 * The vertical angle a player has to look at to see a point.
	 *
	 * @param from where the player looks from
	 * @param to   the point that is looked at
	 * @return the angle around the horizontal axis, in degrees
	 */
	public static float pitchTo(Vec3d from, Vec3d to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
	}

	/**
	 * Moves the aim one frame towards the target of the strongest request.
	 *
	 * <p>Called once per frame, before the camera of the game is updated.
	 */
	public static void step() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		if (player == null || world == null || client.currentScreen != null) {
			release();
			return;
		}

		float delta = frameDelta();
		RotationRequest winner = strongest();
		RotationAim aim = winner == null ? null : winner.aim();

		if (aim == null || aim.target() == null || !aim.target().isAlive() || aim.target().isRemoved()) {
			idle(delta, player);
			return;
		}

		Entity target = aim.target();
		Vec3d eyes = player.getEyePos();
		Vec3d point = aim.point();

		float desiredYaw = yawTo(eyes, point);
		float desiredPitch = pitchTo(eyes, point);

		// A silent aim starts from the angle the aim is at, a visible one from the angle of the
		// camera: the visible one has to fight with the mouse for the camera anyway.
		float currentYaw = aim.mode() == RotationMode.SILENT_HEAD ? aimYaw : player.getYaw();
		float currentPitch = aim.mode() == RotationMode.SILENT_HEAD ? aimPitch : player.getPitch();

		float newYaw;
		float newPitch;

		if (aim.math() == AimMath.BLATANT) {
			newYaw = desiredYaw;
			newPitch = desiredPitch;
		} else if (aim.math() == AimMath.WIND_MOUSE) {
			WIND.move(currentYaw, currentPitch, desiredYaw, desiredPitch,
					aim.yawSpeed() * speedFactor(aim), aim.pitchSpeed() * speedFactor(aim), delta);
			newYaw = WIND.yaw();
			newPitch = WIND.pitch();
		} else {
			float yawStep = aim.yawSpeed() * speedFactor(aim) * delta;
			float pitchStep = aim.pitchSpeed() * speedFactor(aim) * delta;

			newYaw = AngleMath.towards(currentYaw, desiredYaw, yawStep);
			newPitch = MathHelper.clamp(AngleMath.towardsDirect(currentPitch, desiredPitch, pitchStep), -90.0F, 90.0F);
		}

		// The movement is rounded to the step of the mouse, so that the server can believe the
		// angle was made by the mouse of the player.
		newYaw = YAW_QUANTISER.quantise(newYaw - currentYaw, grid()) + currentYaw;
		newPitch = PITCH_QUANTISER.quantise(newPitch - currentPitch, grid()) + currentPitch;

		if (aim.axis() == AimAxis.HORIZONTAL) {
			newPitch = currentPitch;
		} else if (aim.axis() == AimAxis.VERTICAL) {
			newYaw = currentYaw;
		}

		apply(player, aim.mode(), newYaw, newPitch);
		aimTarget = target;
	}

	/**
	 * @param aim the aim that is being moved
	 * @return the factor the speed of the aim is multiplied with
	 */
	private static float speedFactor(RotationAim aim) {
		// A silent aim has to arrive before the next hit, and it does not fight with the mouse
		// for the camera while it does so, so it is allowed to move faster.
		return aim.mode() == RotationMode.SILENT_HEAD ? SILENT_SPEED_FACTOR : 1.0F;
	}

	/**
	 * Turns the player towards the given angle, or hands the angle to the movement packets.
	 *
	 * @param player   the player that is turned
	 * @param rotation how the angle is applied to the player
	 * @param yaw      the horizontal angle, in degrees
	 * @param pitch    the vertical angle, in degrees
	 */
	private static void apply(ClientPlayerEntity player, RotationMode rotation, float yaw, float pitch) {
		if (!tracking) {
			// The angle the camera had before the aim took it over. The aim returns to it when it
			// is let go. The aim itself starts there as well, so that it walks towards the target
			// instead of jumping onto it in the first frame.
			originYaw = player.getYaw();
			originPitch = player.getPitch();
			aimYaw = originYaw;
			aimPitch = originPitch;
		}

		// An aim that runs cancels the walk back that was under way.
		windDownUntilNanos = 0L;

		if (rotation == RotationMode.VISIBLE) {
			player.setYaw(yaw);
			player.setPitch(pitch);

			// The head and the body follow the camera in the same frame. Vanilla copies the head
			// and the body of an entity at the end of its tick, so a rotation written here is the
			// one the renderer smooths out over the frame.
			player.setHeadYaw(yaw);
			player.setBodyYaw(yaw);

			ServerRotation.clear();
		} else {
			// The camera keeps the angle of the mouse; only the head of the player turns towards
			// the aim. The angle of the head is what the movement packets carry, so the server
			// reads the aim as the head of the player and the body keeps facing the way the
			// player looks.
			player.setHeadYaw(yaw);

			ServerRotation.set(yaw, pitch);
		}

		aimYaw = yaw;
		aimPitch = pitch;
		writtenYaw = yaw;
		writtenPitch = pitch;
		tracking = true;
		mode = rotation;
	}

	/**
	 * Lets the aim go when there is nothing to aim at.
	 *
	 * <p>A visible aim is the camera of the player; it stays where it was put. A silent aim is
	 * handed back to the player, so that the server is not told about an angle the player has
	 * already left behind.
	 *
	 * @param delta  the time since the previous frame, in seconds
	 * @param player the player that is turned
	 */
	private static void idle(float delta, ClientPlayerEntity player) {
		if (!tracking) {
			return;
		}

		aimTarget = null;

		if (mode == RotationMode.VISIBLE) {
			if (windDown(delta, player)) {
				return;
			}

			tracking = false;
			aimYaw = player.getYaw();
			aimPitch = player.getPitch();
			resetWind();
			return;
		}

		float step = RETURN_SPEED * delta;

		aimYaw = AngleMath.towards(aimYaw, player.getYaw(), step);
		aimPitch = MathHelper.clamp(AngleMath.towardsDirect(aimPitch, player.getPitch(), step), -90.0F, 90.0F);

		player.setHeadYaw(aimYaw);
		ServerRotation.set(aimYaw, aimPitch);

		if (AngleMath.distance(aimYaw, player.getYaw()) < RETURN_THRESHOLD
				&& Math.abs(player.getPitch() - aimPitch) < RETURN_THRESHOLD) {
			ServerRotation.clear();
			tracking = false;
			aimYaw = player.getYaw();
			aimPitch = player.getPitch();
			resetWind();
		}
	}

	/**
	 * Eases the camera back to the angle it had before the aim took it over.
	 *
	 * <p>The camera does not simply stay where the target was: it walks back to where the player
	 * was looking before the aim started. The walk is given up as soon as the mouse moves, so
	 * that the player is never fought over the camera.
	 *
	 * @param delta  the time since the previous frame, in seconds
	 * @param player the player that is turned
	 * @return whether the camera is still on its way back
	 */
	private static boolean windDown(float delta, ClientPlayerEntity player) {
		long now = System.nanoTime();

		if (windDownUntilNanos == 0L) {
			windDownUntilNanos = now + WIND_DOWN_MILLIS * 1_000_000L;
		}

		if (now >= windDownUntilNanos) {
			return false;
		}

		// The mouse is read after the aim is written, so the difference between the angle of the
		// player and the angle that was written last is the movement of the mouse.
		if (Math.abs(AngleMath.wrapDegrees(player.getYaw() - writtenYaw)) > MOUSE_MOVE_DEGREES
				|| Math.abs(player.getPitch() - writtenPitch) > MOUSE_MOVE_DEGREES) {
			return false;
		}

		float step = WIND_DOWN_SPEED * delta;
		float yaw = AngleMath.towards(aimYaw, originYaw, step);
		float pitch = MathHelper.clamp(AngleMath.towardsDirect(aimPitch, originPitch, step), -90.0F, 90.0F);

		player.setYaw(yaw);
		player.setPitch(pitch);
		player.setHeadYaw(yaw);
		player.setBodyYaw(yaw);

		aimYaw = yaw;
		aimPitch = pitch;
		writtenYaw = yaw;
		writtenPitch = pitch;

		return true;
	}

	/**
	 * Forgets the aim and stops the server from being told about an angle.
	 */
	private static void release() {
		if (!tracking) {
			return;
		}

		tracking = false;
		mode = RotationMode.VISIBLE;
		aimTarget = null;
		windDownUntilNanos = 0L;
		ServerRotation.clear();
		resetWind();
	}

	/**
	 * @return the request that wants the rotation the most, or {@code null} if none of the
	 *         requests wants a rotation at the moment
	 */
	private static RotationRequest strongest() {
		RotationRequest strongest = null;

		for (RotationRequest request : REQUESTS) {
			RotationAim aim = request.aim();

			if (aim == null || aim.target() == null) {
				continue;
			}

			if (strongest == null || request.priority() > strongest.priority()) {
				strongest = request;
			}
		}

		return strongest;
	}

	/**
	 * The size of one step of the mouse, in degrees.
	 *
	 * <p>The mouse of the game moves in steps whose size depends on the sensitivity of the
	 * player: the factor of the sensitivity is raised to the third power and multiplied by one
	 * point two, which is what the game itself uses to turn the counts of the mouse into
	 * degrees. A movement that is rounded to the same steps can be produced by the mouse itself.
	 *
	 * @return the size of one step, in degrees
	 */
	private static float grid() {
		MinecraftClient client = MinecraftClient.getInstance();
		float sensitivity = client.options.getMouseSensitivity().getValue().floatValue();

		float factor = sensitivity * 0.6F + 0.2F;

		return factor * factor * factor * 1.2F;
	}

	/**
	 * Forgets the movement of the wind of the mouse.
	 */
	private static void resetWind() {
		WIND.reset();
		YAW_QUANTISER.reset();
		PITCH_QUANTISER.reset();
	}

	/**
	 * @return the time since the previous frame, in seconds
	 */
	private static float frameDelta() {
		long now = System.nanoTime();
		float elapsed = previousFrameNanos == 0L ? 0.0F : (now - previousFrameNanos) / 1_000_000_000.0F;
		previousFrameNanos = now;

		return Math.min(elapsed, MAX_FRAME_SECONDS);
	}
}
