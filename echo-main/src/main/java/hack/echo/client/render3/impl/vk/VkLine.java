package hack.echo.client.render3.impl.vk;

import hack.echo.client.Echo;
import hack.echo.client.render3.api.Draw3D;
import hack.echo.client.vulkan.CommandBuffer;
import hack.echo.client.vulkan.descriptor.PushConstants;
import hack.echo.client.vulkan.descriptor.SimpleUBO;
import hack.echo.client.vulkan.graphics.VkGraphicsPipeline;
import hack.echo.client.vulkan.graphics.VkShader;
import hack.echo.client.vulkan.memory.MemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.vulkan.VK10.*;

public class VkLine extends VkShader {
    private static final float LINE_WIDTH = 3.0f;
    private final SimpleUBO ubo;
    private final PushConstants pushConstants = new PushConstants(16, VK_SHADER_STAGE_VERTEX_BIT);
    public VkLine(SimpleUBO ubo) {
        super(28, 2);
        this.ubo = ubo;
    }

    @Override
    protected VkGraphicsPipeline.Builder getPipelineBuilder() {
        return VkGraphicsPipeline.builder("3d/line.vert", "3d/basic.frag")
                .stride(28)
                .attribute(0, VK_FORMAT_R32G32B32_SFLOAT, 0)
                .attribute(1, VK_FORMAT_R32G32B32_SFLOAT, 12)
                .attribute(2, VK_FORMAT_R32_SINT, 24)
                .descriptor(ubo)
                .pushConstants(pushConstants)
                .cull(VK_CULL_MODE_NONE)
                .topology(VK_PRIMITIVE_TOPOLOGY_LINE_LIST)
                .depthFunc(Echo.vkContext.depthCompareOp())
                .blend(false);
    }

    public void add(double fx, double fy, double fz, double tx, double ty, double tz, int color) {
        Vec3 o = Draw3D.getInstance().origin;
        long p = ptr();
        MemUtil.putVec3(
                p,
                (float) (fx - o.x),
                (float) (fy - o.y),
                (float) (fz - o.z)
        );
        MemUtil.putVec3(
                p + 12,
                (float) (tx - o.x),
                (float) (ty - o.y),
                (float) (tz - o.z)
        );
        MemUtil.putInt(p + 24, color);
        next();
    }

    @Override
    public void flush(CommandBuffer commandBuffer) {
        var window = Minecraft.getInstance().getWindow();
        MemUtil.putFloat(pushConstants.getPointer(), LINE_WIDTH);
        MemUtil.putFloat(pushConstants.getPointer() + 4, window.getWidth());
        MemUtil.putFloat(pushConstants.getPointer() + 8, window.getHeight());
        MemUtil.putFloat(pushConstants.getPointer() + 12, 0f);
        super.flush(commandBuffer);
    }

    @Override
    public void cleanup() {
        pushConstants.cleanup();
        super.cleanup();
    }
}
