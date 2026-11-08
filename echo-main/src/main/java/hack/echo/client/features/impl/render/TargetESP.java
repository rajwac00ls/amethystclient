package hack.echo.client.features.impl.render;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.event.impl.EventShieldBreak;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.impl.render.targetespmodes.AuroraMode;
import hack.echo.client.features.impl.render.targetespmodes.ChainsMode;
import hack.echo.client.features.impl.render.targetespmodes.CrownMode;
import hack.echo.client.features.impl.render.targetespmodes.GhostMode;
import hack.echo.client.features.impl.render.targetespmodes.MarkerMode;
import hack.echo.client.features.impl.render.targetespmodes.ShieldBreakMode;
import hack.echo.client.utils.strings.Concat;

public class TargetESP extends Feature {
    public static TargetESP instance;
	public TargetESP() {
		super(new FeatureInfo(
			Concat.of("Target ESP"),
			Concat.of("Highlights current target"),
			Category.RENDER
		));
        instance = this;
        modeSetting.onChanged(() -> {
            ghostMode.reset();
            markerMode.reset();
            auroraMode.reset();
            chainsMode.reset();
            crownMode.reset();
            shieldBreakMode.reset();
        });
	}

    @Override
    public void onDisable() {
        ghostMode.reset();
        markerMode.reset();
        auroraMode.reset();
        chainsMode.reset();
        crownMode.reset();
        shieldBreakMode.reset();
    }
    private static final CharSequence GHOST_MODE = Concat.of("Ghost");
    private static final CharSequence MARKER_MODE = Concat.of("Marker");
    private static final CharSequence AURORA_MODE = Concat.of("Aurora");
    private static final CharSequence CHAINS_MODE = Concat.of("Chains");
    private static final CharSequence CROWN_MODE = Concat.of("Crown");
    private static final CharSequence SHIELD_BREAK_MODE = Concat.of("Shield Break");

    public final ModeSetting modeSetting = new ModeSetting(
        Concat.of("Mode"),
        GHOST_MODE,
        GHOST_MODE,
        MARKER_MODE,
        AURORA_MODE,
        CHAINS_MODE,
        CROWN_MODE,
        SHIELD_BREAK_MODE
    );

    private final ColorSetting colorSetting = new ColorSetting(
        Concat.of("Color"),
        255,
        255,
        255,
        200,
        true
    );

    private final GhostMode ghostMode = new GhostMode(colorSetting);
    private final MarkerMode markerMode = new MarkerMode(colorSetting);
    private final AuroraMode auroraMode = new AuroraMode(colorSetting);
    private final ChainsMode chainsMode = new ChainsMode(colorSetting);
    private final CrownMode crownMode = new CrownMode(colorSetting);
    private final ShieldBreakMode shieldBreakMode = new ShieldBreakMode(colorSetting);

    public static boolean isGhostActive() {
        return instance != null && instance.isEnabled() && instance.modeSetting.is(GHOST_MODE);
    }

    public static boolean isMarkerActive() {
        return instance != null && instance.isEnabled() && instance.modeSetting.is(MARKER_MODE);
    }

    public static boolean isAuroraActive() {
        return instance != null && instance.isEnabled() && instance.modeSetting.is(AURORA_MODE);
    }

    public static boolean isChainsActive() {
        return instance != null && instance.isEnabled() && instance.modeSetting.is(CHAINS_MODE);
    }

    public static boolean isCrownActive() {
        return instance != null && instance.isEnabled() && instance.modeSetting.is(CROWN_MODE);
    }

    public static boolean isShieldBreakActive() {
        return instance != null && instance.isEnabled() && instance.modeSetting.is(SHIELD_BREAK_MODE);
    }

    @EventSubscribe
    private void onRender3D(EventRender3D e) {
        if (isNull()) return;
        if (modeSetting.is(GHOST_MODE)) {
            ghostMode.onRender3D(e);
        } else if (modeSetting.is(MARKER_MODE)) {
            markerMode.onRender3D(e);
        } else if (modeSetting.is(AURORA_MODE)) {
            auroraMode.onRender3D(e);
        } else if (modeSetting.is(CHAINS_MODE)) {
            chainsMode.onRender3D(e);
        } else if (modeSetting.is(CROWN_MODE)) {
            crownMode.onRender3D(e);
        } else if (modeSetting.is(SHIELD_BREAK_MODE)) {
            shieldBreakMode.onRender3D(e);
        }
    }

    @EventSubscribe
    private void onShieldBreak(EventShieldBreak e) {
        if (isNull()) return;
        if (!modeSetting.is(SHIELD_BREAK_MODE)) return;
        shieldBreakMode.onShieldBreak(e.getPlayer());
    }
}

