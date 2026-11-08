package com.birowo.client.features.combat;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.platform.combat.PvpVersionService;
import com.birowo.client.platform.entity.EntityQueries;
import com.birowo.client.platform.entity.TargetPicker;
import com.birowo.client.platform.entity.TargetPriority;
import com.birowo.client.platform.entity.TargetVisibility;
import com.birowo.client.platform.event.TargetChangedEvent;
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
 * Attacks the closest valid target in range.
 *
 * <p>The rotation is the delicate part of the module, and it is left to the rotation engine: the
 * module decides what is aimed at, the engine decides how the aim is moved. The engine advances
 * the aim once per frame, before the camera of the game is updated, so that the movement of the
 * camera is smooth and never one frame behind the aim.
 *
 * <p>The point of the target that is aimed at is not simply the head: the module looks for a
 * point of the body that the player can actually see and hit, and the aim moves towards that
 * point. The point wanders slowly over the body instead of standing still on one spot of it,
 * and the target that is aimed at is kept for a moment before another one is taken, so that the
 * aim does not jump between two entities that are close to each other.
 *
 * <p>The hit is only landed when the ray of the aim reaches the body of the target. The aim is
 * the angle the server is told about, so this is the same test the server itself makes when it
 * decides whether a hit lands: the hit cannot miss because the target moved while the aim was on
 * its way.
 *
 * <p>The aim is by default silent: the camera of the player keeps the angle of the mouse and
 * only the head of the player turns towards the target, which is the angle the movement packets
 * carry. The head of the target is therefore looked at without the view of the player moving.
 *
 * <p>The module only chooses the target and the rotation; the hit itself goes through
 * {@code ClientPlayerInteractionManager.attackEntity}, which is the entry point vanilla's own
 * {@code MinecraftClient.doAttack} ends up calling.
 */
public final class KillAuraModule extends Module {
	/**
	 * The distance within which a sprinting player has to stop sprinting before it may hit.
	 */
	private static final float SPRINT_RESET_RANGE = 3.0F;

	/**
	 * The charge the weapon has to have before the hit is made.
	 */
	private static final double FULL_CHARGE = 1.0D;

	private final ConfigValue<AimMath> math = setting(ConfigValues.ofEnum("math", AimMath.REGULAR));
	private final ConfigValue<AimPoint> aimPoint = setting(ConfigValues.ofEnum("aimPoint", AimPoint.HEAD));
	private final ConfigValue<TargetPriority> priority = setting(
			ConfigValues.ofEnum("priority", TargetPriority.NEAREST));
	private final ConfigValue<RotationMode> mode = setting(
			ConfigValues.ofEnum("mode", RotationMode.SILENT_HEAD));
	private final ConfigValue<Double> range = setting(ConfigValues.ofDouble("range", 3.0));
	private final ConfigValue<Double> fov = setting(ConfigValues.ofDouble("fov", 90.0));
	private final ConfigValue<Double> yawSpeed = setting(ConfigValues.ofDouble("yawSpeed", 200.0));
	private final ConfigValue<Double> pitchSpeed = setting(ConfigValues.ofDouble("pitchSpeed", 200.0));
	private final ConfigValue<Double> multipoint = setting(ConfigValues.ofDouble("multipoint", 100.0));
	private final ConfigValue<Double> switchDelay = setting(ConfigValues.ofDouble("switchDelay", 150.0));
	private final ConfigValue<Double> minCharge = setting(ConfigValues.ofDouble("minCharge", FULL_CHARGE));
	private final ConfigValue<Boolean> playersOnly = setting(ConfigValues.ofBoolean("playersOnly", true));
	private final ConfigValue<Boolean> wander = setting(ConfigValues.ofBoolean("wander", true));
	private final ConfigValue<Boolean> requireLineOfSight = setting(ConfigValues.ofBoolean("requireLineOfSight", true));
	private final ConfigValue<Boolean> resetSprint = setting(ConfigValues.ofBoolean("resetSprint", true));

	private final EventBus eventBus;
	private final AttackLimiter limiter;
	private final PvpVersionService pvpVersion;
	private final RotationRequest request = new RotationRequest(RotationPriority.KILL_AURA);
	private final AimPointTracker tracker = new AimPointTracker();

	/**
	 * The entity that is aimed at, picked by the tick.
	 */
	private volatile Entity target;

	/**
	 * The moment the current target was picked, in nanoseconds since the start of the machine.
	 */
	private long targetSinceNanos;

	/**
	 * @param eventBus   the bus the change of the target is announced on
	 * @param keyBinds   the factory used to register this module's key binding
	 * @param limiter    the limiter shared with the other combat module
	 * @param pvpVersion the service that says which PvP rules the server runs with
	 */
	public KillAuraModule(EventBus eventBus, KeyBindFactory keyBinds, AttackLimiter limiter,
			PvpVersionService pvpVersion) {
		super("killaura", "KillAura", "Attacks the closest valid target in range.", ModuleCategory.COMBAT);

		this.eventBus = eventBus;
		this.limiter = limiter;
		this.pvpVersion = pvpVersion;

		RotationEngine.register(request);

		keyBinds.bind("killaura", "key.birowo.killaura", GLFW.GLFW_KEY_K, this::toggle);
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		if (player == null || world == null || client.currentScreen != null || !player.isAlive()
				|| player.isRiding() || player.isUsingItem()) {
			clearTarget();
			return;
		}

		Entity current = target;

		if (current != null && !isValid(player, current)) {
			current = null;
		}

		Entity best = pickTarget(world, player);

		if (current == null) {
			current = best;
		} else if (best != null && best != current && switchDelayPassed()) {
			// The delay keeps the target that is already being aimed at for a moment, so that the
			// aim does not jump between two entities that are close to each other.
			current = best;
		}

		if (current != target) {
			setTarget(current);
		}

		if (current == null) {
			request.setAim(null);
			return;
		}

		boolean onTarget = reachesTarget(player, current);

		Vec3d eyes = player.getEyePos();
		Vec3d wanted = AimPoints.base(current, aimPoint.value(), multipoint.value().floatValue(), eyes);

		if (wander.value()) {
			wanted = tracker.advance(current.getId(), wanted, current.getBoundingBox(), eyes, onTarget);
		}

		double reach = range.value();
		Vec3d visible = AimPointFinder.find(world, current, eyes, wanted,
				RotationEngine.aimYaw(), RotationEngine.aimPitch(), reach * reach,
				requireLineOfSight.value());

		if (visible == null) {
			// No point of the target can be hit, so there is nothing to aim at.
			request.setAim(null);
			return;
		}

		request.setAim(new RotationAim(current, visible, mode.value(),
				yawSpeed.value().floatValue(), pitchSpeed.value().floatValue(), math.value(), AimAxis.BOTH));

		if (!onTarget) {
			return;
		}

		if (requireLineOfSight.value() && !TargetVisibility.hasLineOfSight(world, player, current)) {
			return;
		}

		// The rules of 1.21 interrupt a sprint when the hit lands; the rules of 1.8.9 do not, so
		// the sprint is left alone there.
		if (resetSprint.value() && !pvpVersion.current().legacy() && player.isSprinting()
				&& player.squaredDistanceTo(current) < SPRINT_RESET_RANGE * SPRINT_RESET_RANGE) {
			player.setSprinting(false);
		}

		ClientPlayerInteractionManager interaction = client.interactionManager;

		if (interaction == null || !limiter.ready(player, interaction, minCharge.value().floatValue())) {
			return;
		}

		// The game refuses the hit when the item in hand is not allowed in this world, and so
		// does the module.
		if (!player.getMainHandStack().isItemEnabled(world.getEnabledFeatures())) {
			return;
		}

		interaction.attackEntity(player, current);
		limiter.onAttack();
		player.swingHand(Hand.MAIN_HAND);
	}

	@Override
	protected void onEnable() {
		request.setAim(null);
	}

	@Override
	protected void onDisable() {
		clearTarget();
		tracker.clear();
		request.setAim(null);
	}

	/**
	 * @return whether the current target may be left for another one
	 */
	private boolean switchDelayPassed() {
		double delay = switchDelay.value();

		if (delay <= 0.0D) {
			return true;
		}

		return (System.nanoTime() - targetSinceNanos) / 1_000_000.0D >= delay;
	}

	/**
	 * @param world  the world the entities are looked for in
	 * @param player the player that attacks
	 * @return the target that fits the rule of the module best, or {@code null} when there is none
	 */
	private Entity pickTarget(ClientWorld world, ClientPlayerEntity player) {
		List<Entity> candidates = EntityQueries.entitiesInRange(world, player, range.value(),
				playersOnly.value(), entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.isAttackable());

		Entity best = TargetPicker.pick(candidates, player, priority.value());

		if (best == null) {
			return null;
		}

		// A target the player is not looking at is a target no hand would have hit.
		return TargetPicker.inFieldOfView(player, best, fov.value()) ? best : null;
	}

	/**
	 * @param player    the player that attacks
	 * @param candidate the entity to check
	 * @return whether the entity may still be attacked
	 */
	private boolean isValid(ClientPlayerEntity player, Entity candidate) {
		if (!candidate.isAlive() || candidate.isSpectator() || !candidate.isAttackable()) {
			return false;
		}

		return inReach(player, candidate);
	}

	/**
	 * @param player the player that attacks
	 * @param target the entity that is attacked
	 * @return whether the target is close enough to be hit
	 */
	private boolean inReach(ClientPlayerEntity player, Entity target) {
		double reach = range.value();

		if (target.getBoundingBox().squaredMagnitude(player.getEyePos()) > reach * reach) {
			return false;
		}

		// The item in hand decides how far the hit reaches; the range of the module may only make
		// that reach shorter, never longer.
		return player.canAttackEntityIn(target.getBoundingBox(), 0.0D);
	}

	/**
	 * Tells whether the ray of the aim reaches the body of the target.
	 *
	 * @param player the player that aims
	 * @param target the entity that is attacked
	 * @return whether the aim is on the target
	 */
	private boolean reachesTarget(ClientPlayerEntity player, Entity target) {
		// The check only means something while the engine aims at this very target.
		if (RotationEngine.target() != target) {
			return false;
		}

		return AimPointFinder.rayHits(target, player.getEyePos(),
				RotationEngine.aimYaw(), RotationEngine.aimPitch(), range.value());
	}

	/**
	 * Announces a new target to the rest of the client.
	 *
	 * @param newTarget the entity that is aimed at, or {@code null} when there is none
	 */
	private void setTarget(Entity newTarget) {
		target = newTarget;
		targetSinceNanos = System.nanoTime();
		eventBus.post(new TargetChangedEvent(newTarget));
	}

	private void clearTarget() {
		if (target != null) {
			setTarget(null);
		}
	}
}
