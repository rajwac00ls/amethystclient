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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Wirepoint extends Feature {

    private final FloatSetting range = new FloatSetting(Concat.of("Range"), 8f, 1f, 24f, 0.5f);
    private final FloatSetting height = new FloatSetting(Concat.of("Height"), 1.1f, 0.3f, 3f, 0.1f);
    private final FloatSetting bounce = new FloatSetting(Concat.of("Bounce"), 0.22f, 0f, 1f, 0.02f);
    private final FloatSetting speed = new FloatSetting(Concat.of("Speed"), 1f, 0.1f, 4f, 0.1f);
    private final ColorSetting color = new ColorSetting(Concat.of("Color"), 0, 220, 170, 200);

    public Wirepoint() {
        super(new FeatureInfo(
                Concat.of("Wirepoint"),
                Concat.of("Sine-bobbing marker on the block you look at"),
                Category.RENDER));
    }

    @EventSubscribe
    private void onRender3D(EventRender3D event) {
        if (isNull()) return;

        Vec3 eyePos = mc.player.getEyePosition(event.getTickDelta());
        Vec3 lookDir = mc.player.getLookAngle();
        double range = this.range.getValue();
        Vec3 endPos = eyePos.add(lookDir.scale(range));

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        Vec3 point = hit.getLocation();
        double now = System.currentTimeMillis();
        double sp = speed.getValue();
        double bob = Math.sin(now * 0.0026 * sp) * bounce.getValue();
        double topY = point.y + height.getValue() + bob;

        float alphaNorm = color.getAlphaNormalized() * (float) (0.6 + 0.4 * Math.abs(Math.sin(now * 0.0022 * sp)));
        int mainColor = ARGB.colorFromFloat(alphaNorm,
                color.getRedNormalized(), color.getGreenNormalized(), color.getBlueNormalized());
        int dimColor = ARGB.colorFromFloat(alphaNorm * 0.35f,
                color.getRedNormalized(), color.getGreenNormalized(), color.getBlueNormalized());

        FramebufferTarget target3d = event.getDraw3D().getMinecraftTarget();
        Draw3DUtils.beam(target3d, point.x, point.z, point.y, topY, dimColor);
        Draw3DUtils.ring(target3d, point.x, topY, point.z, 0.35, now * 0.0028 * sp, mainColor, 18);

        double s = 0.12 + 0.08 * Math.abs(Math.sin(now * 0.0022 * sp));
        target3d.box(point.x - s, point.y - s, point.z - s, s * 2, s * 2, s * 2, dimColor);

        target3d.line(eyePos.x, eyePos.y, eyePos.z, point.x, point.y + 0.1, point.z,
                ARGB.colorFromFloat(alphaNorm * 0.22f,
                        color.getRedNormalized(), color.getGreenNormalized(), color.getBlueNormalized()));
    }
}
