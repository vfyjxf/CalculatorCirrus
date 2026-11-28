package dev.vfyjxf.cirrus.ui.workbench.projection;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable visual & text display template for rendering a {@link RecipeContent} diff entry in
 * the GAS projection pipeline and Database diff breakdown.
 *
 * <p>Different {@link RecipeContent} kinds have fundamentally different visual semantics:
 * numeric {@code ValueContent} (energy, time, heat, stress, mana...) belongs in a telemetry meter
 * with comparative bars rather than empty item slots; fluids use tank styling and {@code mB} units;
 * grid cell moves highlight origin-to-destination coordinates instead of duplicating icons;
 * tags and multi-alternative ingredients carry variant markers.
 */
public interface ProjectionDiffTemplate {

    enum Kind {
        valueMeter,
        durability,
        fluid,
        moved,
        tagOrAlternatives,
        stackSlot,
        generic
    }

    record DiffInput(
        SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        String defaultBadge
    ) {
        public @Nullable RecipeContent primaryContent() {
            return after != null ? after : before;
        }
    }

    /**
     * Deferred slot icon renderer sink so batched {@code GuiGraphics} item/fluid renders draw
     * above {@link SceneCanvas} shapes without z-fighting.
     */
    @FunctionalInterface
    interface SlotSink {
        void enqueue(RecipeContent content, int x, int y, int slotSize);
    }

    String id();

    Kind kind();

    /** Higher priority templates win during resolution; lets mods override built-in templates. */
    default int priority() {
        return 0;
    }

    boolean matches(DiffInput input);

    int accentColor(DiffInput input, CirrusPalette palette);

    String formatHeader(DiffInput input);

    String formatDetail(DiffInput input);

    String formatBadge(DiffInput input);

    String describeSingle(RecipeContent content);

    /** Width reserved for the left visual column inside a pipeline diff entry row. */
    int visualColumnWidth(DiffInput input);

    /** Minimum vertical height required by the left visual strip (excluding row padding). */
    default int minVisualHeight(DiffInput input) {
        return 18;
    }

    /** Optional extra vertical space below text lines (e.g. comparative gauge bar for ValueContent). */
    default int extraBodyHeight(DiffInput input) {
        return 0;
    }

    /** Paints the left visual strip inside a pipeline entry row. */
    void paintVisual(
        SceneCanvas canvas,
        DiffInput input,
        int x,
        int y,
        int colWidth,
        int rowHeight,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        SlotSink slotSink
    );

    /** Optional extra visualization painted beneath the text lines inside the entry body. */
    default void paintBodyExtra(
        SceneCanvas canvas,
        DiffInput input,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        WorkbenchThemeMode theme
    ) {}
}
