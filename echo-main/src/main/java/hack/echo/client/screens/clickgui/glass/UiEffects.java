package hack.echo.client.screens.clickgui.glass;

import hack.echo.client.render2.api.Draw2D;
import org.joml.Matrix4f;

public final class UiEffects {

    private UiEffects() {
    }

    public static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static long time() {
        return System.currentTimeMillis();
    }

    public static int darken(int color, float f) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        return rgba(
                Math.max(0, (int) (r * (1f - f))),
                Math.max(0, (int) (g * (1f - f))),
                Math.max(0, (int) (b * (1f - f))),
                a);
    }

    public static int lighten(int color, float f) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        return rgba(
                Math.min(255, (int) (r + (255 - r) * f)),
                Math.min(255, (int) (g + (255 - g) * f)),
                Math.min(255, (int) (b + (255 - b) * f)),
                a);
    }

    public static void dropShadow(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, float radius, int color, float strength) {
        float a = Math.max(0f, Math.min(1f, strength));
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        float[] spreads = { 1.5f, 3.0f, 4.5f, 6.0f };
        float[] alphas = { 0.10f, 0.06f, 0.035f, 0.02f };
        for (int i = 0; i < spreads.length; i++) {
            float s = spreads[i];
            draw.rect(mat, x - s, y - s * 0.6f, w + s * 2f, h + s * 1.2f,
                    Math.max(0f, radius + s), rgba(r, g, b, (int) (alphas[i] * a * 255f)));
        }
    }

    public static void gradientVertical(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, float radius, int topColor, int bottomColor) {
        draw.rect(mat, x, y, w, h, radius, radius, radius, radius, topColor, topColor, bottomColor, bottomColor);
    }

    public static void gradientHorizontal(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, float radius, int leftColor, int rightColor) {
        draw.rect(mat, x, y, w, h, radius, radius, radius, radius, leftColor, rightColor, rightColor, leftColor);
    }

    public static void glow(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, float radius, int color, float strength) {
        float a = Math.max(0f, Math.min(1f, strength));
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        float[] spreads = { 1.2f, 2.4f, 3.6f, 4.8f };
        float[] alphas = { 0.09f, 0.055f, 0.035f, 0.02f };
        for (int i = 0; i < spreads.length; i++) {
            float s = spreads[i];
            draw.rect(mat, x - s, y - s, w + s * 2f, h + s * 2f,
                    Math.max(0f, radius + s), rgba(r, g, b, (int) (alphas[i] * a * 255f)));
        }
    }

    public static float sheenX(float barWidth, long time) {
        float cycle = 1700f;
        float t = (time % cycle) / cycle;
        return t * (barWidth + 44f) - 22f;
    }

    public static void sheen(Draw2D draw, Matrix4f mat, float x, float y, float barWidth, float barHeight, long time, int baseColor) {
        float sx = x + sheenX(barWidth, time);
        int r = Math.min(255, ((baseColor >>> 16) & 0xFF) + 90);
        int g = Math.min(255, ((baseColor >>> 8) & 0xFF) + 90);
        int b = Math.min(255, (baseColor & 0xFF) + 90);
        draw.rect(mat, sx, y, 10f, barHeight, 0f, rgba(r, g, b, 70));
    }
}
