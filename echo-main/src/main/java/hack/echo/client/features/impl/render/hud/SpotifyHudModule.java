package hack.echo.client.features.impl.render.hud;

import hack.echo.client.Echo;
import hack.echo.client.config.ColorConfig;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.features.Category;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.HudFeature;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.spotify.Song;
import hack.echo.client.utils.strings.Concat;
import org.joml.Matrix4f;

import static hack.echo.client.screens.clickgui.glass.GlassUIConstants.*;

public class SpotifyHudModule extends HudFeature {

    private final ModeSetting spotifyMode = new ModeSetting(Concat.of("Spotify Mode"), "Glass", "Glass", "Classic");
    private final BoolSetting showProgress = new BoolSetting(Concat.of("Show Progress"), true);
    private final BoolSetting showCover = new BoolSetting(Concat.of("Show Cover"), true, o -> spotifyMode.is("Glass"));
    private final ColorSetting spotifyPrimaryColor = new ColorSetting(Concat.of("Primary Color"), ColorConfig.SECONDARY_ACCENT.getRGB(), o -> spotifyMode.is("Classic"));
    private final ColorSetting spotifySecondaryColor = new ColorSetting(Concat.of("Secondary Color"), ColorConfig.TEXT_LIGHT_GRAY.getRGB(), o -> spotifyMode.is("Classic"));

    private float cachedWidth = 60f;
    private float cachedHeight = SETTING_HEIGHT;
    private long lastUpdateTime = 0;

    public SpotifyHudModule() {
        super(new FeatureInfo(
            Concat.of("Spotify"),
            Concat.of("Display currently playing song from Spotify"),
            Category.RENDER
        ));
        getXPosition().setValue(0.5f);
        getYPosition().setValue(0.3f);
    }

    @Override
    public float getWidth() {
        return cachedWidth;
    }

    @Override
    public float getHeight() {
        return cachedHeight;
    }

    @EventSubscribe
    public void onRender2D(EventRender2DGui event) {
        if (mc.debugEntries.isOverlayVisible()) return;

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > 500) {
            Echo.spotifyManager.updateCurrentSong();
            lastUpdateTime = now;
        }

        Song song = Echo.spotifyManager.getCurrentSong();
        if (song == null) {
            String status = Echo.spotifyManager.getStatusMessage();
            if (status.isEmpty()) status = "Spotify";
            renderPlaceholder(event.getDraw2D(), new Matrix4f(), event, status);
            return;
        }

        var draw = event.getDraw2D();
        Matrix4f mat = new Matrix4f();
        if (spotifyMode.is("Glass")) {
            renderGlass(draw, mat, song, event);
        } else {
            renderClassic(draw, mat, song, event);
        }
    }

    private void renderPlaceholder(Draw2D draw, Matrix4f mat, EventRender2DGui event, String status) {
        if (Fonts.inter == null) return;

        Concat text = Concat.of(status);
        float fontSize = 7f;
        float textWidth = Fonts.inter.getWidth(text, fontSize);
        float width = 1.5f + 6f + textWidth + PADDING;
        cachedWidth = width;
        cachedHeight = SETTING_HEIGHT;

        float x = getX();
        float y = getY();

        draw.screenImage(mat, event.getBlurTexture(), x, y, width, SETTING_HEIGHT, RADIUS_MD, 1f);
        draw.rect(mat, x, y, width, SETTING_HEIGHT, RADIUS_MD, SURFACE_PANEL.getRGB());
        draw.rect(mat, x, y + 2, 1.5f, SETTING_HEIGHT - 4, 0, ACCENT_PRIMARY.getRGB());
        draw.text(Fonts.inter, mat, text, x + 1.5f + 6f, y + SETTING_HEIGHT / 2f - 4.5f, fontSize, argb(TEXT_ON_SURFACE_SECONDARY));
    }

    private void renderGlass(Draw2D draw, Matrix4f mat, Song song, EventRender2DGui event) {
        if (Fonts.inter == null) return;

        float fontSize = 7f;
        float spacing = 6f;
        float accentBarWidth = 1.5f;

        Concat artistText = Concat.of(song.author);
        Concat titleText = Concat.of(song.name);
        float artistWidth = Fonts.inter.getWidth(artistText, fontSize);
        float titleWidth = Fonts.inter.getWidth(titleText, fontSize);

        float coverSize = showCover.getValue() ? SETTING_HEIGHT - 4 : 0f;
        float coverGap = coverSize > 0 ? spacing : 0f;

        float textAreaWidth = Math.max(artistWidth, titleWidth);
        float bottomRowWidth = textAreaWidth;
        float progressHeight = showProgress.getValue() && song.duration > 0 ? 2f : 0f;
        float progressGap = progressHeight > 0 ? 2f : 0f;

        float textBlockHeight = fontSize * 2f + progressGap + progressHeight + 2f;
        float totalHeight = Math.max(SETTING_HEIGHT, coverSize + 4f);
        totalHeight = Math.max(totalHeight, textBlockHeight + 4f);

        float width = accentBarWidth + spacing + coverSize + coverGap + textAreaWidth + PADDING;
        cachedWidth = width;
        cachedHeight = totalHeight;

        float x = getX();
        float y = getY();

        draw.screenImage(mat, event.getBlurTexture(), x, y, width, totalHeight, RADIUS_MD, 1f);
        draw.rect(mat, x, y, width, totalHeight, RADIUS_MD, SURFACE_PANEL.getRGB());
        draw.rect(mat, x, y + 2, accentBarWidth, totalHeight - 4, 0, ACCENT_PRIMARY.getRGB());

        float currentX = x + accentBarWidth + spacing;

        if (coverSize > 0 && song.cover != null) {
            draw.image(mat, song.cover, currentX, y + 2, coverSize, coverSize, 0, 1f);
            currentX += coverSize + coverGap;
        }

        float textY = y + (totalHeight - textBlockHeight) / 2f;
        draw.text(Fonts.inter, mat, titleText, currentX, textY, fontSize, argb(TEXT_ON_SURFACE_PRIMARY));
        textY += fontSize + 2f;
        draw.text(Fonts.inter, mat, artistText, currentX, textY, fontSize, argb(TEXT_ON_SURFACE_SECONDARY));

        if (showProgress.getValue() && song.duration > 0) {
            textY += fontSize + progressGap;
            float progressWidth = textAreaWidth;
            draw.rect(mat, currentX, textY, progressWidth, progressHeight, RADIUS_SM, SURFACE_INTERACTIVE_MUTED.getRGB());

            long elapsed = song.isPlaying ? song.progressMs + (System.currentTimeMillis() - song.initTimeMs) : song.progressMs;
            float fraction = Math.min(1f, (float) elapsed / (float) song.duration);
            float fillWidth = progressWidth * fraction;
            if (fillWidth > 0) {
                draw.rect(mat, currentX, textY, fillWidth, progressHeight, RADIUS_SM, ACCENT_PRIMARY.getRGB());
            }
        }
    }

    private void renderClassic(Draw2D draw, Matrix4f mat, Song song, EventRender2DGui event) {
        if (Fonts.interSemiBold == null) return;

        float fontSize = 9f;
        float spacing = 4f;

        Concat text = Concat.of(song.author, Concat.of(" - "), Concat.of(song.name));
        float textWidth = Fonts.interSemiBold.getWidth(text, fontSize);

        String progressStr = formatTime(song) + (song.isPlaying ? "" : " (paused)");
        Concat progressText = Concat.of(progressStr);
        float progressWidth = Fonts.interSemiBold.getWidth(progressText, fontSize);

        float width = Math.max(textWidth, progressWidth);
        cachedWidth = width;
        cachedHeight = fontSize * 2f + spacing;

        float x = getX();
        float y = getY();

        draw.textWithShadow(Fonts.interSemiBold, mat, text, x, y, fontSize, spotifyPrimaryColor.getARGB());
        draw.textWithShadow(Fonts.interSemiBold, mat, progressText, x, y + fontSize + spacing, fontSize, spotifySecondaryColor.getARGB());
    }

    private String formatTime(Song song) {
        long elapsed = song.isPlaying ? song.progressMs + (System.currentTimeMillis() - song.initTimeMs) : song.progressMs;
        if (elapsed > song.duration) elapsed = song.duration;

        long totalSecs = elapsed / 1000;
        long totalDurationSecs = song.duration / 1000;

        long min = totalSecs / 60;
        long sec = totalSecs % 60;
        long durMin = totalDurationSecs / 60;
        long durSec = totalDurationSecs % 60;

        return String.format("%d:%02d / %d:%02d", min, sec, durMin, durSec);
    }
}
