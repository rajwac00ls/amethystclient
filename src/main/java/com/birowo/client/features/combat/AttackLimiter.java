package com.birowo.client.features.combat;

import java.util.Random;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;

import com.birowo.client.platform.combat.PvpVersionService;

/**
 * The rate at which the client is allowed to attack.
 *
 * <p>The limiter keeps two rules apart. The first is the rule of the game: a hit is only worth
 * as much as the charge of the weapon allows, and the charge is read from the player itself, so
 * the rule follows the weapon in the hand. A sword needs twelve and a half ticks to charge up,
 * a bare hand five, and the limiter waits for the charge that the module asks for.
 *
 * <p>The second rule is a rhythm of its own: the time between two hits is never the same twice,
 * and it is never shorter than a fortieth of a second. A client that hits at exactly the same
 * moment every time does not look like a hand.
 *
 * <p>The rules of 1.8.9 have no attack cooldown at all, so on a server that runs them the charge
 * is not waited for. Both combat modules share one limiter, so that they can never attack twice
 * in the same moment, no matter which of the two is the faster one.
 */
public final class AttackLimiter {
	/**
	 * The shortest time between two hits, in milliseconds.
	 */
	private static final long MINIMUM_INTERVAL_MILLIS = 40L;

	/**
	 * How far the time between two hits spreads around the shortest one, in milliseconds.
	 */
	private static final long INTERVAL_JITTER_MILLIS = 80L;

	private final PvpVersionService pvpVersion;
	private final Random random = new Random();

	/**
	 * The moment the next hit may be made, in nanoseconds since the start of the machine. Zero
	 * while nothing has been hit yet.
	 */
	private long readyAtNanos;

	/**
	 * @param pvpVersion the service that says which PvP rules the server runs with
	 */
	public AttackLimiter(PvpVersionService pvpVersion) {
		this.pvpVersion = pvpVersion;
	}

	/**
	 * Tells whether a hit may be made now.
	 *
	 * @param player        the player that hits
	 * @param interaction  the manager the hit is made with, which knows whether the game mode
	 *                     limits the attack speed
	 * @param minimumCharge the charge the weapon has to have, from zero to one
	 * @return whether the hit may be made
	 */
	public boolean ready(ClientPlayerEntity player, ClientPlayerInteractionManager interaction, float minimumCharge) {
		if (System.nanoTime() < readyAtNanos) {
			return false;
		}

		// The rules of 1.8.9 have no attack cooldown, so the charge is not waited for.
		if (pvpVersion.current().legacy()) {
			return true;
		}

		// A game mode without a limit on the attack speed has no charge either.
		if (!interaction.hasLimitedAttackSpeed()) {
			return true;
		}

		return player.getAttackCooldownProgress(0.0F) >= minimumCharge;
	}

	/**
	 * Notes that a hit has been made, and arms the limiter for the next one.
	 */
	public void onAttack() {
		long interval = MINIMUM_INTERVAL_MILLIS + (long) (random.nextDouble() * INTERVAL_JITTER_MILLIS);

		readyAtNanos = System.nanoTime() + interval * 1_000_000L;
	}
}
