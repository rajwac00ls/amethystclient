package hack.echo.client.features.impl.detected;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventHandleInput;
import hack.echo.client.event.impl.EventOnAttackEntity;
import hack.echo.client.event.impl.EventPacketSend;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.features.settings.impl.IntSetting;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.core.BlockPos;
//? if >=26.1 {
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
//?}
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Port of TrouserStreak's MaceKill.
// Spoofs a teleport straight up and back down so the server thinks the mace
// smash came from a huge fall, without actually falling.
//
// The server's "moved too quickly" check is CUMULATIVE per tick (it measures
// the total distance from the position at the start of the tick, minus expected
// velocity), not per packet. That's why heights up to ~40 fit in one burst but
// taller ones get flagged. So the teleport is paced: a few 8-block chunks per
// tick spread over multiple ticks, which keeps every single tick under the
// budget no matter how tall the total fall is.
public class MaceDmg extends Feature {

    private final IntSetting fallHeight = new IntSetting(
            Concat.of("Fall Height"), 22, 1, 169);
    private final IntSetting spamPackets = new IntSetting(
            Concat.of("Spam Packets"), 4, 1, 17);
    private final IntSetting chunksPerTick = new IntSetting(
            Concat.of("Chunks Per Tick"), 2, 1, 4);
    private final BoolSetting disableWhenBlocked = new BoolSetting(
            Concat.of("Disable When Blocked"), true);
    private final BoolSetting swingArm = new BoolSetting(
            Concat.of("Swing Arm"), true);
    private final BoolSetting useOffset = new BoolSetting(
            Concat.of("Use Offset"), true);
    private final FloatSetting horizontalOffset = new FloatSetting(
            Concat.of("Horizontal Offset"), 0.05f, 0f, 0.99f, 0.01f);
    private final FloatSetting yOffset = new FloatSetting(
            Concat.of("Y Offset"), 0.01f, 0f, 0.99f, 0.01f);

    private LivingEntity lastTarget;
    private boolean sendingAttacks;
    private boolean teleporting;
    private boolean ascending;
    private boolean spamSent;
    private Vec3 previousPos;
    private double startY;
    private double targetY;
    private double currentY;
    private Packet<?> pendingAttack;

    public MaceDmg() {
        super(new FeatureInfo(
                Concat.of("MaceDMG"),
                Concat.of("Spoofs a fall to make the mace deal max damage."),
                Category.DETECTED
        ));
        fallHeight.describedBy(Concat.of("Blocks above you to spoof the fall from. Teleported in 8 block chunks paced across ticks so tall falls stay under the server's per-tick move budget. You must be out in the open sky, not underground."));
        spamPackets.describedBy(Concat.of("Rotation packets before the teleport. Rotation-only packets carry no position, so they add nothing to the move budget and can also refund budget if you have velocity."));
        chunksPerTick.describedBy(Concat.of("Max teleport chunks (8 blocks each) sent per tick. Lower this if the server still warns 'moved too quickly', raise it for a faster teleport."));
        useOffset.describedBy(Concat.of("Moves you a tiny amount after the hit to avoid fall damage."));
    }

    @Override
    public CharSequence concat() {
        return Concat.ofInt(fallHeight.getValue());
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetTeleport();
    }

    @EventSubscribe
    private void onAttackPre(EventOnAttackEntity.Pre event) {
        if (event.getTarget() instanceof LivingEntity living) {
            lastTarget = living;
        }
    }

    @EventSubscribe
    private void onPacketSend(EventPacketSend event) {
        if (isNull()) return;
        if (sendingAttacks) return;

        if (teleporting) {
            // Keep the server from seeing the client's own positions while the
            // spoofed teleport is in flight, and ignore extra clicks.
            if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
                event.cancel();
            }
            //? if >=26.1 {
            if (event.getPacket() instanceof ServerboundAttackPacket) {
                event.cancel();
            }
            //?} else {
            /*if (event.getPacket() instanceof ServerboundInteractPacket) {
                event.cancel();
            }
            *///?}
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.MACE)) return;
        if (mc.player.isPassenger()) return;

        Entity entity;
        //? if >=26.1 {
        if (!(event.getPacket() instanceof ServerboundAttackPacket packet)) return;
        entity = mc.level.getEntity(packet.entityId());
        //?} else {
        /*if (!(event.getPacket() instanceof ServerboundInteractPacket packet)) return;
        if (!isAttack(packet)) return;
        entity = lastTarget;
        *///?}

        if (!(entity instanceof LivingEntity target)) return;
        if (!target.isAlive()) return;
        if (disableWhenBlocked.getValue() && (target.isBlocking() || target.isInvulnerable())) return;

        int blocks = maxFallHeight();
        if (blocks == 0) return;

        event.cancel();

        previousPos = mc.player.position();
        startY = previousPos.y;
        targetY = startY + blocks;
        currentY = startY;
        ascending = true;
        teleporting = true;
        spamSent = false;
        pendingAttack = event.getPacket();
    }

    @EventSubscribe
    private void onTick(EventHandleInput.Early event) {
        if (!teleporting) return;
        if (isNull() || mc.player.connection == null || mc.player.isDeadOrDying() || mc.player.isRemoved()) {
            resetTeleport();
            return;
        }
        if (pendingAttack == null) {
            resetTeleport();
            return;
        }

        // Pin the client at the trigger spot so gravity can't pull it away from
        // the start position while the server-side teleport plays out.
        mc.player.setPos(previousPos);
        mc.player.setDeltaMovement(Vec3.ZERO);

        if (!spamSent) {
            spamSent = true;
            for (int i = 0; i < spamPackets.getValue(); i++) {
                sendRotation();
            }
        }

        int remaining = chunksPerTick.getValue();
        while (remaining > 0 && teleporting) {
            if (ascending) {
                currentY = Math.min(currentY + 8, targetY);
                sendMove(new Vec3(previousPos.x, currentY, previousPos.z));
                remaining--;
                if (currentY >= targetY) {
                    ascending = false;
                }
            } else {
                if (currentY - 8 > startY) {
                    currentY -= 8;
                    sendMove(new Vec3(previousPos.x, currentY, previousPos.z));
                    remaining--;
                } else {
                    finishTeleport();
                    return;
                }
            }
        }
    }

    private void finishTeleport() {
        sendingAttacks = true;
        try {
            sendMove(new Vec3(previousPos.x, startY, previousPos.z));

            if (swingArm.getValue()) {
                mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            mc.player.connection.send(pendingAttack);
            mc.player.setPos(previousPos);
        } finally {
            sendingAttacks = false;
        }

        if (useOffset.getValue()) {
            Vec3 offsetHome = findOffsetHome(previousPos);
            sendingAttacks = true;
            sendMove(offsetHome);
            sendingAttacks = false;
            mc.player.setPos(offsetHome);
        }

        resetTeleport();
    }

    //? if <26.1 {
    /*private boolean isAttack(ServerboundInteractPacket packet) {
        boolean[] attack = { false };
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 location) {
            }

            @Override
            public void onAttack() {
                attack[0] = true;
            }
        });
        return attack[0];
    }
    *///?}

    private void sendMove(Vec3 pos) {
        sendingAttacks = true;
        mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                pos, mc.player.getYRot(), mc.player.getXRot(), false, mc.player.horizontalCollision));
        sendingAttacks = false;
    }

    private void sendRotation() {
        sendingAttacks = true;
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                mc.player.getYRot(), mc.player.getXRot(), false, mc.player.horizontalCollision));
        sendingAttacks = false;
    }

    private void resetTeleport() {
        teleporting = false;
        ascending = false;
        spamSent = false;
        pendingAttack = null;
    }

    private int maxFallHeight() {
        int maxAbove = (int) (mc.level.getMaxY() - 1 - mc.player.getY());
        return Math.min(fallHeight.getValue(), Math.max(0, maxAbove));
    }

    private Vec3 findOffsetHome(Vec3 base) {
        float dx = horizontalOffset.getValue();
        float dy = yOffset.getValue();

        Vec3[] candidates = new Vec3[] {
                base.add(dx, dy, 0),
                base.add(-dx, dy, 0),
                base.add(0, dy, dx),
                base.add(0, dy, -dx),
                base.add(dx, dy, dx),
                base.add(-dx, dy, -dx),
                base.add(-dx, dy, dx),
                base.add(dx, dy, -dx)
        };

        for (Vec3 pos : candidates) {
            if (!isInvalid(pos)) return pos;
        }

        Vec3 up = base.add(0, dy, 0);
        if (!isInvalid(up)) return up;

        return base;
    }

    private boolean isInvalid(Vec3 pos) {
        if (mc.level == null) return true;
        if (pos.y < mc.level.getMinY() || pos.y > mc.level.getMaxY() - 1) return true;

        BlockPos block = BlockPos.containing(pos);
        if (mc.level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) == null) return true;

        BlockState state = mc.level.getBlockState(block);
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)) {
            return true;
        }

        AABB box = mc.player.getBoundingBox().move(pos.subtract(mc.player.position()));
        return mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
    }
}
