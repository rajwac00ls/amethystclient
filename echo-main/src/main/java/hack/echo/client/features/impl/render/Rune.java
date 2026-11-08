package hack.echo.client.features.impl.render;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.render3.api.Draw3DUtils;
import hack.echo.client.render3.api.FramebufferTarget;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class Rune extends Feature {

    private final FloatSetting range = new FloatSetting(Concat.of("Range"), 6f, 1f, 12f, 0.5f);
    private final FloatSetting ringSize = new FloatSetting(Concat.of("Ring Size"), 0.9f, 0.3f, 3f, 0.05f);
    private final FloatSetting spinSpeed = new FloatSetting(Concat.of("Spin Speed"), 1f, 0.1f, 4f, 0.1f);
    private final FloatSetting pulse = new FloatSetting(Concat.of("Pulse"), 0.5f, 0f, 1f, 0.05f);
    private final ColorSetting color = new ColorSetting(Concat.of("Color"), 170, 80, 255, 180);

    public Rune() {
        super(new FeatureInfo(
                Concat.of("Rune"),
                Concat.of("Animated rune rings cast over your target"),
                Category.RENDER));
    }

    @EventSubscribe
    private void onRender3D(EventRender3D event) {
        if (isNull()) return;

        Entity target = findTarget(event.getTickDelta());
        if (target == null) return;

        AABB box = target.getBoundingBox();
        double cx = box.getCenter().x;
        double cz = box.getCenter().z;
        double width = Math.max(box.getXsize(), box.getZsize());
        double radius = ringSize.getValue() + width * 0.5;

        double now = System.currentTimeMillis();
        double speed = spinSpeed.getValue();
        double pulseA = 0.6 + 0.4 * Math.abs(Math.sin(now * 0.002 * speed));

        float alphaNorm = color.getAlphaNormalized() * (float) (0.55 + 0.45 * pulseA);
        int mainColor = ARGB.colorFromFloat(alphaNorm,
                color.getRedNormalized(), color.getGreenNormalized(), color.getBlueNormalized());
        int dimColor = ARGB.colorFromFloat(alphaNorm * 0.4f,
                color.getRedNormalized(), color.getGreenNormalized(), color.getBlueNormalized());

        double bob = Math.sin(now * 0.0016 * speed) * 0.15 * pulse.getValue();
        double cy = box.minY + width * 0.5 + bob;

        FramebufferTarget target3d = event.getDraw3D().getMinecraftTarget();
        Draw3DUtils.beam(target3d, cx, cz, box.minY, cy, dimColor);
        Draw3DUtils.ring(target3d, cx, cy, cz, radius, now * 0.002 * speed, mainColor, 28);
        Draw3DUtils.ring(target3d, cx, cy + 0.18, cz, radius * 0.8, -now * 0.0016 * speed, dimColor, 20);

        double core = 0.1 + 0.04 * pulseA;
        target3d.box(cx - core, cy - core, cz - core, core * 2, core * 2, core * 2, mainColor);
    }

    private Entity findTarget(float tickDelta) {
        Vec3 eyePos = mc.player.getEyePosition(tickDelta);
        Vec3 lookDir = mc.player.getLookAngle();
        double range = this.range.getValue();
        Vec3 endPos = eyePos.add(lookDir.scale(range));

        AABB searchBox = mc.player.getBoundingBox().expandTowards(lookDir.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                mc.player, eyePos, endPos, searchBox,
                entity -> entity.isPickable() && !entity.isSpectator() && entity != mc.player,
                range * range);

        return hit != null ? hit.getEntity() : null;
    }
}
