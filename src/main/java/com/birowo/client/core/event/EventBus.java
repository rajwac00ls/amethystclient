package com.birowo.client.core.event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A synchronous, in-process publish/subscribe bus.
 *
 * <p>Dispatch is <em>exact class first, then the classes it extends, then the interfaces it
 * implements</em>: a subscriber registered for {@code TickEvent} receives a {@code TickEvent}, and
 * a subscriber registered for {@code Event} receives every event. Handlers run on the thread
 * that posted the event, in registration order.
 *
 * <p>A handler that throws is logged and skipped; it never prevents the remaining handlers from
 * running.
 */
public final class EventBus {
	private static final Logger LOGGER = LoggerFactory.getLogger("BirowoClient/EventBus");

	private final Map<Class<?>, CopyOnWriteArrayList<Subscriber<?>>> subscribers = new ConcurrentHashMap<>();

	/**
	 * Registers a handler that runs every time an event of the given type is posted.
	 *
	 * @param eventType the type of event to listen for
	 * @param handler   the handler to invoke
	 * @param <T>       the event type
	 */
	public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
		subscribers.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
				.add(new Subscriber<>(handler));
	}

	/**
	 * Delivers an event to every matching handler.
	 *
	 * @param event the event to deliver
	 * @param <T>   the event type
	 */
	public <T> void post(T event) {
		for (Class<?> type : typesOf(event.getClass())) {
			CopyOnWriteArrayList<Subscriber<?>> handlers = subscribers.get(type);

			if (handlers == null || handlers.isEmpty()) {
				continue;
			}

			for (Subscriber<?> subscriber : handlers) {
				invoke(event, subscriber);
			}
		}
	}

	/**
	 * The classes an event of the given type is delivered to, in the order they are visited.
	 *
	 * <p>The type itself comes first, then the classes it extends, as long as they are events as
	 * well, and then the interfaces it implements. The interfaces have to be visited on their
	 * own, because an event that is a record implements {@link Event} directly and no class
	 * stands between the two.
	 *
	 * @param type the type of the event
	 * @return the types the event is delivered to
	 */
	private static List<Class<?>> typesOf(Class<?> type) {
		Set<Class<?>> types = new LinkedHashSet<>();

		for (Class<?> current = type; current != null && Event.class.isAssignableFrom(current);
				current = current.getSuperclass()) {
			types.add(current);
		}

		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			collectInterfaces(current, types);
		}

		return List.copyOf(types);
	}

	/**
	 * Adds the interfaces a class implements to the given set.
	 *
	 * @param type  the class whose interfaces are collected
	 * @param types the set the interfaces are added to
	 */
	private static void collectInterfaces(Class<?> type, Set<Class<?>> types) {
		for (Class<?> contract : type.getInterfaces()) {
			if (Event.class.isAssignableFrom(contract)) {
				types.add(contract);
			}

			collectInterfaces(contract, types);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void invoke(T event, Subscriber<?> subscriber) {
		try {
			((Subscriber<T>) subscriber).handler().accept(event);
		} catch (Throwable t) {
			LOGGER.error("Handler for event {} failed", event.getClass().getName(), t);
		}
	}

	private record Subscriber<T>(Consumer<T> handler) {
	}
}
