package dev.vfyjxf.cirrus.ui.workbench;

import org.jspecify.annotations.Nullable;

/**
 * The four logical screen-width breakpoints and their pixel sizes (02 §3 is the only source of these numbers).
 */
public enum WorkbenchBreakpoint {
    wide(40f, 28f, 24f, 34f, 28f, 140f, 80f),
    standard(32f, 24f, 22f, 30f, 24f, 104f, 60f),
    compact(
        26f,
        20f,
        0f,
        28f,
        22f,
        0f,
        0f
    );

    public static final float wideMinWidth = 720f;
    public static final float standardMinWidth = 520f;
    public static final float hysteresisPx = 4f;

    public static final float leftDockMin = 176f;
    public static final float leftDockMax = 320f;
    public static final float rightDockMin = 210f;
    public static final float rightDockMax = 380f;
    public static final float minCenterCanvasWidth = 220f;

    private static final float wideLeftDockDefault = 228f;
    private static final float wideRightDockMin = 248f;
    private static final float wideRightDockRatio = 0.26f;
    private static final float wideRightDockMax = 310f;
    private static final float standardRightDockDefault = 214f;
    private static final float standardRightDockMin = 180f;
    private static final float standardRightDockMax = 260f;

    private final float leftRailWidth;
    private final float modeRailIconSize;
    private final float sessionTabBarHeight;
    private final float topDockHeight;
    private final float bottomStripHeight;
    private final float minimapWidth;
    private final float minimapHeight;

    WorkbenchBreakpoint(
        float leftRailWidth,
        float modeRailIconSize,
        float sessionTabBarHeight,
        float topDockHeight,
        float bottomStripHeight,
        float minimapWidth,
        float minimapHeight
    ) {
        this.leftRailWidth = leftRailWidth;
        this.modeRailIconSize = modeRailIconSize;
        this.sessionTabBarHeight = sessionTabBarHeight;
        this.topDockHeight = topDockHeight;
        this.bottomStripHeight = bottomStripHeight;
        this.minimapWidth = minimapWidth;
        this.minimapHeight = minimapHeight;
    }

    public static WorkbenchBreakpoint classify(float width) {
        if (width >= wideMinWidth) {
            return wide;
        }
        if (width >= standardMinWidth) {
            return standard;
        }
        return compact;
    }

    /** A tier changes only after crossing its boundary by 4px, so resizing near a boundary does not flicker. */
    public static WorkbenchBreakpoint classify(float width, @Nullable WorkbenchBreakpoint previous) {
        if (previous == null) {
            return classify(width);
        }
        WorkbenchBreakpoint exact = classify(width);
        WorkbenchBreakpoint current = previous;
        while (current != exact) {
            boolean widening = current.ordinal() > exact.ordinal();
            WorkbenchBreakpoint next = values()[widening ? current.ordinal() - 1 : current.ordinal() + 1];
            float boundary = widening ? next.minWidth() : current.minWidth();
            if (widening ? width < boundary + hysteresisPx : width > boundary - hysteresisPx) {
                return current;
            }
            current = next;
        }
        return current;
    }

    public float leftRailWidth() {
        return leftRailWidth;
    }

    public float modeRailIconSize() {
        return modeRailIconSize;
    }

    public float sessionTabBarHeight() {
        return sessionTabBarHeight;
    }

    public float topDockHeight() {
        return topDockHeight;
    }

    public float bottomStripHeight() {
        return bottomStripHeight;
    }

    public float leftDockDefaultWidth(float screenWidth) {
        return switch (this) {
            case wide -> clamp(wideLeftDockDefault, leftDockMin, leftDockMax);
            case standard, compact -> 0f;
        };
    }

    public float rightDockDefaultWidth(float screenWidth) {
        return switch (this) {
            case wide -> clamp(wideRightDockRatio * screenWidth, wideRightDockMin, wideRightDockMax);
            case standard -> clamp(standardRightDockDefault, standardRightDockMin, standardRightDockMax);
            case compact -> 0f;
        };
    }

    public float minimapWidth() {
        return minimapWidth;
    }

    public float minimapHeight() {
        return minimapHeight;
    }

    public boolean showsLeftDock() {
        return this == wide;
    }

    public boolean showsRightDock() {
        return this != compact;
    }

    public boolean showsMinimap() {
        return this != compact;
    }

    public static float clampLeftDockWidth(float width) {
        return clamp(width, leftDockMin, leftDockMax);
    }

    public static float clampRightDockWidth(float width) {
        return clamp(width, rightDockMin, rightDockMax);
    }

    /** Compresses the right dock island to its lower bound; below 220px it returns that bound and leaves collapsing to the resizer. */
    public static float clampDockWidthForCanvas(float left, float right, float screenWidth, float railWidth) {
        float allowed = screenWidth - railWidth - Math.max(0f, left) - minCenterCanvasWidth;
        return Math.max(rightDockMin, Math.min(clampRightDockWidth(right), allowed));
    }

    private float minWidth() {
        return switch (this) {
            case wide -> wideMinWidth;
            case standard -> standardMinWidth;
            case compact -> 0f;
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
