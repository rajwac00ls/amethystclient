package hack.echo.client.features.impl.misc;

import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.screens.clickgui.glass.GlassUIConstants;
import hack.echo.client.utils.strings.Concat;

public class ClientThemes extends Feature {

    private static final CharSequence THEME_BLUE = Concat.of("Blue");
    private static final CharSequence THEME_RED = Concat.of("Red");
    private static final CharSequence THEME_PURPLE = Concat.of("Purple");
    private static final CharSequence THEME_BLACK = Concat.of("Black");
    private static final CharSequence THEME_WHITE = Concat.of("White");

    private static final CharSequence FONT_INTER = Concat.of("Inter");
    private static final CharSequence FONT_APTOS = Concat.of("Aptos");
    private static final CharSequence FONT_ARIAL = Concat.of("Arial");

    public final ModeSetting colorTheme = new ModeSetting(
        Concat.of("Color Theme"),
        THEME_BLUE,
        THEME_BLUE,
        THEME_RED,
        THEME_PURPLE,
        THEME_BLACK,
        THEME_WHITE
    );

    public final ModeSetting guiFont = new ModeSetting(
        Concat.of("GUI Font"),
        FONT_INTER,
        FONT_INTER,
        FONT_APTOS,
        FONT_ARIAL
    );

    public ClientThemes() {
        super(new FeatureInfo(
            Concat.of("Client Themes"),
            Concat.of("Client color themes and fonts"),
            Category.INTERNALS
        ));
        colorTheme.onChanged(GlassUIConstants::syncTheme);
        guiFont.onChanged(this::applyGuiFont);
    }

    private void applyGuiFont() {
        if (guiFont.is(FONT_APTOS)) {
            Fonts.setGuiFont(Fonts.aptos);
        } else if (guiFont.is(FONT_ARIAL)) {
            Fonts.setGuiFont(Fonts.arial);
        } else {
            Fonts.setGuiFont(Fonts.getInterBold());
        }
    }
}
