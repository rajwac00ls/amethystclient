package com.birowo.client.platform.rotation;

import java.util.Optional;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Finds the point of a target that the aim should point at.
 *
 * <p>A point on the body of a target is not always a point the hit can reach: the near side of
 * the body may be hidden behind a wall, and the far side of the body cannot be hit at all
 * through the near side. The finder therefore looks for a point that the player can actually
 * see and hit.
 *
 * <p>The point that is asked for is tried first. When it cannot be hit, the body is scanned
 * along a grid of positions, and the point that is closest to the aim and to the point that was
 * asked for is chosen. The scan never picks the very edge of the body, because a point on the
 * edge is a point that a moving target leaves behind at once.
 */
public final class AimPointFinder {
	/**
	 * The distance from the sides of the body, as a part of its size.
	 */
	private static final double HORIZONTAL_INSET = 0.12D;

	/**
	 * The distance from the top and the bottom of the body, as a part of its size.
	 */
	private static final double VERTICAL_INSET = 0.20D;

	/**
	 * The positions the body is scanned at, as a part of its size.
	 */
	private static final double[] SCAN = {0.12D, 0.31D, 0.50D, 0.69D, 0.88D};

	/**
	 * How much the distance to the point that was asked for counts against the angle the aim
	 * would have to turn by.
	 */
	private static final double DISTANCE_WEIGHT = 8.0D;

	private AimPointFinder() {
	}

	/**
	 * Finds the point of the target that the aim should point at.
	 *
	 * @param world          the world the target is in, used to see whether a point is visible
	 * @param target         the entity that is aimed at
	 * @param eyes           where the player looks from, in world coordinates
	 * @param preferred      the point that was asked for, in world coordinates
	 * @param currentYaw     the horizontal angle the aim is at, in degrees
	 * @param currentPitch   the vertical angle the aim is at, in degrees
	 * @param rangeSq        the square of the distance the aim may reach, in blocks
	 * @param requireVisible whether a point that is hidden behind a wall may be used
	 * @return the point to aim at, or {@code null} when no point of the target can be reached
	 */
	public static Vec3d find(ClientWorld world, Entity target, Vec3d eyes, Vec3d preferred,
			float currentYaw, float currentPitch, double rangeSq, boolean requireVisible) {
		Box box = target.getBoundingBox();
		Vec3d wanted = safePoint(box, preferred);

		if (visible(world, target, box, eyes, wanted, rangeSq, requireVisible)) {
			return wanted;
		}

		Vec3d best = null;
		double bestScore = Double.MAX_VALUE;

		for (double x : SCAN) {
			for (double y : SCAN) {
				for (double z : SCAN) {
					Vec3d candidate = point(box, x, y, z);

					if (!visible(world, target, box, eyes, candidate, rangeSq, requireVisible)) {
						continue;
					}

					double score = angle(eyes, candidate, currentYaw, currentPitch)
							+ candidate.distanceTo(wanted) * DISTANCE_WEIGHT;

					if (score < bestScore) {
						bestScore = score;
						best = candidate;
					}
				}
			}
		}

		return best;
	}

	/**
	 * Tells whether the ray of the aim reaches the body of the target.
	 *
	 * <p>The ray starts at the eyes of the player and follows the angle of the aim. It counts as
	 * a hit when it enters the body of the target, whatever side of the body it enters.
	 *
	 * @param target the entity that is aimed at
	 * @param eyes   where the player looks from, in world coordinates
	 * @param yaw    the horizontal angle of the aim, in degrees
	 * @param pitch  the vertical angle of the aim, in degrees
	 * @param range  how far the ray reaches, in blocks
	 * @return whether the ray reaches the body of the target
	 */
	public static boolean rayHits(Entity target, Vec3d eyes, float yaw, float pitch, double range) {
		Box box = target.getBoundingBox();

		double horizontal = Math.cos(Math.toRadians(pitch));
		Vec3d direction = new Vec3d(-Math.sin(Math.toRadians(yaw)) * horizontal,
				-Math.sin(Math.toRadians(pitch)),
				Math.cos(Math.toRadians(yaw)) * horizontal);

		Vec3d end = eyes.add(direction.multiply(range));

		return box.raycast(eyes, end).isPresent();
	}

	/**
	 * Brings a point into the body of the target, away from its edges.
	 *
	 * @param box  the body of the target
	 * @param point the point that is brought in, in world coordinates
	 * @return the point inside the body
	 */
	public static Vec3d safePoint(Box box, Vec3d point) {
		double sizeX = box.maxX - box.minX;
		double sizeY = box.maxY - box.minY;
		double sizeZ = box.maxZ - box.minZ;

		double x = sizeX > 1.0E-9D ? (point.x - box.minX) / sizeX : 0.5D;
		double y = sizeY > 1.0E-9D ? (point.y - box.minY) / sizeY : 0.5D;
		double z = sizeZ > 1.0E-9D ? (point.z - box.minZ) / sizeZ : 0.5D;

		return point(box, x, y, z);
	}

	/**
	 * The point of the body at the given position.
	 *
	 * @param box the body of the target
	 * @param x   the position along the width, as a part of the size of the body
	 * @param y   the position along the height, as a part of the size of the body
	 * @param z   the position along the depth, as a part of the size of the body
	 * @return the point in world coordinates
	 */
	private static Vec3d point(Box box, double x, double y, double z) {
		double sizeX = box.maxX - box.minX;
		double sizeY = box.maxY - box.minY;
		double sizeZ = box.maxZ - box.minZ;

		double safeX = MathHelper.clamp(x, HORIZONTAL_INSET, 1.0D - HORIZONTAL_INSET);
		double safeY = MathHelper.clamp(y, VERTICAL_INSET, 1.0D - VERTICAL_INSET);
		double safeZ = MathHelper.clamp(z, HORIZONTAL_INSET, 1.0D - HORIZONTAL_INSET);

		return new Vec3d(box.minX + sizeX * safeX,
				box.minY + sizeY * safeY,
				box.minZ + sizeZ * safeZ);
	}

	/**
	 * Tells whether the point can be reached.
	 *
	 * @param world          the world the target is in
	 * @param target         the entity that is aimed at
	 * @param box            the body of the target
	 * @param eyes           where the player looks from, in world coordinates
	 * @param point          the point that is checked, in world coordinates
	 * @param rangeSq        the square of the distance the aim may reach, in blocks
	 * @param requireVisible whether a point that is hidden behind a wall may be used
	 * @return whether the point can be reached
	 */
	private static boolean visible(ClientWorld world, Entity target, Box box, Vec3d eyes, Vec3d point,
			double rangeSq, boolean requireVisible) {
		Optional<Vec3d> surface = box.raycast(eyes, point);

		if (surface.isEmpty()) {
			return false;
		}

		Vec3d hit = surface.get();

		if (eyes.squaredDistanceTo(hit) > rangeSq) {
			return false;
		}

		if (!requireVisible) {
			return true;
		}

		HitResult blocked = world.raycast(new RaycastContext(eyes, hit,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target));

		return blocked.getType() == HitResult.Type.MISS;
	}

	/**
	 * @param eyes         where the player looks from, in world coordinates
	 * @param point        the point that is looked at, in world coordinates
	 * @param currentYaw   the horizontal angle the aim is at, in degrees
	 * @param currentPitch the vertical angle the aim is at, in degrees
	 * @return the angle between the aim and the point, in degrees
	 */
	private static double angle(Vec3d eyes, Vec3d point, float currentYaw, float currentPitch) {
		double dx = point.x - eyes.x;
		double dy = point.y - eyes.y;
		double dz = point.z - eyes.z;

		double horizontal = Math.sqrt(dx * dx + dz * dz);

		double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D;
		double pitch = -Math.toDegrees(Math.atan2(dy, horizontal));

		double yawDifference = MathHelper.wrapDegrees((float) (yaw - currentYaw));
		double pitchDifference = pitch - currentPitch;

		return Math.sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference);
	}
}
