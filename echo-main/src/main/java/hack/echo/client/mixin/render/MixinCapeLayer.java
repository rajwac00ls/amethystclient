package hack.echo.client.mixin.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import hack.echo.client.Echo;
import hack.echo.client.features.impl.misc.CapeModule;
import hack.echo.client.utils.ResourceHelper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;

import static hack.echo.client.utils.Imports.mc;

@Mixin(CapeLayer.class)
public abstract class MixinCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public MixinCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Final
    @Shadow
    private HumanoidModel<AvatarRenderState> model;

    @Shadow protected abstract boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType layerType);

    // Returns true only when the cape texture is actually usable, so we never submit an
    // unresolvable identifier (which would render the black missing texture).
    private static boolean ensureCapeTexture(Identifier id, String capePath) {
        // When the resource manager provides the texture, the vanilla lazy-load path handles it.
        if (mc.getResourceManager().getResource(id).isPresent()) return true;
        // Otherwise load it manually (e.g. EchoLoader) and register it, re-registering if a
        // texture reload dropped our DynamicTexture from the manager.
        if (mc.getTextureManager().getTexture(id) instanceof DynamicTexture) return true;
        try (InputStream is = ResourceHelper.getStream("textures/capes/" + capePath + ".png")) {
            if (is == null) return false;
            NativeImage image = NativeImage.read(is);
            mc.getTextureManager().register(id, new DynamicTexture(() -> "echo:capes/" + capePath, image));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void echoware$renderCape(PoseStack matrices, SubmitNodeCollector commands, int light, AvatarRenderState state, float f, float g, CallbackInfo ci) {
        if (Echo.isDestroyed) return;
        if (Echo.featureManager == null || mc.player == null) return;

        CapeModule module = Echo.featureManager.getFeatureByClass(CapeModule.class);
        if (module == null || !module.isEnabled()) return;

        // Only override the local player; vanilla handles everyone else.
        if (state.isInvisible || state.id != mc.player.getId()) return;
        if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) return;

        Identifier capeTexture = Identifier.fromNamespaceAndPath(Echo.MOD_ID, "textures/capes/" + module.getCapePath() + ".png");
        if (!ensureCapeTexture(capeTexture, module.getCapePath())) return;

        matrices.pushPose();
        if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
            matrices.translate(0.0F, -0.053125F, 0.06875F);
        }
        commands.submitModel(
                this.model,
                state,
                matrices,
                RenderTypes.entitySolid(capeTexture),
                light,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor,
                null
        );
        matrices.popPose();
        ci.cancel();
    }
}
