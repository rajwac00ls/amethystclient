package hack.echo.client.features.impl.render.targetespmodes;

import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.utils.combat.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class CrownMode {
    private final ColorSetting colorSetting;
    private static final Minecraft mc = Minecraft.getInstance();

    private static final int CROWN_POINTS = 10;
    private static final int SEGMENTS = 40;

    public CrownMode(ColorSetting colorSetting) {
        this.colorSetting = colorSetting;
    }

    public void reset() {
    }

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;

        LivingEntity target = TargetUtils.getLastAttackedTarget(400);
        if (target == null) return;

        float tick = target.tickCount + event.getTickDelta();
        Vec3 pos = target.getPosition(event.getTickDelta());
        double height = target.getBoundingBox().getYsize();
        double width = target.getBbWidth();
        var target3d = event.getDraw3D().getCustomTarget();

        double bob = Math.sin(tick * 0.08) * 0.05;
        double bandY = pos.y + height + 0.25 + bob;
        double rotation = tick * 0.06;
        double radius = Math.max(0.4, width * 0.85);
        double spikeHeight = radius * 0.9;
        double shortSpikeHeight = radius * 0.5;
        double midY = bandY + shortSpikeHeight * 0.55;

        int white = 0xFFFFFFFF;
        int black = 0xFF000000;
        int whiteDim = new java.awt.Color(255, 255, 255, 120).getRGB();
        int blackDim = new java.awt.Color(0, 0, 0, 120).getRGB();

        drawYinYangBands(event, pos, height, width, tick);

        drawCircle(target3d, pos, rotation, radius, bandY, white, black);
        drawCircle(target3d, pos, rotation, radius * 0.95, midY, whiteDim, blackDim);

        for (int i = 0; i < CROWN_POINTS; i++) {
            boolean tall = i % 2 == 0;
            double a = rotation + Math.toRadians(i * 360.0 / CROWN_POINTS);
            double x = pos.x + Math.cos(a) * radius;
            double z = pos.z + Math.sin(a) * radius;
            double topY = bandY + (tall ? spikeHeight : shortSpikeHeight);

            int spikeColor = (i % 2 == 0) ? white : black;
            int dimColor = (i % 2 == 0) ? whiteDim : blackDim;

            target3d.line(x, bandY, z, x, topY, z, spikeColor);

            double midX = pos.x + Math.cos(a) * radius * 0.92;
            double midZ = pos.z + Math.sin(a) * radius * 0.92;
            target3d.line(x, bandY, z, midX, midY, midZ, dimColor);

            drawBall(target3d, x, topY, z, 0.12, spikeColor);
        }

        drawScallops(target3d, pos, rotation, radius, bandY, spikeHeight, shortSpikeHeight, whiteDim, blackDim);
    }

    private void drawCircle(hack.echo.client.render3.api.FramebufferTarget target3d, Vec3 pos, double rotation, double r, double y, int c1, int c2) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = rotation + Math.toRadians(i * 360.0 / SEGMENTS);
            double a2 = rotation + Math.toRadians((i + 1) * 360.0 / SEGMENTS);
            int color = (i % 2 == 0) ? c1 : c2;
            target3d.line(
                pos.x + Math.cos(a1) * r, y, pos.z + Math.sin(a1) * r,
                pos.x + Math.cos(a2) * r, y, pos.z + Math.sin(a2) * r, color);
        }
    }

    private void drawScallops(hack.echo.client.render3.api.FramebufferTarget target3d, Vec3 pos, double rotation, double radius, double bandY,
            double spikeHeight, double shortSpikeHeight, int c1, int c2) {
        int steps = 6;
        for (int i = 0; i < CROWN_POINTS; i++) {
            double a1 = rotation + Math.toRadians(i * 360.0 / CROWN_POINTS);
            double a2 = rotation + Math.toRadians((i + 1) * 360.0 / CROWN_POINTS);
            double topY1 = bandY + ((i % 2 == 0) ? spikeHeight : shortSpikeHeight);
            double topY2 = bandY + (((i + 1) % 2 == 0) ? spikeHeight : shortSpikeHeight);
            int color = (i % 2 == 0) ? c1 : c2;

            for (int s = 0; s < steps; s++) {
                double t1 = (double) s / steps;
                double t2 = (double) (s + 1) / steps;
                double ang1 = a1 + (a2 - a1) * t1;
                double ang2 = a1 + (a2 - a1) * t2;
                double r1 = radius * (1.0 + 0.12 * Math.sin(Math.PI * t1));
                double r2 = radius * (1.0 + 0.12 * Math.sin(Math.PI * t2));
                double y1 = topY1 + (topY2 - topY1) * t1;
                double y2 = topY1 + (topY2 - topY1) * t2;

                target3d.line(
                    pos.x + Math.cos(ang1) * r1, y1, pos.z + Math.sin(ang1) * r1,
                    pos.x + Math.cos(ang2) * r2, y2, pos.z + Math.sin(ang2) * r2, color);
            }
        }
    }

    private void drawYinYangBands(EventRender3D event, Vec3 pos, double height, double width, float tick) {
        var target3d = event.getDraw3D().getCustomTarget();

        float[][] bands = {
            { 0.20f, 1.0f, 1.05f },
            { 0.50f, -1.3f, 1.25f },
            { 0.80f, 1.6f, 1.45f }
        };

        for (int b = 0; b < bands.length; b++) {
            float yFrac = bands[b][0];
            float speed = bands[b][1];
            float scale = bands[b][2];
            double yBase = pos.y + height * yFrac;
            double bandRadius = width * scale * (1.0 + 0.12 * Math.sin(tick * 0.2 + b));

            for (int pass = 0; pass < 3; pass++) {
                double thickness = (pass - 1) * 0.05;
                for (int i = 0; i < SEGMENTS; i++) {
                    double a1 = Math.toRadians((i + tick * speed) * 360.0 / SEGMENTS);
                    double a2 = Math.toRadians((i + 1 + tick * speed) * 360.0 / SEGMENTS);

                    double ripple1 = Math.sin(a1 * 3.0 + tick * 0.3) * 0.10;
                    double ripple2 = Math.sin(a2 * 3.0 + tick * 0.3) * 0.10;

                    double x1 = pos.x + Math.cos(a1) * bandRadius;
                    double z1 = pos.z + Math.sin(a1) * bandRadius;
                    double y1 = yBase + ripple1 + thickness;

                    double x2 = pos.x + Math.cos(a2) * bandRadius;
                    double z2 = pos.z + Math.sin(a2) * bandRadius;
                    double y2 = yBase + ripple2 + thickness;

                    boolean dark = (i % 2 == 0) ^ (b % 2 == 0);
                    int color = dark ? 0xFF000000 : 0xFFFFFFFF;
                    target3d.line(x1, y1, z1, x2, y2, z2, color);
                }
            }
        }
    }

    private void drawBall(hack.echo.client.render3.api.FramebufferTarget target3d, double x, double y, double z, double size, int color) {
        target3d.line(x - size, y, z, x + size, y, z, color);
        target3d.line(x, y - size, z, x, y + size, z, color);
        target3d.line(x, y, z - size, x, y, z + size, color);
        target3d.line(x - size, y, z, x, y, z - size, color);
        target3d.line(x + size, y, z, x, y, z - size, color);
        target3d.line(x - size, y, z, x, y, z + size, color);
        target3d.line(x + size, y, z, x, y, z + size, color);
    }
}
