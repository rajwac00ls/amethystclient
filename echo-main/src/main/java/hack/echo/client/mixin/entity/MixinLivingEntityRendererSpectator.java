package hack.echo.client.mixin.entity;

import hack.echo.client.Echo;
import hack.echo.client.features.impl.render.SpectatorESP;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererSpectator {

    @Redirect(
            method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
    )
    private void echo$spectatorModel(SubmitNodeCollector collector, Model model, Object state, PoseStack poseStack, RenderType renderType, int packedLight, int packedOverlay, int tintedColor, TextureAtlasSprite atlasSprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        OrderedSubmitNodeCollector ordered = (OrderedSubmitNodeCollector) collector;
        if (isForcedSpectator(state)) {
            tintedColor = applyOpacity(tintedColor);
            if (isOnlyHead(state)) {
                HumanoidModel<?> humanoid = (HumanoidModel<?>) model;
                ordered.submitModelPart(humanoid.head, poseStack, renderType, packedLight, packedOverlay, null, tintedColor, crumblingOverlay);
                ordered.submitModelPart(humanoid.hat, poseStack, renderType, packedLight, packedOverlay, null, tintedColor, crumblingOverlay);
                return;
            }
        }
        ordered.submitModel(model, state, poseStack, renderType, packedLight, packedOverlay, tintedColor, atlasSprite, outlineColor, crumblingOverlay);
    }

    private SpectatorESP module() {
        if (Echo.isDestroyed) return null;
        return Echo.featureManager == null ? null : Echo.featureManager.getFeatureByClass(SpectatorESP.class);
    }

    private boolean isForcedSpectator(Object state) {
        if (!(state instanceof AvatarRenderState avatar)) return false;
        if (!avatar.isSpectator) return false;
        SpectatorESP module = module();
        return module != null && module.isEnabled() && module.isForceModel();
    }

    private boolean isOnlyHead(Object state) {
        if (!(state instanceof AvatarRenderState)) return false;
        SpectatorESP module = module();
        return module != null && module.isEnabled() && module.isForceModel() && module.isOnlyHead();
    }

    private int applyOpacity(int tintedColor) {
        SpectatorESP module = module();
        if (module == null) return tintedColor;
        int alpha = (int) (module.getOpacity() / 100f * 255f);
        return (tintedColor & 0x00FFFFFF) | (alpha << 24);
    }
}
