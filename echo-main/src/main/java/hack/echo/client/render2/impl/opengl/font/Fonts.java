package hack.echo.client.render2.impl.opengl.font;

import hack.echo.client.render2.impl.vulkan.impl.VkText;
import hack.echo.client.utils.VulkanUtil;
import hack.echo.client.vulkan.descriptor.SimpleUBO;
import lombok.Getter;

public class Fonts {

    public static Font arial;
    public static Font inter;
    public static Font interSemiBold;
    public static Font aptos;
    public static Font lucide;

    private static Font interBold;

    public static void init() {
        try {
            arial = new Font("arial");
            inter = new Font("inter");
            interBold = new Font("inter_semi_bold");
            interSemiBold = interBold;
            aptos = new Font("aptos_semi_bold");
            lucide = new Font("lucide");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void destroy() {
        arial.destroy();
        inter.destroy();
        if (interBold != null) interBold.destroy();
        if (aptos != null) aptos.destroy();
        lucide.destroy();
    }

    public static Font getInterBold() {
        return interBold;
    }

    public static void setGuiFont(Font font) {
        if (font != null) {
            interSemiBold = font;
        }
    }
}
