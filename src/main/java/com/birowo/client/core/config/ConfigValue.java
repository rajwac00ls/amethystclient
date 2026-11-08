package com.birowo.client.core.config;

import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;

/**
 * A single typed, serializable setting of a module.
 *
 * <p>A value is always present: reading a malformed value from disk leaves the default in place
 * rather than propagating the error, so a corrupt config can never prevent the client from
 * starting.
 *
 * @param <T> the type of the value
 */
public final class ConfigValue<T> {
	private final String key;
	private final T defaultValue;
	private final TypeAdapter<T> adapter;

	private T value;
	private Function<T, String> formatter;

	ConfigValue(String key, T defaultValue, TypeAdapter<T> adapter) {
		this.key = Objects.requireNonNull(key, "key");
		this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
		this.adapter = Objects.requireNonNull(adapter, "adapter");
		this.value = defaultValue;
	}

	/**
	 * @return the key this value is stored under in the config file
	 */
	public String key() {
		return key;
	}

	/**
	 * @return the current value
	 */
	public T value() {
		return value;
	}

	/**
	 * Sets the value.
	 *
	 * @param value the new value, never {@code null}
	 */
	public void set(T value) {
		this.value = Objects.requireNonNull(value, "value");
	}

	/**
	 * Sets the way the value is written for the reader of the interface.
	 *
	 * @param formatter the formatter, or {@code null} to show the value as it is
	 */
	void format(Function<T, String> formatter) {
		this.formatter = formatter;
	}

	/**
	 * @return the value as it is shown in the settings of the interface
	 */
	public String display() {
		return formatter == null ? String.valueOf(value) : formatter.apply(value);
	}

	/**
	 * Writes this value into the given JSON object.
	 *
	 * @param target the object to write into
	 */
	void write(JsonObject target) {
		JsonElement encoded;

		try {
			encoded = adapter.toJsonTree(value);
		} catch (RuntimeException e) {
			// A value the adapter cannot encode must not cost the user their whole config file.
			encoded = adapter.toJsonTree(defaultValue);
		}

		target.add(key, encoded);
	}

	/**
	 * Moves the value one step in the given direction.
	 *
	 * <p>Booleans turn on and off, numbers move by one step and enumerations move to the next or
	 * the previous constant. A value that has no meaningful direction, such as a string, is left
	 * alone.
	 *
	 * @param direction the direction to move the value in; positive moves forward
	 * @return whether the value changed
	 */
	public boolean shift(int direction) {
		return shift(direction, 1);
	}

	/**
	 * Moves the value by the given number of steps in the given direction.
	 *
	 * @param direction the direction to move the value in; positive moves forward
	 * @param steps     the number of steps to move the value by
	 * @return whether the value changed
	 */
	@SuppressWarnings("unchecked")
	public boolean shift(int direction, int steps) {
		Object current = value;

		if (current instanceof Boolean flag) {
			set((T) (direction > 0 ? Boolean.TRUE : Boolean.FALSE));
			return true;
		}

		if (current instanceof Integer number) {
			set((T) Integer.valueOf(number + direction * steps));
			return true;
		}

		if (current instanceof Double number) {
			set((T) Double.valueOf(number + direction * steps * 0.1D));
			return true;
		}

		if (current instanceof Enum<?> constant) {
			Object[] constants = constant.getDeclaringClass().getEnumConstants();
			int index = Math.floorMod(constant.ordinal() + direction * steps, constants.length);
			set((T) constants[index]);
			return true;
		}

		return false;
	}

	/**
	 * Reads this value from the given JSON element, keeping the current value if the element does
	 * not match the expected type.
	 *
	 * @param element the element to read
	 * @return {@code true} if the element was applied
	 */
	boolean read(JsonElement element) {
		T parsed;

		try {
			parsed = adapter.fromJsonTree(element);
		} catch (RuntimeException e) {
			return false;
		}

		if (parsed == null) {
			return false;
		}

		this.value = parsed;
		return true;
	}
}
