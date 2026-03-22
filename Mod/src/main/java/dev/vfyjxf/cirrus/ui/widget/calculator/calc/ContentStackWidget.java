package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Renders a content stack icon inside a slot.
 * <p>
 * Supports cycling through multiple variants when the content has more than one
 * possible stack (e.g. {@link dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent},
 * {@link dev.vfyjxf.cirrus.api.recipe.content.StackListContent},
 * {@link dev.vfyjxf.cirrus.api.recipe.content.TagStackContent}).
 * Each variant is displayed for {@link #CYCLE_INTERVAL} ticks before advancing.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ContentStackWidget extends Widget {

    static final int SLOT_BORDER = 1;
    static final int CONTENT_SIZE = 16;
    static final int slotSize = CONTENT_SIZE + SLOT_BORDER * 2; // 18

    /**
     * Number of ticks each variant is shown before cycling to the next.
     * 20 ticks ≈ 1 second.
     */
    private static final int CYCLE_INTERVAL = 20;

    private final ImmutableList<ContentStack<?, ?>> stacks;
    private int tickCounter = 0;
    private int lastDisplayedIndex = -1;

    /**
     * Single-stack constructor.
     */
    ContentStackWidget(ContentStack<?, ?> stack) {
        this(Lists.immutable.of(stack));
    }

    /**
     * Multi-variant constructor — cycles through the given stacks.
     */
    ContentStackWidget(ImmutableList<ContentStack<?, ?>> stacks) {
        this.stacks = stacks.reject(ContentStack::isEmpty);
        useStyle(sizeOf(slotSize, slotSize));
        updateTooltip();

        onMouseEnter((mouseX, mouseY, context) -> {
            scene().setHoverTooltip(tooltip());
        });

        onMouseLeave((mouseX, mouseY, context) -> {
            scene().setHoverTooltip(Tooltip.create());
        });
    }

    /**
     * Creates a ContentStackWidget from a {@link RecipeContent}.
     * <p>
     * If the content is a single {@link StackContent}, renders that stack.
     * If it's an enumerable {@link InputContent} (tag, typed list, etc.),
     * collects all variant stacks for cycling display.
     */
    static ContentStackWidget fromContent(RecipeContent content) {
        if (content instanceof StackContent<?, ?> sc) {
            return new ContentStackWidget(sc.content());
        }
        if (content instanceof InputContent input && input.isEnumerable()) {
            MutableList<ContentStack<?, ?>> variants = Lists.mutable.empty();
            for (ContentVariant v : input.enumerate()) {
                if (v instanceof ContentStack<?, ?> cs && cs.notEmpty()) {
                    variants.add(cs.normalize());
                }
            }
            if (variants.notEmpty()) {
                return new ContentStackWidget(variants.toImmutable());
            }
        }
        // fallback: empty
        return new ContentStackWidget(ContentStack.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (stacks.size() > 1) {
            tickCounter++;
            int currentIndex = (tickCounter / CYCLE_INTERVAL) % stacks.size();
            if (currentIndex != lastDisplayedIndex) {
                updateTooltip();
            }
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        var current = currentStack();
        if (current.notEmpty()) {
            var renderer = rendererFor(current);
            canvas.renderLayered(g -> {
                g.pose().pushPose();
                g.pose().translate(SLOT_BORDER, SLOT_BORDER, 0);
                renderer.renderBase(g, current, CONTENT_SIZE, CONTENT_SIZE, partialTicks);
                g.pose().popPose();
            });
        }
    }

    /**
     * Updates the hover tooltip to reflect the currently displayed stack.
     */
    private void updateTooltip() {
        var current = currentStack();
        if (current.isEmpty()) return;
        var renderer = rendererFor(current);
        var tooltipContext = TooltipContext.create();
        Tooltip tooltip = renderer.getTooltip(current.value(), tooltipContext);
        tooltip.setStack(TooltipStack.of(current, (stack) -> stack.cheatsStack().value()));
        setTooltip(tooltip);
        lastDisplayedIndex = stacks.isEmpty() ? -1 : (tickCounter / CYCLE_INTERVAL) % stacks.size();
    }

    private ContentStack<?, ?> currentStack() {
        int index = (tickCounter / CYCLE_INTERVAL) % stacks.size();
        return stacks.get(index);
    }

    @SuppressWarnings({"rawtypes"})
    private static StackRenderer rendererFor(ContentStack<?, ?> stack) {
        return StackRenderer.getStackRender(stack.type());
    }
}
