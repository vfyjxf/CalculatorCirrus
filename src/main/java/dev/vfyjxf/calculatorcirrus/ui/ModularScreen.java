package dev.vfyjxf.calculatorcirrus.ui;

import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidgetGroup;
import dev.vfyjxf.calculatorcirrus.ui.widget.WidgetGroup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class ModularScreen<T extends IWidget> extends Screen {

    protected final IWidgetGroup<T> widgetGroup;

    protected ModularScreen() {
        super(Component.empty());
        this.widgetGroup = new WidgetGroup<>();
    }
}
