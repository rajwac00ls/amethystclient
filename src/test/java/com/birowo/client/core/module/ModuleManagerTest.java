package com.birowo.client.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.event.TickEvent;

class ModuleManagerTest {
	/**
	 * A module that counts what the manager does to it.
	 */
	private static final class CountingModule extends Module {
		private int enables;
		private int disables;
		private int ticks;
		private boolean broken;

		private CountingModule(String id) {
			super(id, id, "a module that counts", ModuleCategory.MISC);
		}

		@Override
		protected void onEnable() {
			enables++;
		}

		@Override
		protected void onDisable() {
			disables++;
		}

		@Override
		public void onTick() {
			ticks++;

			if (broken) {
				throw new IllegalStateException("this module is broken");
			}
		}
	}

	@Test
	void aModuleIsEnabledAndDisabledExactlyOnce() {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		CountingModule module = new CountingModule("counting");

		manager.register(module);

		module.setEnabled(true);
		module.setEnabled(true);
		module.setEnabled(false);
		module.setEnabled(false);

		assertEquals(1, module.enables);
		assertEquals(1, module.disables);
	}

	@Test
	void onlyTheEnabledModulesAreTicked() {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		CountingModule module = new CountingModule("counting");

		manager.register(module);

		bus.post(new TickEvent(0));

		assertEquals(0, module.ticks);

		module.setEnabled(true);
		bus.post(new TickEvent(1));

		assertEquals(1, module.ticks);

		module.setEnabled(false);
		bus.post(new TickEvent(2));

		assertEquals(1, module.ticks);
	}

	@Test
	void aModuleThatFailsDoesNotStopTheOthers() {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		CountingModule broken = new CountingModule("broken");
		CountingModule healthy = new CountingModule("healthy");

		manager.register(broken);
		manager.register(healthy);

		broken.setEnabled(true);
		healthy.setEnabled(true);
		broken.broken = true;

		bus.post(new TickEvent(0));

		assertEquals(1, broken.ticks);
		assertEquals(1, healthy.ticks);
	}

	@Test
	void aModuleIsFoundByItsIdentifier() {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		CountingModule module = new CountingModule("counting");

		manager.register(module);

		assertTrue(manager.byId("counting").isPresent());
		assertTrue(manager.byId("missing").isEmpty());
	}

	@Test
	void aModuleCannotBeRegisteredTwice() {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);

		manager.register(new CountingModule("counting"));

		assertThrows(IllegalArgumentException.class, () -> manager.register(new CountingModule("counting")));
	}

	@Test
	void theEnabledModulesAreListedInTheOrderTheyWereRegistered() {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		CountingModule first = new CountingModule("first");
		CountingModule second = new CountingModule("second");

		manager.register(first);
		manager.register(second);

		second.setEnabled(true);
		first.setEnabled(true);

		List<String> enabled = new ArrayList<>();

		for (Module module : manager.enabledModules()) {
			enabled.add(module.id());
		}

		assertEquals(List.of("first", "second"), enabled);
		assertFalse(manager.modules().isEmpty());
	}
}
