package hack.echo.client.features.impl.misc;

import hack.echo.client.config.ColorConfig;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.features.Category;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.HudFeature;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.utils.VersionInfo;
import hack.echo.client.utils.strings.Concat;
import org.joml.Matrix4f;

public class Version extends HudFeature {

    private static final float FONT_SIZE = 8f;
    private float cachedWidth;
    private float cachedHeight;

    public Version() {
        super(new FeatureInfo(
                Concat.of("Version"),
                Concat.of("Shows the current Echo version and build commit"),
                Category.UTILITY
        ));
        getXPosition().setValue(1f);
        getYPosition().setValue(0.02f);
    }

    @Override
    public float getWidth() {
        return cachedWidth;
    }

    @Override
    public float getHeight() {
        return cachedHeight;
    }

    @Override
    public CharSequence concat() {
        return VersionInfo.get();
    }

    @EventSubscribe
    public void onRender2D(EventRender2DGui event) {
        if (mc.debugEntries.isOverlayVisible()) return;
        if (Fonts.interSemiBold == null) return;

        Concat text = VersionInfo.get();
        cachedWidth = Fonts.interSemiBold.getWidth(text, FONT_SIZE);
        cachedHeight = FONT_SIZE + 2f;

        Draw2D draw = event.getDraw2D();
        draw.textWithShadow(Fonts.interSemiBold, new Matrix4f(), text, getX(), getY(), FONT_SIZE,
                ColorConfig.TEXT_WHITE.getRGB());
    }
}
