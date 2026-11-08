package com.birowo.client.core.event;

import com.birowo.client.core.module.Module;

/**
 * Posted when a module is enabled or disabled.
 *
 * @param module  the module whose state changed
 * @param enabled the state the module is now in
 */
public record ModuleToggleEvent(Module module, boolean enabled) implements Event {
}
