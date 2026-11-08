package hack.echo.client.features.impl.render;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.features.settings.impl.MultiModeSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.render2.impl.opengl.utils.RenderUtil;
import hack.echo.client.render3.api.Draw3DUtils;
import hack.echo.client.render3.api.FramebufferTarget;
import hack.echo.client.utils.combat.HitboxUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerESP extends Feature {

    private static final CharSequence BOX = Concat.of("Box");
    private static final CharSequence TWO_D = Concat.of("2D");
    private static final CharSequence SKELETON = Concat.of("Skeleton");
    private static final CharSequence RINGS = Concat.of("Rings");
    private static final CharSequence ORBIT = Concat.of("Orbit");
    private static final CharSequence SHIELD = Concat.of("Shield");
    private static final CharSequence BRACKETS = Concat.of("Brackets");
    private static final CharSequence TRAIL = Concat.of("Trail");
    private static final CharSequence VELOCITY = Concat.of("Velocity");
    private static final CharSequence HELIX = Concat.of("Helix");
    private static final CharSequence WAVE = Concat.of("Wave");
    private static final CharSequence STARS = Concat.of("Stars");
    private static final CharSequence NAME = Concat.of("Name");
    private static final CharSequence DISTANCE = Concat.of("Distance");
    private static final CharSequence NONE = Concat.of("None");
    private static final CharSequence SLIM = Concat.of("Slim");

    private final MultiModeSetting enabledModes = new MultiModeSetting(Concat.of("Modes"),
            BOX, TWO_D, SKELETON, RINGS, ORBIT, SHIELD, BRACKETS, TRAIL, VELOCITY, HELIX, WAVE, STARS, NAME, DISTANCE);
    private final BoolSetting fill = new BoolSetting(Concat.of("Fill"), false);
    private final ModeSetting healthBar = new ModeSetting(Concat.of("Health Bar"), NONE, NONE, TWO_D, SLIM);
    private final BoolSetting tracers = new BoolSetting(Concat.of("Tracers"), true);
    private final ColorSetting colorSetting = new ColorSetting(Concat.of("Color"), 0x7F8C0000);
    private final IntSetting trailLength = new IntSetting(Concat.of("Trail Length"), 30, 5, 80, p -> enabledModes.isEnabled(TRAIL));

    private final List<ScreenBox> boxes = new ArrayList<>();
    private final Map<UUID, Deque<Vec3>> trails = new HashMap<>();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();

    public PlayerESP() {
        super(new FeatureInfo(
                Concat.of("Player ESP"),
                Concat.of("Highlights players"),
                Category.RENDER
        ));
    }

    @Override
    public void onDisable() {
        trails.clear();
        lastPositions.clear();
        super.onDisable();
    }

    @EventSubscribe
    public void event(EventRender3D event) {
        boxes.clear();
        if (mc.level == null) return;

        FramebufferTarget draw3 = event.getDraw3D().getCustomTarget();
        float tickDelta = event.getTickDelta();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Frustum frustum = new Frustum(event.getModelViewMatrix(), event.getProjectionMatrix());
        frustum.prepare(cam.x, cam.y, cam.z);

        boolean box = enabledModes.isEnabled(BOX);
        boolean twoD = enabledModes.isEnabled(TWO_D);
        boolean skeleton = enabledModes.isEnabled(SKELETON);
        boolean rings = enabledModes.isEnabled(RINGS);
        boolean orbit = enabledModes.isEnabled(ORBIT);
        boolean shield = enabledModes.isEnabled(SHIELD);
        boolean brackets = enabledModes.isEnabled(BRACKETS);
        boolean trail = enabledModes.isEnabled(TRAIL);
        boolean velocity = enabledModes.isEnabled(VELOCITY);
        boolean helix = enabledModes.isEnabled(HELIX);
        boolean wave = enabledModes.isEnabled(WAVE);
        boolean stars = enabledModes.isEnabled(STARS);
        boolean name = enabledModes.isEnabled(NAME);
        boolean distance = enabledModes.isEnabled(DISTANCE);
        boolean bar = healthBar.is(TWO_D);
        boolean slim = healthBar.is(SLIM);

        int fillColor = colorSetting.getARGB();
        int outlineColor = fillColor | 0xFF000000;
        int dim = alphaMul(fillColor, 0.5f);

        Set<UUID> seen = new HashSet<>();

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == mc.player) continue;

            seen.add(player.getUUID());
            Vec3 pos = player.getPosition(tickDelta);
            AABB bb = HitboxUtils.getExpandedBox(player);
            double sx = bb.getXsize();
            double sy = bb.getYsize();
            double sz = bb.getZsize();
            double minX = pos.x - sx / 2.0;
            double minY = pos.y;
            double minZ = pos.z - sz / 2.0;

            if (trail) {
                updateTrail(player, pos);
                drawTrail(draw3, player);
            }

            if (velocity) {
                drawVelocityArrow(draw3, player, pos);
            }

            if (box) {
                drawBox(draw3, minX, minY, minZ, sx, sy, sz, fillColor, outlineColor);
            }

            if (tracers.getValue()) {
                draw3.tracer(pos.x, pos.y + player.getEyeHeight(), pos.z, outlineColor);
            }

            if (skeleton) {
                drawSkeleton(draw3, pos, sy, player.getYRot(tickDelta), outlineColor);
            }

            if (rings) {
                drawRings(draw3, player, pos, tickDelta, sx, sy, fillColor, dim);
            }

            if (orbit) {
                drawOrbit(draw3, player, pos, tickDelta, sx, sy, fillColor, dim, outlineColor);
            }

            if (shield) {
                drawShield(draw3, player, pos, tickDelta, sx, sy, fillColor, dim);
            }

            if (helix) {
                drawHelix(draw3, player, pos, tickDelta, sy, fillColor, dim);
            }

            if (wave) {
                drawWave(draw3, player, pos, tickDelta, sy, fillColor);
            }

            if (stars) {
                drawStars(draw3, player, pos, tickDelta, sy, outlineColor);
            }

            if (twoD || bar || slim || brackets || name || distance) {
                AABB worldBB = new AABB(minX, minY, minZ, minX + sx, minY + sy, minZ + sz);
                if (!frustum.isVisible(worldBB)) continue;

                ScreenBox screenBox = projectBox(event, player, worldBB);
                if (screenBox == null) continue;

                boxes.add(screenBox);
            }
        }

        trails.keySet().removeIf(uuid -> !seen.contains(uuid));
        lastPositions.keySet().removeIf(uuid -> !seen.contains(uuid));
    }

    @EventSubscribe
    public void event(EventRender2DGui event) {
        if (boxes.isEmpty()) return;

        Draw2D draw = event.getDraw2D();
        Matrix4f mat = event.getMatrixStack().last().pose();
        boolean twoD = enabledModes.isEnabled(TWO_D);
        boolean brackets = enabledModes.isEnabled(BRACKETS);
        boolean name = enabledModes.isEnabled(NAME);
        boolean distance = enabledModes.isEnabled(DISTANCE);
        boolean bar = healthBar.is(TWO_D);
        boolean slim = healthBar.is(SLIM);
        int fillColor = colorSetting.getARGB();
        int outlineColor = fillColor | 0xFF000000;

        for (ScreenBox box : boxes) {
            float x = box.x;
            float y = box.y;
            float w = box.w;
            float h = box.h;

            if (name) {
                drawName(draw, mat, box, x, y, w);
            }

            if (distance) {
                drawDistance(draw, mat, box, x, y, h);
            }

            if (twoD) {
                if (fill.getValue()) {
                    draw.rect(mat, x, y, w, h, 0, fillColor);
                }
                drawOutlineRect(draw, mat, x - 1, y - 1, w + 2, h + 2, 1, 0xFF000000);
                drawOutlineRect(draw, mat, x, y, w, h, 1, outlineColor);
            }

            if (brackets) {
                drawBrackets(draw, mat, x, y, w, h, outlineColor);
            }

            if (bar) {
                drawHealthBar(draw, mat, box.player, x, y, w, h);
            } else if (slim) {
                drawSlimHealthBar(draw, mat, box.player, x, y, h);
            }
        }
    }

    private void updateTrail(Player player, Vec3 pos) {
        Deque<Vec3> trail = trails.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        Vec3 last = trail.peekLast();
        if (last == null || last.distanceToSqr(pos) > 0.0025) {
            trail.addLast(pos);
        }
        while (trail.size() > trailLength.getValue()) {
            trail.removeFirst();
        }
    }

    private void drawTrail(FramebufferTarget draw3, Player player) {
        Deque<Vec3> trail = trails.get(player.getUUID());
        if (trail == null || trail.size() < 2) return;

        Vec3 prev = null;
        int index = 0;
        int size = trail.size();
        for (Vec3 point : trail) {
            if (prev != null) {
                float fade = 1f - (float) index / (size - 1);
                int alpha = (int) (20 + fade * 200);
                int color = (alpha << 24) | (outlineColorOf() & 0x00FFFFFF);
                draw3.line(prev.x, prev.y, prev.z, point.x, point.y, point.z, color);
            }
            prev = point;
            index++;
        }
    }

    private void drawVelocityArrow(FramebufferTarget draw3, Player player, Vec3 pos) {
        Vec3 last = lastPositions.put(player.getUUID(), pos);
        if (last == null) return;

        double dx = pos.x - last.x;
        double dz = pos.z - last.z;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < 0.01) return;

        double len = Math.min(1.2, 0.15 + speed * 2.5);
        double nx = dx / speed;
        double nz = dz / speed;
        double baseY = pos.y + 0.1;
        double ex = pos.x + nx * len;
        double ez = pos.z + nz * len;

        int color = velocityColor(speed);
        draw3.line(pos.x, baseY, pos.z, ex, baseY, ez, color);

        double head = 0.18;
        double px = -nz;
        double pz = nx;
        draw3.line(ex, baseY, ez, ex - nx * head + px * head * 0.6, baseY, ez - nz * head + pz * head * 0.6, color);
        draw3.line(ex, baseY, ez, ex - nx * head - px * head * 0.6, baseY, ez - nz * head - pz * head * 0.6, color);
    }

    private int outlineColorOf() {
        return colorSetting.getARGB() | 0xFF000000;
    }

    private int velocityColor(double speed) {
        float t = (float) Math.min(1.0, speed / 0.35);
        float hue = (1f - t) * 0.33f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 1f, 1f) & 0x00FFFFFF);
    }

    private static void drawBox(FramebufferTarget draw3, double x, double y, double z, double sx, double sy, double sz,
            int fill, int outline) {
        draw3.box(x, y, z, sx, sy, sz, fill);
        double maxX = x + sx;
        double maxY = y + sy;
        double maxZ = z + sz;
        draw3.line(x, y, z, maxX, y, z, outline);
        draw3.line(maxX, y, z, maxX, y, maxZ, outline);
        draw3.line(maxX, y, maxZ, x, y, maxZ, outline);
        draw3.line(x, y, maxZ, x, y, z, outline);
        draw3.line(x, maxY, z, maxX, maxY, z, outline);
        draw3.line(maxX, maxY, z, maxX, maxY, maxZ, outline);
        draw3.line(maxX, maxY, maxZ, x, maxY, maxZ, outline);
        draw3.line(x, maxY, maxZ, x, maxY, z, outline);
        draw3.line(x, y, z, x, maxY, z, outline);
        draw3.line(maxX, y, z, maxX, maxY, z, outline);
        draw3.line(maxX, y, maxZ, maxX, maxY, maxZ, outline);
        draw3.line(x, y, maxZ, x, maxY, maxZ, outline);
    }

    private static void drawRings(FramebufferTarget draw3, Player player, Vec3 pos, float tickDelta, double sx, double sy,
            int color, int dim) {
        float tick = player.tickCount + tickDelta;
        double phase = tick * 0.12;
        double radius = Math.max(0.4, sx * 0.9);
        Draw3DUtils.ring(draw3, pos.x, pos.y + 0.06, pos.z, radius, phase, dim, 24);
        Draw3DUtils.ring(draw3, pos.x, pos.y + sy * 0.35, pos.z, radius * 1.15, -phase * 0.8, color, 24);
        Draw3DUtils.ring(draw3, pos.x, pos.y + sy, pos.z, radius * 0.85, phase * 0.6, dim, 20);
        Draw3DUtils.beam(draw3, pos.x, pos.z, pos.y, pos.y + sy, dim);
    }

    private static void drawOrbit(FramebufferTarget draw3, Player player, Vec3 pos, float tickDelta, double sx, double sy,
            int color, int dim, int outline) {
        float tick = player.tickCount + tickDelta;
        double cy = pos.y + sy * 0.5;
        double r = Math.max(1.1, sx * 1.6);
        double[] tilts = {90, 70, 110, 50, 130, 30, 150};
        double[] scales = {1.0, 0.9, 0.9, 0.78, 0.78, 0.66, 0.66};
        double[] speeds = {0.06, -0.09, 0.12, -0.16, 0.2, -0.24, 0.28};
        for (int i = 0; i < tilts.length; i++) {
            double phase = tick * speeds[i] + i * 0.7;
            int col = (i % 2 == 0) ? color : dim;
            drawTiltedRing(draw3, pos.x, cy, pos.z, r * scales[i], phase, Math.toRadians(tilts[i]), col, 32);
        }
        drawOrbitingDot(draw3, pos.x, cy, pos.z, r, tick * 0.5, Math.toRadians(70), outline);
        drawOrbitingDot(draw3, pos.x, cy, pos.z, r * 0.9, -tick * 0.4, Math.toRadians(110), color);
    }

    private static void drawShield(FramebufferTarget draw3, Player player, Vec3 pos, float tickDelta, double sx, double sy,
            int color, int dim) {
        float tick = player.tickCount + tickDelta;
        double maxRadius = Math.max(0.5, sx * 1.15);
        for (int i = 1; i <= 5; i++) {
            double frac = i / 5.0;
            double y = pos.y + sy * frac;
            double r = maxRadius * Math.sin(frac * Math.PI);
            if (r < 0.05) continue;
            Draw3DUtils.ring(draw3, pos.x, y, pos.z, r, tick * (0.1 + i * 0.02),
                    (i % 2 == 0) ? color : dim, 20);
        }
    }

    private static void drawHealthBar(Draw2D draw, Matrix4f mat, Player player, float x, float y, float w, float h) {
        float percent = healthPercent(player);
        float barW = Math.max(2f, w * 0.08f);
        float gap = 2f;
        float bx = x - barW - gap;
        float fillH = h * percent;
        draw.rect(mat, bx - 1, y - 1, barW + 2, h + 2, 0, 0xFF000000);
        draw.rect(mat, bx, y, barW, h, 0, 0xFF202020);
        draw.rect(mat, bx, y + h - fillH, barW, fillH, 0, healthColor(percent));
    }

    private static void drawSlimHealthBar(Draw2D draw, Matrix4f mat, Player player, float x, float y, float h) {
        float percent = healthPercent(player);
        float width = 2f;
        float gap = 4f;
        float fillH = h * percent;
        draw.rect(mat, x - width - gap, y + h - fillH, width, fillH, 0, healthColor(percent));
    }

    private static void drawOutlineRect(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, float thickness,
            int color) {
        draw.rect(mat, x, y, w, thickness, 0, color);
        draw.rect(mat, x, y + h - thickness, w, thickness, 0, color);
        draw.rect(mat, x, y, thickness, h, 0, color);
        draw.rect(mat, x + w - thickness, y, thickness, h, 0, color);
    }

    private static void drawBrackets(Draw2D draw, Matrix4f mat, float x, float y, float w, float h, int color) {
        float len = 7f;
        float th = 1.5f;
        drawBracketCorner(draw, mat, x, y, len, th, 1, 1, color);
        drawBracketCorner(draw, mat, x + w, y, len, th, -1, 1, color);
        drawBracketCorner(draw, mat, x, y + h, len, th, 1, -1, color);
        drawBracketCorner(draw, mat, x + w, y + h, len, th, -1, -1, color);
    }

    private static void drawBracketCorner(Draw2D draw, Matrix4f mat, float cx, float cy, float len, float th,
            int dirX, int dirY, int color) {
        float hx = dirX > 0 ? cx : cx - len;
        float hy = dirY > 0 ? cy - th : cy;
        draw.rect(mat, hx - 1, hy - 1, len + th + 2, th + 2, 0, 0xFF000000);
        draw.rect(mat, hx, hy, len + th, th, 0, color);
        float vx = dirX > 0 ? cx - th : cx;
        float vy = dirY > 0 ? cy : cy - len;
        draw.rect(mat, vx - 1, vy - 1, th + 2, len + th + 2, 0, 0xFF000000);
        draw.rect(mat, vx, vy, th, len + th, 0, color);
    }

    private static void drawHelix(FramebufferTarget draw3, Player player, Vec3 pos, float tickDelta, double sy,
            int color, int dim) {
        float tick = player.tickCount + tickDelta;
        double r = Math.max(0.3, sy * 0.22);
        double y0 = pos.y + 0.05;
        double y1 = pos.y + sy;
        double phase = tick * 0.35;
        double turns = 2.5;
        int segs = 48;
        double prevX = 0;
        double prevY = 0;
        double prevZ = 0;
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / segs;
            double angle = t * turns * Math.PI * 2.0 + phase;
            double x = pos.x + Math.cos(angle) * r;
            double y = y0 + (y1 - y0) * t;
            double z = pos.z + Math.sin(angle) * r;
            if (i > 0) {
                int col = (i % 2 == 0) ? color : dim;
                draw3.line(prevX, prevY, prevZ, x, y, z, col);
            }
            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }

    private static void drawWave(FramebufferTarget draw3, Player player, Vec3 pos, float tickDelta, double sy,
            int color) {
        float tick = player.tickCount + tickDelta;
        for (int i = 0; i < 3; i++) {
            double prog = ((tick * 0.02) + i / 3.0) % 1.0;
            double y = pos.y + sy * prog;
            double radius = 0.3 + prog * 1.0;
            int col = alphaMul(color, (float) (1.0 - prog));
            Draw3DUtils.ring(draw3, pos.x, y, pos.z, radius, tick * 0.5, col, 24);
        }
    }

    private static void drawStars(FramebufferTarget draw3, Player player, Vec3 pos, float tickDelta, double sy,
            int color) {
        float tick = player.tickCount + tickDelta;
        double radius = Math.max(0.55, sy * 0.35);
        double cy = pos.y + sy * 0.55;
        for (int i = 0; i < 7; i++) {
            double angle = i * (Math.PI * 2.0 / 7) + tick * 0.02;
            double wobble = Math.sin(tick * 0.06 + i * 2.1) * 0.12;
            double x = pos.x + Math.cos(angle) * (radius + wobble);
            double y = cy + Math.sin(tick * 0.03 + i * 1.3) * sy * 0.05;
            double z = pos.z + Math.sin(angle) * (radius + wobble);
            float twinkle = 0.55f + 0.45f * ((float) Math.sin(tick * 0.13 + i * 1.7) * 0.5f + 0.5f);
            int col = alphaMul(color, 0.75f + 0.25f * twinkle);
            double s = 0.13;
            draw3.line(x - s, y, z, x + s, y, z, col);
            draw3.line(x, y - s, z, x, y + s, z, col);
            draw3.line(x, y, z - s, x, y, z + s, col);
            draw3.line(x - s * 0.6, y, z - s * 0.6, x + s * 0.6, y, z + s * 0.6, col);
            draw3.line(x - s * 0.6, y, z + s * 0.6, x + s * 0.6, y, z - s * 0.6, col);
        }
    }

    private static void drawName(Draw2D draw, Matrix4f mat, ScreenBox box, float x, float y, float w) {
        String text = box.player.getName().getString();
        float tw = Fonts.interSemiBold.getWidth(text, 8);
        draw.textWithShadow(Fonts.interSemiBold, mat, text, x + w / 2f - tw / 2f, y - 11, 8, 0xFFFFFFFF);
    }

    private static void drawDistance(Draw2D draw, Matrix4f mat, ScreenBox box, float x, float y, float h) {
        String text = (int) Math.round(mc.player.distanceTo(box.player)) + "m";
        float tw = Fonts.interSemiBold.getWidth(text, 7);
        draw.textWithShadow(Fonts.interSemiBold, mat, text, x + box.w / 2f - tw / 2f, y + h + 2, 7, 0xFFFFFFFF);
    }

    private static void drawSkeleton(FramebufferTarget draw3, Vec3 pos, double height, float yawDegrees, int color) {
        double yaw = Math.toRadians(yawDegrees);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double sh = 0.28;
        double h = height;
        double head = h;
        double shoulder = h * 0.82;
        double hand = h * 0.45;
        double hip = h * 0.42;
        double foot = h * 0.12;

        double[] pts = new double[] {
                0, head, 0,      0, shoulder, 0,
                -0.09, head, 0,  0.09, head, 0,
                -sh, shoulder, 0, sh, shoulder, 0,
                -sh, shoulder, 0, -sh, hand, 0.12,
                sh, shoulder, 0,  sh, hand, 0.12,
                0, shoulder, 0,  0, hip, 0,
                -sh * 0.55, hip, 0, -sh * 0.55, foot, 0,
                sh * 0.55, hip, 0,  sh * 0.55, foot, 0
        };

        for (int i = 0; i < pts.length; i += 6) {
            double x1 = pos.x + rightX * pts[i] + forwardX * pts[i + 2];
            double z1 = pos.z + rightZ * pts[i] + forwardZ * pts[i + 2];
            double x2 = pos.x + rightX * pts[i + 3] + forwardX * pts[i + 5];
            double z2 = pos.z + rightZ * pts[i + 3] + forwardZ * pts[i + 5];
            draw3.line(x1, pos.y + pts[i + 1], z1, x2, pos.y + pts[i + 4], z2, color);
        }
    }

    private static void drawTiltedRing(FramebufferTarget draw3, double cx, double cy, double cz,
            double radius, double phase, double tilt, int color, int segments) {
        double cosP = Math.cos(tilt);
        double sinP = Math.sin(tilt);
        double prevX = cx + Math.cos(phase) * radius;
        double prevY = cy + Math.sin(phase) * radius * cosP;
        double prevZ = cz + Math.sin(phase) * radius * sinP;
        for (int i = 1; i <= segments; i++) {
            double a = phase + (Math.PI * 2.0 * i) / segments;
            double x = cx + Math.cos(a) * radius;
            double y = cy + Math.sin(a) * radius * cosP;
            double z = cz + Math.sin(a) * radius * sinP;
            draw3.line(prevX, prevY, prevZ, x, y, z, color);
            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }

    private static void drawOrbitingDot(FramebufferTarget draw3, double cx, double cy, double cz,
            double radius, double phase, double tilt, int color) {
        double cosP = Math.cos(tilt);
        double sinP = Math.sin(tilt);
        double x = cx + Math.cos(phase) * radius;
        double y = cy + Math.sin(phase) * radius * cosP;
        double z = cz + Math.sin(phase) * radius * sinP;
        double s = 0.18;
        draw3.line(x - s, y, z, x + s, y, z, color);
        draw3.line(x, y - s, z, x, y + s, z, color);
        draw3.line(x, y, z - s, x, y, z + s, color);
    }

    private static float healthPercent(Player player) {
        float heal = player.getHealth() + player.getAbsorptionAmount();
        return Math.min(Math.max(heal / player.getMaxHealth(), 0f), 1f);
    }

    private static int healthColor(float percent) {
        float hue = percent / 3f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 1f, 1f) & 0x00FFFFFF);
    }

    private static int alphaMul(int color, float f) {
        return ((int) ((color >>> 24) * f) << 24) | (color & 0x00FFFFFF);
    }

    private static ScreenBox projectBox(EventRender3D event, Player player, AABB bb) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().position();
        Matrix4f mv = event.getModelViewMatrix();
        Matrix4f proj = event.getProjectionMatrix();
        int sw = RenderUtil.getScaledWidth();
        int sh = RenderUtil.getScaledHeight();

        Vec3[] corners = {
                new Vec3(bb.minX, bb.minY, bb.minZ),
                new Vec3(bb.maxX, bb.minY, bb.minZ),
                new Vec3(bb.maxX, bb.minY, bb.maxZ),
                new Vec3(bb.minX, bb.minY, bb.maxZ),
                new Vec3(bb.minX, bb.maxY, bb.minZ),
                new Vec3(bb.maxX, bb.maxY, bb.minZ),
                new Vec3(bb.maxX, bb.maxY, bb.maxZ),
                new Vec3(bb.minX, bb.maxY, bb.maxZ)
        };

        Vector4f[] clip = new Vector4f[8];
        for (int i = 0; i < 8; i++) {
            Vec3 c = corners[i];
            Vector4f v = new Vector4f(
                    (float) (c.x - camPos.x),
                    (float) (c.y - camPos.y),
                    (float) (c.z - camPos.z),
                    1f);
            v.mul(mv);
            v.mul(proj);
            clip[i] = v;
        }

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        float epsilon = 0.05f;
        Rectangle rect = null;
        for (int[] e : edges) {
            Vector4f a = clip[e[0]];
            Vector4f b = clip[e[1]];
            boolean aIn = a.w() > epsilon;
            boolean bIn = b.w() > epsilon;
            if (!aIn && !bIn) continue;
            Vector4f ca = a;
            Vector4f cb = b;
            if (!aIn) {
                float t = (epsilon - a.w()) / (b.w() - a.w());
                ca = lerp4(a, b, t);
            } else if (!bIn) {
                float t = (epsilon - a.w()) / (b.w() - a.w());
                cb = lerp4(a, b, t);
            }
            double ax = (ca.x() / ca.w() * 0.5 + 0.5) * sw;
            double ay = (0.5 - ca.y() / ca.w() * 0.5) * sh;
            double bx = (cb.x() / cb.w() * 0.5 + 0.5) * sw;
            double by = (0.5 - cb.y() / cb.w() * 0.5) * sh;
            if (rect == null) {
                rect = new Rectangle(ax, ay, ax, ay);
            }
            if (ax < rect.x) rect.x = ax;
            if (ay < rect.y) rect.y = ay;
            if (ax > rect.z) rect.z = ax;
            if (ay > rect.w) rect.w = ay;
            if (bx < rect.x) rect.x = bx;
            if (by < rect.y) rect.y = by;
            if (bx > rect.z) rect.z = bx;
            if (by > rect.w) rect.w = by;
        }
        if (rect == null) return null;

        return new ScreenBox(
                player,
                (float) rect.x,
                (float) rect.y,
                (float) (rect.z - rect.x),
                (float) (rect.w - rect.y));
    }

    private static Vector4f lerp4(Vector4f a, Vector4f b, float t) {
        return new Vector4f(
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t,
                a.w() + (b.w() - a.w()) * t
        );
    }

    private static final class ScreenBox {
        final Player player;
        final float x;
        final float y;
        final float w;
        final float h;

        ScreenBox(Player player, float x, float y, float w, float h) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Rectangle {
        double x, y, z, w;

        Rectangle(double x, double y, double z, double w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }
    }
}
