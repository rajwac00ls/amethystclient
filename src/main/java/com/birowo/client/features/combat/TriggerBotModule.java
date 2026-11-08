package com.birowo.client.features.combat;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;

/**
 * Attacks the entity the crosshair is on.
 *
 * <p>The entity under the crosshair is the one the game itself would attack when the player
 * clicks, so the module does nothing but press that click for the player. The delay keeps the
 * attack from happening in the very tick the crosshair reaches the entity, which is what a person
 * does. The moment of the hit is spread around a middle of fifty milliseconds, so that two hits
 * in a row are never exactly the same distance apart.
 *
 * <p>The target is taken from the crosshair of the game, not from a search of its own, and it is
 * checked with the same rules the game uses before it lets a click through: the entity has to be
 * alive, it has to be within the reach of the item in hand, and the weapon has to have charged
 * up as far as the module asks for. The arm is swung as well, so that the hit looks like one the
 * player made.
 *
 * <p>The module does not listen for mouse clicks and does not touch the input system at all. It
 * runs on the client tick, and it does nothing while a screen is open, so that it can never
 * attack while the player is in a menu or in the interface of the client.
 */
public final class TriggerBotModule extends Module {
	/**
	 * The middle of the time the trigger waits, in milliseconds.
	 */
	private static final double JITTER_MEAN_MILLIS = 50.0D;

	/**
	 * How far the time the trigger waits spreads around the middle, in milliseconds.
	 */
	private static final double JITTER_SPREAD_MILLIS = 30.0D;

	/**
	 * The shortest time the trigger waits, in milliseconds.
	 */
	private static final long JITTER_MIN_MILLIS = 0L;

	/**
	 * The longest time the trigger waits, in milliseconds.
	 */
	private static final long JITTER_MAX_MILLIS = 150L;

	private final ConfigValue<Integer> delay = setting(ConfigValues.ofInt("delay", 2));
	private final ConfigValue<Double> range = setting(ConfigValues.ofDouble("range", 3.0));
	private final ConfigValue<Double> minCharge = setting(ConfigValues.ofDouble("minCharge", 1.0));
	private final ConfigValue<Boolean> playersOnly = setting(ConfigValues.ofBoolean("playersOnly", true));
	private final ConfigValue<Boolean> jitter = setting(ConfigValues.ofBoolean("jitter", true));

	private final AttackLimiter limiter;
	private final Random random = new Random();

	private Entity seen;
	private int seenTicks;
	private ClientWorld seenWorld;

	/**
	 * The moment the trigger may fire, in nanoseconds since the start of the machine. Zero while
	 * the trigger is not armed.
	 */
	private long readyAtNanos;

	/**
	 * @param keyBinds the factory used to register this module's key binding
	 * @param limiter  the limiter shared with the other combat module
	 */
	public TriggerBotModule(KeyBindFactory keyBinds, AttackLimiter limiter) {
		super("triggerbot", "TriggerBot", "Attacks the entity the crosshair is on.", ModuleCategory.COMBAT);

		this.limiter = limiter;

		keyBinds.bind("triggerbot", "key.birowo.triggerbot", GLFW.GLFW_KEY_T, this::toggle);
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;

		// A screen is open: the game is either paused or the player is in a menu, and neither is
		// a moment to attack.
		if (player == null || world == null || client.currentScreen != null || !player.isAlive()
				|| player.isUsingItem() || player.isRiding()) {
			reset();
			return;
		}

		// The player changed the world; the entity that was seen belongs to the old one.
		if (world != seenWorld) {
			seenWorld = world;
			reset();
		}

		Entity target = targeted(client, player, world);

		if (target == null) {
			reset();
			return;
		}

		if (target != seen) {
			seen = target;
			seenTicks = 0;
			readyAtNanos = 0L;
		} else {
			seenTicks++;
		}

		if (seenTicks < delay.value()) {
			return;
		}

		ClientPlayerInteractionManager interaction = client.interactionManager;

		if (interaction == null || !limiter.ready(player, interaction, minCharge.value().floatValue())) {
			return;
		}

		long now = System.nanoTime();

		if (readyAtNanos == 0L) {
			readyAtNanos = now + jitterNanos();
		}

		if (now < readyAtNanos) {
			return;
		}

		interaction.attackEntity(player, target);
		limiter.onAttack();
		player.swingHand(Hand.MAIN_HAND);

		// The next hit is armed again, so that the time between two hits is not the same every
		// time.
		readyAtNanos = 0L;
	}

	/**
	 * The time the trigger waits before it fires, in nanoseconds.
	 *
	 * <p>The wait is spread around a middle of fifty milliseconds. A trigger that fires on the
	 * very tick the crosshair arrives never does.
	 *
	 * @return the time to wait, in nanoseconds
	 */
	private long jitterNanos() {
		if (!jitter.value()) {
			return 0L;
		}

		double millis = JITTER_MEAN_MILLIS + random.nextGaussian() * JITTER_SPREAD_MILLIS;
		long clamped = Math.round(Math.max(JITTER_MIN_MILLIS, Math.min(JITTER_MAX_MILLIS, millis)));

		return clamped * 1_000_000L;
	}

	@Override
	protected void onDisable() {
		reset();
		seenWorld = null;
	}

	/**
	 * Forgets the entity that was under the crosshair.
	 */
	private void reset() {
		seen = null;
		seenTicks = 0;
		readyAtNanos = 0L;
	}

	/**
	 * @param client the client whose crosshair is looked at
	 * @param player the player that attacks
	 * @param world  the world the player is in
	 * @return the entity the crosshair is on, or {@code null} if there is none that may be hit
	 */
	private Entity targeted(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
		HitResult hit = client.crosshairTarget;

		if (!(hit instanceof EntityHitResult entityHit)) {
			return null;
		}

		Entity entity = entityHit.getEntity();

		if (entity == null || entity == player || entity.isRemoved() || !entity.isAlive()
				|| entity.isSpectator() || !entity.isAttackable()) {
			return null;
		}

		if (playersOnly.value() && !(entity instanceof AbstractClientPlayerEntity)) {
			return null;
		}

		// The game refuses the hit when the item in hand is not allowed in this world, and so
		// does the module.
		if (!player.getMainHandStack().isItemEnabled(world.getEnabledFeatures())) {
			return null;
		}

		return inReach(player, entity) ? entity : null;
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
}
