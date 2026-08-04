package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Renders recipe content inside a slot without flattening it into stack variants.
 */
public class RecipeContentSlotWidget extends Widget {

    public static final int slotSize = 18;
    private static final int slotBorder = 1;
    private static final int contentSize = 16;
    private static final int cycleInterval = 20;

    private final RecipeContent content;
    private int tickCounter;
    private int lastVisualIndex = -1;
    private boolean slotBackground;
    private boolean decorated;

    public RecipeContentSlotWidget(RecipeContent content) {
        this.content = content == null ? RecipeContent.empty() : content;
        useStyle(sizeOf(slotSize, slotSize));
        updateTooltip();

        onMouseEnter((mouseX, mouseY, context) -> scene().setHoverTooltip(tooltip()));
        onMouseLeave((mouseX, mouseY, context) -> scene().setHoverTooltip(Tooltip.create()));
    }

    public RecipeContentSlotWidget slotBackground() {
        this.slotBackground = true;
        return this;
    }

    public RecipeContentSlotWidget decorated() {
        this.decorated = true;
        return this;
    }

    public boolean drawsSlotBackground() {
        return slotBackground;
    }

    public boolean rendersDecoratedContent() {
        return decorated;
    }

    @Override
    public void tick() {
        super.tick();
        int count = visualCount();
        if (count > 1) {
            tickCounter++;
            if (currentVisualIndex(count) != lastVisualIndex) {
                updateTooltip();
            }
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        if (slotBackground) {
            canvas.texture(CirrusTextures.Background.Slot.dark, 0, 0, width(), height());
        }
        if (content.isEmpty()) {
            return;
        }
        RecipeContentRenderer<RecipeContent> renderer = renderer();
        int count = renderer.visualCount(content);
        if (count <= 0) {
            return;
        }
        var graphics = canvas.layeredGraphics();
        if (decorated) {
            renderer.renderDecoratedAt(graphics, content, currentVisualIndex(count), slotBorder, slotBorder, contentSize, contentSize, partialTicks);
        } else {
            renderer.renderAt(graphics, content, currentVisualIndex(count), slotBorder, slotBorder, contentSize, contentSize, partialTicks);
        }
    }

    private void updateTooltip() {
        if (content.isEmpty()) {
            setTooltip(Tooltip.create());
            return;
        }
        RecipeContentRenderer<RecipeContent> renderer = renderer();
        int count = renderer.visualCount(content);
        if (count <= 0) {
            setTooltip(Tooltip.create());
            return;
        }
        int visualIndex = currentVisualIndex(count);
        TooltipContext context;
        try {
            context = TooltipContext.create();
        } catch (NullPointerException ignored) {
            setTooltip(Tooltip.create());
            lastVisualIndex = visualIndex;
            return;
        }
        Tooltip tooltip = renderer.tooltip(content, visualIndex, context);
        setTooltip(tooltip == null ? Tooltip.create() : tooltip);
        lastVisualIndex = visualIndex;
    }

    private int visualCount() {
        return content.isEmpty() ? 0 : renderer().visualCount(content);
    }

    private int currentVisualIndex(int visualCount) {
        if (visualCount <= 1) {
            return 0;
        }
        return (tickCounter / cycleInterval) % visualCount;
    }

    @SuppressWarnings("unchecked")
    private RecipeContentRenderer<RecipeContent> renderer() {
        return (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer.getRenderer(content);
    }
}
