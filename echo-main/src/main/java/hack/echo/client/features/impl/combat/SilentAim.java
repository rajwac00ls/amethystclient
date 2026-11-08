package hack.echo.client.features.impl.combat;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventMove;
import hack.echo.client.event.impl.MouseUpdateEvent;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.ItemPickerSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.handlers.InputHandler;
import hack.echo.client.utils.combat.CombatUtils;
import hack.echo.client.utils.combat.TargetUtils;
import hack.echo.client.utils.rotation.RotationUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;

public class SilentAim extends Feature {

    public SilentAim() {
        super(new FeatureInfo(
            Concat.of("Silent Aim"),
            Concat.of("Redirects attacks to the nearest target"),
            Category.COMBAT
        ));
    }

    private final FloatSetting range = new FloatSetting(
        Concat.of("Range"), 4.5f, 1.0f, 6.0f, 0.1f);
    private final ModeSetting priority = new ModeSetting(
        Concat.of("Priority"),
        Concat.of("Distance"),
        Concat.of("Distance"), Concat.of("Health"), Concat.of("Angle"));
    private final ModeSetting aimPoint = new ModeSetting(
        Concat.of("Aim Point"),
        Concat.of("Body"),
        Concat.of("Head"), Concat.of("Body"), Concat.of("Feet"));
    private final BoolSetting throughWalls = new BoolSetting(
        Concat.of("Through Walls"), false);
    private final BoolSetting onlyWhileAttacking = new BoolSetting(
        Concat.of("Only While Attacking"), true);
    private final ItemPickerSetting itemWhitelist = new ItemPickerSetting(
        Concat.of("Item Whitelist"));

    @Override
    public void onDisable() {
        if (RotationUtils.isControlledBy(this)) {
            RotationUtils.stopTracking();
        }
        super.onDisable();
    }

    @EventSubscribe
    private void onMouseUpdate(MouseUpdateEvent event) {
        if (isNull() || hack.echo.client.api.MinecraftCompat.getScreen() != null) {
            disengage();
            return;
        }

        if (!isHoldingAllowed()) {
            disengage();
            return;
        }

        if (onlyWhileAttacking.getValue() && !InputHandler.isBindDown(mc.options.keyAttack)) {
            disengage();
            return;
        }

        double rangeValue = range.getValue();
        double rangeSquared = rangeValue * rangeValue;

        List<LivingEntity> targets = mc.level.getEntitiesOfClass(
            LivingEntity.class,
            mc.player.getBoundingBox().inflate(rangeValue),
            e -> e != mc.player && !e.isDeadOrDying() && e.isAlive()
        ).stream()
            .filter(TargetUtils::isTargetAllowed)
            .filter(e -> mc.player.distanceToSqr(e) <= rangeSquared)
            .filter(e -> throughWalls.getValue() || mc.player.hasLineOfSight(e))
            .toList();

        LivingEntity target = selectTarget(targets);
        if (target == null || mc.player.distanceToSqr(target) > rangeSquared) {
            disengage();
            return;
        }

        RotationUtils.aim(this)
            .speed(9999f, 9999f)
            .silent(true)
            .aimType(RotationUtils.AimType.REGULAR)
            .points(getPointsMode())
            .throughWalls(throughWalls.getValue())
            .to(target);
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!RotationUtils.hasSilentRotation() || !RotationUtils.isControlledBy(this)) return;
        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    private void disengage() {
        if (RotationUtils.isControlledBy(this)) {
            RotationUtils.stopTracking();
        }
    }

    private RotationUtils.EntityPoints getPointsMode() {
        if (aimPoint.is(Concat.of("Head"))) return RotationUtils.EntityPoints.STRAIGHT;
        if (aimPoint.is(Concat.of("Feet"))) return RotationUtils.EntityPoints.FEET;
        return RotationUtils.EntityPoints.CENTER;
    }

    private LivingEntity selectTarget(List<LivingEntity> targets) {
        if (targets.isEmpty()) return null;

        if (priority.is(Concat.of("Health"))) {
            return targets.stream()
                .min(Comparator.comparingDouble(LivingEntity::getHealth)
                    .thenComparingDouble(e -> mc.player.distanceToSqr(e)))
                .orElse(null);
        }

        if (priority.is(Concat.of("Angle"))) {
            return targets.stream()
                .min(Comparator.comparingDouble(this::getFovDelta)
                    .thenComparingDouble(e -> mc.player.distanceToSqr(e)))
                .orElse(null);
        }

        return CombatUtils.selectNearestTarget(mc.player, targets);
    }

    private double getFovDelta(LivingEntity entity) {
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var diff = entity.getEyePosition(pt).subtract(mc.player.getEyePosition(pt));
        double horizontalDistance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontalDistance)));
        float yawDiff = Math.abs(net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot()));
        float pitchDiff = Math.abs(targetPitch - mc.player.getXRot());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private boolean isHoldingAllowed() {
        if (itemWhitelist.getSelectedCount() == 0) return true;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.isEmpty()) return itemWhitelist.isSelected(Items.AIR);
        return itemWhitelist.isSelected(mainHand.getItem());
    }
}
