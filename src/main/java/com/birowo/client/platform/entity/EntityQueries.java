package com.birowo.client.platform.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;

/**
 * Finds the entities a module is allowed to act on.
 *
 * <p>Both lookups are index-backed, so they cost roughly what the target set costs rather than
 * what the whole world costs. The client must never iterate every loaded entity per frame.
 */
public final class EntityQueries {
	private EntityQueries() {
	}

	/**
	 * Finds the entities within a range of an origin that satisfy a predicate.
	 *
	 * @param world       the world to search
	 * @param origin      the entity the range is measured from
	 * @param range       the maximum distance, in blocks
	 * @param playersOnly whether to restrict the search to remote players
	 * @param predicate   the filter every candidate must satisfy
	 * @return the matching entities
	 */
	public static List<Entity> entitiesInRange(ClientWorld world, Entity origin, double range,
			boolean playersOnly, Predicate<? super Entity> predicate) {
		if (playersOnly) {
			List<Entity> matches = new ArrayList<>();

			for (AbstractClientPlayerEntity player : world.getPlayers()) {
				if (predicate.test(player) && isWithinRange(origin, player, range)) {
					matches.add(player);
				}
			}

			return matches;
		}

		Box area = Box.of(origin.getEntityPos(), range, range, range);
		return world.getEntitiesByType(TypeFilter.instanceOf(Entity.class), area,
				entity -> predicate.test(entity) && isWithinRange(origin, entity, range));
	}

	/**
	 * @param origin the entity the distance is measured from
	 * @param target the entity being measured
	 * @param range  the maximum distance, in blocks
	 * @return whether the target is within range of the origin
	 */
	public static boolean isWithinRange(Entity origin, Entity target, double range) {
		return origin.squaredDistanceTo(target) <= range * range;
	}
}
