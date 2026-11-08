package hack.echo.client.features.impl.detected;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventHandleInput;
import hack.echo.client.event.impl.EventMovementInput;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.handlers.InputHandler;
import hack.echo.client.handlers.impl.SwapStateManager;
import hack.echo.client.utils.combat.TargetUtils;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.player.PlayerUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

// Mace smash flight.
// Each mace smash bounces the attacker up. Auto-attacking while airborne (with the
// server still recording a high fall distance) chains smashes so you climb; the jump
// repeat keeps you from resting a full tick on the ground, so the server fall
// distance never resets and the chain never breaks.
public class MaceFlight extends Feature {

    private final IntSetting jumpTickRate = new IntSetting(Concat.of("Jump Tick Rate"), 1, 1, 10);
    private final BoolSetting autoAttack = new BoolSetting(Concat.of("Auto Attack"), true);
    private final FloatSetting fallDistance = new FloatSetting(Concat.of("Fall Distance"), 1.5f, 1.5f, 10.0f, 0.1f);
    private final FloatSetting range = new FloatSetting(Concat.of("Range"), 4.0f, 1.0f, 6.0f, 0.1f);
    private final IntSetting attackTickRate = new IntSetting(Concat.of("Attack Tick Rate"), 1, 1, 20);
    private final BoolSetting inputSimulation = new BoolSetting(Concat.of("Input Simulation"), false);
    private final BoolSetting requireAirborne = new BoolSetting(Concat.of("Require Airborne"), true);

    private int tickCounter;

    public MaceFlight() {
        super(new FeatureInfo(
                Concat.of("MaceFlight"),
                Concat.of("Chains mace smash bounces to fly while holding a mace."),
                Category.DETECTED
        ));
        jumpTickRate.describedBy(Concat.of("Forces the jump input every N ticks so you never rest on the ground and the server fall distance stays high."));
        fallDistance.describedBy(Concat.of("Minimum server-synced fall distance required to trigger a smash."));
        attackTickRate.describedBy(Concat.of("Attack every N ticks. The attack fires before the jump within the same tick."));
    }

    @Override
    public CharSequence concat() {
        return Concat.ofFixed(fallDistance.getValue(), 1);
    }

    @EventSubscribe
    private void onTick(EventHandleInput.Early event) {
        if (isNull() || MinecraftCompat.getScreen() != null) return;
        if (SwapStateManager.hasActiveSwaps()) return;
        if (!isHoldingMace()) return;
        if (mc.player.isPassenger()) return;

        tickCounter++;
        if (!autoAttack.getValue()) return;
        if (tickCounter % attackTickRate.getValue() != 0) return;
        if (!canSmash()) return;

        LivingEntity target = resolveTarget();
        if (target == null) return;
        if (!TargetUtils.isTargetAllowed(target)) return;

        mc.hitResult = new EntityHitResult(target);
        mc.crosshairPickEntity = target;
        InputHandler.simulateClick(mc.options.keyAttack, inputSimulation.getValue());
    }

    @EventSubscribe
    private void onMoveInput(EventMovementInput event) {
        if (isNull()) return;
        if (!isHoldingMace()) return;
        if (mc.player.isPassenger()) return;
        if (tickCounter % jumpTickRate.getValue() != 0) return;

        event.jump = true;
    }

    private boolean canSmash() {
        if (requireAirborne.getValue() && mc.player.onGround()) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return false;
        return PlayerUtils.getServerSyncedFallDistance(mc.player) > fallDistance.getValue();
    }

    private LivingEntity resolveTarget() {
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            if (((EntityHitResult) mc.hitResult).getEntity() instanceof LivingEntity target) {
                if (target.isAlive() && !target.isRemoved()) return target;
            }
        }

        double rangeValue = range.getValue();
        double rangeSquared = rangeValue * rangeValue;
        List<LivingEntity> candidates = mc.level.getEntitiesOfClass(
                LivingEntity.class,
                mc.player.getBoundingBox().inflate(rangeValue),
                e -> e != mc.player && e.isAlive() && !e.isDeadOrDying()
        ).stream()
                .filter(TargetUtils::isTargetAllowed)
                .filter(e -> mc.player.distanceToSqr(e) <= rangeSquared)
                .sorted(java.util.Comparator.comparingDouble(mc.player::distanceToSqr))
                .toList();

        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean isHoldingMace() {
        ItemStack mainHand = InventoryUtils.getMainHandItem();
        return mainHand != null && mainHand.is(Items.MACE);
    }
}
