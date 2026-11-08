package hack.echo.client.utils.keyboard;

import org.lwjgl.glfw.GLFW;

import static hack.echo.client.utils.Imports.mc;

public class KeyUtils {
    public static boolean isKeyPressed(int keyCode) {
        if (mc == null || mc.getWindow() == null) return false;
        long handle = mc.getWindow().handle();
        if (keyCode >= GLFW.GLFW_MOUSE_BUTTON_1 && keyCode <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
            return GLFW.glfwGetMouseButton(handle, keyCode) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS;
    }
}
