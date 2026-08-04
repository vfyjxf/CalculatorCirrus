package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollDirection;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;

public final class CirrusScroll {
    private static final int defaultLongContentScrollSpeed = 12;

    private CirrusScroll() {
    }

    public static ScrollState longVerticalState() {
        return longVerticalState(defaultLongContentScrollSpeed);
    }

    public static ScrollState longVerticalState(int scrollSpeed) {
        return ScrollState.create(ScrollDirection.vertical)
            .scrollSpeed(scrollSpeed)
            .longContentWheelAcceleration();
    }
}
