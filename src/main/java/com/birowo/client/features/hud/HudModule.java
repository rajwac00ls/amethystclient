package com.birowo.client.features.hud;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import com.birowo.client.core.config.ConfigValue;
import com.birowo.client.core.config.ConfigValues;
import com.birowo.client.core.event.EventBus;
import com.birowo.client.core.event.ModuleToggleEvent;
import com.birowo.client.core.keybind.KeyBindFactory;
import com.birowo.client.core.module.Module;
import com.birowo.client.core.module.ModuleCategory;
import com.birowo.client.core.module.ModuleManager;
import com.birowo.client.platform.combat.PvpVersionService;
import com.birowo.client.platform.render.HudRenderable;

/**
 * Draws a small status readout on the HUD.
 *
 * <p>Every line of the readout is built only when its content changes, so that the render path
 * does not build a text every frame. The frame rate is the exception: it is read a few times a
 * second, because a number that changes that fast cannot be read anyway.
 */
public final class HudModule extends Module implements HudRenderable {
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int ACCENT_COLOR = 0xFF7FE7FF;
	private static final int LINE_HEIGHT = 10;

	/**
	 * The number of frames between two readings of the frame rate.
	 */
	private static final int FPS_INTERVAL_FRAMES = 10;

	private final ConfigValue<Integer> x = setting(ConfigValues.ofInt("x", 4));
	private final ConfigValue<Integer> y = setting(ConfigValues.ofInt("y", 4));
	private final ConfigValue<Boolean> showFps = setting(ConfigValues.ofBoolean("showFps", true));
	private final ConfigValue<Boolean> showCoordinates = setting(ConfigValues.ofBoolean("showCoordinates", true));
	private final ConfigValue<Boolean> showModules = setting(ConfigValues.ofBoolean("showModules", true));
	private final ConfigValue<Boolean> showPvpVersion = setting(ConfigValues.ofBoolean("showPvpVersion", true));

	private final ModuleManager moduleManager;
	private final PvpVersionService pvpVersion;

	private List<Text> enabledModuleLabels = List.of();
	private boolean moduleListDirty = true;

	private Text fpsLabel = Text.empty();
	private Text coordinateLabel = Text.empty();
	private Text pvpLabel = Text.empty();
	private String pvpLabelText = "";
	private double lastX;
	private double lastY;
	private double lastZ;
	private boolean coordinatesKnown;
	private int frames;

	/**
	 * @param moduleManager the registry the module list is read from
	 * @param eventBus      the bus the module list invalidation is driven by
	 * @param keyBinds      the factory used to register this module's key binding
	 * @param pvpVersion    the service that says which PvP rules the server runs with
	 */
	public HudModule(ModuleManager moduleManager, EventBus eventBus, KeyBindFactory keyBinds,
			PvpVersionService pvpVersion) {
		super("hud", "HUD", "Shows a status readout on the screen.", ModuleCategory.CLIENT);

		this.moduleManager = moduleManager;
		this.pvpVersion = pvpVersion;

		eventBus.subscribe(ModuleToggleEvent.class, event -> moduleListDirty = true);
		keyBinds.bind("hud", "key.birowo.hud", GLFW.GLFW_KEY_G, this::toggle);
	}

	@Override
	public void onTick() {
		if (!moduleListDirty) {
			return;
		}

		moduleListDirty = false;

		List<Text> labels = new ArrayList<>();

		for (Module module : moduleManager.enabledModules()) {
			labels.add(Text.literal(module.displayName()));
		}

		enabledModuleLabels = List.copyOf(labels);
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer textRenderer = client.textRenderer;

		int x = this.x.value();
		int y = this.y.value();

		if (showFps.value()) {
			context.drawTextWithShadow(textRenderer, fpsLabel(client), x, y, TEXT_COLOR);
			y += LINE_HEIGHT;
		}

		if (showCoordinates.value() && client.player != null) {
			context.drawTextWithShadow(textRenderer, coordinates(client), x, y, TEXT_COLOR);
			y += LINE_HEIGHT;
		}

		if (showPvpVersion.value() && client.player != null) {
			context.drawTextWithShadow(textRenderer, pvpVersionLabel(), x, y, TEXT_COLOR);
			y += LINE_HEIGHT;
		}

		if (showModules.value()) {
			for (Text label : enabledModuleLabels) {
				context.drawTextWithShadow(textRenderer, label, x, y, ACCENT_COLOR);
				y += LINE_HEIGHT;
			}
		}
	}
	@Override
	protected void onDisable() {
		coordinatesKnown = false;
		frames = 0;
	}

	/**
	 * @param client the client the frame rate is read from
	 * @return the label that shows the frame rate
	 */
	private Text fpsLabel(MinecraftClient client) {
		if (frames % FPS_INTERVAL_FRAMES == 0) {
			fpsLabel = Text.literal(client.getCurrentFps() + " FPS");
		}

		frames++;

		return fpsLabel;
	}

	/**
	 * @return the label that shows the PvP rules that are in use
	 */
	private Text pvpVersionLabel() {
		String text = "PvP " + pvpVersion.current().label();

		if (!text.equals(pvpLabelText)) {
			pvpLabelText = text;
			pvpLabel = Text.literal(text);
		}

		return pvpLabel;
	}

	/**
	 * @param client the client the position is read from
	 * @return the label that shows the position of the player
	 */
	private Text coordinates(MinecraftClient client) {
		double px = client.player.getX();
		double py = client.player.getY();
		double pz = client.player.getZ();

		if (!coordinatesKnown || px != lastX || py != lastY || pz != lastZ) {
			lastX = px;
			lastY = py;
			lastZ = pz;
			coordinatesKnown = true;

			coordinateLabel = Text.literal(String.format("%.1f %.1f %.1f", px, py, pz));
		}

		return coordinateLabel;
	}
}
