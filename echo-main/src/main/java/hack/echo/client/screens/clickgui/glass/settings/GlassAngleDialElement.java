package hack.echo.client.screens.clickgui.glass.settings;

import hack.echo.client.features.settings.impl.AngleSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.screens.clickgui.TooltipManager;
import hack.echo.client.utils.ClientUtil;
import hack.echo.client.utils.animation.Animation;
import hack.echo.client.utils.animation.Easing;
import hack.echo.client.utils.audio.SoundUtil;
import hack.echo.client.utils.strings.Concat;
import lombok.Getter;
import org.joml.Matrix4f;

import static hack.echo.client.screens.clickgui.glass.GlassUIConstants.*;

public class GlassAngleDialElement implements GlassSettingElement {

    private static final CharSequence DEGREE = Concat.of("\u00B0");
    private static final float DIAL_SIZE = 52f;
    private static final float DIAL_RADIUS = DIAL_SIZE / 2f;
    private static final float STICK_THICKNESS = 2.5f;
    private static final float KNOB_RADIUS = 4f;

    @Getter
    private final AngleSetting setting;

    private float x, y;
    private boolean dragging = false;
    private float lastSoundValue = Float.MIN_VALUE;
    private boolean wasHovered = false;
    private final Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 150);

    public GlassAngleDialElement(AngleSetting setting) {
        this.setting = setting;
        float initial = getDialAngle();
        animation.start(initial, initial);
    }

    @Override
    public void render(Draw2D draw, float x, float y, Matrix4f mat, int mouseX, int mouseY, float delta) {
        this.x = x;
        this.y = y;
        if (dragging) {
            updateValue(mouseX, mouseY);
        }

        float renderX = x + SETTING_INDENT;
        float renderW = PANEL_WIDTH - SETTING_INDENT - 4;

        boolean hovered = isInside(renderX, y, renderW, getHeight(), mouseX, mouseY);
        if (hovered && !wasHovered) {
            SoundUtil.playHover();
        }
        wasHovered = hovered;

        if (hovered) {
            CharSequence description = setting.getDescription();
            if (description != null && description.length() > 0) {
                TooltipManager.request(setting, setting.getNameSequence(), description, mouseX, mouseY);
            }
        }

        draw.rect(mat, renderX, y, renderW, getHeight(), RADIUS_MD,
                wa((hovered ? SURFACE_SETTING_HOVER : SURFACE_SETTING).getRGB(), delta));
        draw.rect(mat, renderX, y + 2, 1.5f, getHeight() - 4, 0, wa(ACCENT_PRIMARY.getRGB(), delta));

        CharSequence name = ClientUtil.truncateName(setting.getNameSequence(), 16);
        draw.text(Fonts.interSemiBold, mat, name, renderX + PADDING, y + 3, 6, argbMul(TEXT_ON_SURFACE_SECONDARY, delta));

        CharSequence value = Concat.of(ClientUtil.intSequence(Math.round(setting.getValue())), DEGREE);
        float valueW = Fonts.interSemiBold.getWidth(value, 6);
        draw.text(Fonts.interSemiBold, mat, value, renderX + renderW - PADDING - valueW, y + 3, 6,
                argbMul(TEXT_ON_SURFACE_MUTED, delta));

        animation.updateTo(getDialAngle());
        float dialAngle = (float) animation.getDelta();

        drawDial(draw, mat, renderX + renderW / 2f, y + SETTING_HEIGHT + DIAL_RADIUS + 2f, DIAL_RADIUS, dialAngle, delta);
    }

    @Override
    public float getHeight() {
        return SETTING_HEIGHT + DIAL_SIZE + 12f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float cx = getCenterX();
        float cy = getCenterY();
        float dist = (float) Math.hypot(mouseX - cx, mouseY - cy);
        if (dist > DIAL_RADIUS + 6f) return false;

        dragging = true;
        lastSoundValue = setting.getValue();
        updateValue(mouseX, mouseY);
        SoundUtil.playClick();
        return true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            setting.notifyChange();
        }
        dragging = false;
    }

    private void drawDial(Draw2D draw, Matrix4f mat, float cx, float cy, float radius, float angle, float delta) {
        int fill = wa(SURFACE_INTERACTIVE_MUTED.getRGB(), delta);
        int ring = wa(CONTROL_KNOB.getRGB(), delta);
        int muted = argbMul(TEXT_ON_SURFACE_MUTED, delta);

        draw.rect(mat, cx - radius, cy - radius, radius * 2f, radius * 2f, radius, fill);
        drawRing(draw, mat, cx, cy, radius, 2f, ring);

        for (int deg = 0; deg < 360; deg += 30) {
            drawLine(draw, mat,
                    pointX(cx, radius - 6f, deg), pointY(cy, radius - 6f, deg),
                    pointX(cx, radius - 1.5f, deg), pointY(cy, radius - 1.5f, deg),
                    1.5f, muted);
        }

        drawLabel(draw, mat, cx, cy, radius, 0, "0", muted);
        drawLabel(draw, mat, cx, cy, radius, 90, "90", muted);
        drawLabel(draw, mat, cx, cy, radius, 180, "180", muted);
        drawLabel(draw, mat, cx, cy, radius, 270, "270", muted);

        float stickTipX = pointX(cx, radius - 4f, angle);
        float stickTipY = pointY(cy, radius - 4f, angle);
        drawLine(draw, mat, cx, cy, stickTipX, stickTipY, STICK_THICKNESS, ring);
        draw.rect(mat, stickTipX - KNOB_RADIUS, stickTipY - KNOB_RADIUS, KNOB_RADIUS * 2f, KNOB_RADIUS * 2f,
                KNOB_RADIUS, ring);
    }

    private void drawLabel(Draw2D draw, Matrix4f mat, float cx, float cy, float radius, float deg,
            String text, int color) {
        float size = 5f;
        float w = Fonts.interSemiBold.getWidth(text, size);
        float px = pointX(cx, radius + 3f, deg);
        float py = pointY(cy, radius + 3f, deg);
        draw.text(Fonts.interSemiBold, mat, text, px - w / 2f, py - size / 2f, size, color);
    }

    private void drawRing(Draw2D draw, Matrix4f mat, float cx, float cy, float radius, float thickness, int color) {
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float deg = i / (float) segments * 360f;
            float px = pointX(cx, radius, deg);
            float py = pointY(cy, radius, deg);
            draw.rect(mat, px - thickness / 2f, py - thickness / 2f, thickness, thickness, 0, color);
        }
    }

    private void drawLine(Draw2D draw, Matrix4f mat, float x1, float y1, float x2, float y2,
            float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len <= 0.01f) return;

        int steps = Math.max(1, (int) Math.ceil(len / thickness));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            draw.rect(mat, x1 + dx * t - thickness / 2f, y1 + dy * t - thickness / 2f,
                    thickness, thickness, 0, color);
        }
    }

    private float pointX(float cx, float radius, float deg) {
        return cx + (float) (Math.sin(Math.toRadians(deg)) * radius);
    }

    private float pointY(float cy, float radius, float deg) {
        return cy - (float) (Math.cos(Math.toRadians(deg)) * radius);
    }

    private float getCenterX() {
        return x + SETTING_INDENT + (PANEL_WIDTH - SETTING_INDENT - 4) / 2f;
    }

    private float getCenterY() {
        return y + SETTING_HEIGHT + DIAL_RADIUS + 2f;
    }

    private float getDialAngle() {
        return setting.getValue();
    }

    private void updateValue(double mouseX, double mouseY) {
        float cx = getCenterX();
        float cy = getCenterY();
        float dx = (float) mouseX - cx;
        float dy = (float) mouseY - cy;
        if (Math.hypot(dx, dy) < 3f) return;

        float deg = (float) Math.toDegrees(Math.atan2(dx, -dy));
        deg = (deg + 360f) % 360f;
        float newVal = (float) Math.clamp(deg, setting.getMinValue(), setting.getMaxValue());
        setting.setValueNoNotify(newVal);

        float range = setting.getMaxValue() - setting.getMinValue();
        float threshold = range * 0.03f;
        if (lastSoundValue == Float.MIN_VALUE || Math.abs(newVal - lastSoundValue) >= threshold) {
            lastSoundValue = newVal;
            SoundUtil.playSliderChange();
        }
    }
}
