package com.birowo.client.platform.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The point of a target that a module asks for.
 *
 * <p>The point is taken from the place the target is drawn at, so that the aim follows the
 * target smoothly between two ticks instead of jumping once per tick.
 */
public final class AimPoints {
	private AimPoints() {
	}

	/**
	 * The point of the target that was asked for.
	 *
	 * @param target     the entity that is aimed at
	 * @param aimPoint   the part of the target that is aimed at
	 * @param multipoint how far the point lies between the centre of the body and the point that
	 *                   was asked for, as a percentage
	 * @param eyes       where the player looks from, in world coordinates
	 * @return the point in world coordinates
	 */
	public static Vec3d base(Entity target, AimPoint aimPoint, float multipoint, Vec3d eyes) {
		MinecraftClient client = MinecraftClient.getInstance();
		float delta = client.getRenderTickCounter().getTickProgress(false);
		Vec3d position = target.getLerpedPos(delta);

		double half = target.getWidth() / 2.0D;
		double height = target.getHeight();

		double minX = position.x - half;
		double maxX = position.x + half;
		double minY = position.y;
		double maxY = position.y + height;
		double minZ = position.z - half;
		double maxZ = position.z + half;

		double centerX = (minX + maxX) / 2.0D;
		double centerY = (minY + maxY) / 2.0D;
		double centerZ = (minZ + maxZ) / 2.0D;

		double x = centerX;
		double y = centerY;
		double z = centerZ;

		switch (aimPoint) {
			case HEAD -> y = maxY - 0.1D;
			case FEET -> y = minY + 0.1D;
			case CLOSEST -> {
				x = Math.max(minX, Math.min(eyes.x, maxX));
				y = Math.max(minY, Math.min(eyes.y, maxY));
				z = Math.max(minZ, Math.min(eyes.z, maxZ));
			}
			case BODY -> {
			}
		}

		float factor = MathHelper.clamp(multipoint / 100.0F, 0.0F, 1.0F);

		return new Vec3d(centerX + (x - centerX) * factor,
				centerY + (y - centerY) * factor,
				centerZ + (z - centerZ) * factor);
	}
}
