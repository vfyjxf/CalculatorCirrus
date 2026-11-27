package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisual;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisualCache;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Compact slot strip for recipe previews that can expose more than one output.
 */
public class RecipeContentSlotsWidget extends Widget implements OreUiTooltipProvider {

    public static final int slotSize = RecipeContentSlotWidget.slotSize;
    public static final int slotBorder = 1;
    public static final int contentSize = 16;
    public static final int defaultGap = 1;
    private static final int overflowHitSize = 9;
    private static final int overflowPreviewLimit = 5;

    private final List<? extends RecipeContent> contents;
    private int limit;
    private int gap = defaultGap;
    private boolean slotBackground;
    private boolean decorated;
    private final RecipeContentVisualCache visualCache = RecipeContentVisualCache.create();

    public RecipeContentSlotsWidget(List<? extends RecipeContent> contents) {
        this.contents = List.copyOf(contents);
        this.limit = this.contents.size();
        updateSize();
    }

    public RecipeContentSlotsWidget limit(int limit) {
        this.limit = Math.max(0, limit);
        updateSize();
        return this;
    }

    public RecipeContentSlotsWidget gap(int gap) {
        this.gap = Math.max(0, gap);
        updateSize();
        return this;
    }

    public RecipeContentSlotsWidget slotBackground() {
        this.slotBackground = true;
        return this;
    }

    public RecipeContentSlotsWidget decorated() {
        this.decorated = true;
        return this;
    }

    public int visibleSlotCount() {
        return visibleSlotCount(contents.size(), limit);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        renderBackgrounds(canvas, this, contents, 0, 0, limit, gap, slotBackground);
        BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch
                .create(canvas.layeredGraphics(), partialTicks);
        submitContents(batch, visualCache, contents, 0, 0, limit, gap, decorated);
        batch.flush();
        renderOverflowIndicator(canvas, contents, 0, 0, limit, gap);
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        if (CirrusUiTheme.shellContext(this) != null) {
            return null;
        }
        Tooltip tooltip = tooltipAt(contents, mouseX, mouseY, limit, gap);
        return tooltip == null ? super.hoverTooltip(mouseX, mouseY) : tooltip;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        int index = slotIndexAt(localX, localY, contents.size(), limit, gap);
        if (index < 0 || index >= contents.size()) {
            return null;
        }
        return RecipeContentSlotWidget.richTooltipFor(contents.get(index), null);
    }

    public static int visibleSlotCount(int count, int limit) {
        return Math.max(0, Math.min(Math.max(0, count), Math.max(0, limit)));
    }

    public static int stripWidth(int count, int limit, int gap) {
        int visible = visibleSlotCount(count, limit);
        if (visible == 0) {
            return 0;
        }
        return visible * slotSize + Math.max(0, visible - 1) * Math.max(0, gap);
    }

    public static int slotIndexAt(double localX, double localY, int count, int limit, int gap) {
        int visible = visibleSlotCount(count, limit);
        if (visible <= 0 || localY < 0 || localY >= slotSize || localX < 0) {
            return -1;
        }
        int stride = slotSize + Math.max(0, gap);
        int index = (int) (localX / stride);
        if (index < 0 || index >= visible) {
            return -1;
        }
        double xInSlot = localX - index * stride;
        return xInSlot >= 0 && xInSlot < slotSize ? index : -1;
    }

    public static void renderBackgrounds(
        SceneCanvas canvas,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap,
        boolean slotBackground
    ) {
        renderBackgrounds(canvas, CirrusTextures.Background.Slot.base, contents, x, y, limit, gap, slotBackground);
    }

    public static void renderBackgrounds(
        SceneCanvas canvas,
        @Nullable Widget widget,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap,
        boolean slotBackground
    ) {
        renderBackgrounds(
            canvas,
            CirrusUiTheme.textures(widget).slotBase(),
            contents,
            x,
            y,
            limit,
            gap,
            slotBackground
        );
    }

    private static void renderBackgrounds(
        SceneCanvas canvas,
        VisualTexture slotTexture,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap,
        boolean slotBackground
    ) {
        if (!slotBackground) {
            return;
        }
        int visible = visibleSlotCount(contents.size(), limit);
        int stride = slotSize + Math.max(0, gap);
        for (int i = 0; i < visible; i++) {
            canvas.texture(slotTexture, x + i * stride, y, slotSize, slotSize);
        }
    }

    public static void submitContents(
        BatchableRecipeContentRenderer.Batch batch,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap,
        boolean decorated
    ) {
        submitContents(batch, RecipeContentVisualCache.create(), contents, x, y, limit, gap, decorated);
    }

    public static void submitContents(
        BatchableRecipeContentRenderer.Batch batch,
        RecipeContentVisualCache visualCache,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap,
        boolean decorated
    ) {
        int visible = visibleSlotCount(contents.size(), limit);
        int stride = slotSize + Math.max(0, gap);
        for (int i = 0; i < visible; i++) {
            RecipeContent content = contents.get(i);
            if (content.isEmpty()) {
                continue;
            }
            RecipeContentVisual visual = visualCache.visual(content);
            if (visual.isEmpty()) {
                continue;
            }
            int slotX = x + i * stride;
            if (decorated) {
                batch.addVisual(visual, slotX + slotBorder, y + slotBorder, contentSize, contentSize);
            } else {
                batch.addBaseVisual(visual, slotX + slotBorder, y + slotBorder, contentSize, contentSize);
            }
        }
    }

    public static void submitVisuals(
        BatchableRecipeContentRenderer.Batch batch,
        List<RecipeContentVisual> visuals,
        int x,
        int y,
        int limit,
        int gap
    ) {
        int visible = visibleSlotCount(visuals.size(), limit);
        int stride = slotSize + Math.max(0, gap);
        for (int i = 0; i < visible; i++) {
            RecipeContentVisual visual = visuals.get(i);
            if (visual.notEmpty()) {
                batch.addVisual(visual, x + i * stride + slotBorder, y + slotBorder, contentSize, contentSize);
            }
        }
    }

    public static void renderStrip(
        SceneCanvas canvas,
        BatchableRecipeContentRenderer.Batch batch,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap,
        boolean slotBackground,
        boolean decorated
    ) {
        renderBackgrounds(canvas, contents, x, y, limit, gap, slotBackground);
        submitContents(batch, contents, x, y, limit, gap, decorated);
    }

    public static void renderOverflowIndicator(
        SceneCanvas canvas,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap
    ) {
        int visible = visibleSlotCount(contents.size(), limit);
        int hidden = Math.max(0, contents.size() - visible);
        if (visible <= 0 || hidden <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String label = "+" + NumberUtils.toSuffix(hidden);
        int slotX = x + (visible - 1) * (slotSize + Math.max(0, gap));
        int textX = slotX + slotSize - 1 - font.width(label);
        int textY = y + slotSize - 9;
        canvas.textBatch(text -> text.drawString(label, textX, textY, 0xFFFFFFFF, true));
    }

    public static void drawOverflowIndicator(
        SceneCanvas.TextBatch text,
        Font font,
        List<? extends RecipeContent> contents,
        int x,
        int y,
        int limit,
        int gap
    ) {
        int visible = visibleSlotCount(contents.size(), limit);
        int hidden = Math.max(0, contents.size() - visible);
        if (visible <= 0 || hidden <= 0) {
            return;
        }
        String label = "+" + NumberUtils.toSuffix(hidden);
        int slotX = x + (visible - 1) * (slotSize + Math.max(0, gap));
        text.drawString(label, slotX + slotSize - 1 - font.width(label), y + slotSize - 9, 0xFFFFFFFF, true);
    }

    public static @Nullable Tooltip tooltipAt(
        List<? extends RecipeContent> contents,
        double localX,
        double localY,
        int limit,
        int gap
    ) {
        int index = slotIndexAt(localX, localY, contents.size(), limit, gap);
        if (index < 0) {
            return null;
        }
        if (isOverflowIndicatorHit(localX, localY, contents.size(), limit, gap)) {
            return overflowTooltip(contents, limit);
        }
        RecipeContent content = contents.get(index);
        if (content.isEmpty()) {
            return null;
        }
        int visualCount = renderer(content).visualCount(content);
        int visualIndex = visualIndex(visualCount);
        Tooltip tooltip = contentTooltip(content, visualIndex);
        return tooltip == null ? fallbackTooltip(content) : tooltip;
    }

    public static @Nullable Tooltip overflowTooltip(List<? extends RecipeContent> contents, int limit) {
        int visible = visibleSlotCount(contents.size(), limit);
        int hidden = Math.max(0, contents.size() - visible);
        if (hidden <= 0) {
            return null;
        }
        Tooltip tooltip = Tooltip.create();
        tooltip.add(CirrusLangs.Calculator.Database.contentsMore.component(hidden));
        int end = Math.min(contents.size(), visible + overflowPreviewLimit);
        for (int i = visible; i < end; i++) {
            RecipeContent content = contents.get(i);
            if (content.isEmpty()) {
                continue;
            }
            tooltip.add(Component.literal("- ").append(contentName(content)));
        }
        int remaining = contents.size() - end;
        if (remaining > 0) {
            tooltip.add(CirrusLangs.Calculator.Database.contentsMore.component(remaining));
        }
        return tooltip;
    }

    private void updateSize() {
        useStyle(sizeOf(stripWidth(contents.size(), limit, gap), slotSize));
    }

    private static boolean isOverflowIndicatorHit(double localX, double localY, int count, int limit, int gap) {
        int visible = visibleSlotCount(count, limit);
        if (visible <= 0 || count <= visible) {
            return false;
        }
        int slotX = (visible - 1) * (slotSize + Math.max(0, gap));
        return localX >= slotX + slotSize - overflowHitSize
                && localX < slotX + slotSize
                && localY >= slotSize - overflowHitSize
                && localY < slotSize;
    }

    private static @Nullable Tooltip contentTooltip(RecipeContent content, int visualIndex) {
        try {
            return renderer(content).tooltip(content, Math.max(0, visualIndex), TooltipContext.create());
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    private static Tooltip fallbackTooltip(RecipeContent content) {
        return Tooltip.create().add(contentName(content));
    }

    private static Component contentName(RecipeContent content) {
        Component name = renderer(content).getName(content);
        return name == null ? Component.empty() : name;
    }

    private static int visualIndex(int visualCount) {
        if (visualCount <= 1) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() / 1000L) % visualCount);
    }

    @SuppressWarnings("unchecked")
    private static RecipeContentRenderer<RecipeContent> renderer(RecipeContent content) {
        return (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer.getRenderer(content);
    }
}
