package hack.echo.client.features.impl.render;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.Echo;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.event.impl.MousePressEvent;
import hack.echo.client.features.Category;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.HudFeature;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.render2.api.CrossTexture;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Font;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.spotify.Song;
import hack.echo.client.spotify.WindowsMediaManager;
import hack.echo.client.utils.strings.Concat;
import org.joml.Matrix4f;

import static hack.echo.client.screens.clickgui.glass.GlassUIConstants.*;

public class MusicDisplay extends HudFeature {

    public MusicDisplay() {
        super(new FeatureInfo(
            Concat.of("Music Display"),
            Concat.of("Floating music overlay with playback controls"),
            Category.RENDER
        ));
        getXPosition().setValue(0.5f);
        getYPosition().setValue(0.05f);
    }

    private final ModeSetting musicSource = new ModeSetting(
        Concat.of("Source"), "Spotify", "Spotify", "Windows", "Auto");
    private final BoolSetting showControls = new BoolSetting(Concat.of("Show Controls"), true);
    private final BoolSetting showCover = new BoolSetting(Concat.of("Show Cover"), true);
    private final FloatSetting overlayScale = new FloatSetting(Concat.of("Scale"), 1.0f, 0.4f, 2.5f, 0.05f);

    private final ColorSetting bgColor = new ColorSetting(Concat.of("Background"), 0xAA1A1A1A);
    private final ColorSetting accentColor = new ColorSetting(Concat.of("Accent"), 0xFF1DB954);
    private final ColorSetting titleColor = new ColorSetting(Concat.of("Title Color"), 0xFFFFFFFF);
    private final ColorSetting artistColor = new ColorSetting(Concat.of("Artist Color"), 0xFFAAAAAA);
    private final ColorSetting timeColor = new ColorSetting(Concat.of("Time Color"), 0xFFCCCCCC);
    private final ColorSetting progressColor = new ColorSetting(Concat.of("Progress Color"), 0xFF1DB954);
    private final ColorSetting progressBgColor = new ColorSetting(Concat.of("Progress Background"), 0x40FFFFFF);

    private static final float PANEL_WIDTH = 200f;
    private static final float COVER_SIZE = 40f;
    private static final float BASE_HEIGHT = 52f;
    private static final float CTRL_HEIGHT = 20f;

    private float cachedWidth = PANEL_WIDTH;
    private float cachedHeight = BASE_HEIGHT + CTRL_HEIGHT;
    private long lastPoll = 0;

    private String title = "";
    private String artist = "";
    private String sourceApp = "";
    private boolean playing = false;
    private double progress = 0.0;
    private double duration = 0.0;
    private int volume = 100;
    private boolean sourceReady = false;
    private boolean usingWindows = false;

    @Override
    public float getWidth() {
        return cachedWidth;
    }

    @Override
    public float getHeight() {
        return cachedHeight;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        WindowsMediaManager.getInstance().start();
    }

    @Override
    public void onDisable() {
        WindowsMediaManager.getInstance().stop();
        super.onDisable();
    }

    // ─── Data ──────────────────────────────────────────────────────

    private void poll() {
        String src = musicSource.getValue().toString();
        usingWindows = src.equals("Windows") || (src.equals("Auto") && WindowsMediaManager.getInstance().isConfigured());

        if (usingWindows) {
            WindowsMediaManager.getInstance().start();
            if (!WindowsMediaManager.getInstance().isConfigured()) {
                title = "Windows media unavailable";
                artist = WindowsMediaManager.getInstance().isBridgePresent()
                        ? "Bridge starting..."
                        : "Missing: " + WindowsMediaManager.getInstance().getBridgePath();
                sourceApp = "";
                playing = false;
                progress = 0.0;
                duration = 0.0;
                volume = 100;
                sourceReady = false;
            } else {
                var st = WindowsMediaManager.getInstance().getCurrentStatus();
                title = st.title();
                artist = st.artist();
                sourceApp = WindowsMediaManager.friendlyAppName(st.sourceApp());
                playing = st.isPlaying();
                progress = st.positionSeconds();
                duration = st.durationSeconds();
                volume = st.volumePercent();
                sourceReady = st.hasMedia();
                if (!sourceReady) {
                    if (title.isBlank()) title = "Nothing playing";
                    artist = "";
                    sourceApp = "";
                }
            }
        } else {
            long now = System.currentTimeMillis();
            if (now - lastPoll > 500) {
                Echo.spotifyManager.updateCurrentSong();
                lastPoll = now;
            }
            Song s = Echo.spotifyManager.getCurrentSong();
            if (s != null) {
                title = s.name;
                artist = s.author;
                sourceApp = "";
                playing = s.isPlaying;
                long elapsed = s.isPlaying ? s.progressMs + (System.currentTimeMillis() - s.initTimeMs) : s.progressMs;
                progress = elapsed / 1000.0;
                duration = s.duration / 1000.0;
                volume = 100;
                sourceReady = true;
            } else {
                title = Echo.spotifyManager.getStatusMessage();
                if (title.isEmpty()) title = "Nothing playing";
                artist = "";
                sourceApp = "";
                playing = false;
                progress = 0.0;
                duration = 0.0;
                volume = 100;
                sourceReady = false;
            }
        }
    }

    // ─── Sizing / positioning ──────────────────────────────────────

    private float panelW() {
        return PANEL_WIDTH * overlayScale.getValue();
    }

    private float panelH() {
        float h = BASE_HEIGHT * overlayScale.getValue();
        if (showControls.getValue()) h += CTRL_HEIGHT * overlayScale.getValue();
        return h;
    }

    private double mouseX() {
        return mc.mouseHandler.xpos() / 2.0;
    }

    private double mouseY() {
        return mc.mouseHandler.ypos() / 2.0;
    }

    // ─── Render ────────────────────────────────────────────────────

    @EventSubscribe
    private void onRender2D(EventRender2DGui event) {
        if (mc.debugEntries.isOverlayVisible()) return;
        poll();

        cachedWidth = panelW();
        cachedHeight = panelH();

        Draw2D draw = event.getDraw2D();
        Matrix4f mat = new Matrix4f();
        float s = overlayScale.getValue();
        float x = getX();
        float y = getY();
        float pw = panelW();
        float ph = panelH();

        float r = RADIUS_MD * Math.min(1f, s);
        draw.screenImage(mat, event.getBlurTexture(), x, y, pw, ph, r, 1f);
        draw.rect(mat, x, y, pw, ph, r, bgColor.getARGB());

        if (sourceReady) {
            renderContent(draw, mat, x, y, pw, ph, s);
        } else {
            renderPlaceholder(draw, mat, x, y, pw, ph, s);
        }
    }

    private void renderContent(Draw2D draw, Matrix4f mat, float x, float y, float pw, float ph, float s) {
        float inset = 6f * s;
        float ix = x + inset;
        float iy = y + inset;
        float iw = pw - inset * 2;
        float cover = showCover.getValue() ? COVER_SIZE * s : 0f;
        float tx = ix + cover + (cover > 0 ? 6f * s : 0f);
        float tw = iw - (cover > 0 ? cover + 6f * s : 0f);

        CrossTexture coverTex = coverTexture();
        if (cover > 0 && coverTex != null) {
            draw.image(mat, coverTex, ix, iy, cover, cover, 4f * s, 1f);
        }

        float ts = 8f * s;
        float as = 6.5f * s;
        float ps = 2f * s;

        Font titleFont = Fonts.interSemiBold != null ? Fonts.interSemiBold : Fonts.inter;
        if (titleFont != null) {
            if (sourceApp.isBlank()) {
                draw.text(titleFont, mat, Concat.of(trimToWidth(title, tw, ts, titleFont)), tx, iy, ts, titleColor.getARGB());
            } else {
                String suffix = " - " + sourceApp;
                float suffixW = titleFont.getWidth(suffix, ts);
                String titlePart = trimToWidth(title, Math.max(1f, tw - suffixW), ts, titleFont);
                draw.text(titleFont, mat, Concat.of(titlePart + suffix), tx, iy, ts, titleColor.getARGB());
            }
        }

        float ay = iy + ts + 2f * s;
        if (Fonts.inter != null) {
            draw.text(Fonts.inter, mat, Concat.of(trimToWidth(artist, tw, as, Fonts.inter)), tx, ay, as, artistColor.getARGB());
        }

        if (duration > 0) {
            float py = iy + ts + as + 5f * s;
            draw.rect(mat, tx, py, tw, ps, 1f, progressBgColor.getARGB());
            float fill = (float) Math.min(1.0, progress / duration);
            if (fill > 0) {
                draw.rect(mat, tx, py, tw * fill, ps, 1f, progressColor.getARGB());
            }
            float ty = py + ps + 1f * s;
            float tms = 5.5f * s;
            if (Fonts.inter != null) {
                draw.text(Fonts.inter, mat, Concat.of(timeStr()), tx, ty, tms, timeColor.getARGB());
            }
        }

        if (showControls.getValue()) {
            renderControls(draw, mat, x, y, pw, ph, s);
        }
    }

    private CrossTexture coverTexture() {
        if (usingWindows) {
            return WindowsMediaManager.getInstance().getArtwork();
        }
        Song s = Echo.spotifyManager.getCurrentSong();
        return s != null ? s.cover : null;
    }

    private void renderPlaceholder(Draw2D draw, Matrix4f mat, float x, float y, float pw, float ph, float s) {
        if (Fonts.inter == null) return;
        float ts = 8f * s;
        float iy = y + (ph - ts) / 2f;
        float maxW = pw - 12f * s;
        draw.text(Fonts.inter, mat, Concat.of(trimToWidth(title, maxW, ts, Fonts.inter)), x + 6f * s, iy, ts, artistColor.getARGB());
    }

    // ─── Controls ──────────────────────────────────────────────────

    private void renderControls(Draw2D draw, Matrix4f mat, float x, float y, float pw, float ph, float s) {
        if (Fonts.inter == null) return;
        float ctrlW = CTRL_HEIGHT * s;
        float gap = 4f * s;
        float cx = x + 6f * s;
        float cy = y + (BASE_HEIGHT * s);
        float glyphSize = 7f * s;

        drawButton(draw, mat, cx, cy, ctrlW, "|<", glyphSize, artistColor.getARGB());
        drawButton(draw, mat, cx + (ctrlW + gap), cy, ctrlW, playing ? "||" : ">", glyphSize, titleColor.getARGB());
        drawButton(draw, mat, cx + 2 * (ctrlW + gap), cy, ctrlW, ">|", glyphSize, artistColor.getARGB());

        float vx = cx + 3 * (ctrlW + gap) + 10f * s;
        float vw = (x + pw - 6f * s) - vx;
        if (vw > 12f * s) {
            float sh = 2.5f * s;
            float sy = cy + (ctrlW - sh) / 2f;
            draw.rect(mat, vx, sy, vw, sh, 1f, progressBgColor.getARGB());
            float vf = Math.max(0f, Math.min(1f, volume / 100f));
            if (vf > 0) {
                draw.rect(mat, vx, sy, vw * vf, sh, 1f, accentColor.getARGB());
            }
            float knob = 5f * s;
            draw.rect(mat, vx + vw * vf - knob / 2f, sy + (sh - knob) / 2f, knob, knob, knob / 2f, titleColor.getARGB());
        }
    }

    private void drawButton(Draw2D draw, Matrix4f mat, float bx, float by, float size, String label,
            float glyphSize, int glyphColor) {
        draw.rect(mat, bx, by, size, size, RADIUS_SM, progressBgColor.getARGB());
        float textW = Fonts.inter.getWidth(label, glyphSize);
        draw.text(Fonts.inter, mat, Concat.of(label), bx + (size - textW) / 2f, by + (size - glyphSize) / 2f, glyphSize, glyphColor);
    }

    // ─── Mouse ─────────────────────────────────────────────────────

    @EventSubscribe
    private void onMousePress(MousePressEvent event) {
        if (event.action != 1) return;
        if (!isEnabled()) return;
        if (MinecraftCompat.getScreen() != null) return;

        double mx = mouseX();
        double my = mouseY();
        float px = getX();
        float py = getY();
        float pw = getWidth();
        float ph = getHeight();

        if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
            float lx = (float) (mx - px);
            float ly = (float) (my - py);
            float s = overlayScale.getValue();
            float ctrlY = BASE_HEIGHT * s;

            if (showControls.getValue() && ly >= ctrlY && ly <= ctrlY + CTRL_HEIGHT * s) {
                handleControl(lx, s);
                event.cancel();
            }
        }
    }

    private void handleControl(float lx, float s) {
        float ctrlW = CTRL_HEIGHT * s;
        float gap = 4f * s;
        float cx = 6f * s;

        if (lx >= cx && lx <= cx + ctrlW) {
            prev();
        } else if (lx >= cx + (ctrlW + gap) && lx <= cx + (ctrlW + gap) + ctrlW) {
            togglePlay();
        } else if (lx >= cx + 2 * (ctrlW + gap) && lx <= cx + 2 * (ctrlW + gap) + ctrlW) {
            next();
        }
    }

    // ─── Playback control ──────────────────────────────────────────

    private void togglePlay() {
        if (usingWindows) {
            WindowsMediaManager.getInstance().togglePlayPause();
            playing = !playing;
        } else {
            spotifyPut(playing ? "pause" : "play");
            playing = !playing;
        }
    }

    private void next() {
        if (usingWindows) {
            WindowsMediaManager.getInstance().next();
        } else {
            spotifyPost("next");
        }
    }

    private void prev() {
        if (usingWindows) {
            WindowsMediaManager.getInstance().previous();
        } else {
            spotifyPost("previous");
        }
    }

    private void spotifyPut(String action) {
        Echo.spotifyManager.sendPlaybackCommand(action, false);
    }

    private void spotifyPost(String action) {
        Echo.spotifyManager.sendPlaybackCommand(action, true);
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private String trimToWidth(String text, float maxWidth, float size, Font font) {
        if (font == null || text == null || text.isEmpty()) return text == null ? "" : text;
        if (maxWidth <= 0f) return "";
        if (font.getWidth(text, size) <= maxWidth) return text;

        String ellipsis = "...";
        int end = text.length();
        while (end > 0) {
            String candidate = text.substring(0, end) + ellipsis;
            if (font.getWidth(candidate, size) <= maxWidth) {
                return candidate;
            }
            end--;
        }
        return ellipsis;
    }

    private String timeStr() {
        long t = Math.round(progress);
        long d = Math.round(duration);
        String s = String.format("%d:%02d / %d:%02d", t / 60, t % 60, d / 60, d % 60);
        if (!playing) s += " (paused)";
        return s;
    }
}
