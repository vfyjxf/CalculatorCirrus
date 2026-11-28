package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.drag.DragContext;
import dev.vfyjxf.cloudlib.api.ui.drag.DragProvider;
import dev.vfyjxf.cloudlib.api.ui.drag.DraggableElement;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Alternate-recipe drawer for one resource: lists every producer in the graph, a click or drag-out pins that recipe.
 */
public class AlternativeProducersDrawerWidget extends Widget {

    public static final int rowHeight = 22;
    public static final int pad = CoPilotPaint.pad;
    public static final int maxRows = 8;

    private final CoPilotHost host;
    private @Nullable ComponentStackKey resource;
    private @Nullable CompiledRecipeGraph graph;
    private boolean persistToWorld;
    private @Nullable String dragHighlight;

    public AlternativeProducersDrawerWidget(CoPilotHost host) {
        this.host = Objects.requireNonNull(host, "host");
        useStyle(sizeOf(220, rowHeight * 3 + pad * 2), flexShrink(0));
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            CompiledRecipe recipe = recipeAt(local.x(), local.y());
            if (recipe == null) {
                return EventDispatch.pass;
            }
            pin(recipe.key());
            CirrusUiSound.playClickSound();
            context.consume();
            return EventDispatch.consumed;
        });
        onMount((scene, context, handle) -> CoPilotDnd.attachProvider(scene, this, handle, new RecipeDragProvider()));
        onMouseLeave((mouseX, mouseY, context) -> dragHighlight = null);
    }

    public AlternativeProducersDrawerWidget resource(@Nullable ComponentStackKey resource) {
        this.resource = resource;
        return this;
    }

    public AlternativeProducersDrawerWidget graph(@Nullable CompiledRecipeGraph graph) {
        this.graph = graph;
        return this;
    }

    public AlternativeProducersDrawerWidget persistToWorld(boolean persistToWorld) {
        this.persistToWorld = persistToWorld;
        return this;
    }

    public @Nullable ComponentStackKey resource() {
        return resource;
    }

    public boolean persistToWorld() {
        return persistToWorld;
    }

    public @Nullable CompiledRecipeGraph resolvedGraph() {
        if (graph != null) {
            return graph;
        }
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        return runtime == null ? null : runtime.currentGraph().orElse(null);
    }

    public List<CompiledRecipe> producers() {
        CompiledRecipeGraph resolved = resolvedGraph();
        if (resolved == null || resource == null) {
            return List.of();
        }
        List<CompiledRecipe> producers = new ArrayList<>(resolved.producersOf(resource));
        return List.copyOf(producers.subList(0, Math.min(maxRows, producers.size())));
    }

    public int rowCount() {
        return producers().size();
    }

    public Rect rowRect(int index) {
        return new Rect(pad, pad + index * rowHeight, Math.max(1, width() - pad * 2), rowHeight - 1);
    }

    public @Nullable CompiledRecipe recipeAt(double localX, double localY) {
        List<CompiledRecipe> producers = producers();
        for (int index = 0; index < producers.size(); index++) {
            if (rowRect(index).contains(localX, localY)) {
                return producers.get(index);
            }
        }
        return null;
    }

    public boolean pin(CompiledRecipeKey recipeKey) {
        if (resource == null || recipeKey == null) {
            return false;
        }
        host.refine(new PinResourceRecipeFeedback(resource, recipeKey, persistToWorld), null);
        return true;
    }

    public List<ComponentStackKey> inputPreview(CompiledRecipe recipe) {
        List<ComponentStackKey> inputs = new ArrayList<>();
        for (InputSlot slot : recipe.inputs()) {
            inputs.add(slot.candidates().get(0));
        }
        return List.copyOf(inputs);
    }

    public List<ComponentStackKey> outputPreview(CompiledRecipe recipe) {
        List<ComponentStackKey> outputs = new ArrayList<>();
        for (OutputSlot slot : recipe.outputs()) {
            outputs.add(slot.resource());
        }
        return List.copyOf(outputs);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette palette = host.palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        CoPilotPaint.card(canvas, 0, 0, width, height, palette, true, false);
        List<CompiledRecipe> producers = producers();
        if (producers.isEmpty()) {
            CoPilotPaint.text(canvas, CirrusLangs.Workbench.Action.replaceRecipe.get(), pad, pad, palette.textMuted());
            return;
        }
        for (int index = 0; index < producers.size(); index++) {
            renderRow(canvas, palette, producers.get(index), rowRect(index), mouseX, mouseY);
        }
    }

    private void renderRow(
        SceneCanvas canvas,
        CirrusPalette palette,
        CompiledRecipe recipe,
        Rect rect,
        int mouseX,
        int mouseY
    ) {
        boolean hover = rect.contains(mouseX, mouseY);
        OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hover, false);
        int cursor = rect.x() + 4;
        int iconY = rect.y() + Math.max(2, (rect.height() - 14) / 2);
        for (ComponentStackKey input : inputPreview(recipe)) {
            OreUiPaint.slot(canvas, cursor, iconY, 14, palette);
            CoPilotPaint.icon(canvas, CoPilotPaint.stackOf(input, 1L), cursor + 1, iconY + 1, 12, palette);
            cursor += 15;
        }
        cursor += 2;
        canvas.fill(cursor, rect.y() + rect.height() / 2, 5, 1, palette.textMuted());
        cursor += 7;
        for (ComponentStackKey output : outputPreview(recipe)) {
            OreUiPaint.slot(canvas, cursor, iconY, 14, palette);
            CoPilotPaint.icon(canvas, CoPilotPaint.stackOf(output, 1L), cursor + 1, iconY + 1, 12, palette);
            cursor += 15;
        }
        Component useLabel = CirrusLangs.Workbench.Copilot.useRecipe.get();
        int pillW = canvas.font().width(useLabel) + 8;
        int pillX = rect.right() - pillW - 4;
        OreUiPaint.raisedButton(
            canvas,
            pillX,
            rect.y() + 4,
            pillW,
            rect.height() - 8,
            palette,
            false,
            hover,
            true,
            false
        );
        CoPilotPaint.text(
            canvas,
            useLabel,
            pillX + 4,
            ChromeText.centeredY(rect.y() + 4, rect.height() - 9, canvas),
            palette.textOnAccent()
        );
        if (cursor + 6 < pillX) {
            String machine = recipe.machineRequirement().compatibleMachines().isEmpty()
                    ? CoPilotPaint.humanizeId(recipe.key().systemId().toString())
                    : StepNodeCardRenderer
                            .machineNameOf(recipe.machineRequirement().compatibleMachines().get(0), null).getString();
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.literal(machine), pillX - cursor - 6),
                cursor + 3,
                ChromeText.centeredY(rect.y(), rect.height(), canvas),
                palette.textSecondary()
            );
        }
    }

    final class RecipeDragProvider implements DragProvider {

        @Override
        public boolean draggable(@Nullable Scene dragScene, InputContext input, DragContext dragContext) {
            return resource != null && parent() != null && isMouseOver(input);
        }

        @Override
        public @Nullable DraggableElement<?> getDraggableElement(
            @Nullable Scene dragScene,
            InputContext input,
            DragContext dragContext
        ) {
            if (resource == null || parent() == null || !isMouseOver(input)) {
                return null;
            }
            FloatPos local = new FloatPos(
                input.mousePos().x() - absolutePos().x(),
                input.mousePos().y() - absolutePos().y()
            );
            CompiledRecipe recipe = recipeAt(local.x(), local.y());
            if (recipe == null) {
                return null;
            }
            dragHighlight = recipe.key().shortString();
            return CoPilotDnd.Carried
                    .of(AlternativeProducersDrawerWidget.this, new DragPayload.RecipeDrag(resource, recipe.key()));
        }
    }
}
