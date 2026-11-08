package hack.echo.client.features.impl.render;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.event.impl.EventSetScreen;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.screens.clickgui.glass.UiEffects;
import hack.echo.client.utils.strings.Concat;
import org.joml.Matrix4f;

public class MenuShade extends Feature {

    private final FloatSetting intensity = new FloatSetting(Concat.of("Intensity"), 0.35f, 0f, 1f, 0.01f);
    private final FloatSetting vignette = new FloatSetting(Concat.of("Vignette"), 0.5f, 0f, 1f, 0.01f);
    private final BoolSetting animated = new BoolSetting(Concat.of("Animated"), true);
    private final ColorSetting topColor = new ColorSetting(Concat.of("Top Color"), 12, 10, 30, 255);
    private final ColorSetting bottomColor = new ColorSetting(Concat.of("Bottom Color"), 30, 8, 48, 255);

    private long openedAt;

    public MenuShade() {
        super(new FeatureInfo(
                Concat.of("Menu Shade"),
                Concat.of("Glass shade behind open menus and containers"),
                Category.RENDER));
    }

    @EventSubscribe
    private void onSetScreen(EventSetScreen event) {
        openedAt = UiEffects.time();
    }

    @EventSubscribe
    private void onRender2D(EventRender2DGui event) {
        if (MinecraftCompat.getScreen() == null) return;

        float fade = Math.min(1f, (UiEffects.time() - openedAt) / 180f);
        float a = intensity.getValue() * fade;
        if (a <= 0.01f) return;

        Draw2D draw = event.getDraw2D();
        Matrix4f mat = new Matrix4f();
        float w = event.getScaledWidth();
        float h = event.getScaledHeight();

        UiEffects.gradientVertical(draw, mat, 0, 0, w, h, 0,
                UiEffects.rgba(topColor.getRed(), topColor.getGreen(), topColor.getBlue(), (int) (topColor.getAlpha() * a)),
                UiEffects.rgba(bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), (int) (bottomColor.getAlpha() * a)));

        float v = vignette.getValue();
        if (v > 0.01f) {
            float edgeH = h * 0.10f;
            float edgeW = w * 0.06f;
            int topA = (int) (90 * v);
            int botA = (int) (150 * v);
            int sideA = (int) (70 * v);
            UiEffects.gradientVertical(draw, mat, 0, 0, w, edgeH, 0,
                    UiEffects.rgba(0, 0, 0, topA), UiEffects.rgba(0, 0, 0, 0));
            UiEffects.gradientVertical(draw, mat, 0, h - edgeH, w, edgeH, 0,
                    UiEffects.rgba(0, 0, 0, 0), UiEffects.rgba(0, 0, 0, botA));
            UiEffects.gradientHorizontal(draw, mat, 0, 0, edgeW, h, 0,
                    UiEffects.rgba(0, 0, 0, sideA), UiEffects.rgba(0, 0, 0, 0));
            UiEffects.gradientHorizontal(draw, mat, w - edgeW, 0, edgeW, h, 0,
                    UiEffects.rgba(0, 0, 0, 0), UiEffects.rgba(0, 0, 0, sideA));
        }

        if (animated.getValue()) {
            long now = UiEffects.time();
            float cycle = 13000f;
            float t = (now % cycle) / cycle;
            float sweepX = -0.25f * w + t * (w + 0.5f * w);
            float sweepY = -0.2f * h + t * (h + 0.4f * h);
            UiEffects.glow(draw, mat, sweepX, sweepY, w * 0.5f, h * 0.5f, w * 0.35f,
                    UiEffects.rgba(topColor.getRed(), topColor.getGreen(), topColor.getBlue(), 255), 0.18f * a);
            UiEffects.glow(draw, mat, -0.3f * w, 0.55f * h, w * 0.4f, h * 0.5f, w * 0.28f,
                    UiEffects.rgba(bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), 255), 0.10f * a);
        }
    }
}
