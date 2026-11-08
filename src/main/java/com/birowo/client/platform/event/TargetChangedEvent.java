package com.birowo.client.platform.event;

import net.minecraft.entity.Entity;

import com.birowo.client.core.event.Event;

/**
 * Posted when the entity a combat module is aiming at changes.
 *
 * <p>The event carries a Minecraft type, which is why it lives in the platform layer rather than
 * in the core.
 *
 * @param target the entity that is aimed at, or {@code null} when nothing is aimed at
 */
public record TargetChangedEvent(Entity target) implements Event {
}
