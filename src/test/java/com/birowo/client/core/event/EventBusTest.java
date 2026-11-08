package com.birowo.client.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EventBusTest {
	private record Started(String name) implements Event {
	}

	private record Stopped(String name) implements Event {
	}

	@Test
	void aHandlerOfTheExactClassIsCalled() {
		EventBus bus = new EventBus();
		List<String> seen = new ArrayList<>();

		bus.subscribe(Started.class, event -> seen.add(event.name()));

		bus.post(new Started("first"));

		assertEquals(List.of("first"), seen);
	}

	@Test
	void aHandlerOfTheParentClassIsCalledAsWell() {
		EventBus bus = new EventBus();
		List<String> seen = new ArrayList<>();

		bus.subscribe(Event.class, event -> seen.add("event"));
		bus.subscribe(Started.class, event -> seen.add("started"));

		bus.post(new Started("first"));

		assertEquals(List.of("started", "event"), seen);
	}

	@Test
	void aHandlerOfAnotherClassIsNotCalled() {
		EventBus bus = new EventBus();
		List<String> seen = new ArrayList<>();

		bus.subscribe(Stopped.class, event -> seen.add(event.name()));

		bus.post(new Started("first"));

		assertTrue(seen.isEmpty());
	}

	@Test
	void aHandlerThatFailsDoesNotStopTheOthers() {
		EventBus bus = new EventBus();
		List<String> seen = new ArrayList<>();

		bus.subscribe(Started.class, event -> {
			throw new IllegalStateException("this handler is broken");
		});
		bus.subscribe(Started.class, event -> seen.add(event.name()));

		bus.post(new Started("first"));

		assertEquals(List.of("first"), seen);
	}

	@Test
	void theHandlersRunInTheOrderTheyWereRegistered() {
		EventBus bus = new EventBus();
		List<String> seen = new ArrayList<>();

		bus.subscribe(Started.class, event -> seen.add("first"));
		bus.subscribe(Started.class, event -> seen.add("second"));
		bus.subscribe(Started.class, event -> seen.add("third"));

		bus.post(new Started("event"));

		assertEquals(List.of("first", "second", "third"), seen);
	}
}
