package com.birowo.client.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.ClientPlayerEntity;

import com.birowo.client.platform.rotation.ServerRotation;

/**
 * Makes the movement packets carry the rotation the server is meant to see.
 *
 * <p>Vanilla builds the movement packet from the rotation of the player entity. A silent rotation
 * needs the packet to carry a different angle than the one the player is looking with, otherwise
 * the server sees the player look away from the target in the very tick the target is attacked.
 *
 * <p>The rotation of the entity is therefore swapped for the duration of the call and restored
 * right after it, which leaves the camera — and the mouse — untouched. The swap has to cover the
 * whole call and not only the packet: the game compares the rotation of this tick with the one of
 * the last tick to decide whether it has to send a rotation at all, and that comparison has to see
 * the rotation of the aim, or the server would never learn about it.
 *
 * <p>That is why there are two injections: one at the head of the method, which swaps the angle
 * in, and one at the return of the method, which puts the angle of the player back. Both use
 * stable anchors, and neither of them changes the control flow of the game.
 *
 * <p>Verified against Minecraft 1.21.11 (Yarn 1.21.11+build.6).
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Unique
	private float birowo$clientYaw;

	@Unique
	private float birowo$clientPitch;

	@Unique
	private boolean birowo$rotationOverridden;

	@Inject(method = "sendMovementPackets", at = @At("HEAD"), require = 1, expect = 1)
	private void birowo$applyServerRotation(CallbackInfo info) {
		if (!ServerRotation.active()) {
			return;
		}

		ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

		this.birowo$clientYaw = player.getYaw();
		this.birowo$clientPitch = player.getPitch();
		this.birowo$rotationOverridden = true;

		player.setYaw(ServerRotation.yaw());
		player.setPitch(ServerRotation.pitch());
	}

	@Inject(method = "sendMovementPackets", at = @At("RETURN"), require = 1, expect = 1)
	private void birowo$restoreClientRotation(CallbackInfo info) {
		if (!this.birowo$rotationOverridden) {
			return;
		}

		this.birowo$rotationOverridden = false;

		ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

		player.setYaw(this.birowo$clientYaw);
		player.setPitch(this.birowo$clientPitch);
	}
}
