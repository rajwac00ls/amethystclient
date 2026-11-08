package hack.echo.client.utils.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import static hack.echo.client.utils.Imports.mc;

public final class CrystalUtils {

    private CrystalUtils() {
    }

    /**
     * Checks if an end crystal can be placed on top of {@code pos}, assuming
     * the block at {@code pos} is already a valid base (obsidian/bedrock).
     */
    public static boolean canPlaceCrystalClientAssumeObsidian(BlockPos pos) {
        if (mc.level == null || pos == null) return false;

        BlockPos up = pos.above();
        BlockPos up2 = up.above();

        if (!mc.level.getBlockState(up).isAir()) return false;
        if (!mc.level.getBlockState(up2).isAir()) return false;

        AABB crystalBox = new AABB(
            up.getX(), up.getY(), up.getZ(),
            up2.getX() + 1.0, up2.getY() + 1.0, up2.getZ() + 1.0
        );

        return mc.level.getEntities(null, crystalBox).isEmpty();
    }
}
