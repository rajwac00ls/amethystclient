package com.birowo.client.core.event;

/**
 * Posted once per client tick, on the client thread.
 *
 * @param tickCount the number of client ticks elapsed since the client started
 */
public record TickEvent(long tickCount) implements Event {
}
