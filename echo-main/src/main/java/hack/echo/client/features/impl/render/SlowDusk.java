package hack.echo.client.features.impl.render;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.screens.clickgui.glass.UiEffects;
import hack.echo.client.utils.StatusUtils;
import hack.echo.client.utils.strings.Concat;
import org.joml.Matrix4f;

public class SlowDusk extends Feature {

    private static final FloatSetting Strength = new FloatSetting(Concat.of("Strength"), 0.28f, 0f, 1f, 0.01f);
    private static final BoolSetting SlowFallBoost = new BoolSetting(Concat.of("Slow Fall Boost"), true);
    private static final FloatSetting BoostAmount = new FloatSetting(Concat.of("Boost Amount"), 0.35f, 0f, 1f, 0.05f);
    private static final BoolSetting Stars = new BoolSetting(Concat.of("Stars"), true);
    private static final ColorSetting SkyTop = new ColorSetting(Concat.of("Sky Top"), 42, 16, 74, 255);
    private static final ColorSetting Horizon = new ColorSetting(Concat.of("Horizon"), 255, 140, 60, 255);

    public SlowDusk() {
        super(new FeatureInfo(
                Concat.of("Slow Dusk"),
                Concat.of("Cinematic dusk tint that deepens while slow falling"),
                Category.RENDER));
    }

    public static float getSkyStrength() {
        float boost = SlowFallBoost.getValue() && StatusUtils.hasSlowFalling() ? BoostAmount.getValue() : 0f;
        return Math.max(0f, Math.min(1f, Strength.getValue() + boost));
    }

    public static int mixSkyColor(int vanilla) {
        float s = getSkyStrength();
        int vr = (vanilla >>> 16) & 0xFF;
        int vg = (vanilla >>> 8) & 0xFF;
        int vb = vanilla & 0xFF;
        return UiEffects.rgba(
                (int) (vr + (SkyTop.getRed() - vr) * s),
                (int) (vg + (SkyTop.getGreen() - vg) * s),
                (int) (vb + (SkyTop.getBlue() - vb) * s),
                255);
    }

    public static int getSunsetColor() {
        return UiEffects.rgba(Horizon.getRed(), Horizon.getGreen(), Horizon.getBlue(), 255);
    }

    @EventSubscribe
    private void onRender2D(EventRender2DGui event) {
        if (isNull()) return;
        if (MinecraftCompat.getScreen() != null) return;
        if (mc.debugEntries.isOverlayVisible()) return;

        float a = getSkyStrength();
        if (a <= 0.01f) return;

        Draw2D draw = event.getDraw2D();
        Matrix4f mat = new Matrix4f();
        float w = event.getScaledWidth();
        float h = event.getScaledHeight();

        UiEffects.gradientVertical(draw, mat, 0, h * 0.82f, w, h * 0.18f, 0,
                UiEffects.rgba(0, 0, 0, 0), UiEffects.rgba(0, 0, 0, (int) (150 * a)));

        UiEffects.glow(draw, mat, 0, h * 0.44f - h * 0.09f, w, h * 0.18f, h * 0.06f,
                getSunsetColor(), 0.30f * a);

        if (Stars.getValue() && a > 0.05f) {
            drawStars(draw, mat, w, h, a);
        }
    }

    private static void drawStars(Draw2D draw, Matrix4f mat, float w, float h, float a) {
        long now = UiEffects.time();
        int count = 56;
        float ceiling = h * 0.42f;
        for (int i = 0; i < count; i++) {
            float fx = fract((float) (Math.sin(i * 12.9898 + 4.8982) * 43758.5453));
            float fy = fract((float) (Math.sin(i * 78.233 + 19.2341) * 12543.1234));
            float twinkle = 0.4f + 0.6f * (float) Math.sin(now * 0.0012 + i * 1.7);
            if (twinkle < 0.05f) continue;
            float px = fx * w;
            float py = fy * ceiling;
            draw.rect(mat, px, py, 1.2f, 1.2f, 0, UiEffects.rgba(255, 244, 220, (int) (130 * a * twinkle)));
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}
