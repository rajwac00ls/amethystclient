package hack.echo.client.features.impl.movement;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventMovementInput;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.features.settings.impl.RangeSetting;
import hack.echo.client.handlers.RotationHandler;
import hack.echo.client.utils.blocks.BlockUtils;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.rotation.RotationUtils;
import hack.echo.client.utils.strings.Concat;
import hack.echo.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class Scaffold extends Feature {

    public Scaffold() {
        super(new FeatureInfo(
                Concat.of("Scaffold"),
                Concat.of("Automatically places blocks while bridging"),
                Category.DETECTED
        ));
    }

    private final ModeSetting mode = new ModeSetting(
            Concat.of("Mode"),
            Concat.of("Ninja"),
            Concat.of("Ninja"),
            Concat.of("Breezily")
    );
    private final RangeSetting crouchDelay = new RangeSetting(Concat.of("Crouch Delay"), 45f, 55f, 0f, 500f, 1f,
            o -> mode.is(Concat.of("Ninja")));
    private final IntSetting breezilyStrafeTicks = new IntSetting(Concat.of("Breezily Strafe Ticks"), 3, 2, 4,
            o -> mode.is(Concat.of("Breezily")));
    private final RangeSetting autoclickCps = new RangeSetting(Concat.of("Autoclick CPS"), 4f, 6f, 1f, 6f, 1f);
    private final BoolSetting disableOnDeath = new BoolSetting(Concat.of("Disable on Death"), true);
    private final BoolSetting disableOnWorldChange = new BoolSetting(Concat.of("Disable on World Change"), true);
    private final BoolSetting fullBlocks = new BoolSetting(Concat.of("Full Blocks Only"), true);
    private final BoolSetting filterFalling = new BoolSetting(Concat.of("Filter Falling"), true);
    private final BoolSetting swing = new BoolSetting(Concat.of("Swing"), true);

    private boolean isCrouching;
    private int crouchWaitTicks;
    private int breezilyStrafeTick;
    private float placeTimer;
    private int targetCps;
    private final Random random = new Random();

    @EventSubscribe
    private void onMoveInput(EventMovementInput e) {
        if (isNull()) return;
        if (disableOnDeath.getValue() && mc.player.isDeadOrDying()) {
            setEnabled(false);
            return;
        }

        e.sprint = false;

        boolean w = e.originalForward;
        boolean s = e.originalBack;
        boolean a = e.originalLeft;
        boolean d = e.originalRight;
        boolean movingHoriz = w || s || a || d;

        if (mode.is(Concat.of("Ninja"))) {
            handleNinja(e, w, s, a, d, movingHoriz);
        } else {
            handleBreezily(e, movingHoriz);
        }
    }

    private void handleNinja(EventMovementInput e, boolean w, boolean s, boolean a, boolean d, boolean movingHoriz) {
        boolean pressRight;
        boolean pressLeft;

        if (movingHoriz) {
            if (w && !s) {
                if (d) {
                    pressRight = false;
                    pressLeft = true;
                } else {
                    pressRight = true;
                    pressLeft = false;
                }
                e.forward = false;
                e.back = true;
                e.right = pressRight;
                e.left = pressLeft;
            } else {
                e.forward = false;
                e.back = true;
                e.left = false;
                e.right = false;
            }
        } else {
            e.forward = false;
            e.back = false;
            e.left = false;
            e.right = false;
        }

        if (mc.player.onGround()) {
            boolean nearEdge = isNearEdge();

            if (nearEdge && !isCrouching) {
                isCrouching = true;
                int lo = (int) crouchDelay.getMinValue();
                int hi = (int) crouchDelay.getMaxValue();
                int delayMs = hi > lo ? lo + random.nextInt(hi - lo + 1) : lo;
                crouchWaitTicks = Math.max(1, delayMs / 50);
            }

            if (isCrouching) {
                e.sneak = true;
                if (!nearEdge) crouchWaitTicks--;
                if (crouchWaitTicks <= 0 && !nearEdge) isCrouching = false;
            } else {
                e.sneak = e.originalSneak;
            }
        } else {
            isCrouching = false;
            crouchWaitTicks = 0;
            e.sneak = true;
        }
    }

    private void handleBreezily(EventMovementInput e, boolean movingHoriz) {
        isCrouching = false;
        crouchWaitTicks = 0;

        if (movingHoriz) {
            breezilyStrafeTick++;
            boolean strafeRight = (breezilyStrafeTick / breezilyStrafeTicks.getValue()) % 2 == 0;
            e.forward = false;
            e.back = true;
            e.right = strafeRight;
            e.left = !strafeRight;
        } else {
            breezilyStrafeTick = 0;
            e.forward = false;
            e.back = false;
            e.left = false;
            e.right = false;
        }

        e.sneak = e.originalSneak;
    }

    private boolean isNearEdge() {
        double px = mc.player.getX();
        int by = (int) Math.floor(mc.player.getY() - 1.0);
        double pz = mc.player.getZ();
        double margin = 0.3;

        double[][] offsets = {
                {0.0, 0.0},
                {-margin, 0.0},
                {margin, 0.0},
                {0.0, -margin},
                {0.0, margin}
        };

        for (double[] offset : offsets) {
            int bx = (int) Math.floor(px + offset[0]);
            int bz = (int) Math.floor(pz + offset[1]);
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()
                    || !state.getFluidState().isEmpty()
                    || state.getCollisionShape(mc.level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        if (disableOnDeath.getValue() && mc.player.isDeadOrDying()) {
            setEnabled(false);
            return;
        }
        if (disableOnWorldChange.getValue() && mc.level != savedLevel) {
            setEnabled(false);
            return;
        }

        ItemStack main = mc.player.getMainHandItem();
        if (main.isEmpty() || !(main.getItem() instanceof BlockItem) || !isValidBlockStack(main)) {
            int slot = findBlockSlot();
            if (slot == -1) return;
            selectSlot(slot);
        }

        BlockPos target = getTargetPos();
        if (target == null) return;

        if (!BlockUtils.isValidPlacement(target)) return;
        if (BlockUtils.collidesWithBlockingEntity(target)) return;

        BlockUtils.PlacementHit placement = BlockUtils.findPlacementHit(target, true);
        if (placement == null) return;

        BlockHitResult hit = placement.hitResult();
        double range = mc.player.blockInteractionRange();
        if (mc.player.getEyePosition().distanceToSqr(hit.getLocation()) > range * range) return;

        RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(true)
                .speed(180f)
                .to(hit.getLocation());

        if (!updatePlaceTimer()) return;

        WorldUtils.placeBlock(hit, swing.getValue());
    }

    private boolean updatePlaceTimer() {
        if (targetCps == 0) {
            targetCps = pickCps();
        }
        placeTimer += targetCps / 20.0f;
        if (placeTimer < 1.0f) {
            return false;
        }
        placeTimer -= 1.0f;
        targetCps = pickCps();
        return true;
    }

    private int pickCps() {
        int lo = (int) autoclickCps.getMinValue();
        int hi = (int) autoclickCps.getMaxValue();
        return hi > lo ? lo + random.nextInt(hi - lo + 1) : lo;
    }

    private BlockPos getTargetPos() {
        Vec3 vec = mc.player.position().add(mc.player.getDeltaMovement()).add(0, -0.75, 0);
        BlockPos target = BlockPos.containing(vec.x, vec.y, vec.z);
        if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
            target = target.below();
        }
        if (target.getY() >= mc.player.blockPosition().getY()) {
            target = mc.player.blockPosition().below();
        }
        return target;
    }

    private boolean isValidBlockStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        BlockState state = block.defaultBlockState();
        if (state.isAir()) return false;
        if (fullBlocks.getValue() && state.getCollisionShape(mc.level, BlockPos.ZERO).isEmpty()) return false;
        if (filterFalling.getValue() && block instanceof FallingBlock
                && FallingBlock.isFree(mc.level.getBlockState(mc.player.blockPosition().below()))) return false;
        return true;
    }

    private int findBlockSlot() {
        return InventoryUtils.findItemWithPredicateInHotbar(this::isValidBlockStack);
    }

    private void selectSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private ClientLevel savedLevel;

    @Override
    public void onEnable() {
        savedLevel = mc.level;
        placeTimer = 0f;
        targetCps = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        isCrouching = false;
        crouchWaitTicks = 0;
        breezilyStrafeTick = 0;
        RotationUtils.releaseControl(this);
        super.onDisable();
    }
}
