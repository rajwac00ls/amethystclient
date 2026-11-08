package hack.echo.client.features.impl.player;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.features.settings.impl.ModeSetting;
import hack.echo.client.handlers.InputHandler;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class SmartSelect extends Feature {

    public SmartSelect() {
        super(new FeatureInfo(
            Concat.of("Smart Select"),
            Concat.of("Auto-selects best tool or weapon for what you're looking at"),
            Category.UTILITY
        ));
    }

    private final BoolSetting switchBack = new BoolSetting(Concat.of("Switch Back"), true);
    private final BoolSetting requireClick = new BoolSetting(Concat.of("Require Click"), true);
    private final ModeSetting weaponMode = new ModeSetting(
        Concat.of("Weapon Priority"),
        Concat.of("Sword"),
        Concat.of("Sword"), Concat.of("Axe"), Concat.of("Both"));
    private final IntSetting tickDelay = new IntSetting(
        Concat.of("Tick Delay"), 0, 0, 5,
        Concat.of(" ticks"));

    private int previousSlot = -1;
    private int delay = 0;

    @Override
    public void onDisable() {
        previousSlot = -1;
        delay = 0;
        super.onDisable();
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;

        if (delay > 0) {
            delay--;
            return;
        }

        if (mc.hitResult == null) {
            trySwitchBack();
            return;
        }

        boolean attacking = !requireClick.getValue() || InputHandler.isBindDown(mc.options.keyAttack);

        if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
            handleBlock((BlockHitResult) mc.hitResult, attacking);
        } else if (mc.hitResult.getType() == HitResult.Type.ENTITY) {
            handleEntity(((EntityHitResult) mc.hitResult).getEntity(), attacking);
        } else {
            trySwitchBack();
        }
    }

    private void handleBlock(BlockHitResult hit, boolean attacking) {
        BlockPos pos = hit.getBlockPos();
        var state = mc.level.getBlockState(pos);

        if (state.isAir()) {
            trySwitchBack();
            return;
        }

        if (!attacking) {
            trySwitchBack();
            return;
        }

        int bestSlot = findBestTool(pos);
        if (bestSlot == -1) {
            trySwitchBack();
            return;
        }

        selectSlot(bestSlot);
    }

    private void handleEntity(net.minecraft.world.entity.Entity entity, boolean attacking) {
        if (!(entity instanceof LivingEntity) && !(entity instanceof EndCrystal)) {
            trySwitchBack();
            return;
        }

        if (!attacking) {
            trySwitchBack();
            return;
        }

        int weaponSlot = findBestWeapon();
        if (weaponSlot == -1) return;

        selectSlot(weaponSlot);
    }

    private void selectSlot(int slot) {
        if (slot == mc.player.getInventory().getSelectedSlot()) return;

        if (previousSlot == -1) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        }

        mc.player.getInventory().setSelectedSlot(slot);
        delay = tickDelay.getValue();
    }

    private void trySwitchBack() {
        if (switchBack.getValue() && previousSlot != -1) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    private int findBestTool(BlockPos pos) {
        int bestSlot = -1;
        float bestSpeed = 1f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            float speed = getMiningSpeed(stack, pos);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private float getMiningSpeed(ItemStack stack, BlockPos pos) {
        if (stack.isEmpty()) return 0f;

        float speed = stack.getDestroySpeed(mc.level.getBlockState(pos));
        if (speed > 1f) {
            var efficiencyEntry = mc.level.registryAccess().get(Enchantments.EFFICIENCY);
            if (efficiencyEntry.isPresent()) {
                int effLvl = EnchantmentHelper.getItemEnchantmentLevel(efficiencyEntry.get(), stack);
                if (effLvl > 0) {
                    speed += effLvl * effLvl + 1;
                }
            }
        }

        return speed;
    }

    private int findBestWeapon() {
        int swordSlot = -1;
        int axeSlot = -1;

        boolean preferSword = weaponMode.is(Concat.of("Sword")) || weaponMode.is(Concat.of("Both"));
        boolean preferAxe = weaponMode.is(Concat.of("Axe")) || weaponMode.is(Concat.of("Both"));

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            var item = stack.getItem();

            if (preferSword && isSword(item) && swordSlot == -1) {
                swordSlot = i;
            }
            if (preferAxe && isAxe(item) && axeSlot == -1) {
                axeSlot = i;
            }
        }

        if (weaponMode.is(Concat.of("Sword"))) return swordSlot;
        if (weaponMode.is(Concat.of("Axe"))) return axeSlot;

        // Both: prefer sword over axe
        return swordSlot != -1 ? swordSlot : axeSlot;
    }

    private boolean isSword(net.minecraft.world.item.Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }

    private boolean isAxe(net.minecraft.world.item.Item item) {
        return item == Items.WOODEN_AXE || item == Items.STONE_AXE
            || item == Items.IRON_AXE || item == Items.GOLDEN_AXE
            || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
    }
}
