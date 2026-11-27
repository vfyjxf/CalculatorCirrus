package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * Default workspace source: the database page wires the real {@link DatabasePage}; the other workspaces keep placeholder widgets until a later work package replaces them.
 */
public final class DefaultWorkspaceProvider implements WorkspaceProvider {

    private final Supplier<Widget> databaseWorkspaceFactory;

    public DefaultWorkspaceProvider() {
        this(DatabasePage::new);
    }

    public DefaultWorkspaceProvider(Supplier<Widget> databaseWorkspaceFactory) {
        this.databaseWorkspaceFactory = Objects.requireNonNull(databaseWorkspaceFactory, "databaseWorkspaceFactory");
    }

    @Override
    public Widget plannerWorkspace(WorkbenchShellContext ctx) {
        return new WorkspacePlaceholder(ctx, 0);
    }

    @Override
    public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
        return new WorkspacePlaceholder(ctx, 1);
    }

    @Override
    public Widget settingsWorkspace(WorkbenchShellContext ctx) {
        return new WorkspacePlaceholder(ctx, 2);
    }

    @Override
    public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
        return databaseWorkspaceFactory.get();
    }

    private static final class WorkspacePlaceholder extends WidgetGroup<Widget> {
        private final WorkbenchShellContext context;
        private final int variant;

        private WorkspacePlaceholder(WorkbenchShellContext context, int variant) {
            this.context = Objects.requireNonNull(context, "context");
            this.variant = variant;
            useStyle(sizeFull(), flexColumn(), minWidth(0), minHeight(0));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            int w = width();
            int h = height();
            if (w <= 0 || h <= 0) {
                return;
            }
            CirrusPalette palette = context.palette();
            canvas.fill(0, 0, w, h, palette.canvasVoid());
            WorkbenchPaint.dotGrid(canvas, w, h, 1f, palette);
            canvas.fill(0, 0, Math.min(3, w), h, accent(palette));
        }

        private int accent(CirrusPalette palette) {
            return switch (variant) {
                case 1 -> palette.costP();
                case 2 -> palette.costS();
                default -> palette.focusRing();
            };
        }
    }
}
