package com.birowo.client.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.core.module.ModuleManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ConfigStoreTest {
	/**
	 * A module with two settings and a state of its own.
	 */
	private static final class SettingModule extends Module {
		private final ConfigValue<Boolean> flag = setting(ConfigValues.ofBoolean("flag", false));
		private final ConfigValue<Double> amount = setting(ConfigValues.ofDouble("amount", 1.5));

		private SettingModule() {
			super("settings", "Settings", "a module with settings", ModuleCategory.MISC);
		}
	}

	private static SettingModule moduleWith(ModuleManager manager) {
		SettingModule module = new SettingModule();
		manager.register(module);
		return module;
	}

	@Test
	void theSettingsSurviveARestart(@TempDir Path directory) {
		Path file = directory.resolve("birowo.json");
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		SettingModule module = moduleWith(manager);

		module.config().register(module.flag);
		module.flag.set(true);
		module.amount.set(9.25);

		new ConfigStore(file).save(manager.modules());

		ModuleManager restored = new ModuleManager(new EventBus());
		SettingModule restoredModule = moduleWith(restored);

		new ConfigStore(file).load(restored.modules());

		assertTrue(restoredModule.flag.value());
		assertEquals(9.25, restoredModule.amount.value(), 0.0001);
	}

	@Test
	void theStateOfAModuleSurvivesARestart(@TempDir Path directory) {
		Path file = directory.resolve("birowo.json");
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		SettingModule module = moduleWith(manager);

		module.setEnabled(true);

		new ConfigStore(file).save(manager.modules());

		ModuleManager restored = new ModuleManager(new EventBus());
		SettingModule restoredModule = moduleWith(restored);

		new ConfigStore(file).load(restored.modules());

		assertTrue(restoredModule.enabled());
	}

	@Test
	void aSettingThatIsNotKnownIsKept(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("birowo.json");
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		moduleWith(manager);

		Files.writeString(file, """
				{"modules":{"settings":{"settings":{"future":42}}}}
				""", StandardCharsets.UTF_8);

		new ConfigStore(file).save(manager.modules());

		JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject settings = root.getAsJsonObject("modules").getAsJsonObject("settings")
				.getAsJsonObject("settings");

		assertEquals(42, settings.get("future").getAsInt());
	}

	@Test
	void aFileThatCannotBeReadIsBackedUpAndTheDefaultsAreUsed(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("birowo.json");
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		SettingModule module = moduleWith(manager);

		Files.writeString(file, "this is not a json file", StandardCharsets.UTF_8);

		new ConfigStore(file).load(manager.modules());

		assertFalse(module.flag.value());
		assertEquals(1.5, module.amount.value(), 0.0001);
		assertTrue(Files.exists(directory.resolve("birowo.json.bak")));
	}

	@Test
	void aFileThatDoesNotExistIsNotAnError(@TempDir Path directory) {
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		SettingModule module = moduleWith(manager);

		new ConfigStore(directory.resolve("missing.json")).load(manager.modules());

		assertFalse(module.enabled());
	}

	@Test
	void theModulesThatAreSavedAreTheOnesThatAreGiven(@TempDir Path directory) {
		Path file = directory.resolve("birowo.json");
		EventBus bus = new EventBus();
		ModuleManager manager = new ModuleManager(bus);
		moduleWith(manager);

		new ConfigStore(file).save(List.of());

		assertFalse(Files.exists(file));
	}
}
