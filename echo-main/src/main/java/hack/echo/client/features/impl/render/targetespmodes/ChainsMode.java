package hack.echo.client.features.impl.render.targetespmodes;

import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.render3.api.FramebufferTarget;
import hack.echo.client.utils.combat.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class ChainsMode {
    private final ColorSetting colorSetting;
    private static final Minecraft mc = Minecraft.getInstance();

    private static final int CHAIN_COUNT = 6;
    private static final int LINKS_PER_CHAIN = 9;
    private static final int LINK_SEGMENTS = 10;

    public ChainsMode(ColorSetting colorSetting) {
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

        int color = new Color(colorSetting.getRed(), colorSetting.getGreen(), colorSetting.getBlue(), colorSetting.getAlpha()).getRGB();
        int dimColor = new Color(colorSetting.getRed(), colorSetting.getGreen(), colorSetting.getBlue(), Math.max(20, colorSetting.getAlpha() / 3)).getRGB();

        for (int chain = 0; chain < CHAIN_COUNT; chain++) {
            double baseAngle = Math.toRadians(chain * 360.0 / CHAIN_COUNT + tick * 0.05);
            double radius = width * 1.15 + 0.2;
            double sway = Math.sin(tick * 0.1 + chain * 2.1) * 0.2;

            Vec3 prevCenter = null;
            for (int link = 0; link < LINKS_PER_CHAIN; link++) {
                double frac = (double) link / (LINKS_PER_CHAIN - 1);
                double y = pos.y + height - (height + 0.25) * frac + 0.1;
                double angle = baseAngle + Math.sin(tick * 0.06 + chain * 1.3) * 0.2 * frac;
                double cx = pos.x + Math.cos(angle) * radius * (1.0 - 0.3 * frac) + sway * frac;
                double cz = pos.z + Math.sin(angle) * radius * (1.0 - 0.3 * frac) + sway * frac;

                Vec3 center = new Vec3(cx, y, cz);
                drawLink(target3d, center, radius * 0.18, color);
                if (prevCenter != null) {
                    target3d.line(prevCenter.x, prevCenter.y, prevCenter.z, center.x, center.y, center.z, dimColor);
                }
                prevCenter = center;
            }
        }
    }

    private void drawLink(FramebufferTarget target3d, Vec3 center, double linkRadius, int color) {
        for (int i = 0; i < LINK_SEGMENTS; i++) {
            double a1 = Math.toRadians(i * 360.0 / LINK_SEGMENTS);
            double a2 = Math.toRadians((i + 1) * 360.0 / LINK_SEGMENTS);
            double x1 = center.x + Math.cos(a1) * linkRadius;
            double z1 = center.z + Math.sin(a1) * linkRadius;
            double x2 = center.x + Math.cos(a2) * linkRadius;
            double z2 = center.z + Math.sin(a2) * linkRadius;
            target3d.line(x1, center.y, z1, x2, center.y, z2, color);
        }
    }
}
