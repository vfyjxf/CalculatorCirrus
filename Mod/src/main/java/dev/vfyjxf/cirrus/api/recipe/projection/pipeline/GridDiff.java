package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * First-class 2D grid diff (e.g., 3x3 crafting grid or 3x1 smithing grid) tracking every cell's
 * before/after content, diff status, and source effects.
 */
public record GridDiff(
    SlotChannel channel,
    GridSpec spec,
    ImmutableList<CellDiff> cells
) {
    public GridDiff {
        Checks.checkNotNull(channel, "channel");
        Checks.checkNotNull(spec, "spec");
        Checks.checkNotNull(cells, "cells");
        Checks.checkArgument(cells.size() == spec.cellCount(), "cells size must equal spec.cellCount()");
    }

    public CellDiff cell(int col, int row) {
        return cells.get(spec.indexOf(col, row));
    }

    public CellDiff cellAtIndex(int index) {
        return cells.get(index);
    }

    public List<CellDiff> changedCells() {
        List<CellDiff> changed = new ArrayList<>();
        for (CellDiff cell : cells) {
            if (cell.isChanged()) {
                changed.add(cell);
            }
        }
        return List.copyOf(changed);
    }

    public boolean hasChanges() {
        for (CellDiff cell : cells) {
            if (cell.isChanged()) {
                return true;
            }
        }
        return false;
    }

    public record CellDiff(
        int col,
        int row,
        int index,
        SlotAddress.GridCell address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        ImmutableList<Namespace> touchedByEffects,
        ImmutableList<ProjectionPlugin> touchedByPlugins
    ) {
        public static CellDiff empty(SlotChannel channel, int col, int row, int width) {
            SlotAddress.GridCell addr = SlotAddress.grid(channel, col, row, width);
            return new CellDiff(
                col,
                row,
                addr.index(),
                addr,
                SlotDiff.DiffStatus.unchanged,
                null,
                null,
                null,
                Lists.immutable.empty(),
                Lists.immutable.empty()
            );
        }

        public static CellDiff fromSlotDiff(SlotAddress.GridCell addr, SlotDiff diff) {
            return new CellDiff(
                addr.col(),
                addr.row(),
                addr.index(),
                addr,
                diff.status(),
                diff.before(),
                diff.after(),
                diff.movedFrom(),
                diff.touchedByEffects(),
                diff.touchedByPlugins()
            );
        }

        public boolean isChanged() {
            return status.isChanged();
        }

        public boolean isOccupied() {
            return after != null || before != null;
        }

        public SlotDiff toSlotDiff() {
            return new SlotDiff(address, status, before, after, movedFrom, touchedByEffects, touchedByPlugins);
        }

        public String deltaBadge() {
            return toSlotDiff().deltaBadge();
        }
    }
}
