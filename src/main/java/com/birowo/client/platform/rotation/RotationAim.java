package com.birowo.client.platform.rotation;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * What a module asks the rotation engine to aim at.
 *
 * <p>A module keeps one of these and hands a new one to the engine whenever its target or one of
 * its settings changes. The record is immutable, so the engine never sees half of an update.
 *
 * @param target     the entity the aim is pointed at, never {@code null}
 * @param point      the point of the target the aim is pointed at, in world coordinates
 * @param mode       whether the camera of the player turns towards the aim or only the head
 * @param yawSpeed   how fast the aim turns around the vertical axis, in degrees per second
 * @param pitchSpeed how fast the aim turns around the horizontal axis, in degrees per second
 * @param math       the way the aim is moved towards the target
 * @param axis       the axes the aim is allowed to move on
 */
public record RotationAim(Entity target, Vec3d point, RotationMode mode, float yawSpeed, float pitchSpeed,
		AimMath math, AimAxis axis) {
}
