package hack.echo.client.features.impl.render;

import hack.echo.client.api.TupleCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.ColorSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.render2.impl.opengl.utils.RenderUtil;
import hack.echo.client.render3.api.FramebufferTarget;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

public class SpectatorESP extends Feature {

    private final BoolSetting esp = new BoolSetting(Concat.of("ESP"), true);
    private final BoolSetting forceModel = new BoolSetting(Concat.of("Force Model"), false);
    private final FloatSetting opacity = new FloatSetting(Concat.of("Opacity"), 100f, 0f, 100f, 1f, Concat.of("%"));
    private final BoolSetting onlyHead = new BoolSetting(Concat.of("Only Head"), false);
    private final BoolSetting nametag = new BoolSetting(Concat.of("Nametag"), true);
    private final BoolSetting vanish = new BoolSetting(Concat.of("Vanish"), true);
    private final ColorSetting colorSetting = new ColorSetting(Concat.of("Color"), 0x7F4D9CFF);

    private final Queue<NameTag> nameTags = new ArrayDeque<>();

    public SpectatorESP() {
        super(new FeatureInfo(
                Concat.of("Spectator ESP"),
                Concat.of("ESP, model rendering and nametags for spectators and vanished players"),
                Category.RENDER
        ));
    }

    public boolean isForceModel() {
        return forceModel.getValue();
    }

    public float getOpacity() {
        return opacity.getValue();
    }

    public boolean isOnlyHead() {
        return onlyHead.getValue();
    }

    public boolean isDetectVanish() {
        return vanish.getValue();
    }

    public static boolean isVanished(Player player) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return false;
        UUID id = player.getUUID();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (info.getProfile().id().equals(id)) return false;
        }
        return true;
    }

    @EventSubscribe
    public void event(EventRender3D event) {
        if (mc.level == null) return;

        var draw3 = event.getDraw3D().getCustomTarget();
        float tickDelta = event.getTickDelta();
        int fillColor = colorSetting.getARGB();
        int outlineColor = fillColor | 0xFF000000;
        boolean box = esp.getValue();
        boolean name = nametag.getValue();

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (player == mc.player) continue;
            if (!player.isSpectator() && !(vanish.getValue() && isVanished(player))) continue;

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
    public void event(EventRender2DGui event) {
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
