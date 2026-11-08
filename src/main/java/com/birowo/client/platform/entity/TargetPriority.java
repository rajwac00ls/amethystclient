package com.birowo.client.platform.entity;

/**
 * The rule a module uses to pick one target out of many.
 */
public enum TargetPriority {
	/**
	 * The target that is closest to the player.
	 */
	NEAREST,

	/**
	 * The target that is closest to the middle of the screen.
	 */
	FIELD_OF_VIEW,

	/**
	 * The target that has the least health left.
	 */
	HEALTH,

	/**
	 * The target of the most wanted kind: a player first, then a monster, then the rest.
	 */
	TYPE,

	/**
	 * The target that was hurt last, so that a fight is finished before another one is started.
	 */
	HURT_TIME,

	/**
	 * The target that has existed the longest.
	 */
	AGE
}
