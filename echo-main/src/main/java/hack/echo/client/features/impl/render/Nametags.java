package hack.echo.client.features.impl.render;

import hack.echo.client.Echo;
import hack.echo.client.api.GuiGraphicsCompat;
import hack.echo.client.api.TupleCompat;
import hack.echo.client.config.ColorConfig;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventRender2DGui;
import hack.echo.client.event.impl.EventRender3D;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.render2.api.Draw2D;
import hack.echo.client.render2.impl.opengl.font.Fonts;
import hack.echo.client.render2.impl.opengl.utils.RenderUtil;
import hack.echo.client.utils.combat.TargetUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Nametags extends Feature {

    private final ModeSetting healthBar = new ModeSetting(
            Concat.of("Health Bar"),
            Concat.of("Bar + Number"),
            Concat.of("Off"),
            Concat.of("Bar"),
            Concat.of("Bar + Number"));
    private final BoolSetting armor = new BoolSetting(Concat.of("Armor"), true);
    private final BoolSetting hands = new BoolSetting(Concat.of("Hands"), false);
    private final BoolSetting itemCount = new BoolSetting(Concat.of("Item Count"), true);
    private final IntSetting range = new IntSetting(Concat.of("Range"), 64, 0, 256);
    private final IntSetting scale = new IntSetting(Concat.of("Scale"), 100, 50, 200);
    private final BoolSetting dynamicScaling = new BoolSetting(Concat.of("Dynamic Scaling"), true);
    private final BoolSetting antiBot = new BoolSetting(Concat.of("AntiBot"), true);

    private final List<Tag> tags = new ArrayList<>();

    public Nametags() {
        super(new FeatureInfo(
                Concat.of("Nametags"),
                Concat.of("Enhanced nametag rendering"),
                Category.RENDER));
    }

    @EventSubscribe
    public void onRender3D(EventRender3D event) {
        tags.clear();
        if (isNull()) return;

        DeltaTracker tick = mc.getDeltaTracker();
        float partial = tick.getGameTimeDeltaPartialTick(false);

        for (Player player : mc.level.players()) {
            if (player == null || player == mc.player) continue;

            Vec3 head = player.getPosition(partial)
                    .add(0, player.getBbHeight() + 0.1, 0);

            TupleCompat<Vec3, Boolean> projection = RenderUtil.project(
                    event.getModelViewMatrix(),
                    event.getProjectionMatrix(),
                    head);

            if (!projection.getB()) continue;

            tags.add(new Tag(player, projection.getA().x(), projection.getA().y()));
        }
    }

    @EventSubscribe
    public void onRender2D(EventRender2DGui event) {
        if (isNull() || tags.isEmpty()) return;

        Draw2D draw = event.getDraw2D();
        Matrix4f mat = event.getMatrixStack().last().pose();
        Freecam freecam = Echo.featureManager.getFeatureByClass(Freecam.class);

        for (Tag tag : tags) {
            Player player = tag.player;

            if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON && !freecam.isEnabled())
                continue;
            if (!player.canBeSeenByAnyone() || player.isRemoved() || player.isSpectator())
                continue;
            if (antiBot.getValue() && TargetUtils.isBot(player))
                continue;

            float distance = mc.player.distanceTo(player);
            if (distance > range.getValue())
                continue;

            renderTag(draw, mat, event.getContext(), player, tag.x, tag.y, distance);
        }
    }

    private void renderTag(Draw2D draw, Matrix4f mat, GuiGraphicsCompat ctx, Player player, double centerX, double headY, float distance) {
        if (Fonts.inter == null) return;

        float scale = calculateScale(distance);
        float fontSize = 9 * scale;
        String name = player.getName().getString();
        float textWidth = Fonts.inter.getWidth(name, fontSize);
        float textHeight = 10 * scale;
        float padding = 2 * scale;
        boolean showBar = !healthBar.is(Concat.of("Off"));
        boolean showNumber = healthBar.is(Concat.of("Bar + Number"));
        float barHeight = showBar ? (showNumber ? 8 * scale : 4 * scale) : 0;

        List<ItemStack> slots = new ArrayList<>();
        if (ctx != null) {
            if (hands.getValue()) {
                slots.add(player.getItemBySlot(EquipmentSlot.OFFHAND));
                slots.add(player.getItemBySlot(EquipmentSlot.MAINHAND));
            }
            if (armor.getValue()) {
                slots.add(player.getItemBySlot(EquipmentSlot.HEAD));
                slots.add(player.getItemBySlot(EquipmentSlot.CHEST));
                slots.add(player.getItemBySlot(EquipmentSlot.LEGS));
                slots.add(player.getItemBySlot(EquipmentSlot.FEET));
            }
        }

        float slotSize = 20 * scale;
        float slotGap = 2 * scale;
        float rowWidth = slots.isEmpty() ? 0 : slots.size() * slotSize + (slots.size() - 1) * slotGap;
        float armorGap = slots.isEmpty() ? 0 : 4 * scale;

        float boxWidth = Math.max(textWidth + 2 * padding, rowWidth);
        float boxHeight = padding + (slots.isEmpty() ? 0 : slotSize + armorGap) + textHeight + padding + barHeight;
        float x = (float) (centerX - boxWidth / 2f);
        float y = (float) (headY - boxHeight - 2);

        draw.rect(mat, x, y, boxWidth, boxHeight, 0, ColorConfig.BACKGROUND_DARK.getRGB());

        float currentY = y + padding;
        if (!slots.isEmpty()) {
            float toGui = mc.getWindow().getGuiScaledWidth() / (float) RenderUtil.getScaledWidth();
            float rowStartX = x + (boxWidth - rowWidth) / 2f;
            for (int i = 0; i < slots.size(); i++) {
                float slotX = rowStartX + i * (slotSize + slotGap);
                draw.rect(mat, slotX, currentY, slotSize, slotSize, 0, 0x66000000);
                ItemStack stack = slots.get(i);
                if (!stack.isEmpty()) {
                    ctx.pushMatrix();
                    ctx.translate((slotX + 2 * scale) * toGui, (currentY + 2 * scale) * toGui);
                    ctx.scale(scale * toGui, scale * toGui);
                    ctx.renderItem(stack, 0, 0);
                    if (itemCount.getValue()) {
                        ctx.renderItemDecorations(mc.font, stack, 0, 0);
                    }
                    ctx.popMatrix();
                }
            }
            currentY += slotSize + armorGap;
        }

        if (showBar) {
            float maxHealth = player.getMaxHealth();
            float health = Math.max(0f, player.getHealth());
            float absorb = Math.max(0f, player.getAbsorptionAmount());
            float ratio = Math.min(1f, health / maxHealth);
            float absorbRatio = Math.min(1f, (health + absorb) / maxHealth);
            float barY = currentY + textHeight + padding;

            draw.rect(mat, x, barY, boxWidth, barHeight, 0, 0xFF000000);
            draw.rect(mat, x, barY, boxWidth * ratio, barHeight, 0, healthColor(ratio));

            if (absorb > 0 && absorbRatio > ratio) {
                draw.rect(mat, x + boxWidth * ratio, barY, boxWidth * (absorbRatio - ratio), barHeight, 0, 0xFFFDCE5C);
            }

            draw.rect(mat, x, barY, boxWidth, 1 * scale, 0, 0x33FFFFFF);
            draw.rect(mat, x, barY + barHeight - 1 * scale, boxWidth, 1 * scale, 0, 0x66000000);

            if (showNumber) {
                String hpText = formatHealth(health);
                float hpSize = 6 * scale;
                float hpWidth = Fonts.inter.getWidth(hpText, hpSize);
                draw.textWithShadow(
                        Fonts.inter,
                        mat,
                        hpText,
                        x + (boxWidth - hpWidth) / 2f,
                        barY + (barHeight - hpSize) / 2f,
                        hpSize,
                        ColorConfig.TEXT_WHITE.getRGB());
            }
        }

        draw.textWithShadow(
                Fonts.inter,
                mat,
                name,
                (float) centerX - textWidth / 2f,
                currentY + padding,
                fontSize,
                ColorConfig.TEXT_WHITE.getRGB());
    }

    private int healthColor(float ratio) {
        if (ratio > 0.6f) return 0xFF55FF55;
        if (ratio > 0.3f) return 0xFFFFFF55;
        return 0xFFFF5555;
    }

    private String formatHealth(float health) {
        if (health == Math.floor(health)) return String.valueOf((int) health);
        return String.format("%.1f", health);
    }

    private float calculateScale(float distance) {
        if (dynamicScaling.getValue()) {
            float baseScale = scale.getValue() / 100f;
            float distanceFactor = 1.0f - (distance / range.getValue());
            return Math.max(baseScale * distanceFactor, 0.5f * baseScale);
        } else {
            return scale.getValue() / 100f;
        }
    }

    private static final class Tag {
        final Player player;
        final double x;
        final double y;

        Tag(Player player, double x, double y) {
            this.player = player;
            this.x = x;
            this.y = y;
        }
    }
}
