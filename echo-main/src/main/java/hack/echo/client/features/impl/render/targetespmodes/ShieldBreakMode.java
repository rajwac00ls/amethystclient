package hack.echo.client.features.impl.render.targetespmodes;

import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.utils.combat.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class ShieldBreakMode {
    private final ColorSetting colorSetting;
    private static final Minecraft mc = Minecraft.getInstance();

    private static final int ANIM_TICKS = 24;
    private static final int RING_SEGMENTS = 40;
    private static final int SHARDS = 12;

    private LivingEntity lastTarget;
    private LivingEntity breakTarget;
    private int animTick = -1;
    private Vec3 breakPos = Vec3.ZERO;

    public ShieldBreakMode(ColorSetting colorSetting) {
        this.colorSetting = colorSetting;
    }

    public void reset() {
        lastTarget = null;
        breakTarget = null;
        animTick = -1;
    }

    public void onShieldBreak(Player player) {
        if (player == null || player == mc.player) return;

        if (player != lastTarget && player != TargetUtils.getLastAttackedTarget(400)) {
            return;
        }

        breakTarget = player;
        animTick = 0;
    }

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;

        lastTarget = TargetUtils.getLastAttackedTarget(400);

        if (animTick < 0) return;

        if (breakTarget == null || !breakTarget.isAlive() || breakTarget.isRemoved()) {
            animTick = -1;
            return;
        }

        Vec3 pos = breakTarget.getPosition(event.getTickDelta());
        breakPos = new Vec3(pos.x, pos.y + breakTarget.getBoundingBox().getYsize() * 0.5, pos.z);

        renderAnimation(event);
        animTick++;
        if (animTick > ANIM_TICKS) {
            animTick = -1;
        }
    }

    private void renderAnimation(EventRender3D event) {
        if (animTick < 0) return;

        float progress = animTick / (float) ANIM_TICKS;
        float eased = 1.0f - (float) Math.pow(1.0 - progress, 3.0);
        var target3d = event.getDraw3D().getCustomTarget();

        int alpha = (int) (0xFF * (1.0 - progress));
        int baseColor = new Color(colorSetting.getRed(), colorSetting.getGreen(), colorSetting.getBlue(), Math.max(10, alpha)).getRGB();
        int brightColor = new Color(Math.min(255, colorSetting.getRed() + 80), Math.min(255, colorSetting.getGreen() + 80), Math.min(255, colorSetting.getBlue() + 80), Math.max(10, alpha)).getRGB();

        double ringRadius = 0.3 + eased * 2.4;
        double y = breakPos.y + 0.2;

        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = Math.toRadians(i * 360.0 / RING_SEGMENTS);
            double a2 = Math.toRadians((i + 1) * 360.0 / RING_SEGMENTS);
            double x1 = breakPos.x + Math.cos(a1) * ringRadius;
            double z1 = breakPos.z + Math.sin(a1) * ringRadius;
            double x2 = breakPos.x + Math.cos(a2) * ringRadius;
            double z2 = breakPos.z + Math.sin(a2) * ringRadius;
            target3d.line(x1, y, z1, x2, y, z2, baseColor);
        }

        for (int i = 0; i < SHARDS; i++) {
            double a = Math.toRadians(i * 360.0 / SHARDS + progress * 40.0);
            double spread = ringRadius * (0.8 + 0.3 * Math.sin(i * 3.7));
            double sx = breakPos.x + Math.cos(a) * spread;
            double sz = breakPos.z + Math.sin(a) * spread;
            double sy = y + 0.3 + eased * 1.4;
            target3d.line(breakPos.x + Math.cos(a) * ringRadius * 0.4, y, breakPos.z + Math.sin(a) * ringRadius * 0.4, sx, sy, sz, brightColor);
        }

        for (int i = 0; i < SHARDS / 2; i++) {
            double a = Math.toRadians(i * 180.0 / (SHARDS / 2) - 90.0 + progress * 60.0);
            double spread = ringRadius * (0.7 + 0.4 * progress);
            double sx = breakPos.x + Math.cos(a) * spread;
            double sz = breakPos.z + Math.sin(a) * spread;
            target3d.line(breakPos.x, y, breakPos.z, sx, y, sz, brightColor);
        }
    }
}
