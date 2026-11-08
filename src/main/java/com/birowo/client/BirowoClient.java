package com.birowo.client;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.birowo.client.core.command.CommandManager;
import com.birowo.client.core.config.ConfigStore;
import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.event.ModuleToggleEvent;
import com.birowo.client.core.event.TickEvent;
import com.birowo.client.core.gui.GuiLayoutStore;
import com.birowo.client.core.gui.GuiScreenLauncher;
import com.birowo.client.core.module.ModuleManager;
import com.birowo.client.features.combat.AttackLimiter;
import com.birowo.client.features.combat.AimAssistModule;
import com.birowo.client.features.combat.KillAuraModule;
import com.birowo.client.features.combat.TriggerBotModule;
import com.birowo.client.features.esp.BlockEspModule;
import com.birowo.client.features.esp.EspModule;
import com.birowo.client.features.esp.TargetEspModule;
import com.birowo.client.features.gui.ClickGuiModule;
import com.birowo.client.features.hud.HudModule;
import com.birowo.client.platform.command.ClientCommands;
import com.birowo.client.platform.command.HelpCommand;
import com.birowo.client.platform.command.ListCommand;
import com.birowo.client.platform.command.PvpVersionCommand;
import com.birowo.client.platform.command.ToggleCommand;
import com.birowo.client.platform.combat.PvpVersionService;
import com.birowo.client.platform.gui.MinecraftGuiScreenLauncher;
import com.birowo.client.platform.keybind.KeyBindManager;
import com.birowo.client.platform.render.RenderDispatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Entry point of the client.
 *
 * <p>Wiring lives here and nowhere else: the core has no Minecraft dependency, the platform holds
 * every Minecraft-facing service, and the features are plain modules that the registry drives.
 */
public final class BirowoClient implements ClientModInitializer {
	/**
	 * The mod identifier, used for the config file and the translation namespace.
	 */
	public static final String MOD_ID = "birowo";

	private static final Logger LOGGER = LoggerFactory.getLogger("BirowoClient");

	private final EventBus eventBus = new EventBus();
	private final ModuleManager moduleManager = new ModuleManager(eventBus);
	private final CommandManager commandManager = new CommandManager();
	private final KeyBindManager keyBindManager = new KeyBindManager("keys");

	private long tickCount;

	@Override
	public void onInitializeClient() {
		RenderDispatcher renderDispatcher = new RenderDispatcher(moduleManager);

		GuiScreenLauncher guiLauncher = new MinecraftGuiScreenLauncher();
		GuiLayoutStore guiLayoutStore = new GuiLayoutStore(guiLayoutPath());
		ClickGuiModule clickGuiModule = new ClickGuiModule(moduleManager, guiLauncher, guiLayoutStore, keyBindManager);

		// Both combat modules share one limiter, so that they can never attack twice in the same
		// moment, whichever of the two is the faster one.
		PvpVersionService pvpVersion = new PvpVersionService();
		AttackLimiter attackLimiter = new AttackLimiter(pvpVersion);

		moduleManager.register(new EspModule(keyBindManager));
		moduleManager.register(new BlockEspModule(keyBindManager));
		moduleManager.register(new TargetEspModule(eventBus, keyBindManager));
		moduleManager.register(new KillAuraModule(eventBus, keyBindManager, attackLimiter, pvpVersion));
		moduleManager.register(new AimAssistModule(keyBindManager));
		moduleManager.register(new TriggerBotModule(keyBindManager, attackLimiter));
		moduleManager.register(clickGuiModule);
		moduleManager.register(new HudModule(moduleManager, eventBus, keyBindManager, pvpVersion));

		commandManager.register(new ToggleCommand(moduleManager));
		commandManager.register(new ListCommand(moduleManager));
		commandManager.register(new HelpCommand(commandManager));
		commandManager.register(new PvpVersionCommand(pvpVersion));

		ClientCommands.register(commandManager);

		// The start of the tick is used rather than the end so that a rotation applied by a
		// module is picked up by the movement packet sent later in the same tick.
		ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
		WorldRenderEvents.AFTER_ENTITIES.register(renderDispatcher::onWorldRender);

		// The overlay is drawn after the vanilla HUD elements so that it sits above the world but
		// below the chat.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Identifier.of(MOD_ID, "overlay"),
				(context, tickCounter) -> renderDispatcher.onHudRender(context, tickCounter));

		ConfigStore configStore = new ConfigStore(configPath());
		configStore.load(moduleManager.modules());

		// Subscribed after the load so that restoring the saved state does not immediately write
		// it back; only a genuine toggle from here on changes the file.
		eventBus.subscribe(ModuleToggleEvent.class, event -> configStore.save(moduleManager.modules()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			configStore.save(moduleManager.modules());
			clickGuiModule.saveLayout();
		});

		LOGGER.info("BirowoClient ready with {} modules", moduleManager.modules().size());
	}

	private void onStartTick(MinecraftClient client) {
		keyBindManager.onTick();
		eventBus.post(new TickEvent(tickCount++));
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	private static Path guiLayoutPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + "_gui.json");
	}
}
