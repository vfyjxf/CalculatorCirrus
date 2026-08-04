package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;

public class ExecutionWidgetGroup extends CompositeWidget<Widget> {
    //region child

    public <W extends Widget> W append(W widget) {
        return addWidget(widget);
    }
    //endregion
}

