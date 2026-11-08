package com.birowo.client.features.combat;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.core.util.AngleMath;
import com.birowo.client.platform.entity.EntityQueries;
import com.birowo.client.platform.entity.TargetPicker;
import com.birowo.client.platform.entity.TargetPriority;
import com.birowo.client.platform.entity.TargetVisibility;
import com.birowo.client.platform.rotation.AimAxis;
import com.birowo.client.platform.rotation.AimMath;
import com.birowo.client.platform.rotation.AimPoint;
import com.birowo.client.platform.rotation.AimPointFinder;
import com.birowo.client.platform.rotation.AimPointTracker;
import com.birowo.client.platform.rotation.AimPoints;
import com.birowo.client.platform.rotation.RotationAim;
import com.birowo.client.platform.rotation.RotationEngine;
import com.birowo.client.platform.rotation.RotationMode;
import com.birowo.client.platform.rotation.RotationPriority;
import com.birowo.client.platform.rotation.RotationRequest;

/**
 * Turns the head of the player towards a target.
 *
 * <p>The module does not attack. It only moves the aim, and it leaves the hit to the player or
 * to one of the modules that attack.
 *
 * <p>The aim is moved by the rotation engine, which advances it once per frame. The module only
 * decides what is aimed at: it looks for the entities that are in range and in the field of
 * view, picks one of them, and hands it to the engine.
 *
 * <p>The point of the target that is aimed at is not simply the head: the module looks for a
 * point of the body that the player can actually see, so that the aim does not settle on a
 * point that is hidden behind a wall.
 *
 * <p>By default the aim is silent: the camera of the player keeps the angle of the mouse and
 * only the head turns towards the target, so the aim helps without taking the view away. A
 * visible aim pulls the camera towards the target instead, and the mouse keeps working: a flick
 * of the mouse outruns the aim.
 *
 * <p>Inside the safe zone the aim is left alone altogether, and a switch to another target is
 * only made when the better target has been the better one for a number of ticks, so that the
 * aim does not jump back and forth.
 */
public final class AimAssistModule extends Module {
	private final ConfigValue<AimMath> math = setting(ConfigValues.ofEnum("math", AimMath.REGULAR));
	private final ConfigValue<AimPoint> aimPoint = setting(ConfigValues.ofEnum("aimPoint", AimPoint.HEAD));
	private final ConfigValue<TargetPriority> priority = setting(
			ConfigValues.ofEnum("priority", TargetPriority.NEAREST));
	private final ConfigValue<RotationMode> mode = setting(
			ConfigValues.ofEnum("mode", RotationMode.SILENT_HEAD));
	private final ConfigValue<Double> range = setting(ConfigValues.ofDouble("range", 4.0));
	private final ConfigValue<Double> fov = setting(ConfigValues.ofDouble("fov", 90.0));
	private final ConfigValue<Double> yawSpeed = setting(ConfigValues.ofDouble("yawSpeed", 150.0));
	private final ConfigValue<Double> pitchSpeed = setting(ConfigValues.ofDouble("pitchSpeed", 150.0));
	private final ConfigValue<Double> multipoint = setting(ConfigValues.ofDouble("multipoint", 100.0));
	private final ConfigValue<Double> safeZone = setting(ConfigValues.ofDouble("safeZone", 1.5));
	private final ConfigValue<Integer> switchTicks = setting(ConfigValues.ofInt("switchTicks", 3));
	private final ConfigValue<AimAxis> axis = setting(ConfigValues.ofEnum("axis", AimAxis.BOTH));
	private final ConfigValue<Boolean> wander = setting(ConfigValues.ofBoolean("wander", true));
	private final ConfigValue<Boolean> playersOnly = setting(ConfigValues.ofBoolean("playersOnly", true));
	private final ConfigValue<Boolean> throughWalls = setting(ConfigValues.ofBoolean("throughWalls", false));

	private final RotationRequest request = new RotationRequest(RotationPriority.AIM_ASSIST);
	private final AimPointTracker tracker = new AimPointTracker();

	/**
	 * The entity the aim is pointed at.
	 */
	private Entity tracked;

	/**
	 * The entity that would be aimed at if the switch were allowed.
	 */
	private Entity candidate;

	/**
	 * The number of ticks the candidate has been the better one for.
	 */
	private int candidateTicks;

	/**
	 * @param keyBinds the factory used to register this module's key binding
	 */
	public AimAssistModule(KeyBindFactory keyBinds) {
		super("aimassist", "AimAssist", "Turns the head towards a target.", ModuleCategory.COMBAT);

		RotationEngine.register(request);

		keyBinds.bind("aimassist", "key.birowo.aimassist", GLFW.GLFW_KEY_V, this::toggle);
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		if (player == null || world == null || client.currentScreen != null || !player.isAlive()) {
			request.setAim(null);
			return;
		}

		Entity target = findTarget(world, player);

		if (target == null) {
			request.setAim(null);
			forgetTarget();
			return;
		}

		tracked = target;

		Vec3d eyes = player.getEyePos();
		Vec3d wanted = AimPoints.base(target, aimPoint.value(), multipoint.value().floatValue(), eyes);

		if (wander.value()) {
			wanted = tracker.advance(target.getId(), wanted, target.getBoundingBox(), eyes, false);
		}

		double reach = range.value();
		Vec3d visible = AimPointFinder.find(world, target, eyes, wanted,
				RotationEngine.aimYaw(), RotationEngine.aimPitch(), reach * reach,
				!throughWalls.value());

		if (visible == null) {
			request.setAim(null);
			return;
		}

		// Inside the safe zone the aim is left alone: the point it is on is good enough, and
		// moving it would only make the aim and the mouse fight each other.
		boolean quiet = mode.value() == RotationMode.SILENT_HEAD;
		float currentYaw = quiet ? RotationEngine.aimYaw() : player.getYaw();
		float currentPitch = quiet ? RotationEngine.aimPitch() : player.getPitch();
		double angle = AngleMath.angleBetween(currentYaw, currentPitch,
				RotationEngine.yawTo(eyes, visible), RotationEngine.pitchTo(eyes, visible));

		if (angle <= safeZone.value()) {
			request.setAim(null);
			return;
		}

		request.setAim(new RotationAim(target, visible, mode.value(),
				yawSpeed.value().floatValue(), pitchSpeed.value().floatValue(), math.value(), axis.value()));
	}

	@Override
	protected void onEnable() {
		request.setAim(null);
	}

	@Override
	protected void onDisable() {
		request.setAim(null);
		forgetTarget();
		tracker.clear();
	}

	/**
	 * Forgets the entity the aim is pointed at.
	 */
	private void forgetTarget() {
		tracked = null;
		candidate = null;
		candidateTicks = 0;
	}

	/**
	 * Looks for the entity that is worth aiming at.
	 *
	 * @param world  the world the entities are looked for in
	 * @param player the player that aims
	 * @return the target, or {@code null} when there is none
	 */
	private Entity findTarget(ClientWorld world, ClientPlayerEntity player) {
		List<Entity> candidates = EntityQueries.entitiesInRange(world, player, range.value(),
				playersOnly.value(), entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.isAttackable());

		Entity best = TargetPicker.pick(candidates, player, priority.value());

		if (best == null) {
			return null;
		}

		if (!TargetPicker.inFieldOfView(player, best, fov.value())) {
			return null;
		}

		if (!throughWalls.value() && !TargetVisibility.hasLineOfSight(world, player, best)) {
			return null;
		}

		return confirmSwitch(candidates, best);
	}

	/**
	 * Decides whether the aim may move from the entity it is on to a better one.
	 *
	 * <p>A switch is only made when the better entity has been the better one for a number of
	 * ticks. Two entities that are close to each other would otherwise make the aim jump back
	 * and forth between them.
	 *
	 * @param candidates the entities that may be aimed at
	 * @param best       the entity that fits the rule of the module best
	 * @return the entity the aim is pointed at
	 */
	private Entity confirmSwitch(List<Entity> candidates, Entity best) {
		if (tracked == null || tracked == best || !candidates.contains(tracked)) {
			candidate = null;
			candidateTicks = 0;
			return best;
		}

		if (candidate != best) {
			candidate = best;
			candidateTicks = 0;
		}

		candidateTicks++;

		if (candidateTicks < switchTicks.value()) {
			return tracked;
		}

		candidate = null;
		candidateTicks = 0;

		return best;
	}
}
