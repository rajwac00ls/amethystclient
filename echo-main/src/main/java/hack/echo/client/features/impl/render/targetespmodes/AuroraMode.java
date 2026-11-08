package hack.echo.client.features.impl.render.targetespmodes;

import hack.echo.client.Echo;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.particle.impl.AuroraParticle;
import hack.echo.client.utils.combat.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class AuroraMode {
    private final ColorSetting colorSetting;
    private LivingEntity lastTarget;
    private static final Minecraft mc = Minecraft.getInstance();

    private static final int HELIX_PARTICLES = 20;
    private static final int RING_PARTICLES = 16;

    public AuroraMode(ColorSetting colorSetting) {
        this.colorSetting = colorSetting;
    }

    public void reset() {
        lastTarget = null;
    }

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;

        LivingEntity target = TargetUtils.getLastAttackedTarget(400);
        if (target == null) {
            lastTarget = null;
            return;
        }

        if (target != lastTarget) {
            lastTarget = target;
            spawnParticles(target);
        }

        float tick = target.tickCount + event.getTickDelta();

        drawRings(event, target, tick);
    }

    private void spawnParticles(LivingEntity target) {
        for (int i = 0; i < HELIX_PARTICLES; i++) {
            Echo.particleManager.addParticle(new AuroraParticle(target, AuroraParticle.Type.HELIX_A, i, HELIX_PARTICLES, colorSetting));
            Echo.particleManager.addParticle(new AuroraParticle(target, AuroraParticle.Type.HELIX_B, i, HELIX_PARTICLES, colorSetting));
        }
        for (int i = 0; i < RING_PARTICLES; i++) {
            Echo.particleManager.addParticle(new AuroraParticle(target, AuroraParticle.Type.RING, i, RING_PARTICLES, colorSetting));
        }
    }

    private void drawRings(EventRender3D event, LivingEntity target, float tick) {
        float tickDelta = event.getTickDelta();
        Vec3 pos = target.getPosition(tickDelta);
        double height = target.getBoundingBox().getYsize();
        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.18f);
        int segments = 32;
        var target3d = event.getDraw3D().getCustomTarget();

        float[][] bands = {
            { 0.25f, 1.0f, 1.4f },   // yFrac, speed, scale
            { 0.5f, -1.4f, 1.8f },
            { 0.75f, 1.8f, 2.2f }
        };

        for (int b = 0; b < bands.length; b++) {
            float yFrac = bands[b][0];
            float speed = bands[b][1];
            float scale = bands[b][2];
            double yBase = pos.y + height * yFrac;
            double radius = target.getBbWidth() * scale * (1.0 + 0.15 * Math.sin(tick * 0.2 + b));

            for (int i = 0; i < segments; i++) {
                double a1 = Math.toRadians((i + tick * speed) * 360.0 / segments);
                double a2 = Math.toRadians((i + 1 + tick * speed) * 360.0 / segments);

                double ripple1 = Math.sin(a1 * 3.0 + tick * 0.3) * 0.12;
                double ripple2 = Math.sin(a2 * 3.0 + tick * 0.3) * 0.12;

                double x1 = pos.x + Math.cos(a1) * radius;
                double z1 = pos.z + Math.sin(a1) * radius;
                double y1 = yBase + ripple1;

                double x2 = pos.x + Math.cos(a2) * radius;
                double z2 = pos.z + Math.sin(a2) * radius;
                double y2 = yBase + ripple2;

                double wave = Math.sin(a1 * 2.0 + tick * 0.4);
                int alpha = (int) (0x30 + 0x60 * (0.5 + 0.5 * wave) + 0x70 * pulse);
                int edgeColor = yinYangColor(i, Math.min(0xFF, alpha));
                target3d.line(x1, y1, z1, x2, y2, z2, edgeColor);
            }
        }

        drawPulseRing(event, target, tick);
    }

    private void drawPulseRing(EventRender3D event, LivingEntity target, float tick) {
        float tickDelta = event.getTickDelta();
        Vec3 pos = target.getPosition(tickDelta);
        double height = target.getBoundingBox().getYsize();
        int segments = 48;
        var target3d = event.getDraw3D().getCustomTarget();

        double phase = (tick * 0.05f) % 1.0;
        double radius = target.getBbWidth() * (1.2 + phase * 2.6);
        double y = pos.y + height * 0.5f;
        double alphaMul = 1.0 - phase;

        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(i * 360.0 / segments);
            double a2 = Math.toRadians((i + 1) * 360.0 / segments);
            double x1 = pos.x + Math.cos(a1) * radius;
            double z1 = pos.z + Math.sin(a1) * radius;
            double x2 = pos.x + Math.cos(a2) * radius;
            double z2 = pos.z + Math.sin(a2) * radius;
            int edgeColor = yinYangColor(i + (int) (tick * 0.5f), (int) (0xB0 * alphaMul));
            target3d.line(x1, y, z1, x2, y, z2, edgeColor);
        }
    }

    private static int yinYangColor(int index, int alpha) {
        boolean dark = (index / 2) % 2 == 0;
        return (alpha << 24) | (dark ? 0x000000 : 0xFFFFFF);
    }
}
