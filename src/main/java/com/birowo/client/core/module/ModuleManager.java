package com.birowo.client.core.module;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.event.ModuleToggleEvent;
import com.birowo.client.core.event.TickEvent;

/**
 * Owns every module and drives their lifecycle.
 *
 * <p>Modules tick in registration order, and a module that throws is logged and skipped so that
 * one broken feature cannot stop the rest of the client from working.
 */
public final class ModuleManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("BirowoClient/ModuleManager");

	private final EventBus eventBus;
	private final Map<String, Module> modules = new LinkedHashMap<>();

	/**
	 * @param eventBus the bus that module state changes are posted to, and that drives the tick
	 */
	public ModuleManager(EventBus eventBus) {
		this.eventBus = eventBus;
		eventBus.subscribe(TickEvent.class, this::onTick);
	}

	/**
	 * Registers a module.
	 *
	 * @param module the module to register
	 * @throws IllegalArgumentException if a module with the same id is already registered
	 */
	public void register(Module module) {
		if (modules.putIfAbsent(module.id(), module) != null) {
			throw new IllegalArgumentException("Duplicate module id: " + module.id());
		}

		module.addStateListener(enabled -> eventBus.post(new ModuleToggleEvent(module, enabled)));
	}

	/**
	 * @param id the module identifier
	 * @return the module with that identifier, if registered
	 */
	public Optional<Module> byId(String id) {
		return Optional.ofNullable(modules.get(id));
	}

	/**
	 * @return every registered module, in registration order
	 */
	public Collection<Module> modules() {
		return Collections.unmodifiableCollection(modules.values());
	}

	/**
	 * @return the currently enabled modules, in registration order
	 */
	public List<Module> enabledModules() {
		return modules.values().stream()
				.filter(Module::enabled)
				.toList();
	}

	/**
	 * Ticks every enabled module. Driven by the {@link TickEvent} this manager subscribes to.
	 *
	 * @param event the tick event
	 */
	private void onTick(TickEvent event) {
		for (Module module : modules.values()) {
			if (!module.enabled()) {
				continue;
			}

			try {
				module.onTick();
			} catch (Throwable t) {
				LOGGER.error("Module {} failed to tick", module.id(), t);
			}
		}
	}
}
