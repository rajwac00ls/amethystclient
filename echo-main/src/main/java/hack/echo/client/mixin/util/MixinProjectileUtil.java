package hack.echo.client.mixin.util;

import hack.echo.client.Echo;
import hack.echo.client.features.impl.player.AntiTrap;
import hack.echo.client.utils.combat.HitboxUtils;
import hack.echo.client.utils.combat.TargetUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ProjectileUtil.class)
public class MixinProjectileUtil {

    @Redirect(method = "getEntityHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB expandHitbox(Entity entity) {
        if (Echo.isDestroyed) return entity.getBoundingBox().inflate(entity.getPickRadius());

        if (AntiTrap.shouldBlockEntityInteraction(entity)) {
            double hiddenY = entity.getY() - 1024.0;
            return new AABB(entity.getX(), hiddenY, entity.getZ(), entity.getX(), hiddenY, entity.getZ());
        }

        if (entity instanceof Player player && !TargetUtils.isTargetAllowed(player)) {
            return entity.getBoundingBox().inflate(entity.getPickRadius());
        }

        return HitboxUtils.getExpandedBox(entity).inflate(entity.getPickRadius());
    }
}
