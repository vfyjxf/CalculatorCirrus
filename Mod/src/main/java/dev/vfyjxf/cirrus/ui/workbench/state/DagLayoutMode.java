package dev.vfyjxf.cirrus.ui.workbench.state;

public enum DagLayoutMode {
    horizontalLr, verticalTb, horizontalRl, verticalBt, systemSwimlane;

    public boolean isVertical() {
        return this == verticalTb || this == verticalBt;
    }

    public boolean isReversed() {
        return this == horizontalRl || this == verticalBt;
    }
}
