package hack.echo.client.mixin.entity;

import hack.echo.client.Echo;
import hack.echo.client.features.impl.render.SpectatorESP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class MixinAvatarRendererSpectator {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void echo$spectatorForceModel(Avatar player, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
        if (Echo.isDestroyed) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || player == null) return;
        if (player == mc.player) return;

        SpectatorESP module = Echo.featureManager == null ? null : Echo.featureManager.getFeatureByClass(SpectatorESP.class);
        if (module == null || !module.isEnabled()) return;
        if (!module.isForceModel()) return;

        boolean spectator = player.isSpectator();
        boolean vanished = !spectator && module.isDetectVanish() && player instanceof Player vanishedPlayer && SpectatorESP.isVanished(vanishedPlayer);
        if (!spectator && !vanished) return;

        state.isSpectator = true;
        state.isInvisibleToPlayer = false;
        state.isInvisible = true;
    }
}
