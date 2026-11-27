package dev.vfyjxf.cirrus.internal.integration.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.planning.bridge.StorageInventorySnapshotAdapter;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.flow.MacTrackpadPinchBridge;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interactive OreUI JEI recipe category for bookmarked Cirrus production flows.
 */
public final class PlanFlowRecipeCategory implements IRecipeCategory<PlanFlowRecipe> {

    public static final RecipeType<PlanFlowRecipe> recipeType = RecipeType
            .create(CirrusConstants.modId, "plan_flow", PlanFlowRecipe.class);

    public static final int width = 276;
    public static final int height = 212;

    // JEI draws recipeBackground / recipePreviewBackground at getRectWithBorder() = area.expandBy(4).
    private static final int jeiBorderCover = 4;
    private static final int frameInset = 2;

    private static final int padX = 2;
    private static final int headerH = 22;
    private static final int targetSlotX = 3;
    private static final int targetSlotY = 0;
    private static final int targetSlotSize = 18;
    private static final int hdrBtnY = 0;
    private static final int hdrBtnSize = 18;
    private static final int execBtnX = width - 4 - hdrBtnSize;
    private static final int calcBtnX = execBtnX - 3 - hdrBtnSize;
    private static final int multPlusW = 12;
    private static final int multReadoutW = 20;
    private static final int multMinusW = 12;
    private static final int multTotalW = multMinusW + multReadoutW + multPlusW;
    private static final int multX = calcBtnX - 4 - multTotalW;

    private static final int tabY = 23;
    private static final int tabH = 16;
    private static final int tabGap = 4;
    private static final int tabW = (width - padX * 2 - tabGap) / 2;

    private static final int bodyX = padX;
    private static final int bodyY = 41;
    private static final int bodyW = width - padX * 2;
    private static final int bodyH = 98;
    private static final int dagAreaX = bodyX + 1;
    private static final int dagAreaY = bodyY + 1;
    private static final int dagAreaW = bodyW - 2;
    private static final int dagCanvasH = bodyH - 2;
    private static final int dagCtrlSize = 16;
    private static final int dagSelectedBarH = 19;

    private static final int dockX = padX;
    private static final int dockY = 142;
    private static final int dockW = width - padX * 2;
    private static final int dockH = 68;
    private static final int dockHeaderH = 18;
    private static final int dockMenuBtnW = 28;
    private static final int dockMenuBtnH = 16;
    private static final int dockViewY = dockY + 1 + dockHeaderH + 1;
    private static final int dockViewH = dockY + dockH - 1 - dockViewY;
    private static final int dockScrollX = dockX + dockW - 7;
    private static final int dockScrollW = 5;

    private static final int gridCols = 9;
    private static final int gridTileW = 27;
    private static final int gridTileH = 22;
    private static final int gridGapX = 2;
    private static final int gridGapY = 1;
    private static final int gridPitchX = gridTileW + gridGapX;
    private static final int gridPitchY = gridTileH + gridGapY;
    private static final int gridStartX = dockX + 3;
    private static final int gridStartY = dockViewY + 1;

    private static final ResourceKey craftingTableIcon = ResourceKey.item("minecraft:crafting_table");

    private static final long activeTtlMs = 180L;

    private record RenderedBounds(
        PlanFlowInteractiveWidget widget,
        int originX,
        int originY,
        float scaleX,
        float scaleY,
        long drawnAtMs
    ) {
        boolean containsScreen(double sx, double sy) {
            double lx = toLocalX(sx);
            double ly = toLocalY(sy);
            return lx >= -jeiBorderCover
                    && lx < width + jeiBorderCover
                    && ly >= -jeiBorderCover
                    && ly < height + jeiBorderCover;
        }

        double toLocalX(double sx) {
            return (sx - originX) / Math.max(0.01f, scaleX);
        }

        double toLocalY(double sy) {
            return (sy - originY) / Math.max(0.01f, scaleY);
        }
    }

    private static final Map<PlanFlowInteractiveWidget, RenderedBounds> renderedWidgets = new IdentityHashMap<>();
    private static @Nullable RenderedBounds draggingBounds;

    private final IDrawable icon;

    public PlanFlowRecipeCategory(IGuiHelper guiHelper) {
        this.icon = JeiTargetButtonHelper.drawable(CirrusTextures.Icon.calculatorJei);
    }

    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        RenderedBounds target = findRenderedAt(event.getMouseX(), event.getMouseY());
        if (target == null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingBounds = null;
            }
            return;
        }
        double lx = target.toLocalX(event.getMouseX());
        double ly = target.toLocalY(event.getMouseY());
        if (target.widget().handlePointerPress(lx, ly, button)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingBounds = target;
            }
            event.setCanceled(true);
        }
    }

    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (draggingBounds == null || event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        float sx = Math.max(0.01f, draggingBounds.scaleX());
        float sy = Math.max(0.01f, draggingBounds.scaleY());
        if (draggingBounds.widget().handlePointerDrag(event.getDragX() / sx, event.getDragY() / sy)) {
            event.setCanceled(true);
        }
    }

    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (draggingBounds == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        RenderedBounds target = draggingBounds;
        draggingBounds = null;
        double lx = target.toLocalX(event.getMouseX());
        double ly = target.toLocalY(event.getMouseY());
        if (target.widget().handlePointerRelease(lx, ly)) {
            event.setCanceled(true);
        }
    }

    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        RenderedBounds target = findRenderedAt(event.getMouseX(), event.getMouseY());
        if (target == null) {
            return;
        }
        double lx = target.toLocalX(event.getMouseX());
        double ly = target.toLocalY(event.getMouseY());
        if (target.widget().handleMouseScrolled(lx, ly, event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private static void recordRendered(
        PlanFlowInteractiveWidget widget,
        int originX,
        int originY,
        float scaleX,
        float scaleY
    ) {
        long now = System.currentTimeMillis();
        renderedWidgets.entrySet().removeIf(e -> now - e.getValue().drawnAtMs() > activeTtlMs * 2L);
        renderedWidgets.put(widget, new RenderedBounds(widget, originX, originY, scaleX, scaleY, now));
    }

    private static @Nullable RenderedBounds findRenderedAt(double screenX, double screenY) {
        long now = System.currentTimeMillis();
        RenderedBounds best = null;
        for (RenderedBounds b : renderedWidgets.values()) {
            if (now - b.drawnAtMs() <= activeTtlMs && b.containsScreen(screenX, screenY)) {
                if (best == null || b.drawnAtMs() >= best.drawnAtMs()) {
                    best = b;
                }
            }
        }
        return best;
    }

    @Override
    public RecipeType<PlanFlowRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return CirrusLangs.Calculator.Jei.PlanFlow.title.get();
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(PlanFlowRecipe recipe) {
        return recipe.registryName();
    }

    @Override
    public Codec<PlanFlowRecipe> getCodec(ICodecHelper codecHelper, IRecipeManager recipeManager) {
        return PlanFlowRecipe.codec;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PlanFlowRecipe recipe, IFocusGroup focuses) {
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = recipe.latestEntry();
        ItemStack outStack = JeiPlanBookmarkBridge.resolveDisplayStack(entry.primaryResource());
        outStack.setCount(1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, targetSlotX + 1, targetSlotY + 1).addItemStack(outStack)
                .addRichTooltipCallback((slotView, tooltip) -> {
                    tooltip.add(
                        Component.literal("\u00d7" + WorkbenchFormatters.formatAmount(entry.primaryAmount()))
                                .withStyle(ChatFormatting.AQUA)
                    );
                    double secs = entry.workSeconds() + entry.waitSeconds();
                    String stepsStr = CirrusLangs.Calculator.Jei.PlanFlow.metaSteps.get(entry.totalSteps()).getString();
                    String timeStr = secs > 0.05
                            ? WorkbenchFormatters.formatSeconds(secs)
                            : CirrusLangs.Workbench.Flow.timeInstant.get().getString();
                    tooltip.add(Component.literal(stepsStr + " \u00b7 " + timeStr).withStyle(ChatFormatting.GRAY));
                });

        // Register raw materials as invisible JEI ingredients so 'U' uses-lookup discovers bookmarked flows.
        List<ItemStack> inputStacks = new ArrayList<>();
        for (CirrusPlanBookmarkStore.MaterialLine mat : entry.rawMaterials()) {
            ItemStack inStack = JeiPlanBookmarkBridge.resolveDisplayStack(mat.resource());
            if (!inStack.isEmpty()) {
                inStack.setCount(1);
                inputStacks.add(inStack);
            }
        }
        if (!inputStacks.isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(inputStacks);
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, PlanFlowRecipe recipe, IFocusGroup focuses) {
        PlanFlowInteractiveWidget interactive = new PlanFlowInteractiveWidget(recipe);
        builder.addWidget(interactive);
        builder.addInputHandler(interactive);
    }

    @Override
    public void draw(
        PlanFlowRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics g,
        double mouseX,
        double mouseY
    ) {
        WorkbenchThemeMode theme = PlanFlowRenderHelper.activeThemeMode();
        CirrusPalette p = PlanFlowRenderHelper.activePalette(theme);
        int bgX = -jeiBorderCover;
        int bgY = -jeiBorderCover;
        int bgW = width + jeiBorderCover * 2;
        int bgH = height + jeiBorderCover * 2;
        CirrusTextures.Workbench.dockIsland(theme).render(g, bgX, bgY, bgW, bgH);
        PlanFlowRenderHelper.drawHeaderBand(
            g,
            bgX + frameInset,
            bgY + frameInset,
            bgW - frameInset * 2,
            headerH,
            p
        );
        PlanFlowRenderHelper.drawOreSlot(g, targetSlotX, targetSlotY, targetSlotSize, false, theme, p);
    }

    private static final class PlanFlowInteractiveWidget implements IRecipeWidget, IJeiInputHandler {

        private record HitAction(int x, int y, int w, int h, Runnable leftAction, @Nullable Runnable rightAction) {
            boolean contains(double mx, double my) {
                return mx >= x && mx < x + w && my >= y && my < y + h;
            }
        }

        private record TipHit(int x, int y, int w, int h, Consumer<ITooltipBuilder> builder) {
            boolean contains(double mx, double my) {
                return mx >= x && mx < x + w && my >= y && my < y + h;
            }
        }

        private record DagNodeHit(float x, float y, float w, float h, PlanFlowViewModel.DagNodeLayout node) {
            boolean contains(double mx, double my) {
                return mx >= x && mx < x + w && my >= y && my < y + h;
            }
        }

        private final PlanFlowRecipe recipe;
        private int activeTab;
        private int scaleMultiplier = 1;
        private boolean dagVertical;
        private PlanFlowViewModel.MaterialFilter dockFilter = PlanFlowViewModel.MaterialFilter.all;
        private PlanFlowViewModel.MaterialSort dockSort = PlanFlowViewModel.MaterialSort.missingFirst;
        private boolean dockListMode;
        private boolean stackUnitMode;
        private boolean dockMenuOpen;
        private @Nullable String selectedStepId;

        private int stationsScrollPx;
        private int dockScrollPx;

        private float dagZoom = 1.0f;
        private float dagPanX;
        private float dagPanY;
        private boolean dagViewportInitialized;
        private boolean dagPointerDown;
        private boolean dagDragging;
        private double dagDragAccum;
        private int cachedLayoutHash;
        private boolean cachedLayoutVertical;
        private PlanFlowViewModel.@Nullable DagLayout cachedDagLayout;

        private final List<HitAction> hits = new ArrayList<>();
        private final List<HitAction> popoverHits = new ArrayList<>();
        private final List<TipHit> tipHits = new ArrayList<>();
        private final List<TipHit> popoverTipHits = new ArrayList<>();
        private final List<DagNodeHit> dagHits = new ArrayList<>();

        private PlanFlowInteractiveWidget(PlanFlowRecipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(0, 0);
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(0, 0, width, height);
        }

        @Override
        public ScreenRectangle getArea() {
            return new ScreenRectangle(0, 0, width, height);
        }

        private PlanFlowViewModel.DagLayout dagLayoutFor(CirrusPlanBookmarkStore.PlanBookmarkEntry entry) {
            int hash = entry.steps().hashCode();
            if (cachedDagLayout == null || cachedLayoutHash != hash || cachedLayoutVertical != dagVertical) {
                cachedDagLayout = PlanFlowViewModel.computeDagLayout(entry.steps(), dagVertical);
                cachedLayoutHash = hash;
                cachedLayoutVertical = dagVertical;
                fitDagViewport(cachedDagLayout);
            } else if (!dagViewportInitialized) {
                fitDagViewport(cachedDagLayout);
            }
            return cachedDagLayout;
        }

        private void fitDagViewport(PlanFlowViewModel.DagLayout layout) {
            float sx = (float) (dagAreaW - 8) / Math.max(1, layout.worldW());
            float sy = (float) (dagCanvasH - 8) / Math.max(1, layout.worldH());
            dagZoom = clampZoom(Math.min(1.0f, Math.min(sx, sy)));
            dagPanX = (dagAreaW - layout.worldW() * dagZoom) * 0.5f;
            dagPanY = (dagCanvasH - layout.worldH() * dagZoom) * 0.5f;
            dagViewportInitialized = true;
        }

        private void zoomAroundCenter(float nextZoom) {
            zoomAt(dagAreaW * 0.5f, dagCanvasH * 0.5f, nextZoom);
        }

        private void zoomAt(float viewX, float viewY, float nextZoom) {
            float clamped = clampZoom(nextZoom);
            if (Math.abs(clamped - dagZoom) < 0.001f) {
                return;
            }
            float worldX = (viewX - dagPanX) / dagZoom;
            float worldY = (viewY - dagPanY) / dagZoom;
            dagZoom = clamped;
            dagPanX = viewX - worldX * dagZoom;
            dagPanY = viewY - worldY * dagZoom;
        }

        private void pollMacTrackpadPinch(double mouseX, double mouseY) {
            if (!isMacOs()) {
                return;
            }
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.getWindow() == null) {
                    return;
                }
                double mag = MacTrackpadPinchBridge.drainMagnification(mc.getWindow().getWindow());
                if (Math.abs(mag) > 1e-5 && hit(mouseX, mouseY, dagAreaX, dagAreaY, dagAreaW, dagCanvasH)) {
                    double clamped = Math.max(-0.5, Math.min(0.5, mag));
                    float factor = (float) Math.exp(clamped * 1.65);
                    zoomAt((float) (mouseX - dagAreaX), (float) (mouseY - dagAreaY), dagZoom * factor);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Headless runs have no active GLFW window.
            }
        }

        private static float clampZoom(float z) {
            return Math.max(0.16f, Math.min(1.45f, z));
        }

        private void adjustScale(int direction) {
            boolean fast = isShiftDown();
            if (direction > 0) {
                scaleMultiplier = Math.min(1024, fast ? scaleMultiplier * 4 : scaleMultiplier + 1);
            } else {
                scaleMultiplier = Math.max(1, fast ? Math.max(1, scaleMultiplier / 4) : scaleMultiplier - 1);
            }
        }

        private void enableLocalScissor(
            GuiGraphics g,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            int lx1,
            int ly1,
            int lx2,
            int ly2
        ) {
            g.enableScissor(
                originX + Math.round(lx1 * scaleX),
                originY + Math.round(ly1 * scaleY),
                originX + Math.round(lx2 * scaleX),
                originY + Math.round(ly2 * scaleY)
            );
        }

        @Override
        public void drawWidget(GuiGraphics g, double mouseX, double mouseY) {
            Matrix4f pose = g.pose().last().pose();
            int originX = Math.round(pose.m30());
            int originY = Math.round(pose.m31());
            float scaleX = Math.abs(pose.m00()) > 1e-4f ? pose.m00() : 1.0f;
            float scaleY = Math.abs(pose.m11()) > 1e-4f ? pose.m11() : 1.0f;
            recordRendered(this, originX, originY, scaleX, scaleY);

            hits.clear();
            popoverHits.clear();
            tipHits.clear();
            popoverTipHits.clear();
            dagHits.clear();

            WorkbenchThemeMode theme = PlanFlowRenderHelper.activeThemeMode();
            CirrusPalette p = PlanFlowRenderHelper.activePalette(theme);
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry = recipe.latestEntry();
            Font font = Minecraft.getInstance().font;
            InventorySnapshot inv = capturePlayerInventory();

            PlanFlowViewModel.MaterialDockModel dockModel = PlanFlowViewModel
                    .buildMaterials(entry, inv, scaleMultiplier, dockFilter, dockSort);
            PlanFlowViewModel.StationsPrepSummary stationsSummary = PlanFlowViewModel
                    .buildStationsPrep(entry, scaleMultiplier);

            if (activeTab != 0 && activeTab != 1) {
                activeTab = 0;
            }

            boolean pointerInPopover = dockMenuOpen && isOverDockPopover(mouseX, mouseY);
            double bgMouseX = pointerInPopover ? -9999.0 : mouseX;
            double bgMouseY = pointerInPopover ? -9999.0 : mouseY;

            drawHeader(g, font, entry, theme, p, bgMouseX, bgMouseY);
            drawTabs(g, font, stationsSummary, theme, p, bgMouseX, bgMouseY);

            CirrusTextures.Workbench.recessed(theme).render(g, bodyX, bodyY, bodyW, bodyH);
            if (activeTab == 0) {
                drawDagTab(g, font, entry, inv, originX, originY, scaleX, scaleY, theme, p, bgMouseX, bgMouseY);
            } else {
                drawStationsTab(
                    g,
                    font,
                    stationsSummary,
                    originX,
                    originY,
                    scaleX,
                    scaleY,
                    theme,
                    p,
                    bgMouseX,
                    bgMouseY
                );
            }

            drawMaterialDock(g, font, entry, dockModel, originX, originY, scaleX, scaleY, theme, p, bgMouseX, bgMouseY);
            if (dockMenuOpen) {
                drawDockOptionsPopover(g, font, entry, theme, p, mouseX, mouseY);
            }
        }

        private void drawHeader(
            GuiGraphics g,
            Font font,
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            long scaledTarget = PlanFlowViewModel.scaleAmount(entry.primaryAmount(), scaleMultiplier);
            PlanFlowRenderHelper
                    .drawSlotCount(g, font, targetSlotX, targetSlotY, targetSlotSize, scaledTarget, false, p, 215f);

            int textX = targetSlotX + targetSlotSize + 5;
            int textMaxW = Math.max(40, multX - 4 - textX);
            String title = PlanFlowRenderHelper.fit(font, entry.title(), textMaxW);
            g.drawString(font, title, textX, 5, p.textPrimary(), false);

            boolean minusHov = hit(mouseX, mouseY, multX, hdrBtnY, multMinusW, hdrBtnSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                multX,
                hdrBtnY,
                multMinusW,
                hdrBtnSize,
                minusHov,
                false,
                false,
                minusHov ? CirrusTextures.Icon.Small.minusSelected : CirrusTextures.Icon.Small.minus,
                5,
                6,
                theme,
                p
            );
            hits.add(new HitAction(multX, hdrBtnY, multMinusW, hdrBtnSize, () -> adjustScale(-1), null));
            tipHits.add(new TipHit(multX, hdrBtnY, multMinusW, hdrBtnSize, tip -> {
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.scaleMinus.get().copy().withStyle(ChatFormatting.AQUA));
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.scaleHint.get().copy().withStyle(ChatFormatting.GRAY));
            }));

            int readX = multX + multMinusW;
            boolean readHov = hit(mouseX, mouseY, readX, hdrBtnY, multReadoutW, hdrBtnSize);
            CirrusTextures.Workbench.recessed(theme).render(g, readX, hdrBtnY, multReadoutW, hdrBtnSize);
            if (scaleMultiplier > 1) {
                PlanFlowRenderHelper.drawBorder(
                    g,
                    readX,
                    hdrBtnY,
                    multReadoutW,
                    hdrBtnSize,
                    OreUiPaint.withAlpha(p.focusRing(), 0.72f)
                );
            }
            String multLabel = "\u00d7" + scaleMultiplier;
            int multInk = scaleMultiplier > 1 ? p.focusRing() : (readHov ? p.textPrimary() : p.textSecondary());
            int multWidth = font.width(multLabel);
            if (multWidth <= multReadoutW - 2) {
                g.drawString(font, multLabel, readX + (multReadoutW - multWidth) / 2, hdrBtnY + 5, multInk, false);
            } else {
                float s = (float) (multReadoutW - 3) / Math.max(1, multWidth);
                g.pose().pushPose();
                g.pose().translate(readX + (multReadoutW - multWidth * s) * 0.5f, hdrBtnY + 6f, 0f);
                g.pose().scale(s, s, 1f);
                g.drawString(font, multLabel, 0, 0, multInk, false);
                g.pose().popPose();
            }
            hits.add(new HitAction(readX, hdrBtnY, multReadoutW, hdrBtnSize, () -> scaleMultiplier = 1, null));
            tipHits.add(new TipHit(readX, hdrBtnY, multReadoutW, hdrBtnSize, tip -> {
                tip.add(
                    CirrusLangs.Calculator.Jei.PlanFlow.scaleReset.get(scaleMultiplier).copy()
                            .withStyle(ChatFormatting.AQUA)
                );
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.scaleHint.get().copy().withStyle(ChatFormatting.GRAY));
            }));

            int plusX = readX + multReadoutW;
            boolean plusHov = hit(mouseX, mouseY, plusX, hdrBtnY, multPlusW, hdrBtnSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                plusX,
                hdrBtnY,
                multPlusW,
                hdrBtnSize,
                plusHov,
                false,
                false,
                plusHov ? CirrusTextures.Icon.Small.plusSelected : CirrusTextures.Icon.Small.plus,
                5,
                6,
                theme,
                p
            );
            hits.add(new HitAction(plusX, hdrBtnY, multPlusW, hdrBtnSize, () -> adjustScale(1), null));
            tipHits.add(new TipHit(plusX, hdrBtnY, multPlusW, hdrBtnSize, tip -> {
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.scalePlus.get().copy().withStyle(ChatFormatting.AQUA));
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.scaleHint.get().copy().withStyle(ChatFormatting.GRAY));
            }));

            boolean calcHov = hit(mouseX, mouseY, calcBtnX, hdrBtnY, hdrBtnSize, hdrBtnSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                calcBtnX,
                hdrBtnY,
                hdrBtnSize,
                hdrBtnSize,
                calcHov,
                false,
                false,
                calcHov ? CirrusTextures.Icon.calculatorTargetPressed : CirrusTextures.Icon.calculatorTarget,
                12,
                12,
                theme,
                p
            );
            hits.add(
                new HitAction(
                    calcBtnX,
                    hdrBtnY,
                    hdrBtnSize,
                    hdrBtnSize,
                    () -> JeiPlanBookmarkBridge.openBookmarkInWorkbench(recipe.latestEntry(), scaleMultiplier),
                    null
                )
            );
            tipHits.add(new TipHit(calcBtnX, hdrBtnY, hdrBtnSize, hdrBtnSize, tip -> {
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.openCalc.get().copy().withStyle(ChatFormatting.AQUA));
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.openCalcDesc.get().copy().withStyle(ChatFormatting.GRAY));
            }));

            boolean execHov = hit(mouseX, mouseY, execBtnX, hdrBtnY, hdrBtnSize, hdrBtnSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                execBtnX,
                hdrBtnY,
                hdrBtnSize,
                hdrBtnSize,
                execHov,
                false,
                true,
                execHov ? CirrusTextures.Icon.startPressed : CirrusTextures.Icon.start,
                12,
                12,
                theme,
                p
            );
            hits.add(
                new HitAction(
                    execBtnX,
                    hdrBtnY,
                    hdrBtnSize,
                    hdrBtnSize,
                    () -> JeiPlanBookmarkBridge.sendBookmarkToExecution(recipe.latestEntry(), scaleMultiplier),
                    null
                )
            );
            tipHits.add(new TipHit(execBtnX, hdrBtnY, hdrBtnSize, hdrBtnSize, tip -> {
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.sendExec.get().copy().withStyle(ChatFormatting.GREEN));
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.sendExecDesc.get().copy().withStyle(ChatFormatting.GRAY));
            }));
        }

        private void drawTabs(
            GuiGraphics g,
            Font font,
            PlanFlowViewModel.StationsPrepSummary stationsSummary,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            String[] labels = {
                    CirrusLangs.Calculator.Jei.PlanFlow.tabFlow.get().getString(),
                    CirrusLangs.Calculator.Jei.PlanFlow.tabStations.get().getString()
            };
            ImageTexture[] icons = {
                    PlanFlowRenderHelper.tabIcon(
                        CirrusTextures.Icon.Execution.Category.timeline,
                        CirrusTextures.Icon.Execution.Category.timelineSelected,
                        activeTab == 0,
                        theme
                    ),
                    PlanFlowRenderHelper.tabIcon(
                        CirrusTextures.Icon.Execution.Category.recipe,
                        CirrusTextures.Icon.Execution.Category.recipeSelected,
                        activeTab == 1,
                        theme
                    )
            };

            for (int i = 0; i < 2; i++) {
                int tx = padX + i * (tabW + tabGap);
                int tw = i == 1 ? (width - padX - tx) : tabW;
                boolean sel = activeTab == i;
                boolean hov = hit(mouseX, mouseY, tx, tabY, tw, tabH);
                PlanFlowRenderHelper
                        .drawTab(g, font, tx, tabY, tw, tabH, icons[i], 10, 10, labels[i], sel, hov, theme, p);
                if (i == 1 && stationsSummary.machinesToBuildTotal() > 0) {
                    g.fill(tx + tw - 6, tabY + 3, tx + tw - 3, tabY + 6, p.provisionMachineStep().accent());
                }
                int idx = i;
                hits.add(new HitAction(tx, tabY, tw, tabH, () -> activeTab = idx, null));
            }
        }

        private void drawDagTab(
            GuiGraphics g,
            Font font,
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
            InventorySnapshot inv,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            PlanFlowViewModel.DagLayout layout = dagLayoutFor(entry);
            pollMacTrackpadPinch(mouseX, mouseY);

            CirrusPlanBookmarkStore.StepLine selectedStep = findStep(entry, selectedStepId);
            int dagVisibleH = selectedStep != null ? (dagCanvasH - dagSelectedBarH) : dagCanvasH;

            g.fill(dagAreaX, dagAreaY, dagAreaX + dagAreaW, dagAreaY + dagVisibleH, p.canvasVoid());

            enableLocalScissor(
                g,
                originX,
                originY,
                scaleX,
                scaleY,
                dagAreaX,
                dagAreaY,
                dagAreaX + dagAreaW,
                dagAreaY + dagVisibleH
            );

            int minorStep = 16;
            int majorStep = 64;
            int minOffX = Math.floorMod(Math.round(dagPanX), minorStep);
            int minOffY = Math.floorMod(Math.round(dagPanY), minorStep);
            for (int gx = dagAreaX + minOffX; gx < dagAreaX + dagAreaW; gx += minorStep) {
                g.fill(gx, dagAreaY, gx + 1, dagAreaY + dagVisibleH, p.canvasGridMinor());
            }
            for (int gy = dagAreaY + minOffY; gy < dagAreaY + dagVisibleH; gy += minorStep) {
                g.fill(dagAreaX, gy, dagAreaX + dagAreaW, gy + 1, p.canvasGridMinor());
            }
            int majOffX = Math.floorMod(Math.round(dagPanX), majorStep);
            int majOffY = Math.floorMod(Math.round(dagPanY), majorStep);
            for (int gx = dagAreaX + majOffX; gx < dagAreaX + dagAreaW; gx += majorStep) {
                g.fill(gx, dagAreaY, gx + 1, dagAreaY + dagVisibleH, p.canvasGridMajor());
            }
            for (int gy = dagAreaY + majOffY; gy < dagAreaY + dagVisibleH; gy += majorStep) {
                g.fill(dagAreaX, gy, dagAreaX + dagAreaW, gy + 1, p.canvasGridMajor());
            }

            Set<String> checked = entry.checkedStepIds();
            g.pose().pushPose();
            g.pose().translate(dagAreaX + dagPanX, dagAreaY + dagPanY, 0f);
            g.pose().scale(dagZoom, dagZoom, 1f);

            long nowMs = System.currentTimeMillis();
            boolean reducedMotion = PlanFlowRenderHelper.reducedMotion();
            for (int pass = 0; pass < 2; pass++) {
                for (PlanFlowViewModel.DagEdgeLayout edge : layout.edges()) {
                    boolean upstream = selectedStepId != null && selectedStepId.equals(edge.to().step().stepId());
                    boolean downstream = selectedStepId != null && selectedStepId.equals(edge.from().step().stepId());
                    boolean incident = upstream || downstream;
                    if ((pass == 0 && incident) || (pass == 1 && !incident)) {
                        continue;
                    }
                    int coreColor;
                    if (upstream) {
                        coreColor = p.edgeUpstreamTrace();
                    } else if (downstream) {
                        coreColor = p.edgeDownstreamTrace();
                    } else {
                        coreColor = PlanFlowRenderHelper.conduitColorFor(edge, p);
                    }
                    boolean dimmed = selectedStepId != null && !incident;
                    PlanFlowRenderHelper.drawDagConduit(
                        g,
                        edge,
                        layout.vertical(),
                        coreColor,
                        incident,
                        dimmed,
                        nowMs,
                        reducedMotion,
                        p
                    );
                }
            }
            // Flush GPU edge quads before textured cards so conduits always rasterize beneath node rims.
            g.flush();

            boolean macro = dagZoom < 0.52f;
            for (PlanFlowViewModel.DagNodeLayout node : layout.nodes()) {
                CirrusPlanBookmarkStore.StepLine step = node.step();
                float sx = dagAreaX + dagPanX + node.worldX() * dagZoom;
                float sy = dagAreaY + dagPanY + node.worldY() * dagZoom;
                float sw = node.w() * dagZoom;
                float sh = node.h() * dagZoom;
                boolean inside = sx + sw >= dagAreaX
                        && sx <= dagAreaX + dagAreaW
                        && sy + sh >= dagAreaY
                        && sy <= dagAreaY + dagVisibleH;
                if (!inside) {
                    continue;
                }
                boolean done = checked.contains(step.stepId());
                boolean selected = step.stepId().equals(selectedStepId);
                boolean tracedUp = selectedStepId != null && isUpstreamOf(layout, step.stepId(), selectedStepId);
                boolean tracedDown = selectedStepId != null && isDownstreamOf(layout, step.stepId(), selectedStepId);
                boolean hov = hit(mouseX, mouseY, dagAreaX, dagAreaY, dagAreaW, dagVisibleH)
                        && !isOverDagOverlayControls(mouseX, mouseY)
                        && mouseX >= sx
                        && mouseX < sx + sw
                        && mouseY >= sy
                        && mouseY < sy + sh;
                drawDagNodeCard(
                    g,
                    font,
                    node,
                    inv,
                    done,
                    selected,
                    hov,
                    tracedUp,
                    tracedDown,
                    macro,
                    scaleMultiplier,
                    theme,
                    p
                );
                dagHits.add(new DagNodeHit(sx, sy, sw, sh, node));
            }
            g.pose().popPose();

            // Render floating DAG controls above 3D item depth (<=165) and below popover (260) / JEI nested tooltip (300).
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 235f);
            int ctrlY = dagAreaY + 3;
            int ctrlSize = dagCtrlSize;
            int plusX = dagAreaX + dagAreaW - 3 - ctrlSize;
            int fitX = plusX - 2 - ctrlSize;
            int minusX = fitX - 2 - ctrlSize;
            int dirX = minusX - 2 - ctrlSize;

            boolean dirHov = hit(mouseX, mouseY, dirX, ctrlY, ctrlSize, ctrlSize);
            ImageTexture dirIcon = dagVertical
                    ? (dirHov ? CirrusTextures.Icon.arrowDownSelected : CirrusTextures.Icon.arrowDown)
                    : CirrusTextures.Icon.arrowRight;
            PlanFlowRenderHelper.drawIconButton(
                g,
                dirX,
                ctrlY,
                ctrlSize,
                ctrlSize,
                dirHov,
                dagVertical,
                false,
                dirIcon,
                10,
                10,
                theme,
                p
            );
            hits.add(new HitAction(dirX, ctrlY, ctrlSize, ctrlSize, () -> dagVertical = !dagVertical, null));
            tipHits.add(
                new TipHit(
                    dirX,
                    ctrlY,
                    ctrlSize,
                    ctrlSize,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dagToggleDir.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );

            boolean minusHov = hit(mouseX, mouseY, minusX, ctrlY, ctrlSize, ctrlSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                minusX,
                ctrlY,
                ctrlSize,
                ctrlSize,
                minusHov,
                false,
                false,
                minusHov ? CirrusTextures.Icon.Small.minusSelected : CirrusTextures.Icon.Small.minus,
                5,
                6,
                theme,
                p
            );
            hits.add(new HitAction(minusX, ctrlY, ctrlSize, ctrlSize, () -> zoomAroundCenter(dagZoom - 0.16f), null));
            tipHits.add(
                new TipHit(
                    minusX,
                    ctrlY,
                    ctrlSize,
                    ctrlSize,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dagZoomOut.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );

            boolean fitHov = hit(mouseX, mouseY, fitX, ctrlY, ctrlSize, ctrlSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                fitX,
                ctrlY,
                ctrlSize,
                ctrlSize,
                fitHov,
                false,
                false,
                fitHov ? CirrusTextures.Icon.searchSelected : CirrusTextures.Icon.search,
                10,
                10,
                theme,
                p
            );
            hits.add(new HitAction(fitX, ctrlY, ctrlSize, ctrlSize, () -> fitDagViewport(layout), null));
            tipHits.add(
                new TipHit(
                    fitX,
                    ctrlY,
                    ctrlSize,
                    ctrlSize,
                    tip -> tip.add(CirrusLangs.Calculator.Jei.PlanFlow.dagFit.get().copy().withStyle(ChatFormatting.AQUA))
                )
            );

            boolean plusHov = hit(mouseX, mouseY, plusX, ctrlY, ctrlSize, ctrlSize);
            PlanFlowRenderHelper.drawIconButton(
                g,
                plusX,
                ctrlY,
                ctrlSize,
                ctrlSize,
                plusHov,
                false,
                false,
                plusHov ? CirrusTextures.Icon.Small.plusSelected : CirrusTextures.Icon.Small.plus,
                5,
                6,
                theme,
                p
            );
            hits.add(new HitAction(plusX, ctrlY, ctrlSize, ctrlSize, () -> zoomAroundCenter(dagZoom + 0.16f), null));
            tipHits.add(
                new TipHit(
                    plusX,
                    ctrlY,
                    ctrlSize,
                    ctrlSize,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dagZoomIn.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );
            g.pose().popPose();

            g.disableScissor();

            if (selectedStep != null) {
                int barY = dagAreaY + dagVisibleH;
                PlanFlowRenderHelper.drawFooterBand(g, dagAreaX, barY, dagAreaW, dagSelectedBarH, p);
                boolean done = checked.contains(selectedStep.stepId());
                int curX = dagAreaX + 4;
                ResourceKey leadIcon = selectedStep.output() != null
                        ? selectedStep.output()
                        : (selectedStep.machineId() != null && !selectedStep.machineId().isBlank()
                                ? ResourceKey.item(selectedStep.machineId())
                                : null);
                if (leadIcon != null) {
                    PlanFlowRenderHelper.renderContent(g, leadIcon, curX, barY + 4, 12, p, theme);
                    curX += 15;
                }

                int btnH = 15;
                int btnY = barY + 2;
                int markW = 44;
                int markX = dagAreaX + dagAreaW - markW - 3;
                boolean markHov = hit(mouseX, mouseY, markX, btnY, markW, btnH);
                PlanFlowRenderHelper.drawCompactTextButton(
                    g,
                    font,
                    markX,
                    btnY,
                    markW,
                    btnH,
                    done
                            ? CirrusLangs.Calculator.Jei.PlanFlow.dagActionDone.get().getString()
                            : CirrusLangs.Calculator.Jei.PlanFlow.dagActionMark.get().getString(),
                    done ? CirrusTextures.Icon.finished : null,
                    8,
                    8,
                    markHov,
                    done,
                    theme,
                    p
                );
                hits.add(
                    new HitAction(
                        markX,
                        btnY,
                        markW,
                        btnH,
                        () -> toggleStepChecked(entry, selectedStep.stepId()),
                        null
                    )
                );

                int rightLimit = markX - 3;
                if (leadIcon != null) {
                    int recW = 36;
                    int recX = markX - recW - 3;
                    boolean recHov = hit(mouseX, mouseY, recX, btnY, recW, btnH);
                    PlanFlowRenderHelper.drawCompactTextButton(
                        g,
                        font,
                        recX,
                        btnY,
                        recW,
                        btnH,
                        CirrusLangs.Calculator.Jei.PlanFlow.dagActionRecipe.get().getString(),
                        null,
                        0,
                        0,
                        recHov,
                        false,
                        theme,
                        p
                    );
                    ResourceKey outRes = leadIcon;
                    hits.add(
                        new HitAction(
                            recX,
                            btnY,
                            recW,
                            btnH,
                            () -> JeiPlanBookmarkBridge.showRecipesFor(outRes),
                            () -> JeiPlanBookmarkBridge.showUsesFor(outRes)
                        )
                    );
                    rightLimit = recX - 4;
                }
                String name = leadIcon != null
                        ? CoPilotPaint.resourceName(leadIcon).getString()
                        : selectedStep.stepId();
                long scaledAmt = PlanFlowViewModel.scaleAmount(selectedStep.amount(), scaleMultiplier);
                String info = name + " \u00d7" + WorkbenchFormatters.formatAmount(scaledAmt);
                g.drawString(
                    font,
                    PlanFlowRenderHelper.fit(font, info, Math.max(16, rightLimit - curX)),
                    curX,
                    barY + 6,
                    done ? p.costS() : p.textPrimary(),
                    false
                );
            }
        }

        private void drawDagNodeCard(
            GuiGraphics g,
            Font font,
            PlanFlowViewModel.DagNodeLayout node,
            InventorySnapshot inv,
            boolean done,
            boolean selected,
            boolean hovered,
            boolean tracedUp,
            boolean tracedDown,
            boolean macro,
            int scale,
            WorkbenchThemeMode theme,
            CirrusPalette p
        ) {
            CirrusPlanBookmarkStore.StepLine step = node.step();
            CirrusPalette.StepTint tint = PlanFlowRenderHelper.stepTintFor(step.kind(), p);
            boolean dark = OreUiPaint.isDark(p);
            int nx = node.worldX();
            int ny = node.worldY();
            int nw = node.w();
            int nh = node.h();

            VisualTexture cardTex = selected
                    ? CirrusTextures.Workbench.cardSelected(theme)
                    : (hovered
                            ? CirrusTextures.Workbench.cardHover(theme)
                            : CirrusTextures.Workbench.cardSurface(theme));
            cardTex.render(g, nx, ny, nw, nh);

            String kind = step.kind();
            boolean isAcquire = "AcquireStep".equals(kind);
            boolean isLinePull = "LinePullStep".equals(kind);
            boolean isLeafPod = isAcquire || isLinePull;
            boolean isProvisionMachine = "ProvisionMachineStep".equals(kind);
            boolean isSetupLine = "SetupLineStep".equals(kind);
            boolean isMachinePod = isProvisionMachine || isSetupLine;
            boolean isConvert = "ConvertStep".equals(kind);
            boolean isCycle = "CycleLoopStep".equals(kind);
            int accent = done ? p.costS() : tint.accent();

            if (!"CraftStep".equals(kind)) {
                g.fill(
                    nx + 2,
                    ny + 2,
                    nx + nw - 2,
                    ny + nh - 2,
                    OreUiPaint.withAlpha(accent, dark ? 0.10f : 0.07f)
                );
            }

            if (tracedUp || tracedDown) {
                PlanFlowRenderHelper.drawBorder(
                    g,
                    nx - 1,
                    ny - 1,
                    nw + 2,
                    nh + 2,
                    tracedUp ? p.edgeUpstreamTrace() : p.edgeDownstreamTrace()
                );
            } else if (done) {
                PlanFlowRenderHelper.drawBorder(g, nx, ny, nw, nh, OreUiPaint.withAlpha(p.costS(), 0.85f));
            } else if (isConvert || isCycle || isLinePull) {
                PlanFlowRenderHelper.drawBorder(g, nx, ny, nw, nh, OreUiPaint.withAlpha(accent, dark ? 0.60f : 0.50f));
            }
            if (node.finalTarget()) {
                PlanFlowRenderHelper.drawCornerBrackets(g, nx - 1, ny - 1, nw + 2, nh + 2, 4, p.costH());
            }

            long scaledAmt = PlanFlowViewModel.scaleAmount(step.amount(), scale);
            ResourceKey primaryRes = step.output() != null
                    ? step.output()
                    : (step.machineId() != null && !step.machineId().isBlank()
                            ? ResourceKey.item(step.machineId())
                            : null);

            if (macro) {
                int slotDim = 22;
                int sx = nx + (nw - slotDim) / 2;
                int sy = ny + (nh - slotDim) / 2;
                if (isMachinePod) {
                    PlanFlowRenderHelper.drawCornerBrackets(g, nx + 2, ny + 2, nw - 4, nh - 4, 4, accent);
                }
                PlanFlowRenderHelper.drawSlotContent(g, primaryRes, sx, sy, slotDim, false, theme, p);
                PlanFlowRenderHelper.drawSlotCount(g, font, sx, sy, slotDim, scaledAmt, false, p, 205f);
                if (done) {
                    drawNodeDoneOverlay(g, nx + nw - 10, ny + 1, theme);
                }
                return;
            }

            if (isLeafPod) {
                int slotDim = 22;
                int sx = nx + (nw - slotDim) / 2;
                int sy = ny + 4;
                PlanFlowRenderHelper.drawSlotContent(g, primaryRes, sx, sy, slotDim, false, theme, p);
                PlanFlowRenderHelper.drawSlotCount(g, font, sx, sy, slotDim, scaledAmt, false, p, 205f);

                long have = primaryRes != null ? inv.usableAmount(primaryRes) : 0L;
                double ratio = Math.max(0.0, Math.min(1.0, (double) have / (double) Math.max(1L, scaledAmt)));
                PlanFlowRenderHelper.drawSplitProgressBar(g, nx + 5, ny + 29, nw - 10, 4, ratio, p);
                if (done) {
                    drawNodeDoneOverlay(g, nx + nw - 10, ny + 1, theme);
                }
                return;
            }

            if (isMachinePod || step.inputs().isEmpty()) {
                PlanFlowRenderHelper.drawCornerBrackets(g, nx + 2, ny + 2, nw - 4, nh - 4, 4, accent);
                int slotDim = 22;
                int sx = nx + (nw - slotDim) / 2;
                int sy = ny + (nh - slotDim) / 2;
                PlanFlowRenderHelper.drawSlotContent(g, primaryRes, sx, sy, slotDim, false, theme, p);
                PlanFlowRenderHelper.drawBorder(g, sx, sy, slotDim, slotDim, OreUiPaint.withAlpha(accent, 0.72f));
                PlanFlowRenderHelper.drawSlotCount(g, font, sx, sy, slotDim, scaledAmt, false, p, 205f);
                if (isProvisionMachine && !done) {
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, 208f);
                    CirrusThemeIcons.resolve(CirrusTextures.Icon.Small.plusSelected, theme)
                            .render(g, nx + nw - 7, ny + 2, 5, 6);
                    g.pose().popPose();
                } else if (!isMachinePod && step.machineId() != null && !step.machineId().isBlank()) {
                    PlanFlowRenderHelper.renderContent(g, ResourceKey.item(step.machineId()), nx + 2, ny + 2, 10, p, theme);
                }
                if (done) {
                    drawNodeDoneOverlay(g, nx + nw - 10, ny + 1, theme);
                }
                return;
            }

            List<ResourceKey> inputs = step.inputs();
            int shownCols = Math.min(3, inputs.size());
            boolean overflow = inputs.size() > 3;
            int shownItems = overflow ? 2 : shownCols;
            int inSlotDim = 18;
            int inStep = 17;
            int inX = nx + 5;
            int inY = ny + (nh - inSlotDim) / 2;
            for (int i = 0; i < shownItems; i++) {
                ResourceKey inKey = inputs.get(i);
                int sx = inX + i * inStep;
                PlanFlowRenderHelper.drawSlotContent(g, inKey, sx, inY, inSlotDim, false, theme, p);
                long inAmt = findScaledInputAmount(step, inKey, scale);
                if (inAmt > 1L) {
                    PlanFlowRenderHelper.drawSlotCount(g, font, sx, inY, inSlotDim, inAmt, false, p, 205f);
                }
            }
            if (overflow) {
                int ox = inX + 2 * inStep;
                PlanFlowRenderHelper.drawOreSlot(g, ox, inY, inSlotDim, false, theme, p);
                String more = "+" + (inputs.size() - 2);
                int mw = font.width(more);
                float ms = mw <= inSlotDim - 3 ? 0.78f : Math.max(0.55f, (inSlotDim - 3f) / Math.max(1, mw));
                g.pose().pushPose();
                g.pose().translate(ox + (inSlotDim - mw * ms) * 0.5f, inY + (inSlotDim - 8f * ms) * 0.5f, 205f);
                g.pose().scale(ms, ms, 1f);
                g.drawString(font, more, 0, 0, p.focusRing(), false);
                g.pose().popPose();
            }

            int inputMatrixW = inSlotDim + (shownCols - 1) * inStep;
            int bridgeX = inX + inputMatrixW;
            int bridgeW = 20;
            String rawMachineId = step.machineId();
            String machineId = (rawMachineId != null && !rawMachineId.isBlank()) ? rawMachineId : null;
            boolean accentArrow = selected || hovered || isConvert || isCycle;

            if (machineId != null) {
                PlanFlowRenderHelper.renderContent(g, ResourceKey.item(machineId), bridgeX + 4, ny + 4, 12, p, theme);
                PlanFlowRenderHelper.drawRecipeArrow(g, bridgeX + 3, bridgeX + bridgeW - 3, ny + 23, p, accent, accentArrow);
            } else if ("CraftStep".equals(kind)) {
                PlanFlowRenderHelper.renderContent(g, craftingTableIcon, bridgeX + 4, ny + 5, 11, p, theme);
                PlanFlowRenderHelper.drawRecipeArrow(g, bridgeX + 3, bridgeX + bridgeW - 3, ny + 23, p, accent, accentArrow);
            } else {
                PlanFlowRenderHelper.drawRecipeArrow(g, bridgeX + 3, bridgeX + bridgeW - 3, ny + nh / 2, p, accent, accentArrow);
            }

            int outDim = 22;
            int outX = bridgeX + bridgeW;
            int outY = ny + (nh - outDim) / 2;
            PlanFlowRenderHelper.drawSlotContent(g, primaryRes, outX, outY, outDim, false, theme, p);
            PlanFlowRenderHelper.drawBorder(g, outX, outY, outDim, outDim, OreUiPaint.withAlpha(accent, 0.62f));
            PlanFlowRenderHelper.drawSlotCount(g, font, outX, outY, outDim, scaledAmt, false, p, 205f);

            if (done) {
                drawNodeDoneOverlay(g, nx + nw - 10, ny + 1, theme);
            }
        }

        private static void drawNodeDoneOverlay(GuiGraphics g, int x, int y, WorkbenchThemeMode theme) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 210f);
            CirrusThemeIcons.resolve(CirrusTextures.Icon.finished, theme).render(g, x, y, 9, 9);
            g.pose().popPose();
        }

        private static long findScaledInputAmount(
            CirrusPlanBookmarkStore.StepLine step,
            ResourceKey input,
            int scale
        ) {
            for (CirrusPlanBookmarkStore.MaterialLine line : step.inputAmounts()) {
                if (input.equals(line.resource())) {
                    return PlanFlowViewModel.scaleAmount(line.needed(), scale);
                }
            }
            return 0L;
        }

        private void drawStationsTab(
            GuiGraphics g,
            Font font,
            PlanFlowViewModel.StationsPrepSummary summary,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            int viewX = bodyX + 1;
            int viewY = bodyY + 1;
            int viewW = bodyW - 2;
            int viewH = bodyH - 2;

            List<PlanFlowViewModel.StationGroup> stations = summary.stations();
            boolean hasCatalysts = !summary.catalysts().isEmpty();
            boolean hasSurplus = !summary.surplusByproducts().isEmpty();

            int cardH = 38;
            int cardGap = 4;
            int extraSectionH = 34;
            int contentH = 8;
            if (!stations.isEmpty()) {
                contentH += stations.size() * (cardH + cardGap) - cardGap;
            } else if (!hasCatalysts && !hasSurplus) {
                contentH = viewH;
            }
            if (hasCatalysts) {
                contentH += (stations.isEmpty() ? 0 : cardGap) + extraSectionH;
            }
            if (hasSurplus) {
                contentH += ((stations.isEmpty() && !hasCatalysts) ? 0 : cardGap) + extraSectionH;
            }
            int maxScroll = Math.max(0, contentH - viewH);
            stationsScrollPx = Math.max(0, Math.min(maxScroll, stationsScrollPx));

            int sbX = viewX + viewW - 7;
            int innerX = viewX + 4;
            int innerW = sbX - innerX - 4;

            enableLocalScissor(g, originX, originY, scaleX, scaleY, viewX, viewY, sbX - 1, viewY + viewH);

            if (stations.isEmpty() && !hasCatalysts && !hasSurplus) {
                String empty = CirrusLangs.Calculator.Jei.PlanFlow.stationsEmpty.get().getString();
                g.drawString(font, empty, viewX + (viewW - font.width(empty)) / 2, viewY + viewH / 2 - 4, p.textMuted(), false);
            } else {
                int curY = viewY + 4 - stationsScrollPx;
                for (PlanFlowViewModel.StationGroup st : stations) {
                    if (curY + cardH >= viewY && curY <= viewY + viewH) {
                        boolean cardHov = hit(mouseX, mouseY, innerX, curY, innerW, cardH)
                                && mouseY >= viewY
                                && mouseY < viewY + viewH;
                        VisualTexture cardBg = cardHov
                                ? CirrusTextures.Workbench.cardHover(theme)
                                : CirrusTextures.Workbench.cardSurface(theme);
                        cardBg.render(g, innerX, curY, innerW, cardH);

                        int slotX = innerX + 6;
                        int slotY = curY + (cardH - 20) / 2;
                        boolean slotHov = cardHov && hit(mouseX, mouseY, slotX, slotY, 20, 20);
                        PlanFlowRenderHelper.drawSlotContent(g, st.iconResource(), slotX, slotY, 20, slotHov, theme, p);
                        if (st.toBuildCount() > 0) {
                            PlanFlowRenderHelper.drawBorder(
                                g,
                                slotX,
                                slotY,
                                20,
                                20,
                                OreUiPaint.withAlpha(p.provisionMachineStep().accent(), 0.72f)
                            );
                            PlanFlowRenderHelper.drawSlotCount(g, font, slotX, slotY, 20, st.toBuildCount(), false, p, 215f);
                        }
                        int hitTop = Math.max(viewY, slotY);
                        int hitBot = Math.min(viewY + viewH, slotY + 20);
                        if (hitBot > hitTop) {
                            ResourceKey machKey = st.iconResource();
                            hits.add(
                                new HitAction(
                                    slotX,
                                    hitTop,
                                    20,
                                    hitBot - hitTop,
                                    () -> JeiPlanBookmarkBridge.showRecipesFor(machKey),
                                    () -> JeiPlanBookmarkBridge.showUsesFor(machKey)
                                )
                            );
                        }

                        int infoX = slotX + 26;
                        int rightEdge = innerX + innerW - 6;
                        String pillText;
                        int pillColor;
                        if (st.toBuildCount() > 0) {
                            pillText = CirrusLangs.Calculator.Jei.PlanFlow.stationsBuild.get(st.toBuildCount()).getString();
                            pillColor = p.provisionMachineStep().accent();
                        } else if (st.automated()) {
                            pillText = CirrusLangs.Calculator.Jei.PlanFlow.stationsAuto.get().getString();
                            pillColor = p.costS();
                        } else {
                            pillText = CirrusLangs.Calculator.Jei.PlanFlow.stationsMeta
                                    .get(st.stepCount(), WorkbenchFormatters.formatAmount(st.totalBatches())).getString();
                            pillColor = p.craftStep().accent();
                        }
                        int pillW = PlanFlowRenderHelper.measureTagPillWidth(font, pillText);
                        PlanFlowRenderHelper
                                .drawTagPill(g, font, rightEdge - pillW, curY + 4, 11, pillText, pillColor, p);

                        String stationName = st.handCraft()
                                ? CirrusLangs.Calculator.Jei.PlanFlow.stationsHandCraft.get().getString()
                                : CoPilotPaint.machineName(st.iconResource(), st.stationId()).getString();
                        int nameMaxW = Math.max(24, rightEdge - pillW - 6 - infoX);
                        g.drawString(font, PlanFlowRenderHelper.fit(font, stationName, nameMaxW), infoX, curY + 5, p.textPrimary(), false);

                        int miniSlotSize = 15;
                        int miniGap = 2;
                        int miniStep = miniSlotSize + miniGap;
                        int subY = curY + 18;
                        boolean showClock = st.totalSeconds() > 0.05;
                        String rightMeta = showClock
                                ? WorkbenchFormatters.formatSeconds(st.totalSeconds())
                                : (st.toBuildCount() > 0 || st.automated()
                                        ? CirrusLangs.Calculator.Jei.PlanFlow.stationsMeta
                                                .get(st.stepCount(), WorkbenchFormatters.formatAmount(st.totalBatches()))
                                                .getString()
                                        : "");
                        int rightMetaW = rightMeta.isEmpty() ? 0 : font.width(rightMeta) + (showClock ? 12 : 0) + 6;
                        if (!rightMeta.isEmpty()) {
                            int rx = rightEdge - font.width(rightMeta);
                            if (showClock) {
                                CirrusThemeIcons.resolve(CirrusTextures.Icon.Sign.Energy.time, theme)
                                        .render(g, rx - 11, subY + 3, 9, 9);
                            }
                            g.drawString(font, rightMeta, rx, subY + 4, p.textSecondary(), false);
                        }

                        int availOutW = Math.max(0, rightEdge - rightMetaW - infoX);
                        List<ResourceKey> outs = st.sampleOutputs();
                        int shown = computeFittedItemCount(font, outs.size(), miniSlotSize, miniGap, availOutW);
                        for (int oi = 0; oi < shown; oi++) {
                            ResourceKey outKey = outs.get(oi);
                            int ox = infoX + oi * miniStep;
                            boolean oHov = cardHov && hit(mouseX, mouseY, ox, subY, miniSlotSize, miniSlotSize);
                            PlanFlowRenderHelper.drawSlotContent(g, outKey, ox, subY, miniSlotSize, oHov, theme, p);
                            int oTop = Math.max(viewY, subY);
                            int oBot = Math.min(viewY + viewH, subY + miniSlotSize);
                            if (oBot > oTop) {
                                hits.add(
                                    new HitAction(
                                        ox,
                                        oTop,
                                        miniSlotSize,
                                        oBot - oTop,
                                        () -> JeiPlanBookmarkBridge.showRecipesFor(outKey),
                                        () -> JeiPlanBookmarkBridge.showUsesFor(outKey)
                                    )
                                );
                                tipHits.add(new TipHit(ox, oTop, miniSlotSize, oBot - oTop, tip -> {
                                    tip.add(CoPilotPaint.resourceName(outKey).copy().withStyle(ChatFormatting.WHITE));
                                    tip.add(
                                        CirrusLangs.Calculator.Jei.PlanFlow.stationsTipClick.get().copy()
                                                .withStyle(ChatFormatting.GRAY)
                                    );
                                }));
                            }
                        }
                        if (outs.size() > shown) {
                            int rem = outs.size() - shown;
                            String more = "+" + rem;
                            int moreW = PlanFlowRenderHelper.measureTagPillWidth(font, more);
                            int moreX = infoX + shown * miniStep;
                            if (moreX + moreW <= infoX + availOutW) {
                                PlanFlowRenderHelper.drawTagPill(g, font, moreX, subY + 2, 11, more, p.focusRing(), p);
                                int mTop = Math.max(viewY, subY + 2);
                                int mBot = Math.min(viewY + viewH, subY + 13);
                                if (mBot > mTop) {
                                    List<ResourceKey> overflowItems = outs.subList(shown, outs.size());
                                    tipHits.add(new TipHit(moreX, mTop, moreW, mBot - mTop, tip -> {
                                        int cap = Math.min(12, overflowItems.size());
                                        for (int k = 0; k < cap; k++) {
                                            tip.add(CoPilotPaint.resourceName(overflowItems.get(k)).copy().withStyle(ChatFormatting.WHITE));
                                        }
                                        if (overflowItems.size() > cap) {
                                            tip.add(Component.literal("... (+" + (overflowItems.size() - cap) + ")").withStyle(ChatFormatting.GRAY));
                                        }
                                    }));
                                }
                            }
                        }

                        int rowTop = Math.max(viewY, curY);
                        int rowBot = Math.min(viewY + viewH, curY + cardH);
                        if (rowBot > rowTop) {
                            tipHits.add(new TipHit(innerX, rowTop, innerW, rowBot - rowTop, tip -> {
                                tip.add(Component.literal(stationName).withStyle(ChatFormatting.AQUA));
                                tip.add(
                                    CirrusLangs.Calculator.Jei.PlanFlow.stationsMeta
                                            .get(st.stepCount(), WorkbenchFormatters.formatAmount(st.totalBatches()))
                                            .copy().withStyle(ChatFormatting.WHITE)
                                );
                                if (st.toBuildCount() > 0) {
                                    tip.add(
                                        CirrusLangs.Calculator.Jei.PlanFlow.stationsBuild.get(st.toBuildCount()).copy()
                                                .withStyle(ChatFormatting.GOLD)
                                    );
                                }
                                tip.add(
                                    CirrusLangs.Calculator.Jei.PlanFlow.stationsTipClick.get().copy()
                                            .withStyle(ChatFormatting.GRAY)
                                );
                            }));
                        }
                    }
                    curY += cardH + cardGap;
                }

                if (hasCatalysts) {
                    drawExtraResourceBand(
                        g,
                        font,
                        innerX,
                        curY,
                        innerW,
                        extraSectionH,
                        viewY,
                        viewH,
                        CirrusLangs.Calculator.Jei.PlanFlow.stationsCatalysts.get().getString(),
                        true,
                        summary.catalysts().stream().map(k -> new CirrusPlanBookmarkStore.MaterialLine(k, 1L)).toList(),
                        false,
                        theme,
                        p,
                        mouseX,
                        mouseY
                    );
                    curY += extraSectionH + cardGap;
                }

                if (hasSurplus) {
                    drawExtraResourceBand(
                        g,
                        font,
                        innerX,
                        curY,
                        innerW,
                        extraSectionH,
                        viewY,
                        viewH,
                        CirrusLangs.Calculator.Jei.PlanFlow.stationsSurplus.get().getString(),
                        false,
                        summary.surplusByproducts(),
                        true,
                        theme,
                        p,
                        mouseX,
                        mouseY
                    );
                }
            }

            g.disableScissor();
            PlanFlowRenderHelper.drawOreScrollbar(g, sbX, viewY + 2, 5, viewH - 4, stationsScrollPx, maxScroll, theme);
        }

        private static int computeFittedItemCount(Font font, int total, int slotSize, int gap, int availW) {
            if (total <= 0 || availW < slotSize) {
                return 0;
            }
            int step = slotSize + gap;
            if (total * step - gap <= availW) {
                return total;
            }
            for (int candidate = total - 1; candidate >= 0; candidate--) {
                int rem = total - candidate;
                int pillW = PlanFlowRenderHelper.measureTagPillWidth(font, "+" + rem);
                int usedW = candidate == 0 ? pillW : (candidate * step + pillW);
                if (usedW <= availW) {
                    return candidate;
                }
            }
            return 0;
        }

        private void drawExtraResourceBand(
            GuiGraphics g,
            Font font,
            int x,
            int y,
            int w,
            int h,
            int clipTop,
            int clipH,
            String heading,
            boolean catalystEmblem,
            List<CirrusPlanBookmarkStore.MaterialLine> items,
            boolean showCount,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            if (y + h < clipTop || y > clipTop + clipH) {
                return;
            }
            CirrusTextures.Workbench.cardSurface(theme).render(g, x, y, w, h);
            ImageTexture bandIcon = catalystEmblem ? CirrusTextures.Icon.setting : CirrusTextures.Icon.finished;
            CirrusThemeIcons.resolve(bandIcon, theme).render(g, x + 6, y + 6, 9, 9);
            g.drawString(font, PlanFlowRenderHelper.fit(font, heading, 74), x + 18, y + 6, p.textSecondary(), false);
            g.drawString(font, "(" + items.size() + ")", x + 18, y + 18, p.textMuted(), false);

            int slotsX = x + 96;
            int rightEdge = x + w - 6;
            int availW = Math.max(0, rightEdge - slotsX);
            int slotSize = 20;
            int slotGap = 2;
            int step = slotSize + slotGap;
            int shown = computeFittedItemCount(font, items.size(), slotSize, slotGap, availW);
            int sy = y + (h - slotSize) / 2;
            for (int i = 0; i < shown; i++) {
                CirrusPlanBookmarkStore.MaterialLine item = items.get(i);
                int sx = slotsX + i * step;
                boolean hov = hit(mouseX, mouseY, sx, sy, slotSize, slotSize) && mouseY >= clipTop && mouseY < clipTop + clipH;
                PlanFlowRenderHelper.drawSlotContent(g, item.resource(), sx, sy, slotSize, hov, theme, p);
                if (showCount) {
                    PlanFlowRenderHelper.drawSlotCount(g, font, sx, sy, slotSize, item.needed(), false, p, 215f);
                }
                int hitTop = Math.max(clipTop, sy);
                int hitBot = Math.min(clipTop + clipH, sy + slotSize);
                if (hitBot > hitTop) {
                    hits.add(
                        new HitAction(
                            sx,
                            hitTop,
                            slotSize,
                            hitBot - hitTop,
                            () -> JeiPlanBookmarkBridge.showRecipesFor(item.resource()),
                            () -> JeiPlanBookmarkBridge.showUsesFor(item.resource())
                        )
                    );
                    tipHits.add(new TipHit(sx, hitTop, slotSize, hitBot - hitTop, tip -> {
                        String nm = CoPilotPaint.resourceName(item.resource()).getString();
                        tip.add(
                            Component.literal(showCount ? (nm + " +" + WorkbenchFormatters.formatAmount(item.needed())) : nm)
                                    .withStyle(ChatFormatting.WHITE)
                        );
                        tip.add(
                            CirrusLangs.Calculator.Jei.PlanFlow.stationsTipClick.get().copy().withStyle(ChatFormatting.GRAY)
                        );
                    }));
                }
            }
            if (items.size() > shown) {
                int rem = items.size() - shown;
                String more = "+" + rem;
                int moreW = PlanFlowRenderHelper.measureTagPillWidth(font, more);
                int moreX = slotsX + shown * step;
                if (moreX + moreW <= rightEdge) {
                    int pillY = y + (h - 11) / 2;
                    PlanFlowRenderHelper.drawTagPill(g, font, moreX, pillY, 11, more, p.focusRing(), p);
                    int mTop = Math.max(clipTop, pillY);
                    int mBot = Math.min(clipTop + clipH, pillY + 11);
                    if (mBot > mTop) {
                        List<CirrusPlanBookmarkStore.MaterialLine> overflowItems = items.subList(shown, items.size());
                        tipHits.add(new TipHit(moreX, mTop, moreW, mBot - mTop, tip -> {
                            int cap = Math.min(12, overflowItems.size());
                            for (int k = 0; k < cap; k++) {
                                CirrusPlanBookmarkStore.MaterialLine oi = overflowItems.get(k);
                                String nm = CoPilotPaint.resourceName(oi.resource()).getString();
                                tip.add(
                                    Component.literal(showCount ? (nm + " +" + WorkbenchFormatters.formatAmount(oi.needed())) : nm)
                                            .withStyle(ChatFormatting.WHITE)
                                );
                            }
                            if (overflowItems.size() > cap) {
                                tip.add(Component.literal("... (+" + (overflowItems.size() - cap) + ")").withStyle(ChatFormatting.GRAY));
                            }
                        }));
                    }
                }
            }
        }

        private void drawMaterialDock(
            GuiGraphics g,
            Font font,
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
            PlanFlowViewModel.MaterialDockModel model,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            CirrusTextures.Workbench.recessed(theme).render(g, dockX, dockY, dockW, dockH);
            PlanFlowRenderHelper.drawHeaderBand(g, dockX + 1, dockY + 1, dockW - 2, dockHeaderH, p);

            int menuBtnW = dockMenuBtnW;
            int menuBtnH = dockMenuBtnH;
            int menuBtnX = dockMenuBtnX();
            int menuBtnY = dockMenuBtnY();
            boolean menuHov = hit(mouseX, mouseY, menuBtnX, menuBtnY, menuBtnW, menuBtnH);
            PlanFlowRenderHelper.drawMenuTriggerButton(g, menuBtnX, menuBtnY, menuBtnW, menuBtnH, menuHov, dockMenuOpen, theme, p);
            hits.add(new HitAction(menuBtnX, menuBtnY, menuBtnW, menuBtnH, () -> dockMenuOpen = !dockMenuOpen, null));
            tipHits.add(new TipHit(menuBtnX, menuBtnY, menuBtnW, menuBtnH, tip -> {
                tip.add(CirrusLangs.Calculator.Jei.PlanFlow.dockMenuTitle.get().copy().withStyle(ChatFormatting.AQUA));
                String filterTxt = switch (dockFilter) {
                    case all -> CirrusLangs.Calculator.Jei.PlanFlow.dockFilterAll.get().getString();
                    case missing -> CirrusLangs.Calculator.Jei.PlanFlow.dockFilterMissing.get().getString();
                    case steps -> CirrusLangs.Calculator.Jei.PlanFlow.dockFilterSteps.get().getString();
                };
                String viewTxt = (dockListMode
                        ? CirrusLangs.Calculator.Jei.PlanFlow.dockViewListLabel
                        : CirrusLangs.Calculator.Jei.PlanFlow.dockViewGridLabel).get().getString();
                String unitTxt = (stackUnitMode
                        ? CirrusLangs.Calculator.Jei.PlanFlow.dockUnitStack
                        : CirrusLangs.Calculator.Jei.PlanFlow.dockUnitRaw).get().getString();
                tip.add(Component.literal(filterTxt + " \u00b7 " + viewTxt + " \u00b7 " + unitTxt).withStyle(ChatFormatting.GRAY));
            }));

            int leftX = dockX + 5;
            CirrusThemeIcons.resolve(CirrusTextures.Icon.databaseSelect, theme).render(g, leftX, dockY + 5, 10, 10);
            String titleStr = dockFilter == PlanFlowViewModel.MaterialFilter.steps
                    ? CirrusLangs.Calculator.Jei.PlanFlow.dockFilterSteps.get().getString()
                    : CirrusLangs.Calculator.Jei.PlanFlow.dockTitle.get().getString();
            g.drawString(font, titleStr, leftX + 13, dockY + 6, p.textPrimary(), false);
            int cursor = leftX + 13 + font.width(titleStr) + 5;

            String statText;
            int statColor;
            double barRatio;
            if (dockFilter == PlanFlowViewModel.MaterialFilter.steps) {
                statText = model.checkedSteps() + "/" + model.totalSteps();
                statColor = model.checkedSteps() >= model.totalSteps() && model.totalSteps() > 0 ? p.costS() : p.focusRing();
                barRatio = model.totalSteps() <= 0 ? 1.0 : (double) model.checkedSteps() / model.totalSteps();
            } else {
                statText = model.readyMaterials() + "/" + model.totalMaterials();
                statColor = model.missingMaterials() == 0
                        ? p.costS()
                        : (model.readyMaterials() > 0 ? p.costH() : p.ruleHardBan());
                barRatio = model.coverageRatio();
            }
            int statPillW = PlanFlowRenderHelper.measureTagPillWidth(font, statText);
            if (cursor + statPillW < menuBtnX - 6) {
                PlanFlowRenderHelper.drawTagPill(g, font, cursor, dockY + 4, 12, statText, statColor, p);
                cursor += statPillW + 6;
            }
            int meterAvail = menuBtnX - 8 - cursor;
            if (meterAvail >= 24) {
                int meterW = Math.min(64, meterAvail);
                if (dockFilter == PlanFlowViewModel.MaterialFilter.steps) {
                    PlanFlowRenderHelper.drawFlatMeter(g, cursor, dockY + 7, meterW, 6, barRatio, statColor, p);
                } else {
                    PlanFlowRenderHelper.drawSplitProgressBar(g, cursor, dockY + 7, meterW, 6, barRatio, p);
                }
            }

            if (dockFilter == PlanFlowViewModel.MaterialFilter.steps) {
                if (dockListMode) {
                    drawDockStepsList(g, font, entry, model.steps(), originX, originY, scaleX, scaleY, theme, p, mouseX, mouseY);
                } else {
                    drawDockStepsGrid(g, font, entry, model.steps(), originX, originY, scaleX, scaleY, theme, p, mouseX, mouseY);
                }
            } else if (dockListMode) {
                drawDockMaterialsList(
                    g,
                    font,
                    model.visibleMaterials(),
                    originX,
                    originY,
                    scaleX,
                    scaleY,
                    theme,
                    p,
                    mouseX,
                    mouseY
                );
            } else {
                drawDockMaterialsGrid(
                    g,
                    font,
                    model.visibleMaterials(),
                    originX,
                    originY,
                    scaleX,
                    scaleY,
                    theme,
                    p,
                    mouseX,
                    mouseY
                );
            }
        }

        private void drawDockMaterialsGrid(
            GuiGraphics g,
            Font font,
            List<PlanFlowViewModel.MaterialRow> rows,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            if (rows.isEmpty()) {
                drawDockEmpty(
                    g,
                    font,
                    dockFilter == PlanFlowViewModel.MaterialFilter.missing
                            ? CirrusLangs.Calculator.Jei.PlanFlow.dockEmptyMissing.get().getString()
                            : CirrusLangs.Calculator.Jei.PlanFlow.dockEmptyAll.get().getString(),
                    dockFilter == PlanFlowViewModel.MaterialFilter.missing ? p.costS() : p.textMuted()
                );
                PlanFlowRenderHelper.drawOreScrollbar(g, dockScrollX, dockViewY + 1, dockScrollW, dockViewH - 2, 0, 0, theme);
                return;
            }

            int totalRows = (rows.size() + gridCols - 1) / gridCols;
            int contentH = totalRows * gridPitchY - gridGapY + 2;
            int maxScroll = Math.max(0, contentH - dockViewH);
            dockScrollPx = Math.max(0, Math.min(maxScroll, dockScrollPx));
            String stackSuffix = stackUnitSuffix();

            enableLocalScissor(
                g,
                originX,
                originY,
                scaleX,
                scaleY,
                dockX + 1,
                dockViewY,
                dockScrollX - 1,
                dockViewY + dockViewH
            );

            for (int i = 0; i < rows.size(); i++) {
                int col = i % gridCols;
                int rowIdx = i / gridCols;
                int cx = gridStartX + col * gridPitchX;
                int cy = gridStartY + rowIdx * gridPitchY - dockScrollPx;
                if (cy + gridTileH < dockViewY || cy > dockViewY + dockViewH) {
                    continue;
                }
                PlanFlowViewModel.MaterialRow m = rows.get(i);
                boolean hov = hit(mouseX, mouseY, cx, cy, gridTileW, gridTileH)
                        && mouseY >= dockViewY
                        && mouseY < dockViewY + dockViewH;
                PlanFlowViewModel.ResourceUnitSpec unitSpec = JeiPlanBookmarkBridge.resolveUnitSpec(m.resource());
                String expectedText = PlanFlowViewModel.formatSlotQuantity(m.needed(), unitSpec, stackUnitMode, stackSuffix);
                String missingText = m.ready()
                        ? ""
                        : ("-" + PlanFlowViewModel.formatSlotQuantity(m.missing(), unitSpec, stackUnitMode, stackSuffix));
                PlanFlowRenderHelper.drawCompactMaterialSlot(
                    g,
                    font,
                    cx,
                    cy,
                    gridTileW,
                    gridTileH,
                    m.resource(),
                    expectedText,
                    missingText,
                    m.ratio(),
                    m.ready(),
                    hov,
                    theme,
                    p
                );

                int hitTop = Math.max(dockViewY, cy);
                int hitBot = Math.min(dockViewY + dockViewH, cy + gridTileH);
                if (hitBot > hitTop) {
                    hits.add(
                        new HitAction(
                            cx,
                            hitTop,
                            gridTileW,
                            hitBot - hitTop,
                            () -> JeiPlanBookmarkBridge.showRecipesFor(m.resource()),
                            () -> JeiPlanBookmarkBridge.showUsesFor(m.resource())
                        )
                    );
                    tipHits.add(
                        new TipHit(
                            cx,
                            hitTop,
                            gridTileW,
                            hitBot - hitTop,
                            tip -> populateMaterialTooltip(tip, m, unitSpec)
                        )
                    );
                }
            }

            g.disableScissor();
            PlanFlowRenderHelper.drawOreScrollbar(
                g,
                dockScrollX,
                dockViewY + 1,
                dockScrollW,
                dockViewH - 2,
                dockScrollPx,
                maxScroll,
                theme
            );
        }

        private void drawDockMaterialsList(
            GuiGraphics g,
            Font font,
            List<PlanFlowViewModel.MaterialRow> rows,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            if (rows.isEmpty()) {
                drawDockEmpty(
                    g,
                    font,
                    dockFilter == PlanFlowViewModel.MaterialFilter.missing
                            ? CirrusLangs.Calculator.Jei.PlanFlow.dockEmptyMissing.get().getString()
                            : CirrusLangs.Calculator.Jei.PlanFlow.dockEmptyAll.get().getString(),
                    dockFilter == PlanFlowViewModel.MaterialFilter.missing ? p.costS() : p.textMuted()
                );
                PlanFlowRenderHelper.drawOreScrollbar(g, dockScrollX, dockViewY + 1, dockScrollW, dockViewH - 2, 0, 0, theme);
                return;
            }

            int rowH = 22;
            int rowGap = 1;
            int pitch = rowH + rowGap;
            int contentH = rows.size() * pitch - rowGap + 2;
            int maxScroll = Math.max(0, contentH - dockViewH);
            dockScrollPx = Math.max(0, Math.min(maxScroll, dockScrollPx));
            int innerX = dockX + 4;
            int innerW = dockScrollX - innerX - 3;
            String stackSuffix = stackUnitSuffix();

            enableLocalScissor(
                g,
                originX,
                originY,
                scaleX,
                scaleY,
                dockX + 1,
                dockViewY,
                dockScrollX - 1,
                dockViewY + dockViewH
            );

            for (int i = 0; i < rows.size(); i++) {
                int ry = dockViewY + 1 + i * pitch - dockScrollPx;
                if (ry + rowH < dockViewY || ry > dockViewY + dockViewH) {
                    continue;
                }
                PlanFlowViewModel.MaterialRow m = rows.get(i);
                boolean hov = hit(mouseX, mouseY, innerX, ry, innerW, rowH)
                        && mouseY >= dockViewY
                        && mouseY < dockViewY + dockViewH;
                VisualTexture cardBg = hov
                        ? CirrusTextures.Workbench.cardHover(theme)
                        : CirrusTextures.Workbench.cardSurface(theme);
                cardBg.render(g, innerX, ry, innerW, rowH);

                PlanFlowRenderHelper.drawSlotContent(g, m.resource(), innerX + 3, ry + 2, 18, false, theme, p);

                PlanFlowViewModel.ResourceUnitSpec unitSpec = JeiPlanBookmarkBridge.resolveUnitSpec(m.resource());
                String pillTxt = m.ready()
                        ? "\u2713"
                        : ("-" + PlanFlowViewModel.formatSlotQuantity(m.missing(), unitSpec, stackUnitMode, stackSuffix));
                int pillColor = m.ready() ? p.costS() : p.ruleHardBan();
                int pillW = PlanFlowRenderHelper.measureTagPillWidth(font, pillTxt);
                int pillX = innerX + innerW - 4 - pillW;
                PlanFlowRenderHelper.drawTagPill(g, font, pillX, ry + 2, 11, pillTxt, pillColor, p);

                String qtyTxt = PlanFlowViewModel.formatSlotQuantity(m.have(), unitSpec, stackUnitMode, stackSuffix) + "/"
                        + PlanFlowViewModel.formatSlotQuantity(m.needed(), unitSpec, stackUnitMode, stackSuffix);
                int qtyX = pillX - 5 - font.width(qtyTxt);
                g.drawString(font, qtyTxt, qtyX, ry + 3, m.ready() ? p.costS() : p.textSecondary(), false);

                String name = CoPilotPaint.resourceName(m.resource()).getString();
                int textX = innerX + 25;
                g.drawString(font, PlanFlowRenderHelper.fit(font, name, Math.max(20, qtyX - 4 - textX)), textX, ry + 3, p.textPrimary(), false);

                PlanFlowRenderHelper.drawSplitProgressBar(g, textX, ry + 14, innerW - 29, 4, m.ratio(), p);

                int hitTop = Math.max(dockViewY, ry);
                int hitBot = Math.min(dockViewY + dockViewH, ry + rowH);
                if (hitBot > hitTop) {
                    hits.add(
                        new HitAction(
                            innerX,
                            hitTop,
                            innerW,
                            hitBot - hitTop,
                            () -> JeiPlanBookmarkBridge.showRecipesFor(m.resource()),
                            () -> JeiPlanBookmarkBridge.showUsesFor(m.resource())
                        )
                    );
                    tipHits.add(
                        new TipHit(
                            innerX,
                            hitTop,
                            innerW,
                            hitBot - hitTop,
                            tip -> populateMaterialTooltip(tip, m, unitSpec)
                        )
                    );
                }
            }

            g.disableScissor();
            PlanFlowRenderHelper.drawOreScrollbar(
                g,
                dockScrollX,
                dockViewY + 1,
                dockScrollW,
                dockViewH - 2,
                dockScrollPx,
                maxScroll,
                theme
            );
        }

        private void drawDockStepsGrid(
            GuiGraphics g,
            Font font,
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
            List<PlanFlowViewModel.StepProgressRow> steps,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            int totalRows = Math.max(1, (steps.size() + gridCols - 1) / gridCols);
            int contentH = totalRows * gridPitchY - gridGapY + 2;
            int maxScroll = Math.max(0, contentH - dockViewH);
            dockScrollPx = Math.max(0, Math.min(maxScroll, dockScrollPx));
            String stackSuffix = stackUnitSuffix();

            enableLocalScissor(
                g,
                originX,
                originY,
                scaleX,
                scaleY,
                dockX + 1,
                dockViewY,
                dockScrollX - 1,
                dockViewY + dockViewH
            );

            for (int i = 0; i < steps.size(); i++) {
                int col = i % gridCols;
                int rowIdx = i / gridCols;
                int cx = gridStartX + col * gridPitchX;
                int cy = gridStartY + rowIdx * gridPitchY - dockScrollPx;
                if (cy + gridTileH < dockViewY || cy > dockViewY + dockViewH) {
                    continue;
                }
                PlanFlowViewModel.StepProgressRow sr = steps.get(i);
                CirrusPlanBookmarkStore.StepLine st = sr.step();
                boolean hov = hit(mouseX, mouseY, cx, cy, gridTileW, gridTileH)
                        && mouseY >= dockViewY
                        && mouseY < dockViewY + dockViewH;
                CirrusPalette.StepTint tint = PlanFlowRenderHelper.stepTintFor(st.kind(), p);
                PlanFlowViewModel.ResourceUnitSpec unitSpec = JeiPlanBookmarkBridge.resolveUnitSpec(st.output());
                String amtText = "\u00d7"
                        + PlanFlowViewModel.formatSlotQuantity(sr.scaledAmount(), unitSpec, stackUnitMode, stackSuffix);
                PlanFlowRenderHelper.drawCompactStepSlot(
                    g,
                    font,
                    cx,
                    cy,
                    gridTileW,
                    gridTileH,
                    st.output(),
                    amtText,
                    tint.accent(),
                    sr.done(),
                    hov,
                    theme,
                    p
                );

                int hitTop = Math.max(dockViewY, cy);
                int hitBot = Math.min(dockViewY + dockViewH, cy + gridTileH);
                if (hitBot > hitTop) {
                    String sid = st.stepId();
                    ResourceKey outRes = st.output();
                    hits.add(
                        new HitAction(
                            cx,
                            hitTop,
                            gridTileW,
                            hitBot - hitTop,
                            () -> toggleStepChecked(entry, sid),
                            outRes != null ? () -> JeiPlanBookmarkBridge.showRecipesFor(outRes) : null
                        )
                    );
                    tipHits.add(
                        new TipHit(cx, hitTop, gridTileW, hitBot - hitTop, tip -> populateStepTooltip(tip, sr))
                    );
                }
            }

            g.disableScissor();
            PlanFlowRenderHelper.drawOreScrollbar(
                g,
                dockScrollX,
                dockViewY + 1,
                dockScrollW,
                dockViewH - 2,
                dockScrollPx,
                maxScroll,
                theme
            );
        }

        private void drawDockStepsList(
            GuiGraphics g,
            Font font,
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
            List<PlanFlowViewModel.StepProgressRow> steps,
            int originX,
            int originY,
            float scaleX,
            float scaleY,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            int rowH = 22;
            int rowGap = 1;
            int pitch = rowH + rowGap;
            int contentH = steps.size() * pitch - rowGap + 2;
            int maxScroll = Math.max(0, contentH - dockViewH);
            dockScrollPx = Math.max(0, Math.min(maxScroll, dockScrollPx));
            int innerX = dockX + 4;
            int innerW = dockScrollX - innerX - 3;

            enableLocalScissor(
                g,
                originX,
                originY,
                scaleX,
                scaleY,
                dockX + 1,
                dockViewY,
                dockScrollX - 1,
                dockViewY + dockViewH
            );

            for (int i = 0; i < steps.size(); i++) {
                int ry = dockViewY + 1 + i * pitch - dockScrollPx;
                if (ry + rowH < dockViewY || ry > dockViewY + dockViewH) {
                    continue;
                }
                PlanFlowViewModel.StepProgressRow sr = steps.get(i);
                CirrusPlanBookmarkStore.StepLine st = sr.step();
                boolean hov = hit(mouseX, mouseY, innerX, ry, innerW, rowH)
                        && mouseY >= dockViewY
                        && mouseY < dockViewY + dockViewH;
                VisualTexture cardBg = hov
                        ? CirrusTextures.Workbench.cardHover(theme)
                        : CirrusTextures.Workbench.cardSurface(theme);
                cardBg.render(g, innerX, ry, innerW, rowH);
                if (sr.done()) {
                    g.fill(innerX + 1, ry + 1, innerX + innerW - 1, ry + rowH - 2, OreUiPaint.withAlpha(p.costS(), 0.14f));
                }

                PlanFlowRenderHelper.drawRegularCheckbox(g, innerX + 4, ry + 6, sr.done(), hov);
                PlanFlowRenderHelper.drawSlotContent(g, st.output(), innerX + 17, ry + 2, 18, false, theme, p);

                CirrusPalette.StepTint tint = PlanFlowRenderHelper.stepTintFor(st.kind(), p);
                String amtStr = "\u00d7" + WorkbenchFormatters.formatAmount(sr.scaledAmount());
                int amtX = innerX + innerW - 5 - font.width(amtStr);
                g.drawString(font, amtStr, amtX, ry + 7, sr.done() ? p.costS() : tint.accent(), false);

                String kindStr = PlanFlowRenderHelper.stepKindLabel(st.kind()).getString();
                int pillW = PlanFlowRenderHelper.measureTagPillWidth(font, kindStr);
                int pillX = amtX - 4 - pillW;
                PlanFlowRenderHelper.drawTagPill(g, font, pillX, ry + 5, 11, kindStr, sr.done() ? p.costS() : tint.accent(), p);

                String name = st.output() != null ? CoPilotPaint.resourceName(st.output()).getString() : st.stepId();
                int textX = innerX + 39;
                g.drawString(
                    font,
                    PlanFlowRenderHelper.fit(font, "#" + sr.orderIndex() + " " + name, Math.max(20, pillX - 4 - textX)),
                    textX,
                    ry + 7,
                    sr.done() ? p.textMuted() : p.textPrimary(),
                    false
                );

                int hitTop = Math.max(dockViewY, ry);
                int hitBot = Math.min(dockViewY + dockViewH, ry + rowH);
                if (hitBot > hitTop) {
                    String sid = st.stepId();
                    ResourceKey outRes = st.output();
                    hits.add(
                        new HitAction(
                            innerX,
                            hitTop,
                            innerW,
                            hitBot - hitTop,
                            () -> toggleStepChecked(entry, sid),
                            outRes != null ? () -> JeiPlanBookmarkBridge.showRecipesFor(outRes) : null
                        )
                    );
                    tipHits.add(new TipHit(innerX, hitTop, innerW, hitBot - hitTop, tip -> populateStepTooltip(tip, sr)));
                }
            }

            g.disableScissor();
            PlanFlowRenderHelper.drawOreScrollbar(
                g,
                dockScrollX,
                dockViewY + 1,
                dockScrollW,
                dockViewH - 2,
                dockScrollPx,
                maxScroll,
                theme
            );
        }

        private void drawDockEmpty(GuiGraphics g, Font font, String text, int color) {
            int centerW = dockScrollX - dockX - 4;
            String fitted = PlanFlowRenderHelper.fit(font, text, Math.max(20, centerW - 12));
            g.drawString(font, fitted, dockX + 2 + (centerW - font.width(fitted)) / 2, dockViewY + dockViewH / 2 - 4, color, false);
        }

        private void populateMaterialTooltip(
            ITooltipBuilder tooltip,
            PlanFlowViewModel.MaterialRow m,
            PlanFlowViewModel.ResourceUnitSpec unitSpec
        ) {
            boolean effectiveStackMode = stackUnitMode ^ isShiftDown();
            String stackSuffix = stackUnitSuffix();
            tooltip.add(CoPilotPaint.resourceName(m.resource()).copy().withStyle(ChatFormatting.WHITE));
            String needDisplay = PlanFlowViewModel.formatTooltipQuantity(m.needed(), unitSpec, effectiveStackMode, stackSuffix);
            tooltip.add(
                CirrusLangs.Calculator.Jei.PlanFlow.dockTipNeed.get(needDisplay).copy().withStyle(ChatFormatting.AQUA)
            );
            String haveDisplay = PlanFlowViewModel.formatTooltipQuantity(m.have(), unitSpec, effectiveStackMode, stackSuffix);
            tooltip.add(
                CirrusLangs.Calculator.Jei.PlanFlow.dockTipHave.get(haveDisplay).copy()
                        .withStyle(m.ready() ? ChatFormatting.GREEN : ChatFormatting.WHITE)
            );
            if (!m.ready()) {
                String missingDisplay = PlanFlowViewModel
                        .formatTooltipQuantity(m.missing(), unitSpec, effectiveStackMode, stackSuffix);
                tooltip.add(
                    CirrusLangs.Calculator.Jei.PlanFlow.dockTipMissing.get(missingDisplay).copy()
                            .withStyle(ChatFormatting.RED)
                );
            } else {
                tooltip.add(CirrusLangs.Calculator.Jei.PlanFlow.dockTipReady.get().copy().withStyle(ChatFormatting.GREEN));
            }
            if (unitSpec.canConvert()) {
                tooltip.add(
                    CirrusLangs.Calculator.Jei.PlanFlow.dockTipUnitHint.get().copy().withStyle(ChatFormatting.DARK_GRAY)
                );
            }
            tooltip.add(CirrusLangs.Calculator.Jei.PlanFlow.dockTipActions.get().copy().withStyle(ChatFormatting.GRAY));
        }

        private static String stackUnitSuffix() {
            String raw = CirrusLangs.Calculator.Jei.PlanFlow.dockUnitStackSuffix.get().getString();
            return raw.startsWith("cirrus.") ? "" : raw;
        }

        private void populateStepTooltip(ITooltipBuilder tooltip, PlanFlowViewModel.StepProgressRow sr) {
            CirrusPlanBookmarkStore.StepLine st = sr.step();
            String name = st.output() != null ? CoPilotPaint.resourceName(st.output()).getString() : st.stepId();
            tooltip.add(
                Component.literal("#" + sr.orderIndex() + " " + name + " \u00d7" + WorkbenchFormatters.formatAmount(sr.scaledAmount()))
                        .withStyle(ChatFormatting.AQUA)
            );
            tooltip.add(PlanFlowRenderHelper.stepKindLabel(st.kind()).copy().withStyle(ChatFormatting.WHITE));
            if (st.machineId() != null && !st.machineId().isBlank()) {
                tooltip.add(
                    CoPilotPaint.machineName(ResourceKey.item(st.machineId()), st.machineId()).copy()
                            .withStyle(ChatFormatting.YELLOW)
                );
            }
            tooltip.add(CirrusLangs.Calculator.Jei.PlanFlow.dockStepTipToggle.get().copy().withStyle(ChatFormatting.GRAY));
        }

        private static int dockMenuBtnX() {
            return dockX + dockW - 4 - dockMenuBtnW;
        }

        private static int dockMenuBtnY() {
            return dockY + 2;
        }

        private static int dockPopoverW() {
            return 148;
        }

        private static int dockPopoverH() {
            return 112;
        }

        private static int dockPopoverX() {
            return dockX + dockW - 3 - dockPopoverW();
        }

        private static int dockPopoverY() {
            return dockY + 1 - dockPopoverH();
        }

        private static boolean isOverDockPopover(double mx, double my) {
            return hit(mx, my, dockPopoverX(), dockPopoverY(), dockPopoverW(), dockPopoverH());
        }

        private void drawDockOptionsPopover(
            GuiGraphics g,
            Font font,
            CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
            WorkbenchThemeMode theme,
            CirrusPalette p,
            double mouseX,
            double mouseY
        ) {
            int px = dockPopoverX();
            int py = dockPopoverY();
            int pw = dockPopoverW();
            int ph = dockPopoverH();
            int pad = 7;
            int innerW = pw - pad * 2;
            int textBtnH = 16;
            int iconBtnH = 18;
            int gap = 3;

            // Stay strictly below JEI's PinnedTooltipRenderer nested hover tooltip layer (+300 Z).
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 260f);
            PlanFlowRenderHelper.drawPopoverFrame(g, px, py, pw, ph, theme, p);

            // Row 1: Filter (All / Short / Steps)
            g.drawString(font, CirrusLangs.Calculator.Jei.PlanFlow.dockMenuFilter.get().getString(), px + pad, py + 6, p.textMuted(), false);
            PlanFlowViewModel.MaterialFilter[] filters = {
                    PlanFlowViewModel.MaterialFilter.all,
                    PlanFlowViewModel.MaterialFilter.missing,
                    PlanFlowViewModel.MaterialFilter.steps
            };
            String[] filterLabels = {
                    CirrusLangs.Calculator.Jei.PlanFlow.dockFilterAll.get().getString(),
                    CirrusLangs.Calculator.Jei.PlanFlow.dockFilterMissing.get().getString(),
                    CirrusLangs.Calculator.Jei.PlanFlow.dockFilterSteps.get().getString()
            };
            int seg3W = (innerW - gap * 2) / 3;
            int r1Y = py + 16;
            for (int i = 0; i < 3; i++) {
                int bx = px + pad + i * (seg3W + gap);
                int bw = i == 2 ? (px + pad + innerW - bx) : seg3W;
                PlanFlowViewModel.MaterialFilter f = filters[i];
                boolean hov = hit(mouseX, mouseY, bx, r1Y, bw, textBtnH);
                PlanFlowRenderHelper.drawCompactTextButton(
                    g, font, bx, r1Y, bw, textBtnH, filterLabels[i], null, 0, 0, hov, dockFilter == f, theme, p
                );
                popoverHits.add(new HitAction(bx, r1Y, bw, textBtnH, () -> {
                    dockFilter = f;
                    dockScrollPx = 0;
                }, null));
            }

            // Row 2: Sort icon buttons (Materials) or Reset Progress (Steps)
            int r2LabelY = py + 38;
            int r2Y = py + 48;
            if (dockFilter == PlanFlowViewModel.MaterialFilter.steps) {
                g.drawString(font, CirrusLangs.Calculator.Jei.PlanFlow.dockMenuProgress.get().getString(), px + pad, r2LabelY, p.textMuted(), false);
                boolean resetHov = hit(mouseX, mouseY, px + pad, r2Y, innerW, iconBtnH);
                PlanFlowRenderHelper.drawCompactTextButton(
                    g,
                    font,
                    px + pad,
                    r2Y,
                    innerW,
                    iconBtnH,
                    CirrusLangs.Calculator.Jei.PlanFlow.dockResetSteps.get().getString(),
                    resetHov ? CirrusTextures.Icon.deleteSelected : CirrusTextures.Icon.delete,
                    9,
                    9,
                    resetHov,
                    false,
                    theme,
                    p
                );
                popoverHits.add(new HitAction(px + pad, r2Y, innerW, iconBtnH, () -> {
                    CirrusPlanBookmarkStore.saveCheckedSteps(entry.id(), Set.of());
                    dockMenuOpen = false;
                }, null));
            } else {
                g.drawString(font, CirrusLangs.Calculator.Jei.PlanFlow.dockMenuSort.get().getString(), px + pad, r2LabelY, p.textMuted(), false);
                PlanFlowViewModel.MaterialSort[] sorts = {
                        PlanFlowViewModel.MaterialSort.missingFirst,
                        PlanFlowViewModel.MaterialSort.amountDesc,
                        PlanFlowViewModel.MaterialSort.readyFirst
                };
                Component[] sortTips = {
                        CirrusLangs.Calculator.Jei.PlanFlow.dockSortMissing.get(),
                        CirrusLangs.Calculator.Jei.PlanFlow.dockSortAmount.get(),
                        CirrusLangs.Calculator.Jei.PlanFlow.dockSortReady.get()
                };
                for (int i = 0; i < 3; i++) {
                    int bx = px + pad + i * (seg3W + gap);
                    int bw = i == 2 ? (px + pad + innerW - bx) : seg3W;
                    PlanFlowViewModel.MaterialSort s = sorts[i];
                    boolean active = dockSort == s;
                    boolean hov = hit(mouseX, mouseY, bx, r2Y, bw, iconBtnH);
                    PlanFlowRenderHelper.drawIconButton(
                        g,
                        bx,
                        r2Y,
                        bw,
                        iconBtnH,
                        hov,
                        active,
                        false,
                        PlanFlowRenderHelper.sortIcon(s, active || hov),
                        12,
                        12,
                        theme,
                        p
                    );
                    popoverHits.add(new HitAction(bx, r2Y, bw, iconBtnH, () -> {
                        dockSort = s;
                        dockScrollPx = 0;
                    }, null));
                    Component tipComp = sortTips[i];
                    popoverTipHits.add(new TipHit(bx, r2Y, bw, iconBtnH, tip -> tip.add(tipComp.copy().withStyle(ChatFormatting.AQUA))));
                }
            }

            // Divider + Row 3: View (Grid/List icons) and Unit (Count x1 / Stack layers icons)
            g.fill(px + pad, py + 72, px + pad + innerW, py + 73, OreUiPaint.cardDivider(p));
            int halfGap = 8;
            int halfW = (innerW - halfGap) / 2;
            int leftX = px + pad;
            int rightX = leftX + halfW + halfGap;
            int r3LabelY = py + 77;
            int r3Y = py + 87;

            g.drawString(font, CirrusLangs.Calculator.Jei.PlanFlow.dockMenuView.get().getString(), leftX, r3LabelY, p.textMuted(), false);
            int subW = (halfW - gap) / 2;
            boolean gridActive = !dockListMode;
            boolean gridHov = hit(mouseX, mouseY, leftX, r3Y, subW, iconBtnH);
            PlanFlowRenderHelper.drawIconButton(
                g,
                leftX,
                r3Y,
                subW,
                iconBtnH,
                gridHov,
                gridActive,
                false,
                PlanFlowRenderHelper.viewToggleIcon(true, gridActive, gridHov, theme),
                10,
                12,
                theme,
                p
            );
            popoverHits.add(new HitAction(leftX, r3Y, subW, iconBtnH, () -> {
                dockListMode = false;
                dockScrollPx = 0;
            }, null));
            popoverTipHits.add(
                new TipHit(
                    leftX,
                    r3Y,
                    subW,
                    iconBtnH,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dockViewGridLabel.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );

            int listX = leftX + subW + gap;
            int listW = leftX + halfW - listX;
            boolean listActive = dockListMode;
            boolean listHov = hit(mouseX, mouseY, listX, r3Y, listW, iconBtnH);
            PlanFlowRenderHelper.drawIconButton(
                g,
                listX,
                r3Y,
                listW,
                iconBtnH,
                listHov,
                listActive,
                false,
                PlanFlowRenderHelper.viewToggleIcon(false, listActive, listHov, theme),
                10,
                12,
                theme,
                p
            );
            popoverHits.add(new HitAction(listX, r3Y, listW, iconBtnH, () -> {
                dockListMode = true;
                dockScrollPx = 0;
            }, null));
            popoverTipHits.add(
                new TipHit(
                    listX,
                    r3Y,
                    listW,
                    iconBtnH,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dockViewListLabel.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );

            g.drawString(font, CirrusLangs.Calculator.Jei.PlanFlow.dockMenuUnit.get().getString(), rightX, r3LabelY, p.textMuted(), false);
            int uSubW = (halfW - gap) / 2;
            boolean countActive = !stackUnitMode;
            boolean countHov = hit(mouseX, mouseY, rightX, r3Y, uSubW, iconBtnH);
            PlanFlowRenderHelper.drawIconButton(
                g,
                rightX,
                r3Y,
                uSubW,
                iconBtnH,
                countHov,
                countActive,
                false,
                PlanFlowRenderHelper.unitIcon(false, countActive || countHov),
                12,
                12,
                theme,
                p
            );
            popoverHits.add(new HitAction(rightX, r3Y, uSubW, iconBtnH, () -> stackUnitMode = false, null));
            popoverTipHits.add(
                new TipHit(
                    rightX,
                    r3Y,
                    uSubW,
                    iconBtnH,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dockUnitCountTip.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );

            int stkX = rightX + uSubW + gap;
            int stkW = rightX + halfW - stkX;
            boolean stkActive = stackUnitMode;
            boolean stkHov = hit(mouseX, mouseY, stkX, r3Y, stkW, iconBtnH);
            PlanFlowRenderHelper.drawIconButton(
                g,
                stkX,
                r3Y,
                stkW,
                iconBtnH,
                stkHov,
                stkActive,
                false,
                PlanFlowRenderHelper.unitIcon(true, stkActive || stkHov),
                12,
                12,
                theme,
                p
            );
            popoverHits.add(new HitAction(stkX, r3Y, stkW, iconBtnH, () -> stackUnitMode = true, null));
            popoverTipHits.add(
                new TipHit(
                    stkX,
                    r3Y,
                    stkW,
                    iconBtnH,
                    tip -> tip.add(
                        CirrusLangs.Calculator.Jei.PlanFlow.dockUnitStackTip.get().copy().withStyle(ChatFormatting.AQUA)
                    )
                )
            );

            g.pose().popPose();
        }

        private boolean isOverDagOverlayControls(double mx, double my) {
            int ctrlY = dagAreaY + 3;
            int ctrlLeft = dagAreaX + dagAreaW - 3 - 4 * dagCtrlSize - 3 * 2;
            if (hit(mx, my, ctrlLeft, ctrlY, dagAreaX + dagAreaW - ctrlLeft, dagCtrlSize + 2)) {
                return true;
            }
            if (selectedStepId != null
                    && hit(mx, my, dagAreaX, dagAreaY + dagCanvasH - dagSelectedBarH, dagAreaW, dagSelectedBarH)) {
                return true;
            }
            return false;
        }

        private void toggleStepChecked(CirrusPlanBookmarkStore.PlanBookmarkEntry entry, String stepId) {
            Set<String> next = new LinkedHashSet<>(entry.checkedStepIds());
            if (!next.remove(stepId)) {
                next.add(stepId);
            }
            CirrusPlanBookmarkStore.saveCheckedSteps(entry.id(), next);
        }

        private boolean handlePointerPress(double mouseX, double mouseY, int button) {
            boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            if (dockMenuOpen) {
                for (int i = popoverHits.size() - 1; i >= 0; i--) {
                    HitAction action = popoverHits.get(i);
                    if (action.contains(mouseX, mouseY)) {
                        Runnable run = right ? action.rightAction() : action.leftAction();
                        if (run != null) {
                            CirrusUiSound.playClickSound();
                            run.run();
                        }
                        return true;
                    }
                }
                if (isOverDockPopover(mouseX, mouseY)) {
                    return true;
                }
                dockMenuOpen = false;
                CirrusUiSound.playClickSound();
                return true;
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                HitAction action = hits.get(i);
                if (action.contains(mouseX, mouseY)) {
                    Runnable run = right ? action.rightAction() : action.leftAction();
                    if (run != null) {
                        CirrusUiSound.playClickSound();
                        run.run();
                        return true;
                    }
                    if (!right) {
                        return true;
                    }
                }
            }
            if (!right && activeTab == 0 && hit(mouseX, mouseY, dagAreaX, dagAreaY, dagAreaW, dagCanvasH)
                    && !isOverDagOverlayControls(mouseX, mouseY)) {
                dagPointerDown = true;
                dagDragging = false;
                dagDragAccum = 0.0;
                return true;
            }
            if (right && activeTab == 0 && hit(mouseX, mouseY, dagAreaX, dagAreaY, dagAreaW, dagCanvasH)
                    && !isOverDagOverlayControls(mouseX, mouseY)) {
                for (int i = dagHits.size() - 1; i >= 0; i--) {
                    DagNodeHit hitNode = dagHits.get(i);
                    if (hitNode.contains(mouseX, mouseY) && hitNode.node().step().output() != null) {
                        CirrusUiSound.playClickSound();
                        JeiPlanBookmarkBridge.showRecipesFor(hitNode.node().step().output());
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean handlePointerDrag(double dragX, double dragY) {
            if (!dagPointerDown) {
                return false;
            }
            dagDragAccum += Math.hypot(dragX, dragY);
            if (dagDragAccum >= 3.0) {
                dagDragging = true;
            }
            dagPanX += (float) dragX;
            dagPanY += (float) dragY;
            return true;
        }

        private boolean handlePointerRelease(double mouseX, double mouseY) {
            if (!dagPointerDown) {
                return false;
            }
            boolean wasDrag = dagDragging;
            dagPointerDown = false;
            dagDragging = false;
            if (!wasDrag) {
                for (int i = dagHits.size() - 1; i >= 0; i--) {
                    DagNodeHit hitNode = dagHits.get(i);
                    if (hitNode.contains(mouseX, mouseY)) {
                        CirrusUiSound.playClickSound();
                        String clickedId = hitNode.node().step().stepId();
                        selectedStepId = clickedId.equals(selectedStepId) ? null : clickedId;
                        return true;
                    }
                }
                selectedStepId = null;
            }
            return true;
        }

        @Override
        public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
            int val = input.getKey().getValue();
            if (val != GLFW.GLFW_MOUSE_BUTTON_LEFT && val != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                return false;
            }
            boolean right = val == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            if (dockMenuOpen) {
                for (int i = popoverHits.size() - 1; i >= 0; i--) {
                    HitAction action = popoverHits.get(i);
                    if (action.contains(mouseX, mouseY)) {
                        Runnable run = right ? action.rightAction() : action.leftAction();
                        if (run != null && !input.isSimulate()) {
                            CirrusUiSound.playClickSound();
                            run.run();
                        }
                        return true;
                    }
                }
                if (isOverDockPopover(mouseX, mouseY)) {
                    return true;
                }
                if (!input.isSimulate()) {
                    dockMenuOpen = false;
                    CirrusUiSound.playClickSound();
                }
                return true;
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                HitAction action = hits.get(i);
                if (action.contains(mouseX, mouseY)) {
                    Runnable run = right ? action.rightAction() : action.leftAction();
                    if (run == null) {
                        continue;
                    }
                    if (!input.isSimulate()) {
                        CirrusUiSound.playClickSound();
                        run.run();
                    }
                    return true;
                }
            }
            if (activeTab == 0 && !isOverDagOverlayControls(mouseX, mouseY)) {
                for (int i = dagHits.size() - 1; i >= 0; i--) {
                    DagNodeHit hitNode = dagHits.get(i);
                    if (hitNode.contains(mouseX, mouseY)) {
                        if (!input.isSimulate()) {
                            CirrusUiSound.playClickSound();
                            if (right && hitNode.node().step().output() != null) {
                                JeiPlanBookmarkBridge.showRecipesFor(hitNode.node().step().output());
                            } else {
                                String clickedId = hitNode.node().step().stepId();
                                selectedStepId = clickedId.equals(selectedStepId) ? null : clickedId;
                            }
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
            if (dockMenuOpen && isOverDockPopover(mouseX, mouseY)) {
                return true;
            }
            if (hit(mouseX, mouseY, multX, hdrBtnY, multTotalW, hdrBtnSize) && Math.abs(scrollDeltaY) > 1e-6) {
                adjustScale(scrollDeltaY > 0 ? 1 : -1);
                CirrusUiSound.playClickSound();
                return true;
            }
            if (hit(mouseX, mouseY, dockX, dockY, dockW, dockH)) {
                int delta = (int) Math.round(-scrollDeltaY * 22.0);
                if (delta != 0) {
                    dockScrollPx = Math.max(0, dockScrollPx + delta);
                    return true;
                }
                return false;
            }
            if (!hit(mouseX, mouseY, bodyX, bodyY, bodyW, bodyH)) {
                return false;
            }
            if (activeTab == 0) {
                if (Math.abs(scrollDeltaX) < 1e-6 && Math.abs(scrollDeltaY) < 1e-6) {
                    return false;
                }
                boolean mac = isMacOs();
                boolean zoomMod = isZoomModifierDown();
                boolean panMode = switch (SpatialDagCanvasWidget.scrollNavigationMode()) {
                    case trackpadPan -> !zoomMod;
                    case mouseZoom -> Math.abs(scrollDeltaX) > 1e-4;
                    case auto -> mac ? !zoomMod : Math.abs(scrollDeltaX) > 1e-4;
                };
                if (panMode) {
                    dagPanX += (float) (scrollDeltaX * 18.0);
                    dagPanY += (float) (scrollDeltaY * 18.0);
                    return true;
                }
                if (Math.abs(scrollDeltaY) < 1e-6) {
                    return false;
                }
                double clamped = Math.max(-4.0, Math.min(4.0, scrollDeltaY));
                float factor = (float) Math.pow(1.12, clamped);
                float vx = (float) Math.max(0.0, Math.min(dagAreaW, mouseX - dagAreaX));
                float vy = (float) Math.max(0.0, Math.min(dagCanvasH, mouseY - dagAreaY));
                zoomAt(vx, vy, dagZoom * factor);
                return true;
            }
            int delta = (int) Math.round(-scrollDeltaY * 20.0);
            stationsScrollPx = Math.max(0, stationsScrollPx + delta);
            return true;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
            if (dockMenuOpen && isOverDockPopover(mouseX, mouseY)) {
                for (int i = popoverTipHits.size() - 1; i >= 0; i--) {
                    TipHit tipHit = popoverTipHits.get(i);
                    if (tipHit.contains(mouseX, mouseY)) {
                        tipHit.builder().accept(tooltip);
                        return;
                    }
                }
                return;
            }
            for (int i = tipHits.size() - 1; i >= 0; i--) {
                TipHit tipHit = tipHits.get(i);
                if (tipHit.contains(mouseX, mouseY)) {
                    tipHit.builder().accept(tooltip);
                    return;
                }
            }
            if (activeTab == 0 && hit(mouseX, mouseY, dagAreaX, dagAreaY, dagAreaW, dagCanvasH)
                    && !isOverDagOverlayControls(mouseX, mouseY)) {
                for (int i = dagHits.size() - 1; i >= 0; i--) {
                    DagNodeHit hitNode = dagHits.get(i);
                    if (hitNode.contains(mouseX, mouseY)) {
                        PlanFlowViewModel.DagNodeLayout node = hitNode.node();
                        CirrusPlanBookmarkStore.StepLine s = node.step();
                        ResourceKey displayRes = s.output() != null
                                ? s.output()
                                : (s.machineId() != null && !s.machineId().isBlank()
                                        ? ResourceKey.item(s.machineId())
                                        : null);
                        String title = displayRes != null
                                ? CoPilotPaint.resourceName(displayRes).getString()
                                : s.stepId();
                        long scaledAmt = PlanFlowViewModel.scaleAmount(s.amount(), scaleMultiplier);
                        tooltip.add(
                            Component.literal(title + " \u00d7" + WorkbenchFormatters.formatAmount(scaledAmt))
                                    .withStyle(ChatFormatting.AQUA)
                        );
                        tooltip.add(PlanFlowRenderHelper.stepKindLabel(s.kind()).copy().withStyle(ChatFormatting.WHITE));
                        if (s.machineId() != null && !s.machineId().isBlank()) {
                            tooltip.add(
                                CoPilotPaint.machineName(ResourceKey.item(s.machineId()), s.machineId()).copy()
                                        .withStyle(ChatFormatting.YELLOW)
                            );
                        }
                        double dur = PlanFlowViewModel
                                .scaleDuration(s.workSeconds() + s.waitSeconds(), scaleMultiplier);
                        if (dur > 0.05) {
                            tooltip.add(
                                Component.literal(WorkbenchFormatters.formatSeconds(dur))
                                        .withStyle(ChatFormatting.GRAY)
                            );
                        }
                        tooltip.add(
                            (s.dependsOn().isEmpty()
                                    ? CirrusLangs.Calculator.Jei.PlanFlow.dagTooltipRoot.get()
                                    : CirrusLangs.Calculator.Jei.PlanFlow.dagTooltipDeps.get(s.dependsOn().size()))
                                            .copy().withStyle(ChatFormatting.GRAY)
                        );
                        return;
                    }
                }
            }
        }
    }

    private static boolean isUpstreamOf(PlanFlowViewModel.DagLayout layout, String candidateId, String targetId) {
        for (PlanFlowViewModel.DagEdgeLayout e : layout.edges()) {
            if (candidateId.equals(e.from().step().stepId()) && targetId.equals(e.to().step().stepId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDownstreamOf(PlanFlowViewModel.DagLayout layout, String candidateId, String sourceId) {
        for (PlanFlowViewModel.DagEdgeLayout e : layout.edges()) {
            if (sourceId.equals(e.from().step().stepId()) && candidateId.equals(e.to().step().stepId())) {
                return true;
            }
        }
        return false;
    }

    private static CirrusPlanBookmarkStore.@Nullable StepLine findStep(
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
        @Nullable String stepId
    ) {
        if (stepId == null) {
            return null;
        }
        for (CirrusPlanBookmarkStore.StepLine s : entry.steps()) {
            if (stepId.equals(s.stepId())) {
                return s;
            }
        }
        return null;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean isMacOs() {
        try {
            return Minecraft.ON_OSX;
        } catch (RuntimeException | LinkageError ignored) {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        }
    }

    private static boolean isShiftDown() {
        try {
            return Screen.hasShiftDown();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isZoomModifierDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return false;
            }
            long win = mc.getWindow().getWindow();
            return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SUPER)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SUPER)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static InventorySnapshot capturePlayerInventory() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null || mc.player == null
                    ? InventorySnapshot.empty
                    : StorageInventorySnapshotAdapter.capture(mc.player);
        } catch (RuntimeException | LinkageError ignored) {
            return InventorySnapshot.empty;
        }
    }
}
