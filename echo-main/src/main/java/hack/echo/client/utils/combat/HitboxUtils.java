package hack.echo.client.utils.combat;

import hack.echo.client.Echo;
import hack.echo.client.features.impl.combat.HitboxMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import static hack.echo.client.utils.Imports.mc;

public final class HitboxUtils {

    private static final double PLAYER_STANDING_HITBOX_HEIGHT = 1.8;

    private HitboxUtils() {}

    public static boolean isEntityAllowed(Entity entity) {
        if (Echo.isDestroyed || Echo.featureManager == null) return false;
        HitboxMod hitboxMod = Echo.featureManager.getFeatureByClass(HitboxMod.class);
        if (hitboxMod == null || !hitboxMod.isEnabled()) return false;

        if (hitboxMod.entities.getSelectedCount() == 0) {
            if (!(entity instanceof Player)) return false;
        } else if (!hitboxMod.entities.isSelected(entity.getType())) {
            return false;
        }

        if (mc.player == null) return false;
        double range = hitboxMod.distanceSetting.getValue();
        return mc.player.distanceToSqr(entity) <= range * range;
    }

    public static AABB getBaseBox(Entity entity) {
        AABB baseBox = entity.getBoundingBox();
        if (Echo.isDestroyed || Echo.featureManager == null) return baseBox;
        HitboxMod hitboxMod = Echo.featureManager.getFeatureByClass(HitboxMod.class);
        if (hitboxMod != null && hitboxMod.isEnabled() && hitboxMod.staticHitbox.getValue()
                && entity instanceof Player player && player.isFallFlying()) {
            return new AABB(baseBox.minX, baseBox.minY, baseBox.minZ,
                    baseBox.maxX, baseBox.minY + PLAYER_STANDING_HITBOX_HEIGHT, baseBox.maxZ);
        }
        return baseBox;
    }

    public static AABB getExpandedBox(Entity entity) {
        AABB baseBox = getBaseBox(entity);
        if (!isEntityAllowed(entity)) return baseBox;
        if (Echo.featureManager == null) return baseBox;

        HitboxMod hitboxMod = Echo.featureManager.getFeatureByClass(HitboxMod.class);
        if (hitboxMod == null) return baseBox;

        if (hitboxMod.uniformExpand.getValue()) {
            float size = hitboxMod.uniformExpandSize.getValue();
            return baseBox.inflate(size, size, size);
        }
        return baseBox.inflate(hitboxMod.expandX.getValue(), hitboxMod.expandY.getValue(), hitboxMod.ExpandZ.getValue());
    }
}
