package com.birowo.client.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings of a single module.
 *
 * <p>Settings are declared in declaration order and looked up by key. Declaration order is
 * preserved so the settings UI can present them in the same order the developer wrote them.
 */
public final class ModuleConfig {
	private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();

	/**
	 * Registers a setting.
	 *
	 * @param value the setting to register
	 * @param <T>   the type of the setting's value
	 * @return the same instance, for chaining
	 */
	public <T> ConfigValue<T> register(ConfigValue<T> value) {
		values.put(value.key(), value);
		return value;
	}

	/**
	 * @return all registered settings, in declaration order
	 */
	public List<ConfigValue<?>> values() {
		return List.copyOf(values.values());
	}
}
