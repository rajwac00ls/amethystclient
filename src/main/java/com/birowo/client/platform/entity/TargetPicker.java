package com.birowo.client.platform.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.birowo.client.platform.rotation.RotationEngine;

/**
 * Picks the target a module aims at.
 *
 * <p>The candidates are the entities a module has already collected; this class only decides
 * which of them is the better one, so that two modules with the same settings aim at the same
 * target.
 */
public final class TargetPicker {
	private TargetPicker() {
	}

	/**
	 * Picks the target that fits the given rule best.
	 *
	 * @param candidates the entities that may be aimed at
	 * @param origin     the entity that aims
	 * @param priority   the rule that decides between the candidates
	 * @return the chosen target, or {@code null} when there is no candidate at all
	 */
	public static Entity pick(List<Entity> candidates, Entity origin, TargetPriority priority) {
		Entity best = null;
		double bestScore = Double.MAX_VALUE;

		for (Entity candidate : candidates) {
			double score = score(candidate, origin, priority);

			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}

		return best;
	}

	/**
	 * @param candidate the entity that is scored
	 * @param origin    the entity that aims
	 * @param priority  the rule that decides between the candidates
	 * @return the score of the candidate; the lower the better
	 */
	private static double score(Entity candidate, Entity origin, TargetPriority priority) {
		switch (priority) {
			case HEALTH -> {
				if (candidate instanceof LivingEntity living) {
					return living.getHealth();
				}

				return Double.MAX_VALUE;
			}
			case FIELD_OF_VIEW -> {
				return fieldOfViewDistance(origin, candidate);
			}
			case TYPE -> {
				return typeWeight(candidate);
			}
			case HURT_TIME -> {
				if (candidate instanceof LivingEntity living) {
					// The lower the time of the last hit, the more recently the target was hurt.
					return living.hurtTime;
				}

				return Double.MAX_VALUE;
			}
			case AGE -> {
				// The older the target, the smaller the score.
				return -candidate.age;
			}
			default -> {
				return origin.squaredDistanceTo(candidate);
			}
		}
	}

	/**
	 * @param candidate the entity that is weighed
	 * @return the weight of the kind of the entity; the lower the more wanted it is
	 */
	private static double typeWeight(Entity candidate) {
		if (candidate instanceof PlayerEntity) {
			return 0.0D;
		}

		if (candidate instanceof Monster) {
			return 1.0D;
		}

		return 2.0D;
	}

	/**
	 * @param origin the entity that looks
	 * @param target the entity that is looked at
	 * @return the angle between the direction the origin looks at and the target, in degrees
	 */
	public static double fieldOfViewDistance(Entity origin, Entity target) {
		Vec3d eyes = origin.getEyePos();
		Vec3d point = target.getEyePos();

		float yaw = RotationEngine.yawTo(eyes, point);
		float pitch = RotationEngine.pitchTo(eyes, point);

		double yawDifference = Math.abs(MathHelper.wrapDegrees(yaw - origin.getYaw()));
		double pitchDifference = Math.abs(pitch - origin.getPitch());

		return Math.sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference);
	}

	/**
	 * @param origin the entity that looks
	 * @param target the entity that is looked at
	 * @param fov    the size of the cone the target has to lie in, in degrees
	 * @return whether the target lies inside the cone
	 */
	public static boolean inFieldOfView(Entity origin, Entity target, double fov) {
		return fieldOfViewDistance(origin, target) <= fov;
	}
}
