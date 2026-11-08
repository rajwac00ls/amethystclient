package hack.echo.client.features.impl.combat;

import hack.echo.client.api.InventoryClickCompat;
import hack.echo.client.api.InventoryClickType;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.mixin.accessors.AbstractContainerScreenAccessor;
import hack.echo.client.mixin.accessors.MinecraftAccessor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import hack.echo.client.utils.math.TimerUtils;
import hack.echo.client.utils.strings.Concat;

// Dear Cyde, my dearest apologies for the mess I've made.
// I feel bad for you needing to read this
// I promise to never do this again
// I love you, give tongue soon
// - graph
public class AutoCart extends Feature {

    private boolean active;
    private int step;
    private int oldSlot;
    private boolean railPlaced;
    private boolean tntPlaced;
    private BlockPos target;
    private final TimerUtils timer = new TimerUtils();

    // Hover Cart settings
    private final BoolSetting hoverAutoOpen = new BoolSetting(
        Concat.of("Hover Auto Open"), false);
    private final BoolSetting hoverMoveToHotbar = new BoolSetting(
        Concat.of("Hover Move to Hotbar"), false);
    private final IntSetting hoverHotbarSlot = new IntSetting(
        Concat.of("Hover Hotbar Slot"), 9, 1, 9,
        Concat.of(""));

    public AutoCart() {
        super(new FeatureInfo(
            Concat.of("AutoCart"),
            Concat.of("Places rails and TNT minecarts while aiming"),
            Category.COMBAT
        ));
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull())
            return;

        handleHoverCart();

        if (!active && mc.player.isUsingItem() && mc.player.getUseItem().getItem() == Items.BOW) {

            if (mc.level != null) {
                var registry = mc.level.registryAccess();
                var flameEntry = registry.get(Enchantments.FLAME);
                if (flameEntry.isPresent()
                        && EnchantmentHelper.getItemEnchantmentLevel(flameEntry.get(), mc.player.getUseItem()) == 0)
                    return;
            }

            target = getTarget();
            if (target == null)
                return;

            if (!timer.hasReached(1000))
                return;

            active = true;
            step = 0;
            oldSlot = mc.player.getInventory().getSelectedSlot();
            railPlaced = false;
            tntPlaced = false;
        }

        if (!active)
            return;

        switch (step) {
            case 0 -> placeRail();
            case 1 -> placeTnt();
            case 2 -> reset();
        }

        step++;
    }

    private void handleHoverCart() {
        if (hoverAutoOpen.getValue()) {
            handleHoverAutoOpen();
        }

        var screen = hack.echo.client.api.MinecraftCompat.getScreen();
        if (screen instanceof InventoryScreen inventoryScreen) {
            handleHover(inventoryScreen.getMenu(), inventoryScreen);
        } else if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            handleHoverCreative(creativeScreen.getMenu(), creativeScreen);
        }
    }

    private void handleHoverAutoOpen() {
        if (hack.echo.client.api.MinecraftCompat.getScreen() instanceof InventoryScreen
                || hack.echo.client.api.MinecraftCompat.getScreen() instanceof CreativeModeInventoryScreen) return;

        int hbIdx = Math.max(0, Math.min(8, hoverHotbarSlot.getValue() - 1));
        if (mc.player.getInventory().getItem(hbIdx).is(Items.TNT_MINECART)) return;

        int containerSize = mc.player.getInventory().getContainerSize();
        for (int i = 1; i < containerSize; i++) {
            if (i == hbIdx) continue;
            if (mc.player.getInventory().getItem(i).is(Items.TNT_MINECART)) {
                hack.echo.client.api.MinecraftCompat.setScreen(new InventoryScreen(mc.player));
                return;
            }
        }
    }

    private void handleHover(AbstractContainerMenu menu, InventoryScreen screen) {
        if (!hoverMoveToHotbar.getValue()) return;
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        Slot focusedSlot = accessor.getHoveredSlot();
        if (focusedSlot == null) return;
        if (!focusedSlot.getItem().is(Items.TNT_MINECART)) return;

        int slotIndex = getSlotIndex(menu, focusedSlot);
        if (slotIndex < 0) return;

        int containerSlot = focusedSlot.container instanceof Inventory
            ? focusedSlot.getContainerSlot() : -1;
        int hbIdx = Math.max(0, Math.min(8, hoverHotbarSlot.getValue() - 1));
        if (containerSlot == hbIdx) return;

        if (mc.player.getInventory().getItem(hbIdx).is(Items.TNT_MINECART)) return;
        if (mc.gameMode == null) return;

        InventoryClickCompat.handleClick(
            menu.containerId, slotIndex, hbIdx,
            InventoryClickType.SWAP, mc.player);
    }

    private void handleHoverCreative(AbstractContainerMenu menu, CreativeModeInventoryScreen screen) {
        if (!hoverMoveToHotbar.getValue()) return;
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        Slot focusedSlot = accessor.getHoveredSlot();
        if (focusedSlot == null) return;
        if (!focusedSlot.getItem().is(Items.TNT_MINECART)) return;

        int slotIndex = getSlotIndex(menu, focusedSlot);
        if (slotIndex < 0) return;

        int containerSlot = focusedSlot.container instanceof Inventory
            ? focusedSlot.getContainerSlot() : -1;
        int hbIdx = Math.max(0, Math.min(8, hoverHotbarSlot.getValue() - 1));
        if (containerSlot == hbIdx) return;

        if (mc.player.getInventory().getItem(hbIdx).is(Items.TNT_MINECART)) return;
        if (mc.gameMode == null) return;

        InventoryClickCompat.handleClick(
            menu.containerId, slotIndex, hbIdx,
            InventoryClickType.SWAP, mc.player);
    }

    private static int getSlotIndex(AbstractContainerMenu menu, Slot slot) {
        return menu.slots.indexOf(slot);
    }

    private void placeRail() {
        if (railPlaced)
            return;
        int slot = findRailSlot();
        if (slot == -1)
            return;
        mc.player.getInventory().setSelectedSlot(slot);
        ((MinecraftAccessor) mc).invokeStartUseItem();
        railPlaced = true;
    }

    private void placeTnt() {
        if (tntPlaced)
            return;
        int slot = findItemSlot(Items.TNT_MINECART);
        if (slot == -1)
            return;
        mc.player.getInventory().setSelectedSlot(slot);
        ((MinecraftAccessor) mc).invokeStartUseItem();
        tntPlaced = true;
    }

    private void reset() {
        if (oldSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(oldSlot);
        active = false;
        step = 0;
        target = null;
        railPlaced = false;
        tntPlaced = false;
        timer.reset();
    }

    private BlockPos getTarget() {
        HitResult hr = mc.hitResult;
        if (hr == null)
            return null;

        if (hr.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hr).getBlockPos().relative(((BlockHitResult) hr).getDirection());
        }

        return null;
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item)
                return i;
        }
        return -1;
    }

    private int findRailSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && isRail(stack.getItem()))
                return i;
        }
        return -1;
    }

    private boolean isRail(Item item) {
        return item == Items.RAIL || item == Items.POWERED_RAIL || item == Items.DETECTOR_RAIL
                || item == Items.ACTIVATOR_RAIL;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (active)
            reset();
    }
}
