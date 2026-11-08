package hack.echo.client.particle.impl;

import hack.echo.client.features.impl.render.TargetESP;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.particle.Particle;
import hack.echo.client.utils.MathUtil;
import hack.echo.client.utils.combat.TargetUtils;
import net.minecraft.world.entity.LivingEntity;

import java.awt.Color;

public class AuroraParticle extends Particle {

    public enum Type { HELIX_A, HELIX_B, RING }

    private final LivingEntity target;
    private final Type type;
    private final int index;
    private final int count;
    private final ColorSetting colorSetting;

    public AuroraParticle(LivingEntity target, Type type, int index, int count, ColorSetting colorSetting) {
        super(0);
        this.target = target;
        this.type = type;
        this.index = index;
        this.count = count;
        this.colorSetting = colorSetting;
    }

    @Override
    public float getSize() {
        return type == Type.RING ? 0.9f : 0.5f;
    }

    @Override
    public boolean isDead() {
        return !TargetESP.isAuroraActive() || TargetUtils.getLastAttackedTarget(400) != target;
    }

    @Override
    public void update(float tickDelta) {
        double px = MathUtil.interpolate(target.xo, target.getX(), tickDelta);
        double py = MathUtil.interpolate(target.yo, target.getY(), tickDelta);
        double pz = MathUtil.interpolate(target.zo, target.getZ(), tickDelta);

        float tick = MathUtil.interpolate(target.tickCount - 1, target.tickCount, tickDelta);
        float height = (float) target.getBoundingBox().getYsize();
        float width = target.getBbWidth();
        float phase = index / (float) count;

        double ox;
        double oy;
        double oz;
        float alphaMul = 1.0f;

        switch (type) {
            case HELIX_A -> {
                float yFrac = (phase + tick * 0.008f) % 1.0f;
                float a = (float) Math.toRadians(phase * 360.0 + tick * 2.0);
                oy = py + yFrac * height;
                ox = Math.cos(a) * width;
                oz = Math.sin(a) * width;
                alphaMul = (float) Math.sin(Math.PI * yFrac);
            }
            case HELIX_B -> {
                float yFrac = 1.0f - ((phase + tick * 0.006f) % 1.0f);
                float b = (float) Math.toRadians(phase * 360.0 - tick * 1.5);
                oy = py + yFrac * height;
                ox = Math.cos(b) * width * 1.3f;
                oz = Math.sin(b) * width * 1.3f;
                alphaMul = (float) Math.sin(Math.PI * yFrac);
            }
            case RING -> {
                float a = (float) Math.toRadians(phase * 360.0 + tick * 1.2);
                oy = py + height * 0.5f + (float) Math.sin(tick * 0.15f) * height * 0.15f;
                ox = Math.cos(a) * width * 2.0f;
                oz = Math.sin(a) * width * 2.0f;
            }
            default -> {
                ox = 0.0;
                oy = 0.0;
                oz = 0.0;
            }
        }

        x = px + ox;
        y = oy;
        z = pz + oz;

        float hue = (colorSetting.getHue() + index * 0.02f + tick * 0.002f) % 1.0f;
        int alpha = (int) (colorSetting.getAlpha() * Math.max(0.05f, alphaMul));
        color = (alpha << 24) | (Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0xFFFFFF);
        rotation = 0.0f;
    }
}
