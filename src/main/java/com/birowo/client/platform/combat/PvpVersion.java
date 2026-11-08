package com.birowo.client.platform.combat;

/**
 * The PvP rules the server runs with.
 *
 * <p>The two versions differ in the rules that decide how fast a player may hit:
 *
 * <ul>
 *   <li><b>1.8.9</b> — there is no attack cooldown, so a hit may land on every tick, and a hit
 *       does not interrupt a sprint.
 *   <li><b>1.21</b> — the attack speed limits how often a hit lands, and a hit interrupts a
 *       sprint.
 * </ul>
 *
 * <p>Every version between 1.9 and 1.21 shares the rules of 1.21, so they are all the modern
 * one.
 */
public enum PvpVersion {
	/**
	 * The rules of Minecraft 1.8.9.
	 */
	LEGACY_1_8_9("1.8.9"),

	/**
	 * The rules of Minecraft 1.21.
	 */
	MODERN_1_21("1.21");

	private final String label;

	PvpVersion(String label) {
		this.label = label;
	}

	/**
	 * @return the name of the version, as it is shown to the player
	 */
	public String label() {
		return label;
	}

	/**
	 * @return whether the rules of 1.8.9 apply, which have no attack cooldown and do not
	 *         interrupt a sprint on a hit
	 */
	public boolean legacy() {
		return this == LEGACY_1_8_9;
	}
}
