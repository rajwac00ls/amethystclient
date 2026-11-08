package hack.echo.client.render3.api;

public final class Draw3DUtils {

    private Draw3DUtils() {
    }

    public static void ring(FramebufferTarget target, double cx, double cy, double cz, double radius,
            double phase, int color, int segments) {
        if (radius <= 0.0 || segments < 3) return;
        double prevX = cx + Math.cos(phase) * radius;
        double prevZ = cz + Math.sin(phase) * radius;
        for (int i = 1; i <= segments; i++) {
            double angle = phase + (Math.PI * 2.0 * i) / segments;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            target.line(prevX, cy, prevZ, x, cy, z, color);
            prevX = x;
            prevZ = z;
        }
    }

    public static void beam(FramebufferTarget target, double x, double z, double fromY, double toY, int color) {
        if (toY <= fromY) return;
        target.line(x, fromY, z, x, toY, z, color);
    }
}
