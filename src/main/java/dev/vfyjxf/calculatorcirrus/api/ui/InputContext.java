package dev.vfyjxf.calculatorcirrus.api.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.calculatorcirrus.api.math.FloatingPoint;
import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidgetGroup;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.StringUtil;


public record InputContext(
        InputConstants.Key key,
        double mouseX, double mouseY,
        int modifiers, boolean released
) {

    public boolean isCtrlDown() {
        return Screen.hasControlDown();
    }

    public boolean isShiftDown() {
        return Screen.hasShiftDown();
    }

    public boolean isAltDown() {
        return Screen.hasAltDown();
    }

    public boolean isMouse() {
        return this.key().getType() == InputConstants.Type.MOUSE;
    }

    public boolean isLeftClick() {
        return isMouse() && key().getValue() == 0;
    }

    public boolean isRightClick() {
        return isMouse() && key().getValue() == 1;
    }

    public boolean isKeyboard() {
        return this.key().getType() == InputConstants.Type.KEYSYM;
    }

    public boolean isAllowedChatCharacter() {
        return isKeyboard() && StringUtil.isAllowedChatCharacter((char) this.key.getValue());
    }

    public boolean is(InputConstants.Key key) {
        return this.key().equals(key);
    }

    public boolean is(KeyMapping keyMapping) {
        return keyMapping.isActiveAndMatches(this.key);
    }

    public FloatingPoint getRelative(IWidget IWidget) {
        IWidgetGroup<?> parent = IWidget.parent();
        if (parent == null) return new FloatingPoint(mouseX(), mouseY());
        Point absolute = IWidget.getAbsolute();
        return new FloatingPoint(mouseX() - absolute.x, mouseY() - absolute.y);
    }
}
