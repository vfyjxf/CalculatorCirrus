package dev.vfyjxf.calculatorcirrus.api.ui;

public record TooltipStack<T>(T value, Class<T> type) {

    @SuppressWarnings("unchecked")
    public <R> R castValue() {
        return (R) value();
    }

}
