package dev.vfyjxf.cirrus.ui.widget.content;

import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.ui.widget.content.StackGrid.GridCell;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayGrid;

/**
 * Batchable grid for rendering stacks.
 * It does not handle properties such as the cell's zIndex to facilitate better batching
 */
public class StackGrid extends CompositeWidget<GridCell<?, ?>> {

    private MutableList<Pair<StackType<?, ?>, MutableList<GridCell<?, ?>>>> stackCells = MutableLists
            .withInitialCapacity(0);

    public StackGrid() {
        useStyle(displayGrid());
    }

    @Override
    protected void renderChildren(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        //region grouping
        if (scene().layoutTree().needsVisit(nodeId()) || stackCells == null) {//sort and group cells
            MutableListMultimap<? extends StackType<?, ?>, GridCell<?, ?>> grouped = children()
                    .groupBy(cell -> cell.stack.type());
            stackCells = MutableLists.withInitialCapacity(grouped.size());
            grouped.forEachKeyMutableList((type, cells) -> stackCells.add(Pair.of(type, cells)));
            stackCells.sort(Comparator.comparingInt(p -> p.getRight().size()));
        }
        //endregion
        for (var cells : stackCells) {
            StackType<?, ?> type = cells.getKey();
            MutableList<GridCell<?, ?>> stacks = cells.getValue();
            throw new UnsupportedOperationException("Not Implemented");
        }
    }

    static class GridCell<T, S> extends CompositeWidget<Widget> {
        final ContentStack<T, S> stack;
        @Nullable
        StackRenderer<T, S> renderer;

        GridCell(ContentStack<T, S> stack) {
            this.stack = stack;
        }
    }
}
