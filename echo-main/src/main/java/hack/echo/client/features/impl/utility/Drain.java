package hack.echo.client.features.impl.utility;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventBlockUpdate;
import hack.echo.client.event.impl.EventMove;
import hack.echo.client.event.impl.EventPacketReceive;
import hack.echo.client.event.impl.EventStartUseItem;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.event.impl.MouseUpdateEvent;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.handlers.RotationHandler;
import hack.echo.client.utils.HitResultUtils;
import hack.echo.client.utils.blocks.BlockUtils;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.player.PlayerUtils;
import hack.echo.client.utils.rotation.RotationConvergenceTracker;
import hack.echo.client.utils.rotation.RotationUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Automatically scans the area for water sources, silently aims at the nearest
 * one inside the configured FOV, and picks it up with an empty bucket. Water is
 * attributed to the player who actually placed it (arm-swing animation, held
 * bucket, or exact local bucket use) and is skipped if that player is a friend.
 * Reuses the AutoAnchor interaction pattern: silent rotations +
 * {@link RotationConvergenceTracker} so the interaction only fires on a tick
 * where Grim's rotation delta gate is closed.
 */
public class Drain extends Feature {

    private static final float ROTATION_SETTLE_THRESHOLD = 1.7f;
    private static final int MAX_AIM_TICKS = 100;
    private static final long OWNER_TTL_MS = 10_000L;
    private static final double OWNER_SEARCH_RADIUS = 6.0;
    private static final long SWING_TTL_MS = 3_000L;
    private static final int SWING_MAP_MAX = 64;
    private static final long BUCKET_PLACE_TTL_MS = 1_000L;

    private enum Step { AIM, SELECT, DRAIN, WAIT }

    private final BoolSetting switchBack = new BoolSetting(Concat.of("Switch Back"), true);
    private final BoolSetting sourceOnly = new BoolSetting(Concat.of("Source Only"), true);
    private final FloatSetting range = new FloatSetting(Concat.of("Range"), 4.5f, 1f, 8f, 0.5f);
    private final FloatSetting fov = new FloatSetting(Concat.of("FOV"), 60f, 0f, 180f, 5f);
    private final BoolSetting silentAim = new BoolSetting(Concat.of("Silent Aim"), true);
    private final IntSetting aimSpeed = new IntSetting(Concat.of("Aim Speed"), 120, 0, 600);
    private final BoolSetting ignoreFriendWater = new BoolSetting(Concat.of("Ignore Friend Water"), true);
    private final IntSetting drainDelay = new IntSetting(Concat.of("Drain Delay"), 3, 0, 20);

    private boolean active;
    private boolean wasAiming;
    private Step step;
    private int stepDelay;
    private int aimTicks;
    private int tickCounter;
    private int oldSlot = -1;
    private BlockPos target;
    private BlockHitResult targetHit;

    private final RotationConvergenceTracker rotation = new RotationConvergenceTracker();

    private final Map<BlockPos, WaterOwner> waterOwners = new HashMap<>();
    private final Map<String, SwingInfo> recentSwings = new HashMap<>();

    private BlockPos lastBucketPlacePos;
    private long lastBucketPlaceTime;

    public Drain() {
        super(new FeatureInfo(
            Concat.of("Drain"),
            Concat.of("Automatically picks up nearby water sources with a bucket, skipping water placed by friends"),
            Category.UTILITY
        ));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        rotation.reset();
        reset();
    }

    @Override
    public void onDisable() {
        reset();
        disengageRotations();
        super.onDisable();
    }

    @EventSubscribe
    private void onBlockUpdate(EventBlockUpdate event) {
        if (isNull() || !ignoreFriendWater.getValue()) return;

        BlockPos pos = event.getPos();
        if (event.getState().getFluidState().is(Fluids.WATER)) {
            if (waterOwners.size() > 256) pruneOwners();
            waterOwners.put(pos, new WaterOwner(null, System.currentTimeMillis()));
        } else {
            waterOwners.remove(pos);
        }
    }

    @EventSubscribe
    private void onStartUseItem(EventStartUseItem.Pre event) {
        if (isNull() || !ignoreFriendWater.getValue()) return;
        if (event.getPlayer() != mc.player) return;

        ItemStack stack = event.getStack();
        if (stack.isEmpty() || !stack.is(Items.WATER_BUCKET)) return;

        BlockPos placePos = computeBucketPlacePos(event.getHitResult());
        if (placePos != null) {
            lastBucketPlacePos = placePos;
            lastBucketPlaceTime = System.currentTimeMillis();
        }
    }

    @EventSubscribe
    private void onPacketReceive(EventPacketReceive event) {
        if (isNull() || !ignoreFriendWater.getValue()) return;
        if (!(event.getPacket() instanceof ClientboundAnimatePacket packet)) return;

        Object action = packet.getAction();
        boolean swingMain;
        if (action instanceof Integer code) {
            swingMain = code == 0;
        } else if (action instanceof Enum<?> animation) {
            swingMain = "SWING_MAIN_HAND".equals(animation.name());
        } else {
            return;
        }
        if (!swingMain) return;

        Entity entity = mc.level.getEntity(packet.getId());
        if (!(entity instanceof Player player) || player == mc.player) return;

        String name = player.getName().getString();
        recentSwings.put(name, new SwingInfo(player.position(), System.currentTimeMillis()));
        if (recentSwings.size() > SWING_MAP_MAX) pruneSwings();

        // The swing packet arrives after the block update for the same
        // placement, so retroactively attribute any nearby unresolved water.
        Vec3 swingPos = player.position();
        double radiusSq = OWNER_SEARCH_RADIUS * OWNER_SEARCH_RADIUS;
        for (Map.Entry<BlockPos, WaterOwner> entry : waterOwners.entrySet()) {
            WaterOwner owner = entry.getValue();
            if (owner.name != null) continue;
            if (Vec3.atCenterOf(entry.getKey()).distanceToSqr(swingPos) > radiusSq) continue;
            owner.name = name;
        }
    }

    @EventSubscribe
    private void onMouseUpdate(MouseUpdateEvent event) {
        if (!active || isNull() || MinecraftCompat.getScreen() != null || step != Step.AIM || target == null) {
            disengageRotations();
            return;
        }

        boolean rotated = RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(silentAim.getValue())
                .speed(aimSpeed.getValue())
                .aimType(RotationUtils.AimType.REGULAR)
                .to(Vec3.atCenterOf(target));

        if (!rotated) {
            disengageRotations();
            return;
        }
        wasAiming = true;
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!wasAiming || !silentAim.getValue()) return;
        if (!RotationUtils.isControlledBy(this) || !RotationUtils.hasSilentRotation()) return;

        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        rotation.update();
        if (isNull() || MinecraftCompat.getScreen() != null) {
            reset();
            disengageRotations();
            return;
        }

        if (++tickCounter % 100 == 0) {
            pruneOwners();
            pruneSwings();
        }

        if (!active) {
            if (stepDelay > 0) {
                stepDelay--;
                return;
            }
            BlockPos found = findTarget();
            if (found == null) return;
            start(found);
            return;
        }

        if (stepDelay > 0) {
            stepDelay--;
            return;
        }

        if (!targetStillValid()) {
            reset();
            return;
        }
        if (isFriendWater(target)) {
            reset();
            return;
        }

        switch (step) {
            case AIM -> aimStep();
            case SELECT -> selectBucketStep();
            case DRAIN -> drainStep();
            case WAIT -> waitStep();
        }
    }

    private void start(BlockPos pos) {
        if (findBucketSlot() == -1) return;
        active = true;
        step = Step.AIM;
        stepDelay = 0;
        aimTicks = 0;
        oldSlot = mc.player.getInventory().getSelectedSlot();
        target = pos;
        targetHit = HitResultUtils.createBlockHit(pos, Direction.UP);
        rotation.reset();
    }

    private void aimStep() {
        aimTicks++;
        if (aimTicks > MAX_AIM_TICKS) {
            reset();
            return;
        }
        if (!readyToInteract()) return;
        if (!aimedAtBlock()) return;
        step = Step.SELECT;
        stepDelay = 0;
    }

    private void selectBucketStep() {
        int slot = findBucketSlot();
        if (slot == -1) {
            reset();
            return;
        }
        InventoryUtils.setInvSlot(slot);
        step = Step.DRAIN;
        stepDelay = 0;
    }

    private void drainStep() {
        if (targetHit == null) {
            reset();
            return;
        }
        BlockUtils.interactWithBlock(targetHit, true);
        rotation.markInteraction();
        step = Step.WAIT;
        stepDelay = 1;
    }

    private void waitStep() {
        if (switchBack.getValue() && oldSlot != -1 && mc.player != null) {
            InventoryUtils.setInvSlot(oldSlot);
        }
        active = false;
        step = Step.AIM;
        stepDelay = drainDelay.getValue();
        aimTicks = 0;
        oldSlot = -1;
        target = null;
        targetHit = null;
        rotation.reset();
    }

    private void reset() {
        if (oldSlot != -1 && mc.player != null) {
            InventoryUtils.setInvSlot(oldSlot);
        }
        active = false;
        step = Step.AIM;
        stepDelay = 0;
        aimTicks = 0;
        oldSlot = -1;
        target = null;
        targetHit = null;
        rotation.reset();
    }

    private void disengageRotations() {
        if (!RotationUtils.isControlledBy(this)) {
            wasAiming = false;
            return;
        }
        RotationUtils.stopTracking();
        wasAiming = false;
    }

    private boolean readyToInteract() {
        return rotation.isDuplicateRotPlaceSafe(ROTATION_SETTLE_THRESHOLD);
    }

    private boolean aimedAtBlock() {
        if (target == null || mc.level == null) return false;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = silentAim.getValue() ? RotationUtils.getSilentYaw() : mc.player.getYRot();
        float pitch = silentAim.getValue() ? RotationUtils.getSilentPitch() : mc.player.getXRot();
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 look = RotationHandler.getRotationVector(pitch, yaw);
        Vec3 end = eye.add(look.scale(mc.player.blockInteractionRange()));
        ClipContext context = new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player);
        HitResult result = mc.level.clip(context);
        return result instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(target);
    }

    private boolean targetStillValid() {
        if (target == null) return false;
        FluidState fluid = mc.level.getFluidState(target);
        if (fluid.isEmpty() || !fluid.is(Fluids.WATER)) return false;
        return !sourceOnly.getValue() || fluid.isSource();
    }

    private BlockPos findTarget() {
        if (mc.player == null || mc.level == null) return null;

        BlockPos playerPos = mc.player.blockPosition();
        int radius = (int) Math.ceil(range.getValue());
        double rangeSq = range.getValue() * range.getValue();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 look = mc.player.getLookAngle();

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    FluidState fluid = mc.level.getFluidState(pos);
                    if (fluid.isEmpty() || !fluid.is(Fluids.WATER)) continue;
                    if (sourceOnly.getValue() && !fluid.isSource()) continue;

                    Vec3 center = Vec3.atCenterOf(pos);
                    double distSq = eye.distanceToSqr(center);
                    if (distSq > rangeSq) continue;
                    if (!isInFov(eye, look, center)) continue;
                    if (isFriendWater(pos)) continue;

                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean isInFov(Vec3 eye, Vec3 look, Vec3 target) {
        float fovDegrees = fov.getValue();
        if (fovDegrees >= 180f) return true;
        Vec3 to = target.subtract(eye).normalize();
        double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1.0, 1.0)));
        return angle <= fovDegrees * 0.5f;
    }

    private boolean isFriendWater(BlockPos pos) {
        if (!ignoreFriendWater.getValue()) return false;
        String owner = getOwner(pos);
        if (owner == null) return false;
        return PlayerUtils.isFriend(owner);
    }

    private String getOwner(BlockPos pos) {
        WaterOwner entry = waterOwners.get(pos);
        if (entry == null) return null;
        if (entry.name == null) {
            entry.name = resolveOwner(pos);
        }
        return entry.name;
    }

    private String resolveOwner(BlockPos pos) {
        if (mc.player != null && isLocalPlacer(pos)) {
            return mc.player.getName().getString();
        }
        String bucket = bucketHolderNear(pos);
        if (bucket != null) return bucket;
        String swinger = recentSwingerNear(pos);
        if (swinger != null) return swinger;
        return nearestPlayerName(pos);
    }

    private boolean isLocalPlacer(BlockPos pos) {
        if (lastBucketPlacePos == null) return false;
        if (System.currentTimeMillis() - lastBucketPlaceTime > BUCKET_PLACE_TTL_MS) return false;
        return pos.equals(lastBucketPlacePos) || pos.closerThan(lastBucketPlacePos, 1.1);
    }

    private BlockPos computeBucketPlacePos(HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHit) || mc.level == null) return null;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return pos;
        return pos.relative(blockHit.getDirection());
    }

    private String bucketHolderNear(BlockPos pos) {
        if (mc.level == null) return null;
        Vec3 center = Vec3.atCenterOf(pos);
        double bestDistSq = OWNER_SEARCH_RADIUS * OWNER_SEARCH_RADIUS;
        String best = null;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (!isBucket(main.getItem()) && !isBucket(off.getItem())) continue;
            double distSq = player.distanceToSqr(center);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player.getName().getString();
            }
        }
        return best;
    }

    private boolean isBucket(Item item) {
        return item == Items.BUCKET || item == Items.WATER_BUCKET;
    }

    private String recentSwingerNear(BlockPos pos) {
        if (mc.level == null) return null;
        Vec3 center = Vec3.atCenterOf(pos);
        long cutoff = System.currentTimeMillis() - SWING_TTL_MS;
        String best = null;
        double bestDistSq = OWNER_SEARCH_RADIUS * OWNER_SEARCH_RADIUS;
        for (Map.Entry<String, SwingInfo> entry : recentSwings.entrySet()) {
            SwingInfo info = entry.getValue();
            if (info.time < cutoff) continue;
            double distSq = info.pos.distanceToSqr(center);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = entry.getKey();
            }
        }
        return best;
    }

    private String nearestPlayerName(BlockPos pos) {
        if (mc.level == null) return null;
        Vec3 center = Vec3.atCenterOf(pos);
        double bestDistSq = OWNER_SEARCH_RADIUS * OWNER_SEARCH_RADIUS;
        String best = null;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            double distSq = player.distanceToSqr(center);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player.getName().getString();
            }
        }
        return best;
    }

    private void pruneOwners() {
        long cutoff = System.currentTimeMillis() - OWNER_TTL_MS;
        waterOwners.entrySet().removeIf(entry -> entry.getValue().time < cutoff);
    }

    private void pruneSwings() {
        long cutoff = System.currentTimeMillis() - SWING_TTL_MS;
        recentSwings.entrySet().removeIf(entry -> entry.getValue().time < cutoff);
    }

    private int findBucketSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.BUCKET) return i;
        }
        return -1;
    }

    private static final class WaterOwner {
        private String name;
        private final long time;

        private WaterOwner(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }

    private static final class SwingInfo {
        private final Vec3 pos;
        private final long time;

        private SwingInfo(Vec3 pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}
