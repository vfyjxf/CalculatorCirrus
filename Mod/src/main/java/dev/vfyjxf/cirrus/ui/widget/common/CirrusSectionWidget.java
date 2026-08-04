package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textSecondary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;

public class CirrusSectionWidget extends CompositeWidget<Widget> {
    public enum Chrome {
        outlinedInset,
        outlinedFlat
    }

    private final Component title;
    private final CirrusFittedTextWidget titleWidget;
    private final WidgetGroup<Widget> body;
    private Chrome chrome = Chrome.outlinedInset;
    private boolean collapsed;

    public CirrusSectionWidget(Component title) {
        this.title = title == null ? Component.empty() : title;
        useStyle(
            flexColumn(),
            minWidth(0),
            minHeight(0),
            gap(3),
            padding(4),
            boxSizingBorderBox()
        );
        chrome(Chrome.outlinedInset);
        titleWidget = addWidget(new CirrusFittedTextWidget(this.title, textPrimary));
        titleWidget.useStyle(heightOf(10), minWidth(0));
        body = addWidget(new WidgetGroup<>());
        body.useStyle(flexColumn(), minWidth(0), minHeight(0), gap(3));
    }

    public CirrusSectionWidget(LangEntry title, Object... args) {
        this(title == null ? Component.empty() : title.component(args));
    }

    public Component title() {
        return title;
    }

    public WidgetGroup<Widget> body() {
        return body;
    }

    public CirrusFittedTextWidget titleWidget() {
        return titleWidget;
    }

    public Chrome chrome() {
        return chrome;
    }

    public CirrusSectionWidget chrome(Chrome chrome) {
        this.chrome = chrome == null ? Chrome.outlinedInset : chrome;
        useStyle(background(switch (this.chrome) {
            case outlinedFlat -> CirrusTextures.Background.Outlined.flat;
            case outlinedInset -> CirrusTextures.Background.Outlined.inset;
        }));
        return this;
    }

    public boolean collapsed() {
        return collapsed;
    }

    public CirrusSectionWidget collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        body.setVisible(!collapsed);
        return this;
    }

    public <W extends Widget> W addContent(W widget) {
        return body.addWidget(widget);
    }

    public CirrusSectionWidget description(Component text) {
        if (text != null && !text.getString().isBlank()) {
            body.addWidget(new CirrusFittedTextWidget(text, textSecondary)).useStyle(heightOf(10), minWidth(0));
        }
        return this;
    }

    public CirrusSectionWidget description(LangEntry text, Object... args) {
        return description(text == null ? Component.empty() : text.component(args));
    }
}
