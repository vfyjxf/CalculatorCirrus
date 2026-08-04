package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;

public final class CirrusCardWidget extends CompositeWidget<Widget> {
    private final Component title;
    private final WidgetGroup<Widget> body;
    private final WidgetGroup<Widget> extension;

    public CirrusCardWidget() {
        this(Component.empty());
    }

    public CirrusCardWidget(Component title) {
        this.title = title == null ? Component.empty() : title;
        useStyle(
            flexColumn(),
            gap(3),
            padding(5),
            minWidth(0),
            flexShrink(0),
            boxSizingBorderBox(),
            background(CirrusTextures.Background.flat)
        );
        if (!this.title.getString().isBlank()) {
            addWidget(new CirrusFittedTextWidget(this.title, textPrimary).textPadding(1, 1))
                .useStyle(heightOf(12), minWidth(0));
        }
        body = addWidget(new WidgetGroup<>());
        body.useStyle(flexColumn(), gap(3), minWidth(0));

        extension = addWidget(new WidgetGroup<>());
        extension.useStyle(flexColumn(), gap(3), minWidth(0), displayNone());
        extension.setVisible(false).setActive(false).setInteractive(false);
    }

    public Component title() {
        return title;
    }

    public WidgetGroup<Widget> body() {
        return body;
    }

    public WidgetGroup<Widget> extension() {
        return extension;
    }

    public <W extends Widget> W addContent(W widget) {
        return body.addWidget(widget);
    }

    public <W extends Widget> W addExtension(W widget) {
        W added = extension.addWidget(widget);
        extension.useStyle(displayFlex());
        extension.setVisible(true).setActive(true).setInteractive(true);
        return added;
    }
}
