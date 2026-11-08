package hack.echo.client.features.impl.combat;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventHandleInput;
import hack.echo.client.event.impl.EventMove;
import hack.echo.client.event.impl.EventStartUseItem;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.event.impl.MouseUpdateEvent;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.features.settings.impl.KeybindSetting;
import hack.echo.client.handlers.InputHandler;
import hack.echo.client.utils.HitResultUtils;
import hack.echo.client.utils.blocks.BlockUtils;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.rotation.RotationConvergenceTracker;
import hack.echo.client.utils.rotation.RotationUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places a rail and a TNT minecart (both in a single tick), then lights a fire
 * on the block between the player and the cart. The trigger is right-clicking
 * a loaded crossbow: the vanilla shot fires normally and the arrow passes
 * through the fire, becomes flaming, and detonates the cart. Placement and
 * ignition reuse the AutoAnchor interaction pattern: silent rotations +
 * {@link RotationConvergenceTracker} so interactions only fire on a tick where
 * Grim's rotation delta gate is closed.
 */
public class XbowCart extends Feature {

    private static final float ROTATION_SETTLE_THRESHOLD = 1.7f;
    private static final int MAX_CART_WAIT_TICKS = 25;
    private static final int MAX_BOOM_WAIT_TICKS = 60;

    private enum Step { PLACE, WAIT_CART, SAFETY, FIRE, WAIT_BOOM }

    private final KeybindSetting activateKeybind = new KeybindSetting(Concat.of("Activate Key"), -1);
    private final BoolSetting safeMode = new BoolSetting(Concat.of("Safe Mode"), false);
    private final BoolSetting silentAim = new BoolSetting(Concat.of("Silent Aim"), true);
    private final IntSetting aimSpeed = new IntSetting(Concat.of("Aim Speed"), 120, 0, 600);

    private boolean active;
    private boolean keyWasDown;
    private boolean wasAiming;
    private Step step;
    private int noCartTicks;
    private int boomTicks;
    private int oldSlot = -1;
    private boolean railPlaced;
    private boolean cartPlaced;
    private boolean slabPlaced;
    private BlockPos target;
    private BlockPos cartBlock;
    private Entity cartEntity;

    private final RotationConvergenceTracker rotation = new RotationConvergenceTracker();

    public XbowCart() {
        super(new FeatureInfo(
            Concat.of("XbowCart"),
            Concat.of("Places a rail and TNT minecart, lights fire so your arrow ignites the cart"),
            Category.COMBAT
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
    private void onKeyPress(EventHandleInput.Early event) {
        if (isNull() || MinecraftCompat.getScreen() != null) return;
        if (activateKeybind.getKey() == -1) return;

        boolean keyDown = InputHandler.isBindDown(activateKeybind.getKey());
        if (keyDown && !keyWasDown && !active) {
            start();
        }
        keyWasDown = keyDown;
    }

    @EventSubscribe
    private void onStartUseItem(EventStartUseItem.Pre event) {
        if (isNull() || MinecraftCompat.getScreen() != null) return;
        if (active) return;
        if (event.getPlayer() != mc.player) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getStack();
        if (stack.isEmpty() || !stack.is(Items.CROSSBOW)) return;
        if (!CrossbowItem.isCharged(stack)) return;

        // The click fires the crossbow's arrow normally in vanilla. The module
        // uses the click as the trigger: it places rail + cart and lights a
        // fire on a nearby block so the arrow ignites and detonates the cart.
        if (getTarget() == null) return;
        start();
    }

    @EventSubscribe
    private void onMouseUpdate(MouseUpdateEvent event) {
        if (!active || isNull() || MinecraftCompat.getScreen() != null) {
            disengageRotations();
            return;
        }

        Vec3 aimPoint = currentAimPoint();
        if (aimPoint == null) {
            disengageRotations();
            return;
        }

        boolean rotated = RotationUtils.aim(this)
                .priority(EventSubscribe.Priority.HIGH)
                .silent(silentAim.getValue())
                .speed(aimSpeed.getValue())
                .aimType(RotationUtils.AimType.REGULAR)
                .to(aimPoint);

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
            return;
        }
        if (!active) return;

        switch (step) {
            case PLACE -> placeRailAndCart();
            case WAIT_CART -> waitForCart();
            case SAFETY -> placeSlabStep();
            case FIRE -> placeFireStep();
            case WAIT_BOOM -> waitForBoom();
        }
    }

    private void start() {
        if (isNull()) return;

        BlockPos newTarget = getTarget();
        if (newTarget == null) return;

        target = newTarget;
        cartBlock = newTarget;
        active = true;
        step = Step.PLACE;
        oldSlot = mc.player.getInventory().getSelectedSlot();
        railPlaced = mc.level.getBlockState(target).is(BlockTags.RAILS);
        cartPlaced = false;
        slabPlaced = false;
        noCartTicks = 0;
        boomTicks = 0;
        cartEntity = null;
        rotation.reset();
    }

    private void placeRailAndCart() {
        if (!readyToInteract()) return;

        int railSlot = railPlaced ? -1 : findRailSlot();
        int cartSlot = findItemSlot(Items.TNT_MINECART);
        if ((railSlot == -1 && !railPlaced) || cartSlot == -1) {
            reset();
            return;
        }

        if (!railPlaced) {
            BlockPos support = target.below();
            if (!mc.level.getBlockState(support).isFaceSturdy(mc.level, support, Direction.UP)) {
                reset();
                return;
            }
            InventoryUtils.setInvSlot(railSlot);
            InteractionResult railResult = BlockUtils.interactWithBlock(
                    HitResultUtils.createBlockHit(support, Direction.UP), true);
            if (!railResult.consumesAction()) {
                reset();
                return;
            }
            railPlaced = true;
        }

        InventoryUtils.setInvSlot(cartSlot);
        BlockUtils.interactWithBlock(HitResultUtils.createBlockHit(target, Direction.UP), true);
        cartPlaced = true;
        rotation.markInteraction();
        step = Step.WAIT_CART;
        noCartTicks = 0;
    }

    private void waitForCart() {
        cartEntity = findCart();
        if (cartEntity == null) {
            noCartTicks++;
            if (noCartTicks > MAX_CART_WAIT_TICKS) {
                reset();
            }
            return;
        }
        slabPlaced = false;
        step = safeMode.getValue() ? Step.SAFETY : Step.FIRE;
    }

    private void placeSlabStep() {
        if (slabPlaced) {
            step = Step.FIRE;
            return;
        }

        int slot = findSlabSlot();
        if (slot == -1) {
            slabPlaced = true;
            step = Step.FIRE;
            return;
        }

        BlockPos far = getFarBlock();
        if (far == null) {
            slabPlaced = true;
            step = Step.FIRE;
            return;
        }

        BlockUtils.PlacementHit placement = BlockUtils.findPlacementHit(far, true);
        if (placement == null) {
            slabPlaced = true;
            step = Step.FIRE;
            return;
        }

        if (!readyToInteract()) return;

        InventoryUtils.setInvSlot(slot);
        InteractionResult result = BlockUtils.interactWithBlock(placement.hitResult(), true);
        if (result.consumesAction()) {
            rotation.markInteraction();
        }
        slabPlaced = true;
        step = Step.FIRE;
    }

    private void placeFireStep() {
        if (cartEntity == null || cartEntity.isRemoved()) {
            cartEntity = findCart();
            if (cartEntity == null) {
                reset();
                return;
            }
        }
        if (!readyToInteract()) return;

        int slot = findItemSlot(Items.FLINT_AND_STEEL);
        if (slot == -1) {
            reset();
            return;
        }

        BlockPos firePos = getFirePos();
        if (firePos == null || !BlockUtils.isValidPlacement(firePos)) {
            reset();
            return;
        }

        BlockUtils.PlacementHit placement = BlockUtils.findPlacementHit(firePos, true);
        if (placement == null) {
            reset();
            return;
        }

        InventoryUtils.setInvSlot(slot);
        InteractionResult result = BlockUtils.interactWithBlock(placement.hitResult(), true);
        if (result.consumesAction()) {
            rotation.markInteraction();
        }
        beginBoomWait();
    }

    private void beginBoomWait() {
        boomTicks = 0;
        step = Step.WAIT_BOOM;
    }

    private void waitForBoom() {
        boomTicks++;
        if (cartEntity == null || cartEntity.isRemoved()) {
            reset();
            return;
        }
        if (cartEntity instanceof MinecartTNT cart && cart.isPrimed()) {
            reset();
            return;
        }
        if (boomTicks > MAX_BOOM_WAIT_TICKS) {
            reset();
        }
    }

    private void reset() {
        if (oldSlot != -1 && mc.player != null) {
            InventoryUtils.setInvSlot(oldSlot);
        }
        active = false;
        step = Step.WAIT_BOOM;
        oldSlot = -1;
        railPlaced = false;
        cartPlaced = false;
        slabPlaced = false;
        noCartTicks = 0;
        boomTicks = 0;
        target = null;
        cartBlock = null;
        cartEntity = null;
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

    private Vec3 currentAimPoint() {
        return switch (step) {
            case PLACE -> target != null ? Vec3.atCenterOf(target) : null;
            case SAFETY -> slabAimPoint();
            case FIRE -> fireAimPoint();
            case WAIT_CART -> cartCenter();
            case WAIT_BOOM -> null;
        };
    }

    private Vec3 slabAimPoint() {
        if (cartBlock == null) return null;
        BlockPos far = getFarBlock();
        return far != null ? Vec3.atCenterOf(far) : null;
    }

    private Vec3 fireAimPoint() {
        BlockPos firePos = getFirePos();
        if (firePos == null) return null;
        BlockUtils.PlacementHit placement = BlockUtils.findPlacementHit(firePos, true);
        return placement != null ? placement.hitResult().getLocation() : Vec3.atCenterOf(firePos);
    }

    private Vec3 cartCenter() {
        if (cartEntity != null && !cartEntity.isRemoved()) {
            return cartEntity.getBoundingBox().getCenter();
        }
        return cartBlock != null ? Vec3.atCenterOf(cartBlock) : null;
    }

    private BlockPos getTarget() {
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hitResult).getBlockPos().relative(((BlockHitResult) hitResult).getDirection());
    }

    private Entity findCart() {
        if (cartBlock == null) return null;
        AABB box = new AABB(cartBlock).inflate(2.0);
        return mc.level.getEntitiesOfClass(MinecartTNT.class, box, EntitySelector.NO_SPECTATORS)
            .stream()
            .findFirst()
            .orElse(null);
    }

    private BlockPos getFirePos() {
        return getCloserBlock();
    }

    private BlockPos getCloserBlock() {
        Direction dir = getCloserDirection();
        return dir != null ? cartBlock.relative(dir) : null;
    }

    private BlockPos getFarBlock() {
        Direction dir = getCloserDirection();
        return dir != null ? cartBlock.relative(dir.getOpposite()) : null;
    }

    private Direction getCloserDirection() {
        if (cartBlock == null) return null;
        Vec3 center = Vec3.atCenterOf(cartBlock);
        Vec3 player = mc.player.position();
        double dx = player.x - center.x;
        double dz = player.z - center.z;
        if (dx * dx + dz * dz < 1.0E-4) return null;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private int findRailSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && isRail(stack.getItem())) return i;
        }
        return -1;
    }

    private boolean isRail(Item item) {
        return item == Items.RAIL || item == Items.POWERED_RAIL || item == Items.DETECTOR_RAIL
            || item == Items.ACTIVATOR_RAIL;
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findSlabSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock) {
                return i;
            }
        }
        return -1;
    }
}
