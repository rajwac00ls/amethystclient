package hack.echo.client.features.impl.render.hud;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.features.Category;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.HudFeature;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.features.settings.impl.StringSetting;
import hack.echo.client.render2.api.CrossTexture;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.utils.ResourceHelper;
import hack.echo.client.utils.strings.Concat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.IntBuffer;
import java.util.List;

public class Companion extends HudFeature {

    private final ModeSetting picture = new ModeSetting(
            Concat.of("Picture"), "Boykisser", "Boykisser", "Tf2", "Custom");
    private final FloatSetting size = new FloatSetting(
            Concat.of("Size"), 96f, 32f, 256f, 4f);
    private final StringSetting customUrl = new StringSetting(
            Concat.of("Custom URL"), "https://example.com/image.png", o -> picture.is(Concat.of("Custom")));

    private CrossTexture boykisserTexture;
    private CrossTexture tf2Texture;
    private float boykisserAspect;
    private float tf2Aspect;

    private String loadedUrl = "";
    private volatile byte[] pendingBytes;
    private volatile boolean loading;

    private CrossTexture customTexture;
    private CompanionGif customGif;
    private List<CrossTexture> gifTextures;
    private int[] gifDelays;
    private int gifFrameIndex;
    private long gifFrameStart;
    private float customAspect = 1f;

    private float cachedWidth = 96f;
    private float cachedHeight = 96f;

    public Companion() {
        super(new FeatureInfo(
                Concat.of("Companion"),
                Concat.of("A small companion picture in the corner of your screen"),
                Category.RENDER
        ));
        customUrl.setMaxLength(1024);
        getXPosition().setValue(0.01f);
        getYPosition().setValue(1f);
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

        CrossTexture texture = null;
        float aspect = 1f;

        if (picture.is(Concat.of("Custom"))) {
            if (customUrl.getValue().isBlank()) {
                cleanupCustomTextures();
                return;
            }
            updateCustom();
            if (customGif != null) {
                texture = currentGifFrame();
                aspect = customAspect;
            } else if (customTexture != null) {
                texture = customTexture;
                aspect = customAspect;
            }
        } else {
            cleanupCustomTextures();
            if (picture.is(Concat.of("Tf2"))) {
                if (tf2Texture == null) tf2Texture = CrossTexture.from("textures/gui/tf2.jpg");
                texture = tf2Texture;
                if (tf2Aspect == 0f) tf2Aspect = loadAspect("textures/gui/tf2.jpg");
                aspect = tf2Aspect;
            } else {
                if (boykisserTexture == null) boykisserTexture = CrossTexture.from("textures/gui/boykisser.png");
                texture = boykisserTexture;
                if (boykisserAspect == 0f) boykisserAspect = loadAspect("textures/gui/boykisser.png");
                aspect = boykisserAspect;
            }
        }

        if (texture == null || (texture.glId == -1 && texture.vkImage == null)) return;

        float w = size.getValue();
        float h = w / (aspect == 0f ? 1f : aspect);

        cachedWidth = w;
        cachedHeight = h;

        float x = getX();
        float y = getY();

        Draw2D draw = event.getDraw2D();
        draw.image(new Matrix4f(), texture, x, y, w, h, 6f, 1f);
    }

    private void updateCustom() {
        if (pendingBytes != null) {
            byte[] bytes = pendingBytes;
            pendingBytes = null;
            applyCustomBytes(bytes);
        }
        String url = customUrl.getValue();
        if (url.isBlank() || url.equals(loadedUrl) || loading) return;
        startLoad(url);
    }

    private void startLoad(String url) {
        loadedUrl = url;
        loading = true;
        String target = url;
        Thread thread = new Thread(() -> {
            byte[] bytes = null;
            try {
                bytes = download(target);
            } catch (IOException ignored) {
            }
            if (target.equals(customUrl.getValue())) {
                pendingBytes = bytes;
            }
            loading = false;
        }, "Companion Loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyCustomBytes(byte[] bytes) {
        cleanupCustomTextures();
        CompanionGif gif = CompanionGif.decode(bytes);
        if (gif != null) {
            customGif = gif;
            customAspect = (float) gif.width / (float) gif.height;
            buildGifTextures();
        } else {
            customTexture = CrossTexture.from(bytes);
            customAspect = loadAspect(bytes);
        }
    }

    private void buildGifTextures() {
        var frames = customGif.getFrames();
        gifTextures = new ObjectArrayList<>();
        gifDelays = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            CompanionGif.Frame frame = frames.get(i);
            gifDelays[i] = frame.delayMs;
            var buffer = MemoryUtil.memAlloc(frame.rgba.length);
            buffer.put(frame.rgba).flip();
            gifTextures.add(CrossTexture.fromPixels(buffer, customGif.width, customGif.height));
            MemoryUtil.memFree(buffer);
        }
        gifFrameIndex = 0;
        gifFrameStart = System.currentTimeMillis();
    }

    private CrossTexture currentGifFrame() {
        if (gifTextures == null || gifTextures.isEmpty()) return null;
        if (gifTextures.size() == 1) return gifTextures.get(0);

        long now = System.currentTimeMillis();
        int delay = gifDelays[gifFrameIndex];
        long elapsed = now - gifFrameStart;
        if (delay > 0 && elapsed >= delay) {
            int steps = (int) (elapsed / delay);
            gifFrameIndex = (gifFrameIndex + steps) % gifTextures.size();
            gifFrameStart += (long) steps * delay;
        }
        return gifTextures.get(gifFrameIndex);
    }

    private void cleanupCustomTextures() {
        if (gifTextures != null) {
            for (CrossTexture texture : gifTextures) texture.cleanup();
            gifTextures = null;
        }
        if (customTexture != null) {
            customTexture.cleanup();
            customTexture = null;
        }
        customGif = null;
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private float loadAspect(String path) {
        byte[] bytes = ResourceHelper.getBytes(path);
        if (bytes == null) return 1f;
        return loadAspect(bytes);
    }

    private float loadAspect(byte[] bytes) {
        var buffer = MemoryUtil.memAlloc(bytes.length);
        buffer.put(bytes).flip();

        try (var stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(false);
            var pixels = STBImage.stbi_load_from_memory(buffer, w, h, channels, 4);
            MemoryUtil.memFree(buffer);

            if (pixels == null) return 1f;

            float aspect = (float) w.get(0) / (float) h.get(0);
            STBImage.stbi_image_free(pixels);
            return aspect;
        }
    }
}
