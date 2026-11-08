package hack.echo.client.features.impl.macros;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventStartUseItem;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.handlers.InputHandler;
import hack.echo.client.handlers.RotationHandler;
import hack.echo.client.utils.blocks.BlockUtils;
import hack.echo.client.utils.combat.ExplosionUtils;
import hack.echo.client.utils.combat.TargetUtils;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.keyboard.KeyUtils;
import hack.echo.client.utils.rotation.RotationUtils;
import hack.echo.client.utils.strings.Concat;
import hack.echo.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AnchorMacro extends Feature {

    public AnchorMacro() {
        super(new FeatureInfo(
            Concat.of("Anchor Macro"),
            Concat.of("Charge and explode respawn anchors while holding right click"),
            Category.MACROS
        ));
    }

    private final BoolSetting whileUse = new BoolSetting(
        Concat.of("While Use"), true);
    private final BoolSetting stopOnKill = new BoolSetting(
        Concat.of("Stop on Kill"), false);
    private final BoolSetting clickSimulation = new BoolSetting(
        Concat.of("Click Simulation"), false);

    private final IntSetting switchDelay = new IntSetting(
        Concat.of("Switch Delay"), 0, 0, 20,
        Concat.of(" ticks"));
    private final IntSetting switchChance = new IntSetting(
        Concat.of("Switch Chance"), 100, 0, 100,
        Concat.of("%"));
    private final IntSetting placeChance = new IntSetting(
        Concat.of("Place Chance"), 100, 0, 100,
        Concat.of("%"));

    private final IntSetting glowstoneDelay = new IntSetting(
        Concat.of("Glowstone Delay"), 0, 0, 20,
        Concat.of(" ticks"));
    private final IntSetting glowstoneChance = new IntSetting(
        Concat.of("Glowstone Chance"), 100, 0, 100,
        Concat.of("%"));

    private final IntSetting explodeDelay = new IntSetting(
        Concat.of("Explode Delay"), 0, 0, 20,
        Concat.of(" ticks"));
    private final IntSetting explodeChance = new IntSetting(
        Concat.of("Explode Chance"), 100, 0, 100,
        Concat.of("%"));
    private final IntSetting explodeSlot = new IntSetting(
        Concat.of("Explode Slot"), 1, 1, 9,
        Concat.of(""));

    private final BoolSetting onlyOwn = new BoolSetting(
        Concat.of("Only Own"), false);
    private final BoolSetting onlyCharge = new BoolSetting(
        Concat.of("Only Charge"), false);

    private final BoolSetting safeAnchor = new BoolSetting(
        Concat.of("Safe Anchor"), false
    ).describedBy(Concat.of("Place a glowstone in the best spot to block your own explosion damage before detonating"));
    private final BoolSetting silentAim = new BoolSetting(
        Concat.of("Silent Aim"), false, value -> safeAnchor.getValue()
    ).describedBy(Concat.of("Aim at the safety placement server-side without moving your view"));
    private final IntSetting aimSpeed = new IntSetting(
        Concat.of("Aim Speed"), 120, 1, 600, value -> safeAnchor.getValue()
    );
    private final FloatSetting minDamage = new FloatSetting(
        Concat.of("Min Damage"), 6.0f, 0.0f, 20.0f, 0.5f,
        value -> safeAnchor.getValue()
    ).describedBy(Concat.of("Skip the safety block if the anchor wouldn't deal at least this much damage to you"));

    private static final Random RANDOM = new Random();
    private static final int RMB = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    private static final long SAFETY_TIMEOUT_MILLIS = 2000L;

    private int switchClock = 0;
    private int glowstoneClock = 0;
    private int explodeClock = 0;

    // hashset cuz in a hashset stuff cant repeat
    private final Set<BlockPos> ownedAnchors = new HashSet<>();

    // safe-anchor state for the anchor currently being detonated
    private BlockPos safetyAnchorPos;
    private BlockPos safetyPlacePos;
    private BlockUtils.PlacementHit safetyPlacementHit;
    private boolean safetyPlaced;
    private long safetyStartMillis;

    @Override
    public void onEnable() {
        resetClocks();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetClocks();
        if (RotationUtils.isControlledBy(this)) {
            RotationUtils.stopTracking();
        }
        super.onDisable();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;

        // Release the silent rotation whenever we own it but won't be aiming this tick
        // (RMB released, safe anchor finished, or anchor no longer targeted).
        boolean stillPlacing = isRmbDown() && safeAnchor.getValue() && !safetyPlaced;
        if (RotationUtils.isControlledBy(this) && !stillPlacing) {
            disengageRotations();
        }

        if (hack.echo.client.api.MinecraftCompat.getScreen() != null) return;

        int randomInt = randomInt();

        if (!isRmbDown()) return;

        if (whileUse.getValue() && isHoldingFoodOrShield() && isRmbDown()) return;
        if (stopOnKill.getValue() && WorldUtils.isDeadBodyNearby()) return;

        BlockHitResult hit = resolveAnchorHit();
        if (hit == null) return;
        if (!BlockUtils.isBlockAtPosition(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) return;

        if (onlyOwn.getValue() && !ownedAnchors.contains(hit.getBlockPos())) return;

        mc.options.keyUse.setDown(false);

        if (BlockUtils.isRespawnAnchorUncharged(hit.getBlockPos())) {
            chargeAnchor(hit, randomInt);
        } else if (BlockUtils.isRespawnAnchorCharged(hit.getBlockPos())) {
            explodeAnchor(hit, randomInt);
        }
    }

    private void chargeAnchor(BlockHitResult hit, int randomInt) {
        if (randomInt > placeChance.getValue()) return;

        if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            if (switchClock != switchDelay.getValue()) {
                switchClock++;
                return;
            }
            if (randomInt() <= switchChance.getValue()) {
                switchClock = 0;
                InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE);
            }
        }

        if (mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            if (glowstoneClock != glowstoneDelay.getValue()) {
                glowstoneClock++;
                return;
            }
            if (randomInt() <= glowstoneChance.getValue()) {
                glowstoneClock = 0;
                if (clickSimulation.getValue()) {
                    InputHandler.simulateMouseClick(RMB);
                }
                WorldUtils.placeBlock(hit, true);
            }
        }
    }

    private void explodeAnchor(BlockHitResult hit, int randomInt) {
        if (!onlyCharge.getValue() && safeAnchor.getValue()) {
            if (safetyAnchorPos == null || !safetyAnchorPos.equals(hit.getBlockPos())) {
                safetyAnchorPos = hit.getBlockPos();
                safetyPlacePos = null;
                safetyPlacementHit = null;
                safetyPlaced = false;
                safetyStartMillis = System.currentTimeMillis();
            }

            if (!safetyPlaced) {
                placeSafetyBlock(randomInt);
                return;
            }

            // The safety glowstone can occlude the crosshair; re-target the anchor.
            if (BlockUtils.isBlockAtPosition(hit.getBlockPos(), Blocks.GLOWSTONE)) {
                BlockHitResult anchorHit = findAnchorHit(safetyAnchorPos);
                if (anchorHit != null) hit = anchorHit;
            }
        }

        int slot = explodeSlot.getValue() - 1;

        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (switchClock != switchDelay.getValue()) {
                switchClock++;
                return;
            }
            if (randomInt <= switchChance.getValue()) {
                switchClock = 0;
                mc.player.getInventory().setSelectedSlot(slot);
            }
        }

        if (mc.player.getInventory().getSelectedSlot() == slot) {
            if (explodeClock != explodeDelay.getValue()) {
                explodeClock++;
                return;
            }
            if (randomInt() <= explodeChance.getValue()) {
                explodeClock = 0;
                if (onlyCharge.getValue()) return;
                if (clickSimulation.getValue()) {
                    InputHandler.simulateMouseClick(RMB);
                }
                WorldUtils.placeBlock(hit, true);
                ownedAnchors.remove(hit.getBlockPos());
            }
        }
    }

    // -- Safe anchor --

    private void placeSafetyBlock(int randomInt) {
        if (safetyPlacePos == null) {
            safetyPlacePos = findBestSafetyPos(safetyAnchorPos);
            if (safetyPlacePos == null) {
                safetyPlaced = true;
                return;
            }
            safetyPlacementHit = BlockUtils.findPlacementHit(safetyPlacePos, false, safetyAnchorPos);
            if (safetyPlacementHit == null) {
                safetyPlaced = true;
                return;
            }
        }

        if (System.currentTimeMillis() - safetyStartMillis > SAFETY_TIMEOUT_MILLIS) {
            safetyPlaced = true;
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            if (switchClock != switchDelay.getValue()) {
                switchClock++;
                return;
            }
            if (randomInt <= switchChance.getValue()) {
                switchClock = 0;
                InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE);
            }
            return;
        }

        if (glowstoneClock != glowstoneDelay.getValue()) {
            glowstoneClock++;
            return;
        }

        if (silentAim.getValue() && !isAimedAtSafetyPlacement()) {
            RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(true)
                .speed(aimSpeed.getValue())
                .to(safetyPlacementHit.hitResult().getLocation());
            return;
        }

        if (randomInt() <= glowstoneChance.getValue()) {
            glowstoneClock = 0;
            if (clickSimulation.getValue()) {
                InputHandler.simulateMouseClick(RMB);
            }
            WorldUtils.placeBlock(safetyPlacementHit.hitResult(), true);
        }
        safetyPlaced = true;
    }

    /** Re-derives the anchor hit when the crosshair is occluded by our own glowstone. */
    private BlockHitResult resolveAnchorHit() {
        if (!(mc.hitResult instanceof BlockHitResult clientHit)) return null;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return null;

        if (!safeAnchor.getValue()) return clientHit;

        if (BlockUtils.isBlockAtPosition(clientHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
            return clientHit;
        }

        if (safetyAnchorPos != null
                && BlockUtils.isBlockAtPosition(safetyAnchorPos, Blocks.RESPAWN_ANCHOR)) {
            BlockHitResult anchorHit = findAnchorHit(safetyAnchorPos);
            if (anchorHit != null) return anchorHit;
        }
        return null;
    }

    /** Synthetic hit on a still-visible anchor face (top edges survive a side glowstone). */
    private BlockHitResult findAnchorHit(BlockPos anchorPos) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 center = Vec3.atCenterOf(anchorPos);

        Vec3[] points = {
            center.add(0.0, 0.5, 0.0),
            center.add(0.49, 0.5, 0.0),
            center.add(-0.49, 0.5, 0.0),
            center.add(0.0, 0.5, 0.49),
            center.add(0.0, 0.5, -0.49),
            center.add(0.49, 0.5, 0.49)
        };

        for (Vec3 point : points) {
            HitResult r = mc.level.clip(new ClipContext(
                eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player
            ));
            if (r.getType() == HitResult.Type.MISS || r.getLocation().distanceToSqr(point) < 1.0e-6) {
                return new BlockHitResult(point, Direction.UP, anchorPos, false);
            }
        }
        return null;
    }

    /** Best glowstone spot: blocks the most rays aimed at us and the least at the target. */
    private BlockPos findBestSafetyPos(BlockPos anchorPos) {
        if (mc.player == null || mc.level == null) return null;

        float selfDamage = ExplosionUtils.getAnchorDamageTo(mc.player, anchorPos);
        if (minDamage.getValue() > 0.0f && selfDamage < minDamage.getValue()) return null;

        Vec3 anchorCenter = Vec3.atCenterOf(anchorPos);
        AABB selfBox = mc.player.getBoundingBox();

        LivingEntity target = TargetUtils.findClosestResolvedTarget(mc.player.getEyePosition(), 1.0f);
        AABB targetBox = null;
        if (target != null) {
            Vec3 offset = target.getPosition(1.0f).subtract(target.position());
            targetBox = target.getBoundingBox().move(offset);
        }

        BlockPos best = null;
        double bestScore = 0.0;
        for (BlockPos candidate : safetyCandidates(anchorPos)) {
            AABB blockBox = new AABB(candidate);
            double selfBlocked = blockedFraction(anchorCenter, selfBox, blockBox);
            if (selfBlocked < 0.05) continue;
            if (!isValidSafetyPos(candidate)) continue;

            double targetBlocked = targetBox != null
                ? blockedFraction(anchorCenter, targetBox, blockBox) : 0.0;

            // Prefer positions that block our own rays and spare the target's.
            double score = selfBlocked - targetBlocked * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private List<BlockPos> safetyCandidates(BlockPos anchorPos) {
        List<BlockPos> candidates = new ArrayList<>();
        if (mc.player == null) return candidates;

        // Anchor-adjacent spots (in front / beside / above the anchor).
        for (Direction dir : Direction.values()) {
            candidates.add(anchorPos.relative(dir));
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                candidates.add(anchorPos.offset(dx, 0, dz));
            }
        }

        // Player feet ring on the half-plane toward the anchor.
        BlockPos feet = BlockPos.containing(mc.player.getPosition(1.0f));
        Vec3 feetCenter = Vec3.atCenterOf(feet);
        Vec3 toAnchor = Vec3.atCenterOf(anchorPos).subtract(feetCenter);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = feet.offset(dx, 0, dz);
                Vec3 toCand = Vec3.atCenterOf(pos).subtract(feetCenter);
                if (toCand.x * toAnchor.x + toCand.z * toAnchor.z <= 0) continue;
                candidates.add(pos);
            }
        }
        return candidates;
    }

    private boolean isValidSafetyPos(BlockPos pos) {
        if (pos == null || pos.equals(safetyAnchorPos)) return false;
        if (!BlockUtils.isValidPlacement(pos)) return false;
        if (BlockUtils.collidesWithPlayer(pos)) return false;
        if (BlockUtils.collidesWithBlockingEntity(pos)) return false;
        if (!BlockUtils.hasSupportingFace(pos)) return false;

        // Never support the glowstone on the anchor itself: right-clicking the
        // anchor with glowstone charges it instead of placing a block.
        BlockUtils.PlacementHit hit = BlockUtils.findPlacementHit(pos, false, safetyAnchorPos);
        if (hit == null) return false;

        Vec3 eye = mc.player.getEyePosition(1.0f);
        double range = mc.player.blockInteractionRange();
        return eye.distanceToSqr(hit.hitResult().getLocation()) <= range * range;
    }

    /** Fraction of vanilla explosion rays (anchor center to target box) that pass through {@code blockBox}. */
    private static double blockedFraction(Vec3 explosionCenter, AABB targetBox, AABB blockBox) {
        double xStep = 1.0 / (targetBox.getXsize() * 2.0 + 1.0);
        double yStep = 1.0 / (targetBox.getYsize() * 2.0 + 1.0);
        double zStep = 1.0 / (targetBox.getZsize() * 2.0 + 1.0);
        if (xStep <= 0.0 || yStep <= 0.0 || zStep <= 0.0) return 0.0;

        AABB rayBox = blockBox.inflate(1.0E-4);
        int blocked = 0;
        int total = 0;
        for (double x = 0.0; x <= 1.0; x += xStep) {
            for (double y = 0.0; y <= 1.0; y += yStep) {
                for (double z = 0.0; z <= 1.0; z += zStep) {
                    Vec3 sample = new Vec3(
                        Mth.lerp(x, targetBox.minX, targetBox.maxX),
                        Mth.lerp(y, targetBox.minY, targetBox.maxY),
                        Mth.lerp(z, targetBox.minZ, targetBox.maxZ)
                    );
                    if (rayBox.clip(sample, explosionCenter).isPresent()) blocked++;
                    total++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) blocked / total;
    }

    private boolean isAimedAtSafetyPlacement() {
        if (mc.player == null || safetyPlacementHit == null) return true;
        float[] target = RotationUtils.calculate(
            mc.player.getEyePosition(1.0f),
            safetyPlacementHit.hitResult().getLocation()
        );
        float yaw = RotationHandler.getServerYaw();
        float pitch = RotationHandler.getServerPitch();
        return Math.abs(Mth.wrapDegrees(yaw - target[0])) < 0.5f
            && Math.abs(pitch - target[1]) < 0.5f;
    }

    /** Returns the silent rotation to the camera so the crosshair/aim isn't left stuck
     *  at the safety placement after the anchor cycle ends. */
    private void disengageRotations() {
        if (!RotationUtils.isControlledBy(this)) return;
        if (RotationHandler.isHasSilentRotation()) {
            if (RotationUtils.cleanup(this, true)) return;
        } else {
            RotationUtils.stopTracking();
        }
    }

    @EventSubscribe
    private void onUseItem(EventStartUseItem.Pre event) {
        if (isNull()) return;
        if (!event.getStack().is(Items.RESPAWN_ANCHOR)) return;
        if (!event.isTargetingBlock()) return;

        BlockHitResult hit = event.getBlockHitResult();
        BlockPos pos = hit.getBlockPos();
        if (!mc.level.getBlockState(pos).canBeReplaced()) {
            pos = pos.relative(hit.getDirection());
        }
        ownedAnchors.add(pos);

        if (BlockUtils.isRespawnAnchorCharged(hit.getBlockPos())) {
            ownedAnchors.remove(pos);
        }
    }

    private boolean isHoldingFoodOrShield() {
        boolean isFood = InventoryUtils.isFood(mc.player.getMainHandItem())
                || InventoryUtils.isFood(mc.player.getOffhandItem());
        boolean isShield = mc.player.getMainHandItem().getItem() instanceof ShieldItem
                || mc.player.getOffhandItem().getItem() instanceof ShieldItem;
        return isFood || isShield;
    }

    private boolean isRmbDown() {
        return KeyUtils.isKeyPressed(RMB);
    }

    private int randomInt() {
        return RANDOM.nextInt(100) + 1;
    }

    private void resetClocks() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        safetyAnchorPos = null;
        safetyPlacePos = null;
        safetyPlacementHit = null;
        safetyPlaced = false;
    }
}
