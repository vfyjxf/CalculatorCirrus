package dev.vfyjxf.cirrus.ui;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class CirrusMouse {
    private CirrusMouse() {
    }

    public static boolean leftButtonDown() {
        return buttonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public static boolean rightButtonDown() {
        return buttonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    public static boolean middleButtonDown() {
        return buttonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    }

    public static boolean buttonDown(int button) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }
}
