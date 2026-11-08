package hack.echo.client.features.impl.render.hud;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.features.Category;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.HudFeature;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.screens.clickgui.glass.UiEffects;
import hack.echo.client.utils.strings.Concat;
import org.joml.Matrix4f;

import java.util.Locale;

import static hack.echo.client.screens.clickgui.glass.GlassUIConstants.RADIUS_MD;
import static hack.echo.client.screens.clickgui.glass.GlassUIConstants.SURFACE_PANEL;

public class Speedbar extends HudFeature {

    private static final float PANEL_WIDTH = 116f;
    private static final float PANEL_HEIGHT = 24f;

    private final FloatSetting maxSpeed = new FloatSetting(Concat.of("Max Speed"), 12f, 2f, 60f, 1f);
    private final FloatSetting glowStrength = new FloatSetting(Concat.of("Glow"), 0.6f, 0f, 1f, 0.05f);
    private final BoolSetting showLabel = new BoolSetting(Concat.of("Show Label"), true);
    private final ColorSetting barColor = new ColorSetting(Concat.of("Bar Color"), 0, 150, 255, 230);
    private final ColorSetting trackColor = new ColorSetting(Concat.of("Track Color"), 255, 255, 255, 40);
    private final ColorSetting textColor = new ColorSetting(Concat.of("Text Color"), 245, 247, 255, 235);

    public Speedbar() {
        super(new FeatureInfo(
                Concat.of("Speedbar"),
                Concat.of("Glass speed bar with animated sheen"),
                Category.RENDER));
    }

    @Override
    public float getWidth() {
        return PANEL_WIDTH;
    }

    @Override
    public float getHeight() {
        return PANEL_HEIGHT;
    }

    @EventSubscribe
    private void onRender2D(EventRender2DGui event) {
        if (isNull()) return;
        if (mc.debugEntries.isOverlayVisible()) return;

        Draw2D draw = event.getDraw2D();
        Matrix4f mat = new Matrix4f();
        float x = getX();
        float y = getY();

        UiEffects.dropShadow(draw, mat, x, y, PANEL_WIDTH, PANEL_HEIGHT, RADIUS_MD, 0xFF000000, 0.55f);
        draw.screenImage(mat, event.getBlurTexture(), x, y, PANEL_WIDTH, PANEL_HEIGHT, RADIUS_MD, 1f);
        draw.rect(mat, x, y, PANEL_WIDTH, PANEL_HEIGHT, RADIUS_MD, SURFACE_PANEL.getRGB());

        double bps = currentSpeed();
        float ratio = (float) Math.min(1.0, bps / maxSpeed.getValue());

        if (showLabel.getValue() && Fonts.interSemiBold != null) {
            String label = String.format(Locale.ROOT, "SPD %.1f m/s", bps);
            draw.text(Fonts.interSemiBold, mat, label, x + 8f, y + 5f, 7f, textColor.getARGB());
        }

        float barX = x + 8f;
        float barY = y + 16f;
        float barW = PANEL_WIDTH - 16f;
        float barH = 6f;
        draw.rect(mat, barX, barY, barW, barH, 3f, trackColor.getARGB());

        float fill = barW * ratio;
        if (fill > 1f) {
            int base = barColor.getARGB();
            UiEffects.gradientHorizontal(draw, mat, barX, barY, fill, barH, 3f,
                    UiEffects.darken(base, 0.45f), base);
            if (glowStrength.getValue() > 0.01f) {
                UiEffects.glow(draw, mat, barX, barY, fill, barH, 4f, base, 0.5f * glowStrength.getValue());
                UiEffects.sheen(draw, mat, barX, barY, fill, barH, UiEffects.time(), base);
            }
        }
    }

    private double currentSpeed() {
        double dx = mc.player.getDeltaMovement().x;
        double dz = mc.player.getDeltaMovement().z;
        return Math.hypot(dx, dz) * 20.0;
    }
}
