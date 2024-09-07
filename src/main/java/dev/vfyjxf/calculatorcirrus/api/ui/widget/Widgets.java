package dev.vfyjxf.calculatorcirrus.api.ui.widget;

public final class Widgets {

    public static Widget widget() {
        return new WidgetFrame();
    }

    public static <T extends Widget> WidgetGroup<T> widgetGroup() {
        return new WidgetGroupFrame<>();
    }

    private static class WidgetFrame extends Widget {

    }

    private static class WidgetGroupFrame<T extends Widget> extends WidgetGroup<T> {

    }

    private Widgets() {
    }
}
