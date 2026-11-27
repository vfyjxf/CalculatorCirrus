package dev.vfyjxf.cirrus.ui.workbench;

import org.jspecify.annotations.Nullable;

/**
 * Pure geometry: takes the viewport size and shell state, returns the pixel rectangle of every region.
 */
public record WorkbenchShellLayout(
    float width,
    float height,
    WorkbenchBreakpoint breakpoint,
    boolean zen,
    float leftDockWidth,
    float rightDockWidth,
    Rect leftRail,
    Rect sessionTabBar,
    Rect topDock,
    Rect leftDock,
    Rect stage,
    Rect rightDock,
    Rect bottomStrip,
    Rect minimap,
    Rect commandPalette,
    Rect toastStack
) {

    public static final float zenRailSize = 24f;
    public static final float zenMinStageAreaRatio = 0.92f;
    public static final float minimapInset = 10f;
    public static final float paletteMaxWidth = 640f;
    public static final float paletteMinTop = 36f;
    public static final float paletteTopRatio = 0.12f;
    public static final float paletteHeight = 240f;
    public static final float viewportMargin = 20f;
    public static final float toastWidth = 288f;
    public static final float toastRowHeight = 52f;
    public static final float toastGap = 6f;
    public static final int toastMaxVisible = 3;
    public static final float toastRightInset = 12f;
    public static final float toastBottomInset = 12f;

    public record Rect(float x, float y, float width, float height) {
        public static final Rect empty = new Rect(0f, 0f, 0f, 0f);

        public Rect {
            width = Math.max(0f, width);
            height = Math.max(0f, height);
        }

        public static Rect of(float x, float y, float width, float height) {
            return new Rect(x, y, width, height);
        }

        public float right() {
            return x + width;
        }

        public float bottom() {
            return y + height;
        }

        public float area() {
            return width * height;
        }

        public boolean empty() {
            return width <= 0f || height <= 0f;
        }

        public boolean within(float boundWidth, float boundHeight) {
            return x >= 0f && y >= 0f && right() <= boundWidth && bottom() <= boundHeight;
        }

        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }

        public boolean contains(Rect other) {
            return x <= other.x && y <= other.y && right() >= other.right() && bottom() >= other.bottom();
        }
    }

    public static WorkbenchShellLayout compute(float width, float height) {
        float w = finite(width);
        float h = finite(height);
        WorkbenchBreakpoint breakpoint = WorkbenchBreakpoint.classify(w);
        return compute(
            w,
            h,
            breakpoint,
            false,
            breakpoint.leftDockDefaultWidth(w),
            breakpoint.rightDockDefaultWidth(w)
        );
    }

    public static WorkbenchShellLayout compute(
        float width,
        float height,
        @Nullable WorkbenchBreakpoint bp,
        boolean zen,
        float leftDockWidth,
        float rightDockWidth
    ) {
        float w = finite(width);
        float h = finite(height);
        WorkbenchBreakpoint breakpoint = bp == null ? WorkbenchBreakpoint.classify(w) : bp;
        Rect palette = commandPaletteRect(w, h);
        if (zen) {
            float railSize = Math.min(zenRailSize, Math.min(w, h));
            Rect stage = new Rect(0f, 0f, w, h);
            Rect minimap = minimapRect(
                stage,
                WorkbenchBreakpoint.wide.minimapWidth(),
                WorkbenchBreakpoint.wide.minimapHeight()
            );
            return new WorkbenchShellLayout(
                w,
                h,
                breakpoint,
                true,
                0f,
                0f,
                new Rect(0f, 0f, railSize, railSize),
                Rect.empty,
                Rect.empty,
                Rect.empty,
                stage,
                Rect.empty,
                Rect.empty,
                minimap,
                palette,
                toastRect(w, h, 0f)
            );
        }
        float railWidth = Math.min(breakpoint.leftRailWidth(), w);
        float tabHeight = Math.min(breakpoint.sessionTabBarHeight(), h);
        float topHeight = Math.min(breakpoint.topDockHeight(), Math.max(0f, h - tabHeight));
        float bottomHeight = Math.min(breakpoint.bottomStripHeight(), Math.max(0f, h - tabHeight - topHeight));
        float contentY = Math.min(h, tabHeight + topHeight);
        float contentHeight = Math.max(0f, h - contentY - bottomHeight);
        Rect leftRail = new Rect(0f, 0f, railWidth, h);
        Rect sessionTabBar = new Rect(railWidth, 0f, Math.max(0f, w - railWidth), tabHeight);
        Rect topDock = new Rect(railWidth, tabHeight, Math.max(0f, w - railWidth), topHeight);
        Rect bottomStrip = new Rect(
            railWidth,
            Math.max(0f, h - bottomHeight),
            Math.max(0f, w - railWidth),
            bottomHeight
        );

        float available = Math.max(0f, w - railWidth);
        float leftRequest = leftDockWidth < 0f ? breakpoint.leftDockDefaultWidth(w) : leftDockWidth;
        float rightRequest = rightDockWidth < 0f ? breakpoint.rightDockDefaultWidth(w) : rightDockWidth;
        float left = breakpoint.showsLeftDock() && leftRequest > 0f
                ? WorkbenchBreakpoint.clampLeftDockWidth(leftRequest)
                : 0f;
        float right = breakpoint.showsRightDock() && rightRequest > 0f
                ? WorkbenchBreakpoint.clampRightDockWidth(rightRequest)
                : 0f;
        float maxDocksWidth = Math.max(0f, available - WorkbenchBreakpoint.minCenterCanvasWidth);
        if (left + right > maxDocksWidth) {
            right = Math.max(0f, maxDocksWidth - left);
        }
        if (left + right > maxDocksWidth) {
            left = Math.max(0f, maxDocksWidth);
            right = Math.max(0f, maxDocksWidth - left);
        }

        Rect leftDock = new Rect(railWidth, contentY, left, contentHeight);
        Rect rightDock = new Rect(Math.max(railWidth, w - right), contentY, right, contentHeight);
        Rect stage = new Rect(railWidth + left, contentY, Math.max(0f, available - left - right), contentHeight);
        Rect minimap = breakpoint.showsMinimap()
                ? minimapRect(stage, breakpoint.minimapWidth(), breakpoint.minimapHeight())
                : Rect.empty;
        return new WorkbenchShellLayout(
            w,
            h,
            breakpoint,
            false,
            left,
            right,
            leftRail,
            sessionTabBar,
            topDock,
            leftDock,
            stage,
            rightDock,
            bottomStrip,
            minimap,
            palette,
            toastRect(w, h, bottomHeight)
        );
    }

    /**
     * Non-planning modes hide the planning-only chrome and let the workspace fill everything below the tab bar.
     */
    public WorkbenchShellLayout workspaceOnly() {
        float railWidth = leftRail.width();
        Rect fullStage = new Rect(
            railWidth,
            sessionTabBar.height(),
            Math.max(0f, width - railWidth),
            Math.max(0f, height - sessionTabBar.height())
        );
        return new WorkbenchShellLayout(
            width,
            height,
            breakpoint,
            zen,
            0f,
            0f,
            leftRail,
            sessionTabBar,
            Rect.empty,
            Rect.empty,
            fullStage,
            Rect.empty,
            Rect.empty,
            Rect.empty,
            commandPalette,
            toastStack
        );
    }

    public float stageAreaRatio() {
        float viewportArea = width * height;
        return viewportArea <= 0f ? 0f : stage.area() / viewportArea;
    }

    public static float defaultLeftDockWidth(float width) {
        float w = finite(width);
        return WorkbenchBreakpoint.classify(w).leftDockDefaultWidth(w);
    }

    public static float defaultRightDockWidth(float width) {
        float w = finite(width);
        return WorkbenchBreakpoint.classify(w).rightDockDefaultWidth(w);
    }

    private static Rect minimapRect(Rect stage, float minimapWidth, float minimapHeight) {
        float w = Math.min(minimapWidth, stage.width());
        float h = Math.min(minimapHeight, stage.height());
        if (w <= 0f || h <= 0f) {
            return Rect.empty;
        }
        float x = Math.max(stage.x(), stage.right() - minimapInset - w);
        float maxY = Math.max(stage.y(), stage.bottom() - h);
        float y = Math.min(maxY, stage.y() + minimapInset);
        return new Rect(x, y, w, h);
    }

    private static Rect commandPaletteRect(float width, float height) {
        float w = Math.min(paletteMaxWidth, Math.max(0f, width - viewportMargin * 2f));
        float minTop = height < 240f ? Math.min(paletteMinTop, Math.max(6f, height * 0.10f)) : paletteMinTop;
        float y = Math.min(height, Math.max(minTop, paletteTopRatio * height));
        float h = Math.min(paletteHeight, Math.max(0f, height - y - 8f));
        float x = Math.max(0f, (width - w) / 2f);
        return new Rect(x, y, Math.min(w, Math.max(0f, width - x)), h);
    }

    private static Rect toastRect(float width, float height, float bottomHeight) {
        float w = Math.min(toastWidth, Math.max(0f, width - toastRightInset * 2f));
        float h = toastMaxVisible * toastRowHeight + (toastMaxVisible - 1) * toastGap;
        float clampedH = Math.min(h, Math.max(0f, height - toastBottomInset * 2f));
        float x = Math.max(0f, width - toastRightInset - w);
        float y = Math.max(0f, height - toastBottomInset - clampedH);
        return new Rect(x, y, Math.min(w, Math.max(0f, width - x)), clampedH);
    }

    private static float finite(float value) {
        return Float.isFinite(value) && value > 0f ? value : 0f;
    }
}
