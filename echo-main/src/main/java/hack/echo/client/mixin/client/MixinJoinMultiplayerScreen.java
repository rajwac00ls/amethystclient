package hack.echo.client.mixin.client;

import hack.echo.client.api.MinecraftCompat;
import hack.echo.client.auth.TokenSession;
import hack.echo.client.screens.TokenLoginScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {

    protected MixinJoinMultiplayerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void echo$addSessionButtons(CallbackInfo ci) {
        int x = 4;
        int y = 4;
        int w = 78;
        int h = 20;
        this.addRenderableWidget(Button.builder(Component.literal("Login"), b ->
                        MinecraftCompat.setScreen(new TokenLoginScreen(this)))
                .bounds(x, y, w, h)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Revert"), b -> TokenSession.revert())
                .bounds(x + w + 2, y, w, h)
                .build());
    }
}
