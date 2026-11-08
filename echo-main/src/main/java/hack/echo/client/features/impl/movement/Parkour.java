package hack.echo.client.features.impl.movement;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventMovementInput;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class Parkour extends Feature {

    public Parkour() {
        super(new FeatureInfo(
                Concat.of("Parkour"),
                Concat.of("Auto-jumps at block edges while moving"),
                Category.MOVEMENT
        ));
    }

    @EventSubscribe
    private void onMoveInput(EventMovementInput e) {
        if (isNull()) return;
        if (e.originalJump) return;
        if (e.originalSneak) return;
        if (!mc.player.onGround()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;
        if (mc.player.isPassenger() || mc.player.isFallFlying()) return;

        Direction dir = getMoveDirection(e);
        if (dir == null) return;
        if (!isAtEdge(dir)) return;

        e.jump = true;
    }

    private Direction getMoveDirection(EventMovementInput e) {
        float forward = e.originalForward ? 1 : e.originalBack ? -1 : 0;
        float strafe = e.originalLeft ? 1 : e.originalRight ? -1 : 0;
        if (forward == 0 && strafe == 0) return null;

        double yaw = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dx = -sin * forward + cos * strafe;
        double dz = cos * forward + sin * strafe;
        return dominantDirection(dx, dz);
    }

    private boolean isAtEdge(Direction dir) {
        BlockPos feet = mc.player.blockPosition();
        BlockState ground = mc.level.getBlockState(feet.below());
        if (ground.getCollisionShape(mc.level, feet.below()).isEmpty()) return false;

        BlockPos ahead = feet.relative(dir);
        BlockState aheadState = mc.level.getBlockState(ahead);
        if (!aheadState.getCollisionShape(mc.level, ahead).isEmpty()) return false;

        BlockState aboveAhead = mc.level.getBlockState(ahead.above());
        if (!aboveAhead.getCollisionShape(mc.level, ahead.above()).isEmpty()) return false;

        BlockState floorAhead = mc.level.getBlockState(ahead.below());
        return floorAhead.getCollisionShape(mc.level, ahead.below()).isEmpty();
    }

    private Direction dominantDirection(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
