package com.birowo.client.platform.entity;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Tells whether a target can be seen from where the player stands.
 */
public final class TargetVisibility {
	/**
	 * The slack of the check, in squared blocks. It keeps the block the player is standing in
	 * from counting as an obstacle.
	 */
	private static final double SLACK = 0.1D;

	private TargetVisibility() {
	}

	/**
	 * @param world  the world the ray travels through
	 * @param origin the entity that looks
	 * @param target the entity that is looked at
	 * @return whether nothing stands between the two
	 */
	public static boolean hasLineOfSight(ClientWorld world, Entity origin, Entity target) {
		Vec3d start = origin.getEyePos();
		Vec3d end = target.getEntityPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);

		BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, origin));

		if (hit.getType() == HitResult.Type.MISS) {
			return true;
		}

		return start.squaredDistanceTo(hit.getPos()) >= start.squaredDistanceTo(end) - SLACK;
	}
}
