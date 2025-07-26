package dev.vfyjxf.calculatorcirrus.ui;

import dev.vfyjxf.calculatorcirrus.api.ui.widget.Widget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.WidgetGroup;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.Widgets;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class ModularScreen<T extends Widget> extends Screen {

    protected final WidgetGroup<T> widgetGroup;

    protected ModularScreen() {
        super(Component.empty());
        this.widgetGroup = Widgets.widgetGroup();
    }
}
