package com.birowo.client.core.event;

/**
 * Marker for every event that travels through the {@link EventBus}.
 *
 * <p>Events are immutable value objects. An event that carries a Minecraft type must live in the
 * platform layer, never in {@code core}.
 */
public interface Event {
}
