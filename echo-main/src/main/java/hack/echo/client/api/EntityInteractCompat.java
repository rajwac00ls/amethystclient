package hack.echo.client.api;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public final class EntityInteractCompat {

    private EntityInteractCompat() {
    }

    public static void useOnEntity(Entity entity) {
        if (entity == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        //? if >=26.0 {
        mc.gameMode.interact(mc.player, entity, new EntityHitResult(entity), InteractionHand.MAIN_HAND);
        //?} else {
        /*mc.gameMode.interact(mc.player, entity, InteractionHand.MAIN_HAND);
        *///?}
    }
}
