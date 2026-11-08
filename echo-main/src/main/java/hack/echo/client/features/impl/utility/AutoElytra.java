package hack.echo.client.features.impl.utility;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventTick;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.utils.inventory.InventoryUtils;
import hack.echo.client.utils.strings.Concat;

public class AutoElytra extends Feature {

    private boolean jumpHeld = false;

    public AutoElytra() {
        super(new FeatureInfo(
            Concat.of("Auto Elytra"),
            Concat.of("Automatically jumps when wearing an elytra in the air so you can start flying"),
            Category.UTILITY
        ));
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    public void onTick(EventTick event) {
        if (mc.player == null || mc.level == null) return;
        if (hack.echo.client.api.MinecraftCompat.getScreen() != null) {
            releaseJump();
            return;
        }

        if (mc.player.isFallFlying() || mc.player.getAbilities().flying) {
            releaseJump();
            return;
        }

        if (!InventoryUtils.isWearingElytra()) {
            releaseJump();
            return;
        }

        if (mc.player.onGround() || mc.player.isInWater() || mc.player.isPassenger() || mc.player.onClimbable()) {
            releaseJump();
            return;
        }

        mc.options.keyJump.setDown(true);
        jumpHeld = true;
    }

    private void releaseJump() {
        if (!jumpHeld) return;
        mc.options.keyJump.setDown(false);
        jumpHeld = false;
    }

    @Override
    public void onDisable() {
        releaseJump();
        super.onDisable();
    }

    @Override
    public void onEnable() {
        jumpHeld = false;
        super.onEnable();
    }
}
