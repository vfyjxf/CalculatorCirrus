package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

public class CirrusPageWidget extends CompositeWidget<Widget> {
    public CirrusPageWidget() {
        this(6, 5);
    }

    public CirrusPageWidget(int padding, int gap) {
        useStyle(
            flexColumn(),
            sizeFull(),
            background(CirrusTextures.Background.inset),
            padding(padding),
            gap(gap)
        );
    }

    protected LabelWidget addTitle(Component text) {
        LabelWidget label = CirrusText.primary(text);
        addWidget(label);
        return label;
    }

    protected WidgetGroup<Widget> addVerticalList(int rowGap) {
        WidgetGroup<Widget> list = new WidgetGroup<>();
        list.useStyle(flexColumn(), flexGrow(1), minHeight(0), minWidth(0), gap(rowGap));
        addWidget(list);
        return list;
    }
}
