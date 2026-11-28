package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Renders recipe content inside a slot without flattening it into stack variants.
 */
public class RecipeContentSlotWidget extends Widget implements OreUiTooltipProvider {

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

        onMouseEnter((mouseX, mouseY, context) -> {
            if (CirrusUiTheme.shellContext(this) == null) {
                scene().setHoverTooltip(tooltip());
            }
        });
        onMouseLeave((mouseX, mouseY, context) -> {
            if (CirrusUiTheme.shellContext(this) == null) {
                scene().setHoverTooltip(Tooltip.create());
            }
        });
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        if (CirrusUiTheme.shellContext(this) != null) {
            return null;
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (content.isEmpty()) {
            return null;
        }
        return richTooltipFor(content, null);
    }

    public static @Nullable ContentStack<?, ?> previewStack(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        if (content instanceof DurabilityContent durability) {
            return previewStack(durability.ingredient());
        }
        if (content instanceof StackContent<?, ?> stackContent) {
            ContentStack<?, ?> stack = stackContent.content();
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof StackListContent listContent) {
            for (ContentStack<?, ?> stack : listContent.stacks()) {
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        if (content instanceof TypedStackListContent<?, ?> typedList) {
            for (ContentStack<?, ?> stack : typedList.stacks()) {
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        TooltipStack<?> tipStack = ExecutionOverlayContentHelper.tooltipStack(content);
        if (tipStack != null && tipStack.value() instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            return ContentStack.fromContent(itemStack.getItem(), Math.max(1L, itemStack.getCount()));
        }
        return null;
    }

    public static @Nullable OreUiRichTooltip richTooltipFor(
        @Nullable RecipeContent content,
        @Nullable String roleBadge
    ) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        if (content instanceof DurabilityContent durability) {
            return durabilityRichTooltip(durability, roleBadge);
        }
        String name = ExecutionOverlayContentHelper.label(content);
        ContentStack<?, ?> stack = previewStack(content);
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(name);
        if (stack != null) {
            builder.icon(stack);
        }
        if (roleBadge != null && !roleBadge.isBlank()) {
            OreUiRichTooltip.Tone tone = "OUTPUT".equalsIgnoreCase(roleBadge)
                    ? OreUiRichTooltip.Tone.positive
                    : OreUiRichTooltip.Tone.accent;
            builder.badge(roleBadge, tone);
        }
        if (content instanceof ValueContent<?> val) {
            builder.keyValue("Amount", NumberUtils.toSuffix(val.amount()), OreUiRichTooltip.Tone.costK);
        } else if (content.amount() > 0L) {
            builder.keyValue(
                "Amount",
                "\u00d7" + NumberUtils.toSuffix(content.amount()),
                OreUiRichTooltip.Tone.primary
            );
        }
        if (content.chance() < 0.999f) {
            int pct = Math.max(1, Math.round(content.chance() * 100f));
            builder.keyValue("Chance", pct + "%", OreUiRichTooltip.Tone.costC);
        }
        if (content instanceof TagStackContent<?> tagContent) {
            builder.keyValue("Tag", "#" + tagContent.tagKey().location(), OreUiRichTooltip.Tone.secondary);
        } else if (content instanceof StackListContent listContent && listContent.stacks().size() > 1) {
            builder.keyValue("Alternatives", listContent.stacks().size() + " items", OreUiRichTooltip.Tone.secondary);
        } else if (content instanceof TypedStackListContent<?, ?> typedList && typedList.stacks().size() > 1) {
            builder.keyValue("Alternatives", typedList.stacks().size() + " items", OreUiRichTooltip.Tone.secondary);
        }
        if (stack != null && stack.isItem()) {
            var location = stack.kind().getLocation();
            if (location != null) {
                builder.subtitle(location.toString());
            }
        }
        return builder.build();
    }

    private static OreUiRichTooltip durabilityRichTooltip(
        DurabilityContent durability,
        @Nullable String roleBadge
    ) {
        RecipeContent underlying = durability.ingredient();
        String baseName = ExecutionOverlayContentHelper.label(underlying);
        ContentStack<?, ?> stack = previewStack(underlying);
        OreUiRichTooltip.Tone durTone = durability.consumesWholeTool()
                ? OreUiRichTooltip.Tone.danger
                : OreUiRichTooltip.Tone.warning;
        String durBadge = "-" + durability.durabilityCost() + " DUR";
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(baseName)
                .badge(roleBadge == null || roleBadge.isBlank() ? durBadge : roleBadge + " " + durBadge, durTone);
        if (stack != null) {
            builder.icon(stack);
            if (stack.isItem()) {
                var location = stack.kind().getLocation();
                if (location != null) {
                    builder.subtitle(location.toString());
                }
            }
        }
        builder.keyValue(
            CirrusLangs.Content.durabilityCost.component().getString(),
            "-" + durability.durabilityCost() + " / craft",
            durTone
        );
        int maxDur = durability.maxDurability();
        if (maxDur > 0) {
            builder.keyValue(
                "Tool Life",
                durability.usesPerTool() + " crafts / tool (" + maxDur + " max)",
                OreUiRichTooltip.Tone.secondary
            );
        }
        if (durability.amount() > 1L) {
            builder.keyValue(
                "Tools Required",
                "\u00d7" + NumberUtils.toSuffix(durability.amount()),
                OreUiRichTooltip.Tone.primary
            );
        }
        if (underlying instanceof TagStackContent<?> tagContent) {
            builder.keyValue("Tag", "#" + tagContent.tagKey().location(), OreUiRichTooltip.Tone.secondary);
        } else if (underlying instanceof StackListContent listContent && listContent.stacks().size() > 1) {
            builder.keyValue("Alternatives", listContent.stacks().size() + " items", OreUiRichTooltip.Tone.secondary);
        } else if (underlying instanceof TypedStackListContent<?, ?> typedList && typedList.stacks().size() > 1) {
            builder.keyValue("Alternatives", typedList.stacks().size() + " items", OreUiRichTooltip.Tone.secondary);
        }
        builder.footer(CirrusLangs.Content.durabilityCostDesc.component(durability.durabilityCost()).getString());
        return builder.build();
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
            canvas.texture(CirrusUiTheme.textures(this).slotBase(), 0, 0, width(), height());
        }
        if (content.isEmpty()) {
            return;
        }
        if (content instanceof ValueContent<?> val) {
            ValueTypeGaugeRenderer.drawMiniEmblem(
                canvas,
                val.type().typeId().toString(),
                slotBorder,
                slotBorder,
                contentSize,
                CirrusUiTheme.palette(this),
                CirrusUiTheme.mode(this),
                1.0f
            );
            if (decorated && val.amount() > 0L) {
                OreUiSlotAmount.drawInSlot(canvas, 0, 0, width(), val.amount(), CirrusUiTheme.palette(this));
            }
            return;
        }
        RecipeContentRenderer<RecipeContent> renderer = renderer();
        int count = renderer.visualCount(content);
        if (count <= 0) {
            return;
        }
        var graphics = canvas.layeredGraphics();
        if (content instanceof DurabilityContent) {
            renderer.renderDecoratedAt(
                graphics,
                content,
                currentVisualIndex(count),
                slotBorder,
                slotBorder,
                contentSize,
                contentSize,
                partialTicks
            );
            return;
        }
        if (decorated && content.amount() <= 1L) {
            renderer.renderDecoratedAt(
                graphics,
                content,
                currentVisualIndex(count),
                slotBorder,
                slotBorder,
                contentSize,
                contentSize,
                partialTicks
            );
        } else {
            renderer.renderAt(
                graphics,
                content,
                currentVisualIndex(count),
                slotBorder,
                slotBorder,
                contentSize,
                contentSize,
                partialTicks
            );
            if (decorated && content.amount() > 1L) {
                OreUiSlotAmount.drawInSlot(canvas, 0, 0, width(), content.amount(), CirrusUiTheme.palette(this));
            }
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
