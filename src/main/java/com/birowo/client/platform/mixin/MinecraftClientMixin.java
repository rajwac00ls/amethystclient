package com.birowo.client.platform.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;

import com.birowo.client.platform.rotation.RotationEngine;

/**
 * Moves the aim of the client before the camera of the game is updated.
 *
 * <p>The game reads the mouse, then updates the camera with the angle the player looks at, and
 * only then draws the world. A rotation that is applied after the camera has been updated is
 * therefore one frame old: the player looks at the place the aim had when the frame was drawn.
 *
 * <p>Applying the rotation at the head of the rendering method puts it in front of the camera
 * update, so that the angle of this frame is the angle the player sees. The mouse is read after
 * this point, which keeps the finer movement of the mouse in the hands of the player.
 *
 * <p>Verified against Minecraft 1.21.11 (Yarn 1.21.11+build.6).
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "render(Z)V", at = @At("HEAD"), require = 1, expect = 1)
	private void birowo$stepRotation(CallbackInfo info) {
		RotationEngine.step();
	}
}
