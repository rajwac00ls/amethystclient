package hack.echo.client.features.impl.utility;

import hack.echo.client.api.InventoryClickCompat;
import hack.echo.client.api.InventoryClickType;
import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventSetScreen;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.mixin.accessors.AbstractContainerScreenAccessor;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public class HoverTotem extends Feature {

    public HoverTotem() {
        super(new FeatureInfo(
            Concat.of("Hover Totem"),
            Concat.of("Swap totems to offhand by hovering over them"),
            Category.UTILITY
        ));
    }

    private final BoolSetting autoOpen = new BoolSetting(
        Concat.of("Auto Open"), false);
    private final BoolSetting autoOffhand = new BoolSetting(
        Concat.of("Auto Offhand"), true);
    private final BoolSetting moveToHotbar = new BoolSetting(
        Concat.of("Move to Hotbar"), false);
    private final IntSetting hotbarSlot = new IntSetting(
        Concat.of("Hotbar Slot"), 9, 1, 9,
        Concat.of(""));
    private final BoolSetting doubleHand = new BoolSetting(
        Concat.of("Double Hand"), false);

    @EventSubscribe
    private void onTick(EventTick e) {
        if (isNull()) return;

        if (autoOpen.getValue()) {
            handleAutoOpen();
        }

        var screen = hack.echo.client.api.MinecraftCompat.getScreen();
        if (screen instanceof InventoryScreen inventoryScreen) {
            handleHover(inventoryScreen.getMenu(), inventoryScreen);
        } else if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            handleHoverCreative(creativeScreen.getMenu(), creativeScreen);
        }
    }

    @EventSubscribe
    private void onScreenOpen(EventSetScreen e) {
        if (isNull()) return;
        if (!doubleHand.getValue()) return;
        if (!(e.getScreen() instanceof InventoryScreen
                || e.getScreen() instanceof CreativeModeInventoryScreen)) return;

        int hbIdx = Math.max(0, Math.min(8, hotbarSlot.getValue() - 1));
        mc.player.getInventory().setSelectedSlot(hbIdx);
    }

    private void handleAutoOpen() {
        if (hack.echo.client.api.MinecraftCompat.getScreen() instanceof InventoryScreen
                || hack.echo.client.api.MinecraftCompat.getScreen() instanceof CreativeModeInventoryScreen) return;

        int hbIdx = Math.max(0, Math.min(8, hotbarSlot.getValue() - 1));
        boolean offhandHas = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        boolean hotbarHas = mc.player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING);

        if (offhandHas && (!moveToHotbar.getValue() || hotbarHas)) return;

        boolean hasTotemInInventory = false;
        int containerSize = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < containerSize; i++) {
            if (i == 40 || i == hbIdx) continue;
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                hasTotemInInventory = true;
                break;
            }
        }

        if (hasTotemInInventory) {
            hack.echo.client.api.MinecraftCompat.setScreen(new InventoryScreen(mc.player));
        }
    }

    private void handleHover(AbstractContainerMenu menu, InventoryScreen screen) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        Slot focusedSlot = accessor.getHoveredSlot();
        if (focusedSlot == null) return;
        if (!focusedSlot.getItem().is(Items.TOTEM_OF_UNDYING)) return;

        int slotIndex = getSlotIndex(menu, focusedSlot);
        if (slotIndex < 0) return;

        int containerSlot = focusedSlot.container instanceof Inventory
            ? focusedSlot.getContainerSlot() : -1;
        if (containerSlot == 40) return;

        int hbIdx = Math.max(0, Math.min(8, hotbarSlot.getValue() - 1));
        if (containerSlot == hbIdx && moveToHotbar.getValue()) return;

        boolean needOffhand = autoOffhand.getValue()
            && !mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        boolean needHotbar = moveToHotbar.getValue()
            && !mc.player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING);

        if (!needOffhand && !needHotbar) return;
        if (mc.gameMode == null) return;

        if (needOffhand) {
            InventoryClickCompat.handleClick(
                menu.containerId, slotIndex, 40,
                InventoryClickType.SWAP, mc.player);
        } else if (needHotbar) {
            InventoryClickCompat.handleClick(
                menu.containerId, slotIndex, hbIdx,
                InventoryClickType.SWAP, mc.player);
        }
    }

    private void handleHoverCreative(AbstractContainerMenu menu, CreativeModeInventoryScreen screen) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        Slot focusedSlot = accessor.getHoveredSlot();
        if (focusedSlot == null) return;
        if (!focusedSlot.getItem().is(Items.TOTEM_OF_UNDYING)) return;

        int slotIndex = getSlotIndex(menu, focusedSlot);
        if (slotIndex < 0) return;

        int containerSlot = focusedSlot.container instanceof Inventory
            ? focusedSlot.getContainerSlot() : -1;
        if (containerSlot == 40) return;

        int hbIdx = Math.max(0, Math.min(8, hotbarSlot.getValue() - 1));
        if (containerSlot == hbIdx && moveToHotbar.getValue()) return;

        boolean needOffhand = autoOffhand.getValue()
            && !mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        boolean needHotbar = moveToHotbar.getValue()
            && !mc.player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING);

        if (!needOffhand && !needHotbar) return;
        if (mc.gameMode == null) return;

        if (needOffhand) {
            InventoryClickCompat.handleClick(
                menu.containerId, slotIndex, 40,
                InventoryClickType.SWAP, mc.player);
        } else if (needHotbar) {
            InventoryClickCompat.handleClick(
                menu.containerId, slotIndex, hbIdx,
                InventoryClickType.SWAP, mc.player);
        }
    }

    private static int getSlotIndex(AbstractContainerMenu menu, Slot slot) {
        return menu.slots.indexOf(slot);
    }
}
