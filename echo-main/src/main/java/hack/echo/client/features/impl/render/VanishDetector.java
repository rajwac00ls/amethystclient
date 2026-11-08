package hack.echo.client.features.impl.render;

import hack.echo.client.api.TupleCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.impl.render.hud.NotificationsHudModule;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.render2.impl.opengl.utils.RenderUtil;
import hack.echo.client.render3.api.FramebufferTarget;
import hack.echo.client.utils.ChatUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class VanishDetector extends Feature {

    private final BoolSetting notifyHud = new BoolSetting(Concat.of("Notifications"), true);
    private final BoolSetting notifyChat = new BoolSetting(Concat.of("Chat Message"), false);
    private final ColorSetting textColor = new ColorSetting(Concat.of("Text Color"), 0xFFFFFFFF,
        o -> notifyHud.getValue() || notifyChat.getValue(), false);
    private final IntSetting textSize = new IntSetting(Concat.of("Text Size"), 7, 4, 20,
        o -> notifyHud.getValue());

    private final BoolSetting esp = new BoolSetting(Concat.of("ESP"), true);
    private final BoolSetting tracer = new BoolSetting(Concat.of("Tracer"), true, o -> esp.getValue());
    private final BoolSetting nametag = new BoolSetting(Concat.of("Nametag"), true, o -> esp.getValue());
    private final ColorSetting espColor = new ColorSetting(Concat.of("ESP Color"), 0x7FFF4444,
        o -> esp.getValue(), false);
    private final IntSetting graceTicks = new IntSetting(Concat.of("Grace Ticks"), 10, 0, 100);

    private final Map<UUID, Integer> suspicion = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> notified = new HashSet<>();
    private final Queue<NameTag> nameTags = new ArrayDeque<>();

    public VanishDetector() {
        super(new FeatureInfo(
            Concat.of("Vanish Detector"),
            Concat.of("Detects players in vanish (in world but absent from the tab list)"),
            Category.RENDER
        ));
    }

    @Override
    public void onEnable() {
        clearState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        clearState();
        super.onDisable();
    }

    private void clearState() {
        suspicion.clear();
        vanished.clear();
        notified.clear();
        nameTags.clear();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull() || mc.getConnection() == null) return;

        Set<UUID> tabUuids = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().id();
            if (id != null) tabUuids.add(id);
        }

        Set<UUID> inWorld = new HashSet<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            inWorld.add(player.getUUID());
        }

        for (UUID uuid : inWorld) {
            if (tabUuids.contains(uuid)) {
                suspicion.remove(uuid);
                vanished.remove(uuid);
                notified.remove(uuid);
                continue;
            }

            int ticks = suspicion.merge(uuid, 1, Integer::sum);
            if (ticks < graceTicks.getValue()) continue;

            vanished.add(uuid);
            if (notified.add(uuid)) {
                Player player = mc.level.getPlayerByUUID(uuid);
                if (player != null) notifyVanished(player);
            }
        }

        suspicion.keySet().retainAll(inWorld);
        vanished.retainAll(inWorld);
        notified.retainAll(inWorld);
    }

    private void notifyVanished(Player player) {
        String message = Concat.of(player.getName().getString(), " is in vanish").toString();

        if (notifyHud.getValue()) {
            NotificationsHudModule.notifyCustom(this, message, textColor.getARGB(), textSize.getValue());
        }
        if (notifyChat.getValue()) {
            ChatUtils.chat(hexColorPrefix(textColor.getRGB()) + message);
        }
    }

    @EventSubscribe
    public void onRender3D(EventRender3D event) {
        if (isNull() || vanished.isEmpty()) return;

        nameTags.clear();
        boolean box = esp.getValue();
        boolean line = tracer.getValue();
        boolean name = nametag.getValue();
        if (!box && !line && !name) return;

        FramebufferTarget draw3 = event.getDraw3D().getCustomTarget();
        float tickDelta = event.getTickDelta();
        int fillColor = espColor.getARGB();
        int outlineColor = fillColor | 0xFF000000;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || player == mc.player) continue;
            if (!vanished.contains(player.getUUID())) continue;

            Vec3 pos = player.getPosition(tickDelta);
            AABB bb = player.getBoundingBox();
            double sx = bb.getXsize();
            double sy = bb.getYsize();
            double sz = bb.getZsize();
            double minX = pos.x - sx / 2.0;
            double minY = pos.y;
            double minZ = pos.z - sz / 2.0;

            if (box) {
                draw3.box(minX, minY, minZ, sx, sy, sz, fillColor);
                drawBoxOutline(draw3, minX, minY, minZ, sx, sy, sz, outlineColor);
            }
            if (line) {
                draw3.tracer(pos.x, pos.y + sy, pos.z, outlineColor);
            }
            if (name) {
                TupleCompat<Vec3, Boolean> projection = RenderUtil.project(
                        event.getModelViewMatrix(),
                        event.getProjectionMatrix(),
                        new Vec3(pos.x, pos.y + sy, pos.z));
                if (projection.getB()) {
                    Vec3 p = projection.getA();
                    nameTags.add(new NameTag(player, (float) p.x(), (float) p.y()));
                }
            }
        }
    }

    @EventSubscribe
    public void onRender2D(EventRender2DGui event) {
        while (!nameTags.isEmpty()) {
            NameTag tag = nameTags.poll();
            drawNameTag(event, tag.player, tag.x, tag.y);
        }
    }

    private void drawNameTag(EventRender2DGui event, Player player, float x, float y) {
        if (Fonts.inter == null) return;

        var draw = event.getDraw2D();
        CharSequence name = player.getName().getString();
        float fontSize = 9f;
        float width = Fonts.inter.getWidth(name, fontSize);
        float height = 10f;
        float padding = 2f;

        event.getMatrixStack().pushPose();
        Matrix4f mat = event.getMatrixStack().last().pose();
        draw.rect(mat, x - width / 2 - padding, y - height - padding, width + 2 * padding, height + padding, 0, 0x99000000);
        draw.textWithShadow(Fonts.inter, mat, name, x - width / 2, y - height, fontSize, Color.WHITE.getRGB());
        event.getMatrixStack().popPose();
    }

    private static String hexColorPrefix(int rgb) {
        StringBuilder prefix = new StringBuilder("§x");
        for (char c : String.format("%06x", rgb).toCharArray()) {
            prefix.append('§').append(c);
        }
        return prefix.toString();
    }

    private static void drawBoxOutline(FramebufferTarget draw, double minX, double minY, double minZ, double sx, double sy, double sz, int color) {
        double maxX = minX + sx;
        double maxY = minY + sy;
        double maxZ = minZ + sz;
        draw.line(minX, minY, minZ, maxX, minY, minZ, color);
        draw.line(maxX, minY, minZ, maxX, minY, maxZ, color);
        draw.line(maxX, minY, maxZ, minX, minY, maxZ, color);
        draw.line(minX, minY, maxZ, minX, minY, minZ, color);
        draw.line(minX, maxY, minZ, maxX, maxY, minZ, color);
        draw.line(maxX, maxY, minZ, maxX, maxY, maxZ, color);
        draw.line(maxX, maxY, maxZ, minX, maxY, maxZ, color);
        draw.line(minX, maxY, maxZ, minX, maxY, minZ, color);
        draw.line(minX, minY, minZ, minX, maxY, minZ, color);
        draw.line(maxX, minY, minZ, maxX, maxY, minZ, color);
        draw.line(maxX, minY, maxZ, maxX, maxY, maxZ, color);
        draw.line(minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private record NameTag(Player player, float x, float y) {
    }
}
