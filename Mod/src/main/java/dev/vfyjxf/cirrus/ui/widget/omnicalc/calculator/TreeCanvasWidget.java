package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.common.collect.MapMaker;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisual;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisualCache;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Sign;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeTooltipPreview;
import dev.vfyjxf.cirrus.ui.process.ProcessEdge;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Pos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.cloudlib.api.ui.texture.BatchableTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;

/**
 * Tree diagram widget — renders a node-based tree matching the web app's TreeDiagram.
 * Each node is a compact card (nodeW × nodeH) connected by Manhattan-routed lines.
 */
public class TreeCanvasWidget extends CompositeWidget<Widget> {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Calculator");

    //region Metrics
    // Node dimensions (world-space pixels)
    private static final int nodeW = 188;
    private static final int nodeH = 82;
    private static final int gapX = 20;
    private static final int rowH = 122;
    private static final int focusBtnW = 18;
    private static final int focusBtnH = 18;
    private static final int collapseBtnW = 18;
    private static final int collapseBtnH = 16;
    private static final int stepSectionGap = 6;
    private static final int cardExtensionH = 14;
    private static final int cardMainSlot = 22;
    private static final int cardMachineSlot = 18;
    private static final int cardSmallSlot = 14;
    private static final int cardRecipeMinSlot = 7;

    // Color accents still used by line/text overlays
    private static final int trackedRing = 0xFFEEF3F8;
    private static final int externalSelectedRing = 0xFF7FAFD6;
    private static final int linkDefault = 0xFF6B7077;
    private static final int linkCatalyst = 0xFF8E939A;
    private static final int linkTracked = 0xFFFFFFFF;
    private static final int layerLine = 0x70A9A9A9;
    private static final int dotClr = 0xFF666666;
    private static final int dotClrRoot = 0xFFFFFFFF;
    private static final int mmVp = 0xCFFFFFFF;
    private static final int matBaseFill = 0xFFCFCFCF;
    private static final int matShadow = 0xFF555555;
    private static final int matHighlight = 0xFFFFFFFF;
    private static final int matFrame = 0xFFE6E6E6;

    // Layout
    private static final int mmW = 128;
    private static final int mmH = 78;
    private static final int mmPad = 6;
    private static final int mmContentPad = 5;
    private static final double mmScaleShrink = 0.94;
    private static final int panelW = 200;
    private static final int panelPad = 5;
    private static final int barH = 3;
    private static final int boxH = 40;
    private static final int entryH = boxH + barH;
    private static final int entryGap = 3;
    private static final int secHdrH = 16;
    private static final int totalsH = 52;
    private static final int panelIconSz = 16;
    private static final int iconSlotSz = 22;
    private static final int indCellSz = 10;
    private static final int scrollbarW = 8;
    private static final int materialPanelHeaderH = 20;
    private static final int materialPanelSearchH = 18;
    private static final int machineCandidatePanelW = 96;
    private static final int machineCandidatePanelHeaderH = 18;
    private static final int machineCandidateCell = 22;
    private static final int machineCandidatePanelMaxRows = 3;
    private static final int contentCandidatePanelW = 160;
    private static final int contentCandidatePanelHeaderH = 18;
    private static final int contentCandidateCell = 20;
    private static final int contentCandidateCellGap = 0;
    private static final int contentCandidatePanelMaxCells = 21;
    private static final int contentCandidateGridX = 6;
    private static final int contentCandidateGridY = contentCandidatePanelHeaderH + 4;
    private static final int tagCandidateCell = 16;
    private static final int candidateScrollbarW = 6;
    private static final int maxTreeWidth = 3500;  // Width cap for auto-collapse
    private static final int maxLayoutCoordinate = 1_000_000_000;
    private static final float minZoom = 0.15f;
    private static final float maxZoom = 3.0f;
    private static final float detailLodZoom = 0.10f;
    private static final float zoomSnap = 0.001f;
    private static final float zoomSmoothSpeed = 0.6f;
    private static final float matScrollSpeed = 25f;
    private static final float matScrollSmooth = 0.35f;
    private static final float contentCandidateScrollSmooth = 0.35f;
    private static final long cycleIntervalTicks = 40;
    private static final int cardMetaText = 0xFF656E77;
    private static final int maxLayerLabelCache = 128;
    //endregion

    //region Inner Types

    /** A laid-out tree node with position. */
    private static class LayoutNode {
        final ProcessNode processNode;
        final String pathId;
        final int depth;
        final List<LayoutNode> children = new ArrayList<>();
        @Nullable List<ProcessEdge> incomingEdges;
        @Nullable LayoutLink incomingLink;
        int treeWidth;   // width of this subtree in pixels
        int x, y;        // top-left corner in world space
        boolean collapsed;
        boolean autoCollapsed;
        boolean referenceOnly;
        int originalChildCount;
        @Nullable NodeRenderModel renderModel;

        LayoutNode(ProcessNode pn, String pathId, int depth) {
            this.processNode = pn;
            this.pathId = pathId;
            this.depth = depth;
        }
        boolean isLeaf() { return processNode == null || children.isEmpty() && originalChildCount == 0; }
    }

    private record LayoutLink(LayoutNode source, LayoutNode target, String id) {}

    private enum MatCategory {
        EXTERNAL_INPUT(CirrusLangs.Calc.Tree.Material.externalInput, 0xFF55FFAA),
        BYPRODUCT(CirrusLangs.Calc.Tree.Material.byproduct, 0xFFFFCC44),
        CATALYST(CirrusLangs.Calc.Tree.Material.catalyst, 0xFFBB77FF),
        INTERMEDIATE(CirrusLangs.Calc.Tree.Material.intermediate, 0xFF9EA7AF);
        final dev.vfyjxf.cloudlib.data.lang.LangEntry label;
        final int color;
        MatCategory(dev.vfyjxf.cloudlib.data.lang.LangEntry l, int c) { this.label = l; this.color = c; }
    }

    private record MaterialEntry(
            String name, MaterialSlot refSlot,
            long needed, long produced, long missing, long inventory,
            MatCategory category, List<Integer> sources, List<Integer> consumers,
            double chance) {}

    private record DeferredItem(ItemStack stack, int x, int y, float scale) {}
    private record DeferredFluid(FluidStack fluid, int x, int y, float scale, float pt) {}
    private record LayerRange(int first, int last) {
        private boolean isEmpty() {
            return last < first;
        }
    }
    private record RenderOrigin(int x, int y) {
        private int localX(int worldX) {
            return localCoord(worldX, x);
        }

        private int localY(int worldY) {
            return localCoord(worldY, y);
        }
    }
    private static final class DynamicResourceTexture implements SizedTexture, BatchableTexture {
        private final ResourceLocation location;
        private final int width;
        private final int height;

        private DynamicResourceTexture(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.blit(location, x, y, width, height, 0, 0, this.width, this.height, this.width, this.height);
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            emitter.textured(location, x, y, width, height, 0.0f, 0.0f, 1.0f, 1.0f, color);
        }
    }
    private static final class RenderSession {
        private final SceneCanvas canvas;
        private final float pt;
        private @Nullable List<DeferredItem> deferredItems;
        private @Nullable List<DeferredFluid> deferredFluids;
        private @Nullable RecipeContentVisual[] contentVisuals;
        private @Nullable int[] contentVisualIndices;
        private @Nullable int[] contentXs;
        private @Nullable int[] contentYs;
        private @Nullable int[] contentSizes;
        private int contentCount;
        private @Nullable ContentStack<?, ?>[] baseStacks;
        private @Nullable int[] baseStackXs;
        private @Nullable int[] baseStackYs;
        private @Nullable int[] baseStackSizes;
        private int baseStackCount;
        private @Nullable String[] amountOverlayTexts;
        private @Nullable int[] amountOverlayXs;
        private @Nullable int[] amountOverlayYs;
        private int amountOverlayCount;
        private @Nullable int[] catalystOverlayXs;
        private @Nullable int[] catalystOverlayYs;
        private int catalystOverlayCount;
        private @Nullable int[] selectionOverlayXs;
        private @Nullable int[] selectionOverlayYs;
        private @Nullable int[] selectionOverlaySizes;
        private @Nullable int[] selectionOverlayColors;
        private int selectionOverlayCount;
        private @Nullable int[] machineOverlayXs;
        private @Nullable int[] machineOverlayYs;
        private @Nullable int[] machineOverlayColors;
        private @Nullable int[] machineOverlayFlags;
        private int machineOverlayCount;

        private RenderSession(SceneCanvas canvas, float pt) {
            this.canvas = canvas;
            this.pt = pt;
        }

        private void addContent(RecipeContentVisual visual, int visualIndex, int x, int y, int size) {
            if (visual != null && visual.notEmpty() && size > 0) {
                int index = contentCount++;
                contentVisuals = grow(contentVisuals, contentCount);
                contentVisualIndices = grow(contentVisualIndices, contentCount);
                contentXs = grow(contentXs, contentCount);
                contentYs = grow(contentYs, contentCount);
                contentSizes = grow(contentSizes, contentCount);
                contentVisuals[index] = visual;
                contentVisualIndices[index] = Math.max(0, visualIndex);
                contentXs[index] = x;
                contentYs[index] = y;
                contentSizes[index] = size;
            }
        }

        private void addBaseStack(ContentStack<?, ?> stack, int x, int y, int size) {
            if (stack != null && stack.notEmpty() && size > 0) {
                int index = baseStackCount++;
                baseStacks = grow(baseStacks, baseStackCount);
                baseStackXs = grow(baseStackXs, baseStackCount);
                baseStackYs = grow(baseStackYs, baseStackCount);
                baseStackSizes = grow(baseStackSizes, baseStackCount);
                baseStacks[index] = stack;
                baseStackXs[index] = x;
                baseStackYs[index] = y;
                baseStackSizes[index] = size;
            }
        }

        private void deferItem(ItemStack stack, int x, int y, float scale) {
            if (deferredItems == null) {
                deferredItems = new ArrayList<>(4);
            }
            deferredItems.add(new DeferredItem(stack, x, y, scale));
        }

        private void deferFluid(FluidStack fluid, int x, int y, float scale, float pt) {
            if (deferredFluids == null) {
                deferredFluids = new ArrayList<>(4);
            }
            deferredFluids.add(new DeferredFluid(fluid, x, y, scale, pt));
        }

        private void addAmountOverlay(String text, int x, int y) {
            int index = amountOverlayCount++;
            amountOverlayTexts = grow(amountOverlayTexts, amountOverlayCount);
            amountOverlayXs = grow(amountOverlayXs, amountOverlayCount);
            amountOverlayYs = grow(amountOverlayYs, amountOverlayCount);
            amountOverlayTexts[index] = text;
            amountOverlayXs[index] = x;
            amountOverlayYs[index] = y;
        }

        private void addCatalystOverlay(int x, int y) {
            int index = catalystOverlayCount++;
            catalystOverlayXs = grow(catalystOverlayXs, catalystOverlayCount);
            catalystOverlayYs = grow(catalystOverlayYs, catalystOverlayCount);
            catalystOverlayXs[index] = x;
            catalystOverlayYs[index] = y;
        }

        private void addSelectionOverlay(int x, int y, int size, int color) {
            int index = selectionOverlayCount++;
            selectionOverlayXs = grow(selectionOverlayXs, selectionOverlayCount);
            selectionOverlayYs = grow(selectionOverlayYs, selectionOverlayCount);
            selectionOverlaySizes = grow(selectionOverlaySizes, selectionOverlayCount);
            selectionOverlayColors = grow(selectionOverlayColors, selectionOverlayCount);
            selectionOverlayXs[index] = x;
            selectionOverlayYs[index] = y;
            selectionOverlaySizes[index] = size;
            selectionOverlayColors[index] = color;
        }

        private void addMachineCandidateOverlay(int x, int y, int color, boolean current, boolean hovered) {
            int index = machineOverlayCount++;
            machineOverlayXs = grow(machineOverlayXs, machineOverlayCount);
            machineOverlayYs = grow(machineOverlayYs, machineOverlayCount);
            machineOverlayColors = grow(machineOverlayColors, machineOverlayCount);
            machineOverlayFlags = grow(machineOverlayFlags, machineOverlayCount);
            machineOverlayXs[index] = x;
            machineOverlayYs[index] = y;
            machineOverlayColors[index] = color;
            machineOverlayFlags[index] = (current ? 1 : 0) | (hovered ? 2 : 0);
        }

        private void flushIcons() {
            BatchableRecipeContentRenderer.Batch currentBatch = null;
            if (contentCount > 0 || baseStackCount > 0) {
                currentBatch = BatchableRecipeContentRenderer.Batch.create(canvas.layeredGraphics(), pt);
                for (int i = 0; i < contentCount; i++) {
                    int size = contentSizes[i];
                    currentBatch.addBaseVisual(contentVisuals[i], contentVisualIndices[i], contentXs[i], contentYs[i], size, size);
                }
                for (int i = 0; i < baseStackCount; i++) {
                    int size = baseStackSizes[i];
                    currentBatch.addBaseStack(baseStacks[i], baseStackXs[i], baseStackYs[i], size, size);
                }
                currentBatch.flush();
                if (contentCount > 0) {
                    Arrays.fill(contentVisuals, 0, contentCount, null);
                }
                if (baseStackCount > 0) {
                    Arrays.fill(baseStacks, 0, baseStackCount, null);
                }
                contentCount = 0;
                baseStackCount = 0;
            }
            boolean hasItems = deferredItems != null && !deferredItems.isEmpty();
            boolean hasFluids = deferredFluids != null && !deferredFluids.isEmpty();
            if (!hasItems && !hasFluids) {
                return;
            }
            var graphics = currentBatch == null ? canvas.layeredGraphics() : currentBatch.graphics();
            Lighting.setupFor3DItems();
            try {
                if (hasItems) {
                    for (DeferredItem item : deferredItems) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(item.x, item.y, 0);
                        graphics.pose().scale(item.scale, item.scale, 1);
                        graphics.pose().last().normal().identity();
                        graphics.renderItem(item.stack, 0, 0);
                        graphics.pose().popPose();
                    }
                }
                if (hasFluids) {
                    for (DeferredFluid fluid : deferredFluids) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(fluid.x, fluid.y, 0);
                        graphics.pose().scale(fluid.scale, fluid.scale, 1);
                        graphics.pose().last().normal().identity();
                        FluidStackRenderer.instance.renderBase(graphics, fluid.fluid, 16, 16, fluid.pt);
                        graphics.pose().popPose();
                    }
                }
                graphics.flush();
            } finally {
                Lighting.setupForFlatItems();
                if (deferredItems != null) {
                    deferredItems.clear();
                }
                if (deferredFluids != null) {
                    deferredFluids.clear();
                }
            }
        }

        private void finish() {
            flushIcons();
            flushSlotOverlays(canvas);
            flushMachineCandidateOverlays(canvas);
        }

        private void flushSlotOverlays(SceneCanvas canvas) {
            if (amountOverlayCount > 0) {
                for (int i = 0; i < amountOverlayCount; i++) {
                    canvas.drawString(amountOverlayTexts[i], amountOverlayXs[i], amountOverlayYs[i], textWhite, true);
                }
                if (amountOverlayTexts != null) {
                    Arrays.fill(amountOverlayTexts, 0, amountOverlayCount, null);
                }
                amountOverlayCount = 0;
            }
            if (catalystOverlayCount > 0) {
                for (int i = 0; i < catalystOverlayCount; i++) {
                    canvas.texture(Icon.Sign.Amount.catalyst, catalystOverlayXs[i], catalystOverlayYs[i], 5, 5);
                }
                catalystOverlayCount = 0;
            }
            if (selectionOverlayCount > 0) {
                for (int i = 0; i < selectionOverlayCount; i++) {
                    int x = selectionOverlayXs[i];
                    int y = selectionOverlayYs[i];
                    int size = selectionOverlaySizes[i];
                    drawOutline(canvas, x, y, size, size, selectionOverlayColors[i]);
                    int markSize = Math.min(9, Math.max(6, size - 2));
                    canvas.texture(Icon.finished, x + size - markSize - 1, y + 1, markSize, markSize);
                }
                selectionOverlayCount = 0;
            }
        }

        private void flushMachineCandidateOverlays(SceneCanvas canvas) {
            if (machineOverlayCount <= 0) {
                return;
            }
            for (int i = 0; i < machineOverlayCount; i++) {
                int x = machineOverlayXs[i];
                int y = machineOverlayYs[i];
                int color = machineOverlayColors[i];
                int flags = machineOverlayFlags[i];
                if ((flags & 1) != 0) {
                    drawOutline(canvas, x, y, machineCandidateCell, machineCandidateCell, color);
                    canvas.texture(Icon.finished, x + machineCandidateCell - 10, y + 1, 9, 9);
                } else if ((flags & 2) != 0) {
                    canvas.fill(x + 1, y + 1, machineCandidateCell - 2, machineCandidateCell - 2,
                            withAlpha(color, 0x24));
                }
            }
            machineOverlayCount = 0;
        }

        private static int[] grow(@Nullable int[] values, int minCapacity) {
            if (values == null) {
                return new int[Math.max(4, minCapacity)];
            }
            if (minCapacity <= values.length) {
                return values;
            }
            return Arrays.copyOf(values, Math.max(minCapacity, values.length * 2));
        }

        private static String[] grow(@Nullable String[] values, int minCapacity) {
            if (values == null) {
                return new String[Math.max(4, minCapacity)];
            }
            if (minCapacity <= values.length) {
                return values;
            }
            return Arrays.copyOf(values, Math.max(minCapacity, values.length * 2));
        }

        private static RecipeContentVisual[] grow(@Nullable RecipeContentVisual[] values, int minCapacity) {
            if (values == null) {
                return new RecipeContentVisual[Math.max(16, minCapacity)];
            }
            if (minCapacity <= values.length) {
                return values;
            }
            return Arrays.copyOf(values, Math.max(minCapacity, values.length * 2));
        }

        @SuppressWarnings("unchecked")
        private static ContentStack<?, ?>[] grow(@Nullable ContentStack<?, ?>[] values, int minCapacity) {
            if (values == null) {
                return new ContentStack[Math.max(16, minCapacity)];
            }
            if (minCapacity <= values.length) {
                return values;
            }
            return Arrays.copyOf(values, Math.max(minCapacity, values.length * 2));
        }
    }
    private record SlotGridPlan(int slotSize, int columns, int rows) {}
    private record WorldViewport(double minX, double minY, double maxX, double maxY) {
        boolean intersectsNode(LayoutNode node) {
            return node.x + nodeW >= minX && node.x <= maxX
                    && node.y + nodeH >= minY && node.y <= maxY;
        }

        boolean intersectsConnection(LayoutLink link) {
            int startX = link.source.x + nodeW / 2;
            int startY = link.source.y + nodeH;
            int endX = link.target.x + nodeW / 2;
            int endY = link.target.y;
            return Math.max(Math.min(startX, endX), minX) <= Math.min(Math.max(startX, endX), maxX)
                    && Math.max(Math.min(startY, endY), minY) <= Math.min(Math.max(startY, endY), maxY);
        }

        int firstLayer() {
            return Math.max(0, (int) Math.floor(minY / rowH) - 1);
        }

        int lastLayer(int maxDepth) {
            return Math.min(maxDepth, (int) Math.floor(maxY / rowH) + 1);
        }

        LayerRange layerRange(int maxDepth) {
            return new LayerRange(firstLayer(), lastLayer(maxDepth));
        }
    }
    public record ContentCandidateSelection(String pathId, String inputKey, String variantKey, int visualIndex) {}

    /** Breadcrumb entry for path context / trace display. */
    public record BreadcrumbEntry(String name, String pathId) {}
    public record MaterialPanelPalette(int baseFill, int shadow, int highlight, int frame) {}
    public record StepCardChrome(int machineTitleColor, boolean machineTitleShadow,
                                 boolean usesSlotBackedMachineIcon, boolean usesSlotBackedOutputIcon,
                                 boolean usesDrawnSeparator, boolean focusButtonUsesTreeGlyph, int sectionGap,
                                 boolean hasIntrinsicZone, boolean hasExtensionZone,
                                 boolean rendersRecipeFlow, boolean leafShowsCandidates,
                                 boolean leafRendersMachineSlot, boolean rendersMachinePlaceholder,
                                 boolean usesSingleCardBackground, boolean usesDividerLines,
                                 boolean machineOptionIndicatorAvoidsText, boolean extensionUsesIconMetrics,
                                 boolean hasProjectionAction, boolean usesLeftAccentStrip,
                                 boolean collapseChildCountRenderedInTooltip) {}
    public record CardZone(int x, int y, int w, int h) {}
    public record NodeCardLayout(CardZone intrinsic, CardZone machine, CardZone actions,
                                 CardZone recipe, CardZone inputs, CardZone arrow, CardZone outputs,
                                 CardZone primaryOutput, CardZone candidates, CardZone extension,
                                 int intrinsicDividerY, int dividerY, int actionGap) {}
    public record TagLeafCardLayout(CardZone header, CardZone candidates, CardZone action) {}
    private record SlotVisual(RecipeContent content, RecipeContentVisual visual, int count) {}
    private record NodeRenderModel(
            List<MachineInfo> machines,
            int accentColor,
            int nodeAccent,
            @Nullable MaterialSlot primaryOutput,
            @Nullable SlotVisual primaryVisual,
            @Nullable MaterialSlot selectableSlot,
            @Nullable SlotVisual selectableVisual,
            List<@Nullable SlotVisual> inputVisuals,
            List<@Nullable SlotVisual> outputVisuals,
            boolean recipeNode,
            boolean tagLikePrimary,
            boolean extensionVisible
    ) {}
    public record MinimapViewportRect(int x, int y, int w, int h) {}
    public record LayoutDebugInfo(
            int graphNodes,
            int graphEdges,
            int graphSinks,
            int graphSources,
            @Nullable String targetNodeId,
            @Nullable String preferredRootNodeId,
            int layoutNodes,
            int layoutLinks,
            int rootChildCount,
            int fullRootChildCount,
            boolean rootCollapsed,
            int maxDepth,
            int collapseLevel,
            int layoutWidth,
            int layoutHeight,
            String currentRootPathId
    ) {
        public String toDebugText() {
            return "graph=" + graphNodes + "/" + graphEdges
                    + " sinks=" + graphSinks
                    + " sources=" + graphSources
                    + " preferredRoot=" + preferredRootNodeId
                    + " target=" + targetNodeId
                    + " layout=" + layoutNodes + "/" + layoutLinks
                    + " rootChildren=" + rootChildCount
                    + " fullRootChildren=" + fullRootChildCount
                    + " rootCollapsed=" + rootCollapsed
                    + " depth=" + maxDepth
                    + " collapseLevel=" + collapseLevel
                    + " size=" + layoutWidth + "x" + layoutHeight
                    + " currentRoot=" + currentRootPathId;
        }
    }
    //endregion

    //region State

    private @Nullable ProcessGraph graph;
    private @Nullable ProcessNode targetNode;
    private long craftCount = 1;

    // Tree layout
    private @Nullable LayoutNode rootLayout;
    private List<LayoutNode> flatNodes = List.of();
    private List<LayoutLink> flatLinks = List.of();
    private Map<String, LayoutNode> layoutNodeByPath = Map.of();
    private Map<Integer, List<LayoutNode>> layerNodes = Map.of();
    private Map<Integer, List<LayoutNode>> layerNodesByX = Map.of();
    private int maxDepth = 0;
    private int layoutWidth = 2000, layoutHeight = 1000;
    // Minimap bounding box (world coords, computed from flatNodes)
    private int mmWorldMinX, mmWorldMinY, mmWorldW = 1, mmWorldH = 1;
    private Set<String> collapsedPaths = new HashSet<>();
    private Set<String> userExpandedPaths = new HashSet<>();
    private boolean forceExpandAll = false;
    private @Nullable String trackedNodeId = null;
    private Set<String> trackedPathIds = Set.of();
    private int highlightedLayer = -1;  // -1 = none
    private @Nullable LayoutNode fullRootLayout;
    private String currentRootPathId = "root";
    private @Nullable Runnable onSubtreeChanged;
    private Consumer<RecipeEntry<?>> onProjectionEdit = ignored -> {};
    private Consumer<ContentCandidateSelection> onContentCandidateSelected = ignored -> {};

    // Viewport
    private double panX = 0, panY = 0;
    private float zoom = 1.0f, targetZoom = 1.0f;
    private boolean dragging = false;
    private double dragStartX, dragStartY, dragPanStartX, dragPanStartY;
    private double zoomFocusWX, zoomFocusWY, zoomFocusSX, zoomFocusSY;
    private boolean pendingAutoFit = false;

    // Hover
    private @Nullable String hoveredNodeId = null;
    private @Nullable String hoveredCollapseNodeId = null;
    private @Nullable String hoveredMachineNodeId = null;
    private @Nullable String hoveredProjectionNodeId = null;
    private @Nullable String machineCandidatePanelNodeId = null;
    private @Nullable String contentCandidatePanelNodeId = null;
    private final Map<String, String> selectedMachineByPath = new HashMap<>();
    private final Map<String, Integer> selectedContentCandidateByPath = new HashMap<>();
    private Map<String, String> contentVariantPreferences = Map.of();
    private final Map<String, Integer> contentCandidateScrollRowsByPath = new HashMap<>();
    private final Map<String, Float> contentCandidateDisplayScrollRowsByPath = new HashMap<>();
    private @Nullable String externalHoveredNodeId = null;
    private @Nullable String externalSelectedNodeId = null;
    private boolean minimapDragging = false;

    // Material panel
    private boolean showMaterialPanel = false;
    private float matScrollY = 0, matScrollTargetY = 0;
    private final ScrollState materialWheelState = CirrusScroll.longVerticalState(Math.round(matScrollSpeed));
    private final ScrollState contentCandidateWheelState = CirrusScroll.longVerticalState(1);
    private boolean scrollbarDragging = false;
    private float scrollbarDragStartY = 0, scrollbarDragStartScroll = 0;
    private Map<MatCategory, List<MaterialEntry>> materialByCategory = new LinkedHashMap<>();
    private Map<MatCategory, List<MaterialEntry>> materialFilteredByCategory = new LinkedHashMap<>();
    private Map<String, MaterialEntry> materialEntryByName = Map.of();
    private int materialPanelContentH = 0;
    private int materialFilteredEntryCount = 0;
    private int materialFilterEvaluationCount = 0;
    private long totalTimeTicks = 0;
    private @Nullable String hoveredMatName = null;
    // Search / filter
    private String matFilterText = "";
    private boolean matSearchActive = false;
    private int matFilteredContentH = 0;
    // Sticky header tracking (set per frame in renderMaterialContent)
    private @Nullable MatCategory stickyHeaderCat = null;
    private int stickyHeaderCount = 0;
    private int stickyHeaderOffsetY = 0; // how much to nudge up when next section pushes

    // Rendering
    private long machineCycleTicks = 0;
    private static final int truncationCacheMaxEntries = 4096;
    private final LinkedHashMap<Long, String> truncationCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            return size() > truncationCacheMaxEntries;
        }
    };
    private final RecipeContentVisualCache visualCache = RecipeContentVisualCache.create(2048);
    private final IdentityHashMap<RecipeContent, RecipeContentVisual> frameVisualCache = new IdentityHashMap<>(512);
    private final IdentityHashMap<ItemStack, ContentStack<?, ?>> itemStackVisualCache = new IdentityHashMap<>(128);
    private final IdentityHashMap<FluidStack, ContentStack<?, ?>> fluidStackVisualCache = new IdentityHashMap<>(64);
    private final ArrayList<LayoutNode> visibleNodesScratch = new ArrayList<>(256);
    private final ArrayList<LayoutLink> visibleLinksScratch = new ArrayList<>(256);
    private final ArrayList<LayoutNode> visibleTrackedNodesScratch = new ArrayList<>(32);
    private final ArrayList<LayoutLink> visibleTrackedLinksScratch = new ArrayList<>(32);
    private int[] minimapPixelScratch = new int[0];
    private boolean minimapRunsDirty = true;
    private int minimapCacheContentW = -1;
    private int minimapCacheContentH = -1;
    private int minimapCacheWorldMinX = 0;
    private int minimapCacheWorldMinY = 0;
    private int minimapCacheWorldW = 0;
    private int minimapCacheWorldH = 0;
    private int minimapCacheNodeCount = -1;
    private int minimapCacheLinkCount = -1;
    private double minimapCacheScale = -1.0;
    private double minimapCacheDrawX = 0.0;
    private double minimapCacheDrawY = 0.0;
    private @Nullable DynamicTexture minimapTexture;
    private @Nullable ResourceLocation minimapTextureLocation;
    private @Nullable SizedTexture minimapTextureView;
    private int minimapTextureW = -1;
    private int minimapTextureH = -1;
    private final String[] layerLabelCache = new String[maxLayerLabelCache];
    private final Map<String, List<LayoutNode>> pathChildrenCache = new HashMap<>();
    private final Map<String, List<ProcessEdge>> incomingEdgesByNodeId = new HashMap<>();
    private final Map<String, List<MachineInfo>> machineCandidatesByNodeId = new HashMap<>();
    private final Map<String, NodeRenderModel> renderModelByNodeId = new HashMap<>();
    private final ArrayDeque<String> layoutBuildStack = new ArrayDeque<>();
    private final HashSet<String> layoutBuildStackIds = new HashSet<>();
    private final HashSet<String> layoutExpandedProcessNodeIds = new HashSet<>();
    //endregion

    //region Constructor
    public TreeCanvasWidget() {
        setFocusable(true);
        onDestroy(self -> releaseGraph());
        onMouseClicked((input, ctx) -> {
            FloatPos local = input.mouseRelative(TreeCanvasWidget.this);
            double mx = local.x(), my = local.y();
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;
            // Material panel owns its input region so clicks do not pass through to tree nodes.
            if (showMaterialPanel && isInMaterialPanel(mx, my)) {
                if (input.isLeftClick() && isInMaterialSearchField(mx, my)) {
                    matSearchActive = true;
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                if (input.isLeftClick() && isInMaterialSearchClear(mx, my) && !matFilterText.isEmpty()) {
                    matFilterText = "";
                    updateFilteredContentH();
                    matScrollY = 0;
                    matScrollTargetY = 0;
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                if (input.isLeftClick() && isOnScrollbarThumb(mx, my)) {
                    scrollbarDragging = true;
                    scrollbarDragStartY = (float) my;
                    scrollbarDragStartScroll = matScrollTargetY;
                } else if (input.isLeftClick()) {
                    matSearchActive = false;
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }
            // Minimap click
            if (input.isLeftClick() && isInMinimap(mx, my)) {
                minimapClickToPan(mx, my);
                minimapDragging = true;
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (input.isLeftClick() && machineCandidatePanelNodeId != null) {
                if (isInMachineCandidatePanel(mx, my)) {
                    if (isInMachineCandidatePanelClose(mx, my)) {
                        machineCandidatePanelNodeId = null;
                        CirrusUiSound.playClickSound();
                    } else {
                        int candidateIndex = machineCandidateIndexAt(mx, my);
                        if (candidateIndex >= 0) {
                            selectMachineCandidate(machineCandidatePanelNodeId, candidateIndex);
                            machineCandidatePanelNodeId = null;
                        }
                    }
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                machineCandidatePanelNodeId = null;
            }
            if (input.isLeftClick() && contentCandidatePanelNodeId != null) {
                if (isInContentCandidatePanel(mx, my)) {
                    if (isInContentCandidatePanelClose(mx, my)) {
                        contentCandidatePanelNodeId = null;
                        CirrusUiSound.playClickSound();
                    } else {
                        int candidateIndex = contentCandidateIndexAt(mx, my);
                        if (candidateIndex >= 0) {
                            selectContentCandidate(contentCandidatePanelNodeId, candidateIndex);
                            contentCandidatePanelNodeId = null;
                        }
                    }
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                contentCandidatePanelNodeId = null;
            }
            // Left click on collapse button
            if (input.isLeftClick()) {
                LayoutNode collapseHit = findCollapseButtonAt(mx, my);
                if (collapseHit != null) {
                    toggleCollapse(collapseHit.pathId);
                    CirrusUiSound.playClickSound();
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                // Click on a node to track it
                LayoutNode nodeHit = findNodeAt(mx, my);
                if (nodeHit != null) {
                    double wx = panX + mx / zoom, wy = panY + my / zoom;
                    if (nodeHit.originalChildCount > 0 && containsWorldPoint(focusButtonBounds(nodeHit.x, nodeHit.y), wx, wy)) {
                        focusSubgraph(nodeHit.pathId);
                        CirrusUiSound.playClickSound();
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                    if (isProjectionActionHit(nodeHit, mx, my)) {
                        setTrackedNodeId(nodeHit.pathId);
                        RecipeEntry<?> recipe = nodeHit.processNode == null ? null : nodeHit.processNode.recipe();
                        if (recipe != null) {
                            scene().nextTick(() -> onProjectionEdit.accept(recipe));
                            CirrusUiSound.playClickSound();
                        }
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                    if (isContentCandidateActionHit(nodeHit, mx, my)) {
                        setTrackedNodeId(nodeHit.pathId);
                        openContentCandidatePanel(nodeHit.pathId);
                        CirrusUiSound.playClickSound();
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                    if (isMachineSlotHit(nodeHit, mx, my)) {
                        setTrackedNodeId(nodeHit.pathId);
                        hoveredMachineNodeId = nodeHit.pathId;
                        machineCandidatePanelNodeId = nodeHit.pathId;
                        CirrusUiSound.playClickSound();
                        ctx.cancel();
                        return EventDispatch.consumed;
                    }
                    setTrackedNodeId(nodeHit.pathId);
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
            }
            // Middle-click drag
            if (input.key().getValue() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                dragging = true;
                zoom = targetZoom;
                dragStartX = mx; dragStartY = my;
                dragPanStartX = panX; dragPanStartY = panY;
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });
        onMouseReleased((input, ctx) -> {
            if (scrollbarDragging) { scrollbarDragging = false; ctx.cancel(); return EventDispatch.consumed; }
            if (minimapDragging) { minimapDragging = false; ctx.cancel(); return EventDispatch.consumed; }
            if (dragging) { dragging = false; ctx.cancel(); }
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, ctx) -> {
            FloatPos local = input.mouseRelative(TreeCanvasWidget.this);
            double mx = local.x(), my = local.y();
            if (scrollbarDragging) {
                if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
                    scrollbarDragging = false;
                    return EventDispatch.pass;
                }
                int visibleH = panelContentVisibleH();
                int maxScroll = materialPanelMaxScroll(visibleH);
                if (maxScroll <= 0) {
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                int thumbH = Math.max(16, (int) ((float) visibleH / matFilteredContentH * visibleH));
                int trackH = visibleH - thumbH;
                if (trackH > 0 && maxScroll > 0) {
                    float delta = (float) my - scrollbarDragStartY;
                    matScrollTargetY = Math.max(0, Math.min(maxScroll, scrollbarDragStartScroll + delta / trackH * maxScroll));
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (minimapDragging) { minimapClickToPan(mx, my); ctx.cancel(); return EventDispatch.consumed; }
            if (dragging) {
                panX = dragPanStartX - (mx - dragStartX) / zoom;
                panY = dragPanStartY - (my - dragStartY) / zoom;
                snapPanToPixelGrid();
                ctx.cancel();
            }
            return EventDispatch.consumed;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, ctx) -> {
            FloatPos local = sceneToLocal(mouseX, mouseY);
            double mx = local.x(), my = local.y();
            if (mx < 0 || my < 0 || mx > width() || my > height()) return EventDispatch.pass;
            if (showMaterialPanel && isInMaterialPanel(mx, my)) {
                int maxScroll = materialPanelMaxScroll();
                if (maxScroll <= 0) {
                    return EventDispatch.pass;
                }
                matScrollTargetY = Math.max(0, Math.min(maxScroll,
                        matScrollTargetY + materialWheelState.wheelDelta(0, scrollY).y()));
                ctx.cancel();
                return EventDispatch.consumed;
            }
            if (contentCandidatePanelNodeId != null && isInContentCandidatePanel(mx, my)) {
                if (scrollContentCandidatePanel(scrollY)) {
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
            }
            zoomFocusWX = panX + mx / (double) zoom;
            zoomFocusWY = panY + my / (double) zoom;
            zoomFocusSX = mx; zoomFocusSY = my;
            if (scrollY > 0) targetZoom = Math.min(maxZoom, targetZoom * 1.15f);
            else if (scrollY < 0) targetZoom = Math.max(minZoom, targetZoom / 1.15f);
            ctx.cancel();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, ctx) -> {
            int key = input.key().getValue();
            if (showMaterialPanel && handleMaterialPanelScrollKey(key)) {
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                navigateWithinLayer(-1);
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                navigateWithinLayer(1);
                return EventDispatch.consumed;
            }
            if (!showMaterialPanel && !matSearchActive) {
                if (key == GLFW.GLFW_KEY_UP) {
                    navigateLayers(-1);
                    return EventDispatch.consumed;
                }
                if (key == GLFW.GLFW_KEY_DOWN) {
                    navigateLayers(1);
                    return EventDispatch.consumed;
                }
            }
            if (!matSearchActive) return EventDispatch.pass;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                matSearchActive = false; matFilterText = ""; updateFilteredContentH();
                matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_DELETE && !matFilterText.isEmpty()) {
                matFilterText = "";
                updateFilteredContentH(); matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !matFilterText.isEmpty()) {
                matFilterText = matFilterText.substring(0, matFilterText.length() - 1);
                updateFilteredContentH(); matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onCharTyped((codePoint, modifiers, ctx) -> {
            if (!matSearchActive) return EventDispatch.pass;
            char c = (char) codePoint;
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-' || c == '.' || c == ':' || c == '/' || c == '#'
                    || c == '>' || c == '<' || c == '=') {
                matFilterText += c;
                updateFilteredContentH(); matScrollY = 0; matScrollTargetY = 0;
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseLeave((mouseX, mouseY, ctx) -> {
            scrollbarDragging = false;
            minimapDragging = false;
            dragging = false;
            hoveredNodeId = null;
            hoveredMachineNodeId = null;
        });
    }
    //endregion

    //region Public API

    public TreeCanvasWidget setCraftCount(long count) {
        this.craftCount = Math.max(1, count);
        return this;
    }

    public void toggleMaterialPanel() {
        showMaterialPanel = !showMaterialPanel;
        matScrollY = 0; matScrollTargetY = 0;
        matSearchActive = false; matFilterText = "";
        updateFilteredContentH();
    }

    public boolean isMaterialPanelVisible() { return showMaterialPanel; }

    static MaterialPanelPalette debugMaterialPanelPalette() {
        return new MaterialPanelPalette(matBaseFill, matShadow, matHighlight, matFrame);
    }

    static StepCardChrome debugStepCardChrome() {
        return new StepCardChrome(cardMetaText, false, true, true, false, true, stepSectionGap,
                true, true, true, true, false, false,
                true, false, true, true, true, false, true);
    }

    static NodeCardLayout debugRecipeNodeCardLayout() {
        return recipeNodeCardLayout(0, 0);
    }

    static TagLeafCardLayout debugTagLeafCardLayout() {
        return tagLeafCardLayout(0, 0);
    }

    static int debugNodeWidth() {
        return nodeW;
    }

    static int debugNodeHeight() {
        return nodeH;
    }

    static int debugMinimapWidth() {
        return mmW;
    }

    static int debugMinimapHeight() {
        return mmH;
    }

    static boolean debugMinimapShowsTitle() {
        return false;
    }

    static boolean debugTrackedLinksUseGlow() {
        return false;
    }

    static boolean debugStepCardsRenderTierByDefault() {
        return false;
    }

    static boolean debugMaterialPanelSearchAlwaysVisible() {
        return true;
    }

    static boolean debugMachineCandidatePanelUsesCustomRendering() {
        return true;
    }

    static int debugMachineCandidatePanelMaxRows() {
        return machineCandidatePanelMaxRows;
    }

    static boolean debugMachineCandidateSelectionOverlayRendersAfterIcons() {
        return true;
    }

    static boolean debugMachineTooltipsUseTextOnlyMachineRows() {
        return true;
    }

    static int debugContentCandidatePanelMaxCells() {
        return contentCandidatePanelMaxCells;
    }

    static boolean debugTreeContentUsesCachedVisualBatch() {
        return true;
    }

    static boolean debugCollapseToggleReusesVirtualLayoutNodes() {
        return true;
    }

    static boolean debugCollapseControlUsesLightweightDrawing() {
        return true;
    }

    static int debugTagCandidateCellSize() {
        return tagCandidateCell;
    }

    void debugSetSize(int width, int height) {
        setSize(width, height);
    }

    static float debugMachineCandidatePanelScale(float zoom) {
        return machineCandidatePanelScale(zoom);
    }

    static int debugRecipePreviewSlotSize(int inputCount, int outputCount) {
        NodeCardLayout layout = recipeNodeCardLayout(0, 0, false);
        return recipePreviewSlotSize(inputCount, outputCount, layout.inputs(), layout.outputs());
    }

    static boolean debugRecipePreviewUsesUnifiedSlotGroups() {
        return true;
    }

    static boolean debugSingleMaterialLeafUsesCompactLayout() {
        return true;
    }

    static MinimapViewportRect debugClampMinimapViewportRect(int contentX, int contentY, int contentW, int contentH,
                                                             int rawX, int rawY, int rawW, int rawH) {
        return clampMinimapViewportRect(contentX, contentY, contentW, contentH, rawX, rawY, rawW, rawH);
    }

    int debugMaterialEntryCount() {
        return materialByCategory.values().stream().mapToInt(List::size).sum();
    }

    int debugFilteredMaterialEntryCount() {
        return filteredMaterialEntryCount();
    }

    int debugMaterialFilterEvaluationCount() {
        return materialFilterEvaluationCount;
    }

    int debugMaterialFilteredCategoryCount() {
        return materialFilteredByCategory.size();
    }

    int debugMaterialPanelContentTop() {
        return materialPanelContentTop();
    }

    int debugVisibleNodeCount(int viewportWidth, int viewportHeight) {
        WorldViewport viewport = worldViewport(viewportWidth, viewportHeight);
        int count = 0;
        for (LayoutNode node : flatNodes) {
            if (viewport.intersectsNode(node)) {
                count++;
            }
        }
        return count;
    }

    int debugVisibleConnectionCount(int viewportWidth, int viewportHeight) {
        WorldViewport viewport = worldViewport(viewportWidth, viewportHeight);
        int count = 0;
        for (LayoutLink link : flatLinks) {
            if (viewport.intersectsConnection(link)) {
                count++;
            }
        }
        return count;
    }

    boolean debugMaterialPanelNeedsScrollbar(int viewportHeight) {
        return materialPanelNeedsScrollbar(materialPanelVisibleHeight(viewportHeight));
    }

    LayoutDebugInfo debugLayoutInfo() {
        return layoutDebugInfo();
    }

    int debugLayoutNodeCount() {
        return flatNodes.size();
    }

    int debugRenderModelCacheSize() {
        return renderModelByNodeId.size();
    }

    boolean debugRetainsGraphForTests() {
        return graph != null;
    }

    int debugMinimapScratchLengthForTests() {
        return minimapPixelScratch.length;
    }

    int debugRootChildCount() {
        return rootLayout == null ? 0 : rootLayout.originalChildCount;
    }

    boolean debugMachineCandidatePanelVisible() {
        return machineCandidatePanelNodeId != null && machineCandidatePanelBounds() != null;
    }

    boolean debugContentCandidatePanelVisible() {
        return contentCandidatePanelNodeId != null && contentCandidatePanelBounds() != null;
    }

    @Nullable int[] debugMachineCandidatePanelBounds() {
        int[] bounds = machineCandidatePanelBounds();
        return bounds == null ? null : Arrays.copyOf(bounds, bounds.length);
    }

    @Nullable int[] debugContentCandidatePanelBounds() {
        int[] bounds = contentCandidatePanelBounds();
        return bounds == null ? null : Arrays.copyOf(bounds, bounds.length);
    }

    float debugMachineCandidatePanelScale() {
        return machineCandidatePanelScale();
    }

    float debugContentCandidatePanelScale() {
        return contentCandidatePanelScale();
    }

    boolean debugOpenMachineCandidatePanelForPath(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        if (node == null || node.processNode == null) {
            return false;
        }
        machineCandidatePanelNodeId = pathId;
        return true;
    }

    boolean debugOpenContentCandidatePanelForPath(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        if (node == null || node.processNode == null) {
            return false;
        }
        NodeRenderModel model = renderModel(node);
        SlotVisual visual = model.selectableVisual();
        if (model.selectableSlot() == null || visual == null || visual.count() <= 1) {
            return false;
        }
        openContentCandidatePanel(pathId);
        return true;
    }

    boolean debugSelectMachineCandidateForPath(String pathId, int index) {
        return selectMachineCandidate(pathId, index);
    }

    int debugMachineCandidateCount(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        return node == null || node.processNode == null ? 0 : renderModel(node).machines().size();
    }

    List<String> debugMachineCandidateNames(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        if (node == null || node.processNode == null) {
            return List.of();
        }
        return renderModel(node).machines().stream().map(MachineInfo::name).toList();
    }

    boolean debugSelectContentCandidateForPath(String pathId, int index) {
        return selectContentCandidate(pathId, index);
    }

    @Nullable String debugSelectedMachineName(String pathId) {
        return selectedMachineByPath.get(pathId);
    }

    int debugSelectedContentCandidateIndex(String pathId) {
        return selectedContentCandidateByPath.getOrDefault(pathId, 0);
    }

    int debugContentCandidateVisualCount(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        SlotVisual visual = node == null || node.processNode == null ? null : renderModel(node).selectableVisual();
        return visual == null ? 0 : visual.count();
    }

    boolean debugContentCandidateNeedsScrollbar(String pathId) {
        LayoutNode previous = contentCandidatePanelNodeId == null ? null : findLayoutNode(contentCandidatePanelNodeId);
        String previousId = contentCandidatePanelNodeId;
        contentCandidatePanelNodeId = pathId;
        MaterialSlot slot = contentCandidatePanelSlot();
        int[] bounds = contentCandidatePanelBounds();
        float scale = contentCandidatePanelScale();
        SlotVisual visual = slot == null ? null : slotVisual(slot);
        boolean result = visual != null && bounds != null && contentCandidateNeedsScrollbar(visual.count(), bounds[3], scale);
        contentCandidatePanelNodeId = previous == null ? previousId : previous.pathId;
        return result;
    }

    int debugContentCandidateScrollRow(String pathId) {
        return contentCandidateScrollRowsByPath.getOrDefault(pathId, 0);
    }

    float debugContentCandidateDisplayScrollRow(String pathId) {
        return contentCandidateDisplayScrollRowsByPath.getOrDefault(pathId, 0.0f);
    }

    boolean debugScrollContentCandidatePanel(double scrollY) {
        return scrollContentCandidatePanel(scrollY);
    }

    void debugAnimateSmooth() {
        animateSmooth();
    }

    /** Direct viewport manipulation for dump tests. */
    public void setViewport(double px, double py, float z) {
        this.panX = px; this.panY = py;
        this.zoom = Math.max(minZoom, Math.min(maxZoom, z));
        this.targetZoom = this.zoom;
    }

    public double getPanX() { return panX; }
    public double getPanY() { return panY; }
    public float getZoom() { return zoom; }

    boolean debugPendingAutoFit() {
        return pendingAutoFit;
    }

    long debugCraftCount() {
        return craftCount;
    }

    /** Scroll material panel programmatically. */
    public void setMaterialScroll(float scrollY) {
        int maxScroll = materialPanelMaxScroll();
        if (Float.isNaN(scrollY) || Float.isInfinite(scrollY)) {
            scrollY = 0;
        }
        float clamped = Math.max(0, Math.min(scrollY, maxScroll));
        this.matScrollY = clamped;
        this.matScrollTargetY = clamped;
    }

    /** Set material panel search filter for dump tests. */
    public void setMatFilter(String text) {
        String clean = text == null ? "" : text;
        this.matSearchActive = !clean.isEmpty();
        this.matFilterText = clean;
        updateFilteredContentH();
        sanitizeMaterialScroll();
    }

    /** Track a specific node by path ID. */
    public void setTrackedNode(@Nullable String pathId) {
        setTrackedNodeId(pathId);
    }

    public List<LayoutNode> getFlatNodes() { return flatNodes; }

    /** Center viewport on the root node at the given zoom level. */
    public void centerOnRoot(float z) {
        if (rootLayout == null) return;
        this.zoom = Math.max(minZoom, Math.min(maxZoom, z));
        this.targetZoom = this.zoom;
        this.panX = rootLayout.x + nodeW / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = rootLayout.y + nodeH / 2.0 - height() / (2.0 * zoom);
    }

    /** Center viewport on a node in the given layer at the given zoom level.
     *  Picks the node closest to the layer's horizontal centroid. */
    public void centerOnLayer(int layer, float z) {
        var nodes = layerNodes.getOrDefault(layer, List.of());
        if (nodes.isEmpty()) return;
        this.zoom = Math.max(minZoom, Math.min(maxZoom, z));
        this.targetZoom = this.zoom;
        double avgX = nodes.stream().mapToInt(n -> n.x + nodeW / 2).average().orElse(0);
        // Pick the node closest to the centroid (guarantees a node is on screen)
        LayoutNode closest = nodes.stream()
                .min(java.util.Comparator.comparingDouble(n -> Math.abs(n.x + nodeW / 2.0 - avgX)))
                .orElse(nodes.get(0));
        this.panX = closest.x + nodeW / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = closest.y + nodeH / 2.0 - height() / (2.0 * zoom);
    }

    /** Get the maximum tree depth (layer count - 1). */
    public int getMaxDepth() { return maxDepth; }

    // ═════════════ Layer navigation ═════════════

    /** A node of one layer, ordered left-to-right by canvas position. */
    public record LayerNodeView(String pathId, int x, int y) {
    }

    private @Nullable String lastNavigatedPathId;

    /** Nodes of the layer sorted by canvas X (left to right). */
    public List<LayerNodeView> getLayerNodeViewsByX(int layer) {
        List<LayoutNode> nodes = layerNodesByX.get(layer);
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        ArrayList<LayerNodeView> views = new ArrayList<>(nodes.size());
        for (LayoutNode node : nodes) {
            views.add(new LayerNodeView(node.pathId, node.x, node.y));
        }
        return List.copyOf(views);
    }

    /** Index of the first node of the layer (X order) at or right of the viewport's left edge. */
    public int firstVisibleLayerNodeIndex(int layer) {
        List<LayoutNode> nodes = layerNodesByX.get(layer);
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        double viewportMinX = panX;
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).x + nodeW >= viewportMinX) {
                return index;
            }
        }
        return nodes.size() - 1;
    }

    /** Display name of a node (primary output material name, else node label). */
    public @Nullable String layerNodeDisplayName(String pathId) {
        LayoutNode node = layoutNodeByPath.get(pathId);
        return node == null || node.processNode == null ? null : primaryName(node.processNode);
    }

    /** The node's primary visual variant for popup rendering, or null. */
    public @Nullable dev.vfyjxf.cirrus.api.content.value.ContentStack<?, ?> layerNodePrimaryStack(String pathId) {
        LayoutNode node = layoutNodeByPath.get(pathId);
        if (node == null) {
            return null;
        }
        NodeRenderModel model = renderModel(node);
        SlotVisual primaryVisual = model.primaryVisual();
        if (primaryVisual == null) {
            return null;
        }
        dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisual visual = primaryVisual.visual();
        return visual.isEmpty() ? null : visual.currentStack();
    }

    /** Stack count of the node's primary output (for simplified layer cards). */
    public int layerNodePrimaryCount(String pathId) {
        LayoutNode node = layoutNodeByPath.get(pathId);
        if (node == null) {
            return 0;
        }
        SlotVisual primaryVisual = renderModel(node).primaryVisual();
        return primaryVisual == null ? 0 : primaryVisual.count();
    }

    /** Focus (expand ancestors + center + track) a node by path id. */
    public void scrollToLayerNode(String pathId) {
        scrollToNode(pathId);
        lastNavigatedPathId = pathId;
    }

    /** Moves focus to the previous/next node within the current layer (X order). */
    public void navigateWithinLayer(int direction) {
        int layer = navigationLayer();
        List<LayoutNode> nodes = layerNodesByX.get(layer);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        int target = Math.max(0, Math.min(nodes.size() - 1, navigationIndex(nodes) + direction));
        scrollToLayerNode(nodes.get(target).pathId);
    }

    /** Moves focus to the closest node (by X) one layer up/down; skips empty layers. */
    public void navigateLayers(int direction) {
        int layer = navigationLayer();
        int referenceX = navigationReferenceX();
        int step = direction >= 0 ? 1 : -1;
        for (int candidate = layer + step; candidate >= 0 && candidate <= maxDepth; candidate += step) {
            LayoutNode best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (LayoutNode node : layerNodesByX.getOrDefault(candidate, List.of())) {
                int distance = Math.abs(node.x + nodeW / 2 - referenceX);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = node;
                }
            }
            if (best != null) {
                scrollToLayerNode(best.pathId);
                return;
            }
        }
    }

    private int navigationLayer() {
        String tracked = lastNavigatedPathId != null ? lastNavigatedPathId : trackedNodeId;
        LayoutNode node = tracked == null ? null : layoutNodeByPath.get(tracked);
        return node != null ? node.depth : getViewportCenterLayer();
    }

    private int navigationIndex(List<LayoutNode> nodes) {
        String tracked = lastNavigatedPathId != null ? lastNavigatedPathId : trackedNodeId;
        if (tracked != null) {
            for (int index = 0; index < nodes.size(); index++) {
                if (nodes.get(index).pathId.equals(tracked)) {
                    return index;
                }
            }
        }
        int centerX = (int) (panX + (viewportW() / zoom) * 0.5);
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < nodes.size(); index++) {
            int distance = Math.abs(nodes.get(index).x + nodeW / 2 - centerX);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private int navigationReferenceX() {
        String tracked = lastNavigatedPathId != null ? lastNavigatedPathId : trackedNodeId;
        LayoutNode node = tracked == null ? null : layoutNodeByPath.get(tracked);
        if (node != null) {
            return node.x + nodeW / 2;
        }
        return (int) (panX + (viewportW() / zoom) * 0.5);
    }

    /** Layer index at current viewport center (for layer-nav tracking). */
    public int getViewportCenterLayer() {
        if (maxDepth <= 0) return 0;
        double centerWorldY = panY + (height() / zoom) * 0.5;
        int layer = (int) Math.floor(centerWorldY / rowH);
        if (layer < 0) return 0;
        return Math.min(layer, maxDepth);
    }

    /** Set the highlighted (active) layer. -1 = none. */
    public void setHighlightedLayer(int layer) {
        this.highlightedLayer = layer;
    }

    public int getHighlightedLayer() { return highlightedLayer; }

    public void setOnSubtreeChanged(@Nullable Runnable callback) {
        this.onSubtreeChanged = callback;
    }

    public void setOnProjectionEdit(@Nullable Consumer<RecipeEntry<?>> callback) {
        this.onProjectionEdit = callback == null ? ignored -> {} : callback;
    }

    public void setOnContentCandidateSelected(@Nullable Consumer<ContentCandidateSelection> callback) {
        this.onContentCandidateSelected = callback == null ? ignored -> {} : callback;
    }

    public void setContentVariantPreferences(Map<String, String> preferences) {
        this.contentVariantPreferences = Map.copyOf(preferences);
        remapSelectedContentCandidatesFromPreferences();
    }

    public @Nullable String getTrackedNodeId() { return trackedNodeId; }
    public String getCurrentRootPathId() { return currentRootPathId; }

    public int getLayerNodeCount(int layer) {
        return layerNodes.getOrDefault(layer, List.of()).size();
    }

    private void setTrackedNodeId(@Nullable String pathId) {
        if (Objects.equals(this.trackedNodeId, pathId)) {
            return;
        }
        this.trackedNodeId = pathId;
        rebuildTrackedPathCache();
        markMinimapDirty();
    }

    private void rebuildTrackedPathCache() {
        if (trackedNodeId == null || trackedNodeId.isBlank() || layoutNodeByPath.isEmpty()) {
            trackedPathIds = Set.of();
            return;
        }
        String[] parts = trackedNodeId.split("-");
        LinkedHashSet<String> ids = new LinkedHashSet<>(parts.length);
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i == 0) {
                current.append(parts[i]);
            } else {
                current.append("-").append(parts[i]);
            }
            String path = current.toString();
            if (layoutNodeByPath.containsKey(path)) {
                ids.add(path);
            }
        }
        trackedPathIds = ids.isEmpty() ? Set.of() : Set.copyOf(ids);
    }

    /** Highlight a node from external UI (e.g., path breadcrumbs). */
    public void setExternalHoveredNode(@Nullable String pathId) {
        externalHoveredNodeId = pathId;
    }

    /** Persistently select a node from external UI without mutating tracked/root path states. */
    public void setExternalSelectedNode(@Nullable String pathId) {
        externalSelectedNodeId = pathId;
    }

    /** Build recipe tooltip for a specific node path; null when path not found. */
    public @Nullable Tooltip buildNodeTooltipForPath(String pathId) {
        return buildNodeTooltipForPath(pathId, null);
    }

    /** Build recipe tooltip for a specific node path with breadcrumb source metadata. */
    public @Nullable Tooltip buildNodeTooltipForPath(String pathId, @Nullable String sourceLabel) {
        LayoutNode ln = findLayoutNode(pathId);
        LayoutNode fullNode = fullRootLayout != null ? findInTree(fullRootLayout, pathId) : null;
        LayoutNode node = ln != null ? ln : fullNode;
        if (node == null || node.processNode == null) return null;

        Tooltip tip = Tooltip.create();
        appendNodeTooltip(tip, node);
        return tip;
    }

    /** Get pathIds of all nodes in a given layer. */
    public List<String> getLayerNodePathIds(int layer) {
        return layerNodes.getOrDefault(layer, List.of()).stream()
                .map(n -> n.pathId).toList();
    }

    /** Focus on a subtree rooted at the given pathId. */
    public void focusSubgraph(String pathId) {
        if (fullRootLayout == null) return;
        if (pathId.equals(currentRootPathId)) return; // already focused here
        LayoutNode sub = findInTree(fullRootLayout, pathId);
        if (sub == null) return;
        currentRootPathId = pathId;
        rootLayout = sub;
        reflattenFromRoot();
        computeMaterialSummary();
        // Center on the subtree root at current zoom (don't reset zoom)
        centerOnNode(sub.pathId);
        if (onSubtreeChanged != null) onSubtreeChanged.run();
    }

    /** Reset to the full tree view. */
    public void resetToFullTree() {
        if (fullRootLayout == null || "root".equals(currentRootPathId)) return;
        String previousPathId = currentRootPathId;
        currentRootPathId = "root";
        rootLayout = fullRootLayout;
        reflattenFromRoot();
        computeMaterialSummary();
        // Center on the node we came back from, keeping current zoom
        LayoutNode prev = findInTree(fullRootLayout, previousPathId);
        if (prev != null) {
            centerOnNode(prev.pathId);
        }
        if (onSubtreeChanged != null) onSubtreeChanged.run();
    }

    /** Center viewport on a specific node by pathId. */
    public void centerOnNode(String pathId) {
        LayoutNode node = fullRootLayout != null ? findInTree(fullRootLayout, pathId) : null;
        if (node == null) return;
        this.panX = node.x + nodeW / 2.0 - viewportW() / (2.0 * zoom);
        this.panY = node.y + nodeH / 2.0 - height() / (2.0 * zoom);
    }

    /**
     * Navigate the path-context breadcrumb to its corresponding subtree root and
     * keep the clicked node centered after the root switch.
     */
    public void navigatePathContextTo(String pathId) {
        if ("root".equals(pathId)) {
            resetToFullTree();
            centerOnNode("root");
            return;
        }
        focusSubgraph(pathId);
        centerOnNode(pathId);
    }

    /** Expand ancestors, set tracked node, and center on it. */
    public void scrollToNode(String pathId) {
        String[] parts = pathId.split("-");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i == 0) current.append(parts[i]);
            else current.append("-").append(parts[i]);
            String ancestor = current.toString();
            collapsedPaths.remove(ancestor);
            userExpandedPaths.add(ancestor);
            expandExistingNode(ancestor);
        }
        float keepZoom = this.zoom;
        setTrackedNodeId(pathId);
        refreshVisibleLayoutFromFullRoot();
        notifyTreeStructureChanged();
        this.zoom = keepZoom;
        this.targetZoom = keepZoom;
        centerOnNode(pathId);
    }

    /** Select and jump to a node from path breadcrumbs in a full-tree context. */
    public void selectAndJumpToPath(String pathId) {
        if (!"root".equals(currentRootPathId)) {
            resetToFullTree();
        }
        scrollToNode(pathId);
    }

    /** Expand ancestors and focus node, but do not change tracked node or root/subgraph context. */
    public void focusNodeWithoutCropping(String pathId) {
        String[] parts = pathId.split("-");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i == 0) current.append(parts[i]);
            else current.append("-").append(parts[i]);
            String ancestor = current.toString();
            collapsedPaths.remove(ancestor);
            userExpandedPaths.add(ancestor);
            expandExistingNode(ancestor);
        }
        float keepZoom = this.zoom;
        refreshVisibleLayoutFromFullRoot();
        notifyTreeStructureChanged();
        this.zoom = keepZoom;
        this.targetZoom = keepZoom;
        centerOnNode(pathId);
    }

    /** Get breadcrumbs from root to currentRootPathId. */
    public List<BreadcrumbEntry> getPathContextBreadcrumbs() {
        return getBreadcrumbs(currentRootPathId);
    }

    /** Get breadcrumbs from root to trackedNodeId. */
    public List<BreadcrumbEntry> getTraceBreadcrumbs() {
        return trackedNodeId != null ? getBreadcrumbs(trackedNodeId) : List.of();
    }
    //endregion

    //region Internals
    private List<BreadcrumbEntry> getBreadcrumbs(String pathId) {
        if (fullRootLayout == null) return List.of();
        List<BreadcrumbEntry> result = new ArrayList<>();
        String[] parts = pathId.split("-");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i == 0) current.append(parts[i]);
            else current.append("-").append(parts[i]);
            LayoutNode node = findInTree(fullRootLayout, current.toString());
            if (node != null && node.processNode != null) {
                result.add(new BreadcrumbEntry(node.processNode.name(), node.pathId));
            }
        }
        return result;
    }

    private @Nullable LayoutNode findInTree(@Nullable LayoutNode node, String pathId) {
        if (node == null) return null;
        ArrayDeque<LayoutNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            LayoutNode current = stack.pop();
            if (current.pathId.equals(pathId)) {
                return current;
            }
            for (int i = current.children.size() - 1; i >= 0; i--) {
                stack.push(current.children.get(i));
            }
        }
        return null;
    }

    private void reflattenFromRoot() {
        if (rootLayout == null) return;
        if (fullRootLayout != null) {
            refreshVisibleLayoutFromFullRoot();
            return;
        }
        List<LayoutNode> fNodes = new ArrayList<>();
        List<LayoutLink> fLinks = new ArrayList<>();
        Map<Integer, List<LayoutNode>> lNodes = new HashMap<>();
        flatten(rootLayout, fNodes, fLinks, lNodes);
        this.flatNodes = fNodes;
        this.flatLinks = fLinks;
        this.layoutNodeByPath = indexLayoutNodes(fNodes);
        this.layerNodes = freezeLayerNodes(lNodes, false);
        this.layerNodesByX = freezeLayerNodes(lNodes, true);
        updateLayoutMetrics(fNodes, 0);
        rebuildTrackedPathCache();
        markMinimapDirty();
    }

    private int viewportW() {
        return showMaterialPanel ? width() - panelW - 2 : width();
    }

    // ═══════════════════ Data Setup ═══════════════════

    public TreeCanvasWidget setGraph(ProcessGraph graph) {
        this.graph = graph;
        this.showMaterialPanel = false;
        this.matSearchActive = false;
        this.matFilterText = "";
        this.matScrollY = 0;
        this.matScrollTargetY = 0;
        this.currentRootPathId = "root";
        this.externalHoveredNodeId = null;
        this.externalSelectedNodeId = null;
        this.machineCandidatePanelNodeId = null;
        this.contentCandidatePanelNodeId = null;
        this.trackedNodeId = null;
        this.trackedPathIds = Set.of();
        this.minimapRunsDirty = true;
        this.forceExpandAll = false;
        this.selectedMachineByPath.clear();
        this.selectedContentCandidateByPath.clear();
        this.contentCandidateScrollRowsByPath.clear();
        this.contentCandidateDisplayScrollRowsByPath.clear();
        this.targetNode = selectTargetNode(graph);
        rebuildTree();
        logLayoutState("tree.setGraph");
        remapSelectedContentCandidatesFromPreferences();
        resetView();
        computeMaterialSummary();
        return this;
    }

    void releaseGraph() {
        this.graph = null;
        this.targetNode = null;
        this.rootLayout = null;
        this.fullRootLayout = null;
        this.flatNodes = List.of();
        this.flatLinks = List.of();
        this.layoutNodeByPath = Map.of();
        this.layerNodes = Map.of();
        this.layerNodesByX = Map.of();
        this.maxDepth = 0;
        this.layoutWidth = 500;
        this.layoutHeight = 500;
        this.mmWorldMinX = 0;
        this.mmWorldMinY = 0;
        this.mmWorldW = 1;
        this.mmWorldH = 1;
        this.collapsedPaths.clear();
        this.userExpandedPaths.clear();
        this.forceExpandAll = false;
        this.trackedNodeId = null;
        this.trackedPathIds = Set.of();
        this.highlightedLayer = -1;
        this.currentRootPathId = "root";
        this.onSubtreeChanged = null;
        this.onProjectionEdit = ignored -> {};
        this.onContentCandidateSelected = ignored -> {};
        this.panX = 0;
        this.panY = 0;
        this.zoom = 1.0f;
        this.targetZoom = 1.0f;
        this.dragging = false;
        this.pendingAutoFit = false;
        this.hoveredNodeId = null;
        this.hoveredCollapseNodeId = null;
        this.hoveredMachineNodeId = null;
        this.hoveredProjectionNodeId = null;
        this.machineCandidatePanelNodeId = null;
        this.contentCandidatePanelNodeId = null;
        this.selectedMachineByPath.clear();
        this.selectedContentCandidateByPath.clear();
        this.contentVariantPreferences = Map.of();
        this.contentCandidateScrollRowsByPath.clear();
        this.contentCandidateDisplayScrollRowsByPath.clear();
        this.externalHoveredNodeId = null;
        this.externalSelectedNodeId = null;
        this.minimapDragging = false;
        this.showMaterialPanel = false;
        this.matScrollY = 0;
        this.matScrollTargetY = 0;
        this.scrollbarDragging = false;
        this.materialByCategory = new LinkedHashMap<>();
        this.materialFilteredByCategory = new LinkedHashMap<>();
        this.materialEntryByName = Map.of();
        this.materialPanelContentH = 0;
        this.materialFilteredEntryCount = 0;
        this.materialFilterEvaluationCount = 0;
        this.totalTimeTicks = 0;
        this.hoveredMatName = null;
        this.matFilterText = "";
        this.matSearchActive = false;
        this.matFilteredContentH = 0;
        this.stickyHeaderCat = null;
        this.stickyHeaderCount = 0;
        this.stickyHeaderOffsetY = 0;
        this.visibleNodesScratch.clear();
        this.visibleLinksScratch.clear();
        this.visibleTrackedNodesScratch.clear();
        this.visibleTrackedLinksScratch.clear();
        this.minimapPixelScratch = new int[0];
        this.minimapRunsDirty = true;
        this.minimapCacheContentW = -1;
        this.minimapCacheContentH = -1;
        this.minimapCacheWorldMinX = 0;
        this.minimapCacheWorldMinY = 0;
        this.minimapCacheWorldW = 0;
        this.minimapCacheWorldH = 0;
        this.minimapCacheNodeCount = -1;
        this.minimapCacheLinkCount = -1;
        this.minimapCacheScale = -1.0;
        this.minimapCacheDrawX = 0.0;
        this.minimapCacheDrawY = 0.0;
        this.minimapTextureW = -1;
        this.minimapTextureH = -1;
        Arrays.fill(this.layerLabelCache, null);
        clearRenderCaches();
        this.layoutBuildStack.clear();
        this.layoutBuildStackIds.clear();
        this.layoutExpandedProcessNodeIds.clear();
        releaseMinimapTexture();
    }

    private @Nullable ProcessNode selectTargetNode(ProcessGraph graph) {
        String preferredRoot = graph.preferredRootNodeId();
        if (preferredRoot != null) {
            ProcessNode preferred = graph.node(preferredRoot);
            if (preferred != null) {
                return preferred;
            }
        }
        List<ProcessNode> sinks = graph.sinks();
        if (sinks.isEmpty()) {
            return null;
        }
        return sinks.stream()
                .max(Comparator
                        .comparingInt((ProcessNode node) -> graph.incomingEdges(node.id()).size())
                        .thenComparing(node -> node.recipe() != null)
                        .thenComparing(ProcessNode::id))
                .orElse(sinks.get(0));
    }

    private int collapseLevel = 0; // current auto-collapse aggressiveness

    private void rebuildTree() {
        rebuildTree(true);
    }

    /**
     * @param autoCollapse if true, reset collapseLevel and iterate width constraint.
     *                     if false, keep current collapseLevel (used for user-initiated toggle).
     */
    private void rebuildTree(boolean autoCollapse) {
        if (graph == null || targetNode == null) {
            rootLayout = null; fullRootLayout = null;
            flatNodes = List.of(); flatLinks = List.of();
            layoutNodeByPath = Map.of();
            layerNodes = Map.of(); layerNodesByX = Map.of(); maxDepth = 0;
            rebuildTrackedPathCache();
            clearRenderCaches();
            return;
        }
        clearRenderCaches();
        if (autoCollapse) {
            // Build tree, iteratively increasing collapse aggressiveness until width fits
            collapseLevel = 0;
            for (int attempt = 0; attempt < 6; attempt++) {
                pathChildrenCache.clear();
                fullRootLayout = buildLayoutTreeRoot(targetNode);
                computeWidths(fullRootLayout);
                if (fullRootLayout.treeWidth <= maxTreeWidth) break;
                collapseLevel++;
            }
        } else {
            // User-initiated toggle: keep current collapseLevel, just rebuild once
            pathChildrenCache.clear();
            fullRootLayout = buildLayoutTreeRoot(targetNode);
            computeWidths(fullRootLayout);
        }

        refreshVisibleLayoutFromFullRoot();
    }

    private void refreshVisibleLayoutFromFullRoot() {
        if (fullRootLayout == null) {
            rootLayout = null;
            flatNodes = List.of();
            flatLinks = List.of();
            layoutNodeByPath = Map.of();
            layerNodes = Map.of();
            layerNodesByX = Map.of();
            maxDepth = 0;
            layoutWidth = 500;
            layoutHeight = 500;
            mmWorldMinX = 0;
            mmWorldMinY = 0;
            mmWorldW = 1;
            mmWorldH = 1;
            rebuildTrackedPathCache();
            markMinimapDirty();
            return;
        }
        computeWidths(fullRootLayout);
        int offsetX = Math.max(0, (Math.min(2000, maxTreeWidth) - fullRootLayout.treeWidth) / 2);
        assignPositions(fullRootLayout, offsetX);

        // Use subtree root if set
        LayoutNode effectiveRoot = fullRootLayout;
        if (!"root".equals(currentRootPathId)) {
            LayoutNode sub = findInTree(fullRootLayout, currentRootPathId);
            if (sub != null) effectiveRoot = sub;
            else currentRootPathId = "root";
        }
        rootLayout = effectiveRoot;

        // Flatten from effective root
        List<LayoutNode> fNodes = new ArrayList<>();
        List<LayoutLink> fLinks = new ArrayList<>();
        Map<Integer, List<LayoutNode>> lNodes = new HashMap<>();
        flatten(effectiveRoot, fNodes, fLinks, lNodes);

        this.flatNodes = fNodes;
        this.flatLinks = fLinks;
        this.layoutNodeByPath = indexLayoutNodes(fNodes);
        this.layerNodes = freezeLayerNodes(lNodes, false);
        this.layerNodesByX = freezeLayerNodes(lNodes, true);
        updateLayoutMetrics(fNodes, fullRootLayout.treeWidth + offsetX * 2);
        rebuildTrackedPathCache();
        markMinimapDirty();

        // Auto-track the deepest main trunk node
        if (trackedNodeId == null) {
            setTrackedNodeId(deepestDescendant(effectiveRoot).pathId);
        }
    }

    private void updateLayoutMetrics(List<LayoutNode> nodes, int widthCandidate) {
        if (nodes.isEmpty()) {
            this.maxDepth = 0;
            this.layoutWidth = Math.max(500, widthCandidate);
            this.layoutHeight = 500;
            mmWorldMinX = 0;
            mmWorldMinY = 0;
            mmWorldW = 1;
            mmWorldH = 1;
            return;
        }
        int depth = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (LayoutNode node : nodes) {
            depth = Math.max(depth, node.depth);
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x + nodeW);
            maxY = Math.max(maxY, node.y + nodeH);
        }
        this.maxDepth = depth;
        int contentWidth = Math.max(1, maxX - minX);
        int targetWidth = widthCandidate > 0 ? widthCandidate : contentWidth + 200;
        this.layoutWidth = Math.max(500, targetWidth);
        this.layoutHeight = Math.max(500, (maxDepth + 1) * rowH + 200);
        mmWorldMinX = minX;
        mmWorldMinY = minY;
        mmWorldW = Math.max(1, maxX - minX);
        mmWorldH = Math.max(1, maxY - minY);
    }

    private LayoutDebugInfo layoutDebugInfo() {
        int graphNodes = graph == null ? 0 : graph.nodeCount();
        int graphEdges = graph == null ? 0 : graph.edgeCount();
        int graphSinks = graph == null ? 0 : graph.sinks().size();
        int graphSources = graph == null ? 0 : graph.sources().size();
        return new LayoutDebugInfo(
                graphNodes,
                graphEdges,
                graphSinks,
                graphSources,
                targetNode == null ? null : targetNode.id(),
                graph == null ? null : graph.preferredRootNodeId(),
                flatNodes.size(),
                flatLinks.size(),
                rootLayout == null ? 0 : rootLayout.originalChildCount,
                fullRootLayout == null ? 0 : fullRootLayout.originalChildCount,
                rootLayout != null && rootLayout.collapsed,
                maxDepth,
                collapseLevel,
                layoutWidth,
                layoutHeight,
                currentRootPathId
        );
    }

    private void logLayoutState(String event) {
        LayoutDebugInfo info = layoutDebugInfo();
        if (info.graphNodes() > 1 && info.layoutNodes() <= 1) {
            logger.warn("{} {}", event, info.toDebugText());
        } else if (logger.isDebugEnabled()) {
            logger.debug("{} {}", event, info.toDebugText());
        }
    }

    private void clearRenderCaches() {
        frameVisualCache.clear();
        pathChildrenCache.clear();
        incomingEdgesByNodeId.clear();
        machineCandidatesByNodeId.clear();
        renderModelByNodeId.clear();
        visualCache.clear();
        itemStackVisualCache.clear();
        fluidStackVisualCache.clear();
    }

    private void markMinimapDirty() {
        minimapRunsDirty = true;
    }

    private void releaseMinimapTexture() {
        if (minimapTextureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(minimapTextureLocation);
        } else if (minimapTexture != null) {
            minimapTexture.close();
        }
        minimapTexture = null;
        minimapTextureLocation = null;
        minimapTextureView = null;
        minimapTextureW = -1;
        minimapTextureH = -1;
    }

    private void ensureMinimapRuns(SceneCanvas canvas) {
        MinimapProjection proj = minimapProjection();
        if (proj == null) {
            minimapRunsDirty = false;
            return;
        }
        int contentW = proj.contentW;
        int contentH = proj.contentH;
        if (!minimapRunsDirty
                && minimapCacheContentW == contentW
                && minimapCacheContentH == contentH
                && minimapCacheWorldMinX == mmWorldMinX
                && minimapCacheWorldMinY == mmWorldMinY
                && minimapCacheWorldW == mmWorldW
                && minimapCacheWorldH == mmWorldH
                && minimapCacheNodeCount == flatNodes.size()
                && minimapCacheLinkCount == flatLinks.size()
                && Double.compare(minimapCacheScale, proj.scale) == 0
                && Double.compare(minimapCacheDrawX, proj.drawX) == 0
                && Double.compare(minimapCacheDrawY, proj.drawY) == 0) {
            return;
        }
        rebuildMinimapRuns(proj, canvas);
    }

    private void rebuildMinimapRuns(MinimapProjection proj, SceneCanvas canvas) {
        int contentW = proj.contentW;
        int contentH = proj.contentH;
        if (contentW <= 0 || contentH <= 0 || mmWorldW <= 0 || mmWorldH <= 0) {
            releaseMinimapTexture();
            minimapRunsDirty = false;
            minimapCacheContentW = contentW;
            minimapCacheContentH = contentH;
            minimapCacheWorldMinX = mmWorldMinX;
            minimapCacheWorldMinY = mmWorldMinY;
            minimapCacheWorldW = mmWorldW;
            minimapCacheWorldH = mmWorldH;
            minimapCacheNodeCount = flatNodes.size();
            minimapCacheLinkCount = flatLinks.size();
            minimapCacheScale = proj.scale;
            minimapCacheDrawX = proj.drawX;
            minimapCacheDrawY = proj.drawY;
            return;
        }
        int pixelCount = contentW * contentH;
        if (minimapPixelScratch.length < pixelCount) {
            minimapPixelScratch = new int[pixelCount];
        }
        Arrays.fill(minimapPixelScratch, 0, pixelCount, 0);
        double sx = proj.scale;
        double sy = proj.scale;
        double ox = proj.drawX;
        double oy = proj.drawY;

        for (LayoutLink link : flatLinks) {
            int color = isNodeInTrackedPath(link.target.pathId) ? 0xFF9BA8B6 : 0xFF6F747B;
            int x0 = clampToInt((link.source.x + nodeW / 2.0 - mmWorldMinX) * sx + ox - proj.contentX, 0, contentW - 1);
            int y0 = clampToInt((link.source.y + nodeH - mmWorldMinY) * sy + oy - proj.contentY, 0, contentH - 1);
            int x1 = clampToInt((link.target.x + nodeW / 2.0 - mmWorldMinX) * sx + ox - proj.contentX, 0, contentW - 1);
            int y1 = clampToInt((link.target.y - mmWorldMinY) * sy + oy - proj.contentY, 0, contentH - 1);
            int midY = (y0 + y1) >>> 1;
            stampMinimapLine(contentW, contentH, x0, y0, x0, midY, color);
            stampMinimapLine(contentW, contentH, x0, midY, x1, midY, color);
            stampMinimapLine(contentW, contentH, x1, midY, x1, y1, color);
        }

        for (LayoutNode ln : flatNodes) {
            int rx = clampToInt((ln.x - mmWorldMinX) * sx + ox - proj.contentX, 0, contentW - 1);
            int ry = clampToInt((ln.y - mmWorldMinY) * sy + oy - proj.contentY, 0, contentH - 1);
            int rw = Math.max(1, (int) Math.ceil(nodeW * sx));
            int rh = Math.max(1, (int) Math.ceil(nodeH * sy));
            int color = minimapNodeColor(ln);
            stampMinimapRect(contentW, contentH, rx, ry, rw, rh, color);
        }

        uploadMinimapTexture(contentW, contentH);
        minimapRunsDirty = false;
        minimapCacheContentW = contentW;
        minimapCacheContentH = contentH;
        minimapCacheWorldMinX = mmWorldMinX;
        minimapCacheWorldMinY = mmWorldMinY;
        minimapCacheWorldW = mmWorldW;
        minimapCacheWorldH = mmWorldH;
        minimapCacheNodeCount = flatNodes.size();
        minimapCacheLinkCount = flatLinks.size();
        minimapCacheScale = proj.scale;
        minimapCacheDrawX = proj.drawX;
        minimapCacheDrawY = proj.drawY;
    }

    private void uploadMinimapTexture(int contentW, int contentH) {
        if (minimapTexture == null || minimapTextureW != contentW || minimapTextureH != contentH) {
            releaseMinimapTexture();
            minimapTexture = new DynamicTexture(contentW, contentH, true);
            minimapTextureLocation = Minecraft.getInstance().getTextureManager().register("cirrus_tree_minimap", minimapTexture);
            minimapTextureView = new DynamicResourceTexture(minimapTextureLocation, contentW, contentH);
            minimapTextureW = contentW;
            minimapTextureH = contentH;
        }
        NativeImage pixels = minimapTexture.getPixels();
        if (pixels == null) {
            minimapTextureView = null;
            return;
        }
        int index = 0;
        for (int y = 0; y < contentH; y++) {
            for (int x = 0; x < contentW; x++) {
                pixels.setPixelRGBA(x, y, argbToAbgr(minimapPixelScratch[index++]));
            }
        }
        minimapTexture.upload();
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00)
                | ((argb & 0x00FF0000) >>> 16)
                | ((argb & 0x000000FF) << 16);
    }

    private void stampMinimapRect(int contentW, int contentH, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || color == 0) return;
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(contentW, x + w);
        int y1 = Math.min(contentH, y + h);
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * contentW;
            for (int xx = x0; xx < x1; xx++) {
                minimapPixelScratch[row + xx] = color;
            }
        }
    }

    private void stampMinimapLine(int contentW, int contentH, int x0, int y0, int x1, int y1, int color) {
        if (color == 0) return;
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            if (x0 >= 0 && x0 < contentW && y0 >= 0 && y0 < contentH) {
                minimapPixelScratch[y0 * contentW + x0] = color;
            }
            if (x0 == x1 && y0 == y1) break;
            int twiceErr = err << 1;
            if (twiceErr > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (twiceErr < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static int clampToInt(double value, int min, int max) {
        int v = (int) Math.round(value);
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private LayoutNode buildLayoutTreeRoot(ProcessNode pn) {
        layoutBuildStack.clear();
        layoutBuildStackIds.clear();
        layoutExpandedProcessNodeIds.clear();
        return buildLayoutTree(pn, 0, "root");
    }

    private LayoutNode buildLayoutTree(ProcessNode pn, int depth, String pathId) {
        ProcessGraph currentGraph = graph;
        if (currentGraph == null) {
            return new LayoutNode(pn, pathId, depth);
        }
        LayoutNode node = new LayoutNode(pn, pathId, depth);
        initializeLayoutNode(node, pn);
        if (!shouldBuildChildren(node)) {
            return node;
        }

        enterLayoutNode(node);
        ArrayDeque<LayoutBuildFrame> stack = new ArrayDeque<>();
        stack.push(new LayoutBuildFrame(node));
        while (!stack.isEmpty()) {
            LayoutBuildFrame frame = stack.peek();
            List<ProcessEdge> incoming = frame.node.incomingEdges == null
                    ? List.of()
                    : frame.node.incomingEdges;
            if (frame.nextChildIndex >= incoming.size()) {
                stack.pop();
                leaveLayoutNode(frame.node);
                continue;
            }

            int childIndex = frame.nextChildIndex++;
            ProcessEdge edge = incoming.get(childIndex);
            ProcessNode child = currentGraph.node(edge.fromNodeId());
            if (child == null) {
                continue;
            }
            LayoutNode childNode = new LayoutNode(child, frame.node.pathId + "-" + childIndex, frame.node.depth + 1);
            initializeLayoutNode(childNode, child);
            frame.node.children.add(childNode);
            if (shouldBuildChildren(childNode)) {
                enterLayoutNode(childNode);
                stack.push(new LayoutBuildFrame(childNode));
            }
        }
        return node;
    }

    private void initializeLayoutNode(LayoutNode node, ProcessNode pn) {
        ProcessGraph currentGraph = graph;
        List<ProcessEdge> incoming = currentGraph == null
                ? List.of()
                : incomingEdgesByNodeId.computeIfAbsent(pn.id(), ignored -> List.copyOf(currentGraph.incomingEdges(pn.id())));
        node.incomingEdges = incoming;
        node.originalChildCount = incoming.size();

        boolean activeCycleTerminus = layoutBuildStackIds.contains(pn.id());
        boolean repeatedReference = forceExpandAll
                && !activeCycleTerminus
                && !layoutExpandedProcessNodeIds.add(pn.id());
        if (activeCycleTerminus || repeatedReference) {
            node.originalChildCount = 0;
            node.collapsed = false;
            node.autoCollapsed = true;
            node.referenceOnly = true;
            return;
        }

        boolean shouldAutoCollapse = false;
        int inputCount = incoming.size();
        int cl = collapseLevel;
        if (node.depth == 0) shouldAutoCollapse = inputCount > Math.max(3, 20 - cl * 4);
        else if (node.depth == 1) shouldAutoCollapse = inputCount > Math.max(2, 12 - cl * 3);
        else if (node.depth <= 3) shouldAutoCollapse = inputCount > Math.max(2, 6 - cl * 2);
        else if (node.depth <= 6) shouldAutoCollapse = inputCount >= Math.max(2, 4 - cl);
        else if (node.depth <= 10) shouldAutoCollapse = inputCount >= Math.max(1, 3 - cl);
        else shouldAutoCollapse = true;

        boolean isCollapsed = !forceExpandAll && (collapsedPaths.contains(node.pathId)
                || (shouldAutoCollapse && !userExpandedPaths.contains(node.pathId)));
        node.collapsed = isCollapsed;
        node.autoCollapsed = shouldAutoCollapse && !userExpandedPaths.contains(node.pathId) && !collapsedPaths.contains(node.pathId);
    }

    private boolean shouldBuildChildren(LayoutNode node) {
        return !node.collapsed && node.originalChildCount > 0 && node.processNode != null;
    }

    private void enterLayoutNode(LayoutNode node) {
        String id = node.processNode.id();
        layoutBuildStack.addLast(id);
        layoutBuildStackIds.add(id);
    }

    private void leaveLayoutNode(LayoutNode node) {
        String id = node.processNode.id();
        if (!layoutBuildStack.isEmpty() && Objects.equals(layoutBuildStack.peekLast(), id)) {
            layoutBuildStack.removeLast();
        } else {
            layoutBuildStack.remove(id);
        }
        layoutBuildStackIds.remove(id);
    }

    private static final class LayoutBuildFrame {
        private final LayoutNode node;
        private int nextChildIndex;

        private LayoutBuildFrame(LayoutNode node) {
            this.node = node;
        }
    }

    private void ensureChildrenBuilt(LayoutNode node) {
        if (node == null || node.processNode == null || graph == null) {
            return;
        }
        if (!node.children.isEmpty() || node.originalChildCount <= 0) {
            return;
        }
        boolean seededCurrent = false;
        String currentNodeId = node.processNode.id();
        if (!layoutBuildStackIds.contains(currentNodeId)) {
            layoutBuildStack.addLast(currentNodeId);
            layoutBuildStackIds.add(currentNodeId);
            seededCurrent = true;
        }
        try {
            ensureChildrenBuiltWithActiveStack(node);
        } finally {
            if (seededCurrent) {
                layoutBuildStack.removeLast();
                layoutBuildStackIds.remove(currentNodeId);
            }
        }
    }

    private void ensureChildrenBuiltWithActiveStack(LayoutNode node) {
        ProcessGraph currentGraph = graph;
        if (currentGraph == null) {
            return;
        }
        String cacheKey = childCacheKey(node);
        List<LayoutNode> cached = pathChildrenCache.get(cacheKey);
        if (cached != null) {
            node.children.addAll(cached);
            return;
        }
        List<ProcessEdge> incoming = node.incomingEdges != null
                ? node.incomingEdges
                : incomingEdgesByNodeId.computeIfAbsent(node.processNode.id(), ignored -> List.copyOf(currentGraph.incomingEdges(node.processNode.id())));
        node.incomingEdges = incoming;
        ArrayList<LayoutNode> built = new ArrayList<>(incoming.size());
        int idx = 0;
        for (ProcessEdge edge : incoming) {
            ProcessNode child = currentGraph.node(edge.fromNodeId());
            if (child != null) {
                built.add(buildLayoutTree(child, node.depth + 1, node.pathId + "-" + idx));
            }
            idx++;
        }
        List<LayoutNode> immutable = List.copyOf(built);
        pathChildrenCache.put(cacheKey, immutable);
        node.children.addAll(immutable);
    }

    private static String childCacheKey(LayoutNode node) {
        return node.pathId + "@" + node.depth;
    }

    private void computeWidths(LayoutNode n) {
        ArrayDeque<WidthFrame> stack = new ArrayDeque<>();
        stack.push(new WidthFrame(n, false));
        while (!stack.isEmpty()) {
            WidthFrame frame = stack.pop();
            LayoutNode node = frame.node;
            if (!frame.childrenVisited) {
                stack.push(new WidthFrame(node, true));
                if (!node.collapsed) {
                    for (int i = node.children.size() - 1; i >= 0; i--) {
                        stack.push(new WidthFrame(node.children.get(i), false));
                    }
                }
                continue;
            }
            if (node.children.isEmpty() || node.collapsed) {
                node.treeWidth = nodeW;
                continue;
            }
            long w = 0;
            for (int i = 0; i < node.children.size(); i++) {
                w += node.children.get(i).treeWidth;
                if (i > 0) w += gapX;
                if (w >= maxLayoutCoordinate) {
                    w = maxLayoutCoordinate;
                    break;
                }
            }
            node.treeWidth = Math.max(nodeW, clampLayoutCoordinate(w));
        }
    }

    private void assignPositions(LayoutNode n, int startX) {
        ArrayDeque<PositionFrame> stack = new ArrayDeque<>();
        stack.push(new PositionFrame(n, startX));
        while (!stack.isEmpty()) {
            PositionFrame frame = stack.pop();
            LayoutNode node = frame.node;
            node.x = clampLayoutCoordinate((long) frame.startX + node.treeWidth / 2L - nodeW / 2L);
            node.y = clampLayoutCoordinate((long) node.depth * rowH + (rowH / 2 - nodeH / 2));

            if (node.collapsed || node.children.isEmpty()) {
                continue;
            }
            long childrenSum = (long) Math.max(0, node.children.size() - 1) * gapX;
            for (LayoutNode child : node.children) {
                childrenSum += child.treeWidth;
                if (childrenSum >= maxLayoutCoordinate) {
                    childrenSum = maxLayoutCoordinate;
                    break;
                }
            }
            int offset = clampLayoutCoordinate((long) frame.startX + (node.treeWidth - childrenSum) / 2L);
            int[] childOffsets = new int[node.children.size()];
            for (int i = 0; i < node.children.size(); i++) {
                LayoutNode child = node.children.get(i);
                childOffsets[i] = offset;
                offset = saturatingAdd(offset, saturatingAdd(child.treeWidth, gapX));
            }
            for (int i = node.children.size() - 1; i >= 0; i--) {
                stack.push(new PositionFrame(node.children.get(i), childOffsets[i]));
            }
        }
    }

    private void flatten(LayoutNode n, List<LayoutNode> nodes, List<LayoutLink> links,
                          Map<Integer, List<LayoutNode>> byLayer) {
        ArrayDeque<LayoutNode> stack = new ArrayDeque<>();
        stack.push(n);
        while (!stack.isEmpty()) {
            LayoutNode node = stack.pop();
            nodes.add(node);
            byLayer.computeIfAbsent(node.depth, k -> new ArrayList<>()).add(node);
            if (node.collapsed) {
                continue;
            }
            for (LayoutNode child : node.children) {
                LayoutLink link = new LayoutLink(node, child, node.pathId + "->" + child.pathId);
                child.incomingLink = link;
                links.add(link);
            }
            for (int i = node.children.size() - 1; i >= 0; i--) {
                stack.push(node.children.get(i));
            }
        }
    }

    private record WidthFrame(LayoutNode node, boolean childrenVisited) {}
    private record PositionFrame(LayoutNode node, int startX) {}

    private static Map<String, LayoutNode> indexLayoutNodes(List<LayoutNode> nodes) {
        if (nodes.isEmpty()) {
            return Map.of();
        }
        Map<String, LayoutNode> index = new HashMap<>(nodes.size() * 2);
        for (LayoutNode node : nodes) {
            index.put(node.pathId, node);
        }
        return index;
    }

    private static Map<Integer, List<LayoutNode>> freezeLayerNodes(Map<Integer, List<LayoutNode>> source, boolean sortByX) {
        if (source.isEmpty()) {
            return Map.of();
        }
        ArrayList<Integer> layers = new ArrayList<>(source.keySet());
        layers.sort(Integer::compareTo);
        LinkedHashMap<Integer, List<LayoutNode>> out = new LinkedHashMap<>(layers.size());
        for (Integer layer : layers) {
            List<LayoutNode> nodes = source.getOrDefault(layer, List.of());
            if (nodes.isEmpty()) {
                out.put(layer, List.of());
                continue;
            }
            ArrayList<LayoutNode> copy = new ArrayList<>(nodes);
            if (sortByX) {
                copy.sort(Comparator.comparingInt(node -> node.x));
            }
            out.put(layer, List.copyOf(copy));
        }
        return out;
    }

    private int subtreeDepth(LayoutNode n) {
        int max = 0;
        ArrayDeque<DepthFrame> stack = new ArrayDeque<>();
        stack.push(new DepthFrame(n, 0));
        while (!stack.isEmpty()) {
            DepthFrame frame = stack.pop();
            max = Math.max(max, frame.relativeDepth);
            for (int i = frame.node.children.size() - 1; i >= 0; i--) {
                stack.push(new DepthFrame(frame.node.children.get(i), frame.relativeDepth + 1));
            }
        }
        return max;
    }

    private LayoutNode deepestDescendant(LayoutNode root) {
        LayoutNode deepest = root;
        ArrayDeque<LayoutNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            LayoutNode node = stack.pop();
            if (node.depth > deepest.depth) {
                deepest = node;
            }
            for (int i = node.children.size() - 1; i >= 0; i--) {
                stack.push(node.children.get(i));
            }
        }
        return deepest;
    }

    private record DepthFrame(LayoutNode node, int relativeDepth) {}

    private void toggleCollapse(String pathId) {
        LayoutNode target = fullRootLayout != null ? findInTree(fullRootLayout, pathId) : findLayoutNode(pathId);
        if (target == null || target.originalChildCount <= 0) {
            return;
        }
        boolean currentlyCollapsed = target.collapsed;
        if (currentlyCollapsed) {
            // Expand: remove from manual collapse, add to user-expanded (overrides auto)
            collapsedPaths.remove(pathId);
            userExpandedPaths.add(pathId);
            target.collapsed = false;
            target.autoCollapsed = false;
            ensureChildrenBuilt(target);
        } else {
            // Collapse: add to manual collapse, remove from user-expanded
            collapsedPaths.add(pathId);
            userExpandedPaths.remove(pathId);
            target.collapsed = true;
            target.autoCollapsed = false;
        }
        forceExpandAll = false;
        refreshVisibleLayoutFromFullRoot();
        notifyTreeStructureChanged();
    }

    private void expandExistingNode(String pathId) {
        LayoutNode node = fullRootLayout == null ? null : findInTree(fullRootLayout, pathId);
        if (node == null) {
            return;
        }
        node.collapsed = false;
        node.autoCollapsed = false;
        ensureChildrenBuilt(node);
    }

    private void expandSubtree(LayoutNode node) {
        if (node == null) return;
        ArrayDeque<LayoutNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            LayoutNode current = stack.pop();
            current.collapsed = false;
            current.autoCollapsed = false;
            ensureChildrenBuilt(current);
            for (int i = current.children.size() - 1; i >= 0; i--) {
                stack.push(current.children.get(i));
            }
        }
    }

    /** Toggle collapse state of a node by pathId. For dump tests. */
    public void toggleNodeCollapse(String pathId) {
        toggleCollapse(pathId);
    }

    /**
     * Returns the absolute screen position of the collapse button center for the given pathId.
     * Returns null if the node is not found or has no children.
     * The returned coordinates are relative to this widget's parent (screen-space).
     */
    public @Nullable Pos getCollapseButtonScreenPos(String pathId) {
        for (LayoutNode ln : flatNodes) {
            if (ln.pathId.equals(pathId) && ln.originalChildCount > 0) {
                // Button center in world coords: x + nodeW/2, y + nodeH - 2 + 10
                double wx = ln.x + nodeW / 2.0;
                double wy = ln.y + nodeH - 2 + 10;
                // Transform to screen coords relative to this widget
                double sx = (wx - panX) * zoom;
                double sy = (wy - panY) * zoom;
                // Add absolute position offset
                Pos abs = absolutePos();
                return new Pos((int) (abs.x() + sx), (int) (abs.y() + sy));
            }
        }
        return null;
    }

    /** Get list of collapsed node pathIds in current layout. */
    public List<String> getCollapsedNodePaths() {
        List<String> result = new ArrayList<>();
        for (LayoutNode ln : flatNodes) {
            if (ln.collapsed) result.add(ln.pathId);
        }
        return result;
    }

    /** Reset all user collapse/expand overrides back to auto-collapse defaults. */
    public void resetCollapseState() {
        forceExpandAll = false;
        collapsedPaths.clear();
        userExpandedPaths.clear();
        rebuildTree();
        notifyTreeStructureChanged();
        resetView();
    }

    /** Expand every currently reachable tree node and disable auto-collapse until the graph resets. */
    public void expandAllNodes() {
        forceExpandAll = true;
        collapsedPaths.clear();
        userExpandedPaths.clear();
        currentRootPathId = "root";
        rebuildTree(false);
        computeMaterialSummary();
        notifyTreeStructureChanged();
        resetView();
    }

    public boolean isForceExpandAll() {
        return forceExpandAll;
    }

    private void notifyTreeStructureChanged() {
        if (onSubtreeChanged != null) onSubtreeChanged.run();
    }

    public void resetView() {
        pendingAutoFit = true;
        fitContent();
    }

    /** Auto-fit all content into the viewport, centering both axes. */
    private void fitContent() {
        if (flatNodes.isEmpty() || width() <= 0 || height() <= 0) return;
        int vw = viewportW();
        int vh = height();
        // Compute actual content bounds from flat nodes
        int contentW = Math.max(1, mmWorldW);
        int contentH = Math.max(1, mmWorldH);
        // Compute zoom to fit content with padding
        float zx = (float) vw / (contentW + 60);
        float zy = (float) vh / (contentH + 60);
        zoom = Math.max(minZoom, Math.min(1.0f, Math.min(zx, zy)));
        targetZoom = zoom;
        // Center content in viewport
        double contentCenterX = mmWorldMinX + contentW / 2.0;
        double contentCenterY = mmWorldMinY + contentH / 2.0;
        panX = contentCenterX - vw / (2.0 * zoom);
        panY = contentCenterY - vh / (2.0 * zoom);
        pendingAutoFit = false;
    }

    public int getLayoutWidth() { return layoutWidth; }
    public int getLayoutHeight() { return layoutHeight; }

    // ═══════════════════ Material Summary ═══════════════════

    private void computeMaterialSummary() {
        if (graph == null || flatNodes.isEmpty()) {
            materialByCategory = new LinkedHashMap<>();
            materialFilteredByCategory = new LinkedHashMap<>();
            materialEntryByName = Map.of();
            materialPanelContentH = 0; totalTimeTicks = 0;
            materialFilteredEntryCount = 0;
            materialFilterEvaluationCount = 0;
            return;
        }
        Map<String, long[]> amounts = new LinkedHashMap<>();
        Map<String, MaterialSlot> slotRef = new LinkedHashMap<>();
        Set<String> catalystNames = new LinkedHashSet<>();
        Map<String, List<Integer>> producerSteps = new HashMap<>(), consumerSteps = new HashMap<>();
        Map<String, Double> chanceAccum = new HashMap<>();
        Map<String, Integer> chanceCount = new HashMap<>();
        Map<String, MaterialEntry> byName = new HashMap<>();
        long totalTime = 0;
        int stepNum = 0;
        for (LayoutNode ln : flatNodes) {
            if (ln.referenceOnly) {
                continue;
            }
            stepNum++;
            ProcessNode node = ln.processNode;
            if (node == null) continue;
            totalTime += node.durationTicks();
            for (MaterialSlot input : node.inputs()) {
                amounts.computeIfAbsent(input.materialName(), k -> new long[2])[0] += input.amount();
                slotRef.putIfAbsent(input.materialName(), input);
                if (input.isCatalyst()) catalystNames.add(input.materialName());
                consumerSteps.computeIfAbsent(input.materialName(), k -> new ArrayList<>()).add(stepNum);
            }
            for (MaterialSlot output : node.outputs()) {
                amounts.computeIfAbsent(output.materialName(), k -> new long[2])[1] += output.amount();
                slotRef.putIfAbsent(output.materialName(), output);
                producerSteps.computeIfAbsent(output.materialName(), k -> new ArrayList<>()).add(stepNum);
                chanceAccum.merge(output.materialName(), output.chance(), Double::sum);
                chanceCount.merge(output.materialName(), 1, Integer::sum);
            }
        }
        this.totalTimeTicks = totalTime;
        Map<MatCategory, List<MaterialEntry>> byCategory = new LinkedHashMap<>();
        for (MatCategory cat : MatCategory.values()) byCategory.put(cat, new ArrayList<>());
        for (var e : amounts.entrySet()) {
            String name = e.getKey();
            long consumed = e.getValue()[0], produced = e.getValue()[1];
            long missing = Math.max(0, consumed - produced);
            long inventory = Math.max(0, produced - consumed);
            double avgChance = chanceCount.containsKey(name)
                    ? chanceAccum.get(name) / chanceCount.get(name) : 1.0;
            MatCategory cat;
            if (catalystNames.contains(name)) cat = MatCategory.CATALYST;
            else if (consumed > 0 && produced == 0) cat = MatCategory.EXTERNAL_INPUT;
            else if (consumed == 0 && produced > 0) cat = MatCategory.BYPRODUCT;
            else cat = MatCategory.INTERMEDIATE;
            MaterialEntry materialEntry = new MaterialEntry(name, slotRef.get(name),
                    consumed, produced, missing, inventory, cat,
                    producerSteps.getOrDefault(name, List.of()),
                    consumerSteps.getOrDefault(name, List.of()), avgChance);
            byCategory.get(cat).add(materialEntry);
            byName.put(name, materialEntry);
        }
        byCategory.values().removeIf(List::isEmpty);
        this.materialByCategory = byCategory;
        this.materialEntryByName = byName;
        int h = 0;
        for (var entry : byCategory.entrySet()) {
            h += secHdrH + 2;
            h += entry.getValue().size() * (entryH + entryGap);
            h += 6;
        }
        h += totalsH + 10;
        this.materialPanelContentH = h;
        updateFilteredContentH();
    }

    private void updateFilteredContentH() {
        String lower = normalizeMaterialSearch(matFilterText);
        int h = 0;
        int count = 0;
        int evaluations = 0;
        Map<MatCategory, List<MaterialEntry>> filteredByCategory = new LinkedHashMap<>();
        if (lower.isBlank()) {
            for (var entry : materialByCategory.entrySet()) {
                List<MaterialEntry> entries = entry.getValue();
                if (entries.isEmpty()) {
                    continue;
                }
                filteredByCategory.put(entry.getKey(), entries);
                count += entries.size();
                h += secHdrH + 2 + entries.size() * (entryH + entryGap) + 6;
            }
        } else {
            for (var entry : materialByCategory.entrySet()) {
                ArrayList<MaterialEntry> matches = new ArrayList<>();
                for (MaterialEntry materialEntry : entry.getValue()) {
                    evaluations++;
                    if (matchesMaterialFilter(materialEntry, lower)) {
                        matches.add(materialEntry);
                    }
                }
                if (!matches.isEmpty()) {
                    List<MaterialEntry> entries = List.copyOf(matches);
                    filteredByCategory.put(entry.getKey(), entries);
                    count += entries.size();
                    h += secHdrH + 2 + entries.size() * (entryH + entryGap) + 6;
                }
            }
        }
        h += totalsH + 10;
        materialFilteredByCategory = filteredByCategory;
        materialFilteredEntryCount = count;
        materialFilterEvaluationCount = evaluations;
        matFilteredContentH = h;
        sanitizeMaterialScroll();
    }

    private static String normalizeMaterialSearch(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesMaterialFilter(MaterialEntry entry, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String haystack = (entry.name() + " " + entry.category().label.string() + " " + entry.category().name())
                .toLowerCase(Locale.ROOT);
        for (String token : filter.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            boolean matched = switch (token) {
                case "missing", "缺少" -> entry.missing() > 0;
                case "need", "needed", "required", "req", "需求" -> entry.needed() > 0;
                case "available", "have", "已有", "surplus", "余" -> entry.inventory() > 0 || entry.produced() > 0;
                case "produced", "produce", "output", "out", "产出" -> entry.produced() > 0;
                case "input", "in", "输入" -> entry.category() == MatCategory.EXTERNAL_INPUT;
                case "intermediate", "mid", "中间" -> entry.category() == MatCategory.INTERMEDIATE;
                case "byproduct", "extra", "副产物" -> entry.category() == MatCategory.BYPRODUCT;
                case "catalyst", "tool", "催化剂", "工具" -> entry.category() == MatCategory.CATALYST;
                default -> matchesMaterialNumericToken(entry, token) || haystack.contains(token);
            };
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesMaterialNumericToken(MaterialEntry entry, String token) {
        String[] operators = {">=", "<=", ">", "<", "="};
        for (String op : operators) {
            int idx = token.indexOf(op);
            if (idx <= 0 || idx + op.length() >= token.length()) {
                continue;
            }
            String field = token.substring(0, idx);
            Long expected = parseSearchNumber(token.substring(idx + op.length()));
            if (expected == null) {
                return false;
            }
            Long actual = materialSearchValue(entry, field);
            return actual != null && compareSearchValue(actual, expected, op);
        }
        return false;
    }

    private static @Nullable Long materialSearchValue(MaterialEntry entry, String field) {
        return switch (field) {
            case "missing", "miss", "缺" -> entry.missing();
            case "need", "needed", "required", "req", "需求" -> entry.needed();
            case "produced", "produce", "output", "out", "产出" -> entry.produced();
            case "available", "have", "surplus", "余" -> entry.inventory();
            default -> null;
        };
    }

    private static boolean compareSearchValue(long actual, long expected, String operator) {
        return switch (operator) {
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case "=" -> actual == expected;
            default -> false;
        };
    }

    private static @Nullable Long parseSearchNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1;
        if (text.endsWith("k")) {
            multiplier = 1_000;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 1_000_000;
            text = text.substring(0, text.length() - 1);
        }
        try {
            return Math.round(Double.parseDouble(text) * multiplier);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sanitizeMaterialScroll() {
        int maxScroll = materialPanelMaxScroll();
        if (Float.isNaN(matScrollY) || Float.isInfinite(matScrollY)) {
            matScrollY = 0;
        }
        if (Float.isNaN(matScrollTargetY) || Float.isInfinite(matScrollTargetY)) {
            matScrollTargetY = 0;
        }
        matScrollY = Math.max(0, Math.min(matScrollY, maxScroll));
        matScrollTargetY = Math.max(0, Math.min(matScrollTargetY, maxScroll));
    }

    private boolean handleMaterialPanelScrollKey(int key) {
        int maxScroll = materialPanelMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        float next = matScrollTargetY;
        if (key == GLFW.GLFW_KEY_DOWN) {
            next += matScrollSpeed;
        } else if (key == GLFW.GLFW_KEY_UP) {
            next -= matScrollSpeed;
        } else if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            next += Math.max(matScrollSpeed, panelContentVisibleH() - entryH);
        } else if (key == GLFW.GLFW_KEY_PAGE_UP) {
            next -= Math.max(matScrollSpeed, panelContentVisibleH() - entryH);
        } else if (key == GLFW.GLFW_KEY_HOME) {
            next = 0;
        } else if (key == GLFW.GLFW_KEY_END) {
            next = maxScroll;
        } else {
            return false;
        }
        matScrollTargetY = Math.max(0, Math.min(next, maxScroll));
        return true;
    }

    // ═══════════════════ Animation ═══════════════════

    private void animateSmooth() {
        sanitizeMaterialScroll();
        if (Math.abs(targetZoom - zoom) >= zoomSnap) {
            zoom += (targetZoom - zoom) * zoomSmoothSpeed;
            if (Math.abs(targetZoom - zoom) < zoomSnap) zoom = targetZoom;
            panX = zoomFocusWX - zoomFocusSX / zoom;
            panY = zoomFocusWY - zoomFocusSY / zoom;
        }
        snapPanToPixelGrid();
        if (Math.abs(matScrollTargetY - matScrollY) > 0.5f) {
            matScrollY += (matScrollTargetY - matScrollY) * matScrollSmooth;
            if (Math.abs(matScrollTargetY - matScrollY) < 0.5f) matScrollY = matScrollTargetY;
        }
        animateContentCandidateScroll();
    }

    // ═══════════════════ Rendering ═══════════════════

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (graph == null || flatNodes.isEmpty()) {
            canvas.fill(0, 0, width(), height(), bgPanel);
            canvas.drawCenteredString(CirrusLangs.Calc.Tree.noRecipeData.string(), width() / 2, height() / 2 - 4, textWeak);
            return;
        }
        Font font = Minecraft.getInstance().font;
        if (pendingAutoFit && width() > 0 && height() > 0) {
            fitContent();
        }
        animateSmooth();
        updateHover(mouseX, mouseY);
        machineCycleTicks++;
        frameVisualCache.clear();

        int vw = viewportW();
        canvas.fill(0, 0, vw, height(), bgPanel);

        Pos abs = absolutePos();
        canvas.withClip(abs.x(), abs.y(), vw, height(), () -> {
            WorldViewport viewport = worldViewport(vw, height());
            drawLayerOverlay(canvas, font);
            RenderOrigin origin = renderOrigin(viewport);
            canvas.pushTransform();
            canvas.scale(zoom);
            canvas.translate((float) (origin.x() - panX), (float) (origin.y() - panY));

            drawGrid(canvas);
            drawLayerBands(canvas, font, origin);
            collectVisibleTreeBatch(viewport, visibleNodesScratch, visibleLinksScratch);
            drawConnections(canvas, visibleLinksScratch, origin);
            renderVisibleTreeBatch(canvas, font, visibleNodesScratch, partialTicks, origin);
            canvas.popTransform();
        });

        drawMinimap(canvas, mouseX, mouseY);
        drawContentCandidatePanel(canvas, font, mouseX, mouseY, partialTicks);
        drawMachineCandidatePanel(canvas, font, mouseX, mouseY, partialTicks);
        if (showMaterialPanel) drawMaterialPanel(canvas, font, mouseX, mouseY, partialTicks);
    }

    // ═══════════════════ Grid & Layer Bands ═══════════════════

    private void drawGrid(SceneCanvas canvas) {
        // Intentionally left blank: user requested removing background dot grid.
    }

    private RenderOrigin renderOrigin(WorldViewport viewport) {
        return new RenderOrigin((int) Math.floor(viewport.minX()), (int) Math.floor(viewport.minY()));
    }

    private static int localCoord(int world, int origin) {
        long local = (long) world - origin;
        if (local < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (local > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) local;
    }

    private static int saturatingAdd(int a, int b) {
        long value = (long) a + b;
        return clampLayoutCoordinate(value);
    }

    private static int clampLayoutCoordinate(long value) {
        if (value < -maxLayoutCoordinate) return -maxLayoutCoordinate;
        if (value > maxLayoutCoordinate) return maxLayoutCoordinate;
        return (int) value;
    }

    private void drawLayerBands(SceneCanvas canvas, Font font, RenderOrigin origin) {
        double visMinY = panY, visMaxY = panY + height() / zoom;
        int bandX = localCoord((int) (panX - 200), origin.x());
        int bandW = (int) (viewportW() / zoom + 400);
        LayerRange range = visibleLayerRange();
        if (range.isEmpty()) {
            return;
        }

        for (int i = range.first(); i <= range.last(); i++) {
            int bandTop = i * rowH;
            int bandBot = bandTop + rowH;
            if (bandBot < visMinY || bandTop > visMaxY) continue;
            int localBandTop = origin.localY(bandTop);

            boolean isHighlighted = (i == highlightedLayer);

            // Alternating band background (even layers slightly lighter)
            if (isHighlighted) {
                canvas.fill(bandX, localBandTop, bandW, rowH, withAlpha(green, 0x30));
            } else if (i % 2 == 0) {
                canvas.fill(bandX, localBandTop, bandW, rowH, 0x30FFFFFF);
            }

            // Horizontal divider line at top of layer
            canvas.fill(bandX, localBandTop, bandW, 1, isHighlighted ? withAlpha(green, 0xD8) : layerLine);
            if (isHighlighted) {
                canvas.fill(bandX, origin.localY(bandBot - 1), bandW, 1, withAlpha(green, 0xD8));
            }

        }
    }

    private void drawLayerOverlay(SceneCanvas canvas, Font font) {
        int labelX = 23;
        // Watermark scale is reduced by ~0.15 ratio to keep it lighter/smaller at all zoom levels.
        float bgScale = Math.max(0.78f, Math.min(1.03f, 0.87f + (zoom - 0.7f) * 0.06f));
        double visMinWX = panX;
        double visMaxWX = panX + viewportW() / zoom;
        int worldRepeatAnchorX = 320;
        int worldRepeatSpacing = 500;
        LayerRange range = visibleLayerRange();
        if (range.isEmpty()) {
            return;
        }

        for (int i = range.first(); i <= range.last(); i++) {
            int bandTopWorld = i * rowH;
            int bandBottomWorld = bandTopWorld + rowH;
            int bandTop = (int) Math.round((bandTopWorld - panY) * zoom);
            int bandBottom = (int) Math.round((bandBottomWorld - panY) * zoom);
            if (bandBottom < 0 || bandTop > height()) continue;

            boolean isHighlighted = (i == highlightedLayer);
            String layerLabel = layerLabel(i);
            int scaledLabelW = Math.max(1, (int) Math.round(font.width(layerLabel) * bgScale));
            int bgLabelY = bandTop + Math.max(8, (bandBottom - bandTop) / 2 - 8);
            int bgLabelColor = isHighlighted ? withAlpha(0xFF7F9589, 0x90) : withAlpha(0xFF7A7A7A, 0x58);

            int firstRepeatIndex = (int) Math.floor((visMinWX - worldRepeatAnchorX) / worldRepeatSpacing) - 1;
            int lastRepeatIndex = (int) Math.ceil((visMaxWX - worldRepeatAnchorX) / worldRepeatSpacing) + 1;
            for (int repeatIndex = firstRepeatIndex; repeatIndex <= lastRepeatIndex; repeatIndex++) {
                double worldX = worldRepeatAnchorX + (double) repeatIndex * worldRepeatSpacing;
                int x = (int) Math.round((worldX - panX) * zoom);
                // Only cull labels that are fully outside. Partially visible labels should
                // still render and let clip do natural edge trimming.
                if (x + scaledLabelW < labelX + 2 || x > viewportW() - 8) continue;
                canvas.pushTransform();
                canvas.translate(x, bgLabelY);
                canvas.scale(bgScale);
                canvas.drawString(layerLabel, 0, 0, bgLabelColor, false);
                canvas.popTransform();
            }
        }
    }

    // ═══════════════════ Connections (Manhattan routing) ═══════════════════

    private boolean isNodeInTrackedPath(String nodeId) {
        return trackedPathIds.contains(nodeId);
    }

    private WorldViewport worldViewport(int viewportWidth, int viewportHeight) {
        double safeZoom = Math.max(minZoom, zoom);
        int w = Math.max(0, viewportWidth);
        int h = Math.max(0, viewportHeight);
        return new WorldViewport(panX, panY, panX + w / safeZoom, panY + h / safeZoom);
    }

    private LayerRange visibleLayerRange() {
        return worldViewport(viewportW(), height()).layerRange(maxDepth);
    }

    private void collectVisibleTreeBatch(WorldViewport viewport, List<LayoutNode> nodes, List<LayoutLink> links) {
        nodes.clear();
        links.clear();
        visibleTrackedNodesScratch.clear();
        visibleTrackedLinksScratch.clear();
        LayerRange range = viewport.layerRange(maxDepth);
        if (range.isEmpty()) {
            return;
        }
        for (int layer = range.first(); layer <= range.last(); layer++) {
            List<LayoutNode> layerList = layerNodesByX.getOrDefault(layer, List.of());
            int first = firstNodeEndingAfter(layerList, viewport.minX());
            for (int i = first; i < layerList.size(); i++) {
                LayoutNode node = layerList.get(i);
                if (node.x > viewport.maxX()) {
                    break;
                }
                if (!viewport.intersectsNode(node)) {
                    continue;
                }
                LayoutLink incoming = node.incomingLink;
                if (incoming != null && viewport.intersectsConnection(incoming)) {
                    if (isNodeInTrackedPath(incoming.target.pathId)) {
                        visibleTrackedLinksScratch.add(incoming);
                    } else {
                        links.add(incoming);
                    }
                }
                if (isNodeInTrackedPath(node.pathId)) {
                    visibleTrackedNodesScratch.add(node);
                } else {
                    nodes.add(node);
                }
            }
        }
        links.addAll(visibleTrackedLinksScratch);
        nodes.addAll(visibleTrackedNodesScratch);
    }

    private static int firstNodeEndingAfter(List<LayoutNode> nodes, double worldX) {
        int low = 0;
        int high = nodes.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (nodes.get(mid).x + nodeW < worldX) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static int firstNodeCenterAfter(List<LayoutNode> nodes, double worldX) {
        int low = 0;
        int high = nodes.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (nodes.get(mid).x + nodeW / 2.0 < worldX) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private String layerLabel(int layer) {
        if (layer >= 0 && layer < layerLabelCache.length) {
            String label = layerLabelCache[layer];
            if (label == null) {
                label = "LAYER " + layer;
                layerLabelCache[layer] = label;
            }
            return label;
        }
        return "LAYER " + layer;
    }

    private void drawConnections(SceneCanvas canvas, List<LayoutLink> visibleLinks, RenderOrigin origin) {
        for (LayoutLink link : visibleLinks) {
            drawConnection(canvas, link, isNodeInTrackedPath(link.target.pathId), origin);
        }
    }

    private void drawConnection(SceneCanvas canvas, LayoutLink link, boolean tracked, RenderOrigin origin) {
        int startX = origin.localX(link.source.x + nodeW / 2);
        int startY = origin.localY(link.source.y + nodeH);
        int endX = origin.localX(link.target.x + nodeW / 2);
        int endY = origin.localY(link.target.y);
        int midY = (startY + endY) / 2;

        boolean isCatalyst = link.target.processNode != null && hasCatalystInput(link.target.processNode);

        int color = tracked ? linkTracked : (isCatalyst ? linkCatalyst : linkDefault);
        int thickness = tracked ? 3 : 2;

        canvas.fill(startX - thickness / 2, startY, thickness, midY - startY, color);
        int hx1 = Math.min(startX, endX) - thickness / 2;
        int hx2 = Math.max(startX, endX) + thickness / 2;
        canvas.fill(hx1, midY - thickness / 2, hx2 - hx1, thickness, color);
        canvas.fill(endX - thickness / 2, midY, thickness, endY - midY, color);
    }

    private int nodeAccent(LayoutNode ln) {
        if (ln.pathId.equals(trackedNodeId)) return linkTracked;
        if (ln.depth == 0) return dotClrRoot;
        ProcessNode pn = ln.processNode;
        if (pn == null) return dotClr;
        if (hasCatalystInput(pn)) return 0xFF95AED4;
        if (!pn.outputs().isEmpty()) {
            return pn.outputs().get(0).category().color();
        }
        return dotClr;
    }

    private static boolean hasCatalystInput(ProcessNode node) {
        for (MaterialSlot input : node.inputs()) {
            if (input.isCatalyst()) {
                return true;
            }
        }
        return false;
    }

    private int minimapNodeColor(LayoutNode ln) {
        if (ln.pathId.equals(trackedNodeId)) return linkTracked;
        if (ln.depth == 0) return withAlpha(dotClrRoot, 0xE0);
        return withAlpha(0xFFA1ACBA, 0xD2);
    }

    // ═══════════════════ Minimap ═══════════════════

    private int minimapX() {
        return viewportW() - mmW - mmPad;
    }

    private int minimapY() {
        return mmPad;
    }

    private record MinimapProjection(int contentX, int contentY, int contentW, int contentH,
                                     double scale, double drawX, double drawY, double drawW, double drawH) {}

    private static MinimapViewportRect clampMinimapViewportRect(int contentX, int contentY, int contentW, int contentH,
                                                                int rawX, int rawY, int rawW, int rawH) {
        if (contentW <= 0 || contentH <= 0) {
            return new MinimapViewportRect(contentX, contentY, 1, 1);
        }
        int vpW = Math.max(1, Math.min(rawW, contentW));
        int vpH = Math.max(1, Math.min(rawH, contentH));
        int minX = contentX;
        int minY = contentY;
        int maxX = contentX + contentW - vpW;
        int maxY = contentY + contentH - vpH;
        int x = Math.max(minX, Math.min(rawX, maxX));
        int y = Math.max(minY, Math.min(rawY, maxY));
        int right = Math.min(contentX + contentW, x + vpW);
        int bottom = Math.min(contentY + contentH, y + vpH);
        return new MinimapViewportRect(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private @Nullable MinimapProjection minimapProjection() {
        if (mmWorldW <= 0 || mmWorldH <= 0) return null;
        int mmX = minimapX();
        int mmY = minimapY();
        int contentX = mmX + mmContentPad;
        int contentY = mmY + mmContentPad;
        int contentH = mmH - mmContentPad * 2;
        int contentW = mmW - mmContentPad * 2;
        if (contentW <= 0 || contentH <= 0) return null;
        double fitScale = Math.min((double) contentW / mmWorldW, (double) contentH / mmWorldH);
        double scale = fitScale * mmScaleShrink;
        if (scale <= 0) return null;
        double drawW = mmWorldW * scale;
        double drawH = mmWorldH * scale;
        double drawX = contentX + (contentW - drawW) * 0.5;
        double drawY = contentY + (contentH - drawH) * 0.5;
        return new MinimapProjection(contentX, contentY, contentW, contentH, scale, drawX, drawY, drawW, drawH);
    }

    private void drawMinimap(SceneCanvas canvas, int mouseX, int mouseY) {
        if (flatNodes.isEmpty() || mmWorldW <= 0 || mmWorldH <= 0) return;
        MinimapProjection proj = minimapProjection();
        if (proj == null) return;

        int contentX = proj.contentX;
        int contentY = proj.contentY;
        int contentH = proj.contentH;
        int contentW = proj.contentW;
        // Keep minimap with a single background layer (no extra chrome frame).
        canvas.texture(Background.inset, contentX, contentY, contentW, contentH);
        ensureMinimapRuns(canvas);
        if (minimapTextureView != null) {
            canvas.texture(minimapTextureView, contentX, contentY, contentW, contentH);
        }

        // Viewport indicator (pan is in world-space, offset by bounding box origin)
        int rawVpW = Math.max(4, (int) Math.round((viewportW() / zoom) * proj.scale));
        int rawVpH = Math.max(4, (int) Math.round((height() / zoom) * proj.scale));
        int rawVpX = (int) Math.round(proj.drawX + (panX - mmWorldMinX) * proj.scale);
        int rawVpY = (int) Math.round(proj.drawY + (panY - mmWorldMinY) * proj.scale);
        MinimapViewportRect vp = clampMinimapViewportRect(contentX, contentY, contentW, contentH,
                rawVpX, rawVpY, rawVpW, rawVpH);
        canvas.fill(vp.x() + 1, vp.y() + 1, Math.max(1, vp.w() - 2), Math.max(1, vp.h() - 2), 0x1AFFFFFF);
        canvas.fill(vp.x(), vp.y(), vp.w(), 1, mmVp);
        canvas.fill(vp.x(), vp.y() + vp.h() - 1, vp.w(), 1, mmVp);
        canvas.fill(vp.x(), vp.y(), 1, vp.h(), mmVp);
        canvas.fill(vp.x() + vp.w() - 1, vp.y(), 1, vp.h(), mmVp);
    }

    private boolean isInMinimap(double mx, double my) {
        int mmX = minimapX();
        int mmY = minimapY();
        return mx >= mmX && mx <= mmX + mmW && my >= mmY && my <= mmY + mmH;
    }

    private void minimapClickToPan(double mx, double my) {
        MinimapProjection proj = minimapProjection();
        if (proj == null) return;
        double clampedMx = Math.max(proj.drawX, Math.min(mx, proj.drawX + proj.drawW));
        double clampedMy = Math.max(proj.drawY, Math.min(my, proj.drawY + proj.drawH));
        double worldX = (clampedMx - proj.drawX) / proj.scale + mmWorldMinX;
        double worldY = (clampedMy - proj.drawY) / proj.scale + mmWorldMinY;
        double vpWorldW = viewportW() / zoom, vpWorldH = height() / zoom;
        panX = Math.max(0, Math.min(worldX - vpWorldW / 2.0, layoutWidth - vpWorldW));
        panY = Math.max(0, Math.min(worldY - vpWorldH / 2.0, layoutHeight - vpWorldH));
        snapPanToPixelGrid();
    }

    private void snapPanToPixelGrid() {
        if (zoom <= 0f || Double.isNaN(panX) || Double.isNaN(panY)) return;
        double snappedX = Math.round(panX * zoom) / zoom;
        double snappedY = Math.round(panY * zoom) / zoom;
        panX = snappedX;
        panY = snappedY;
    }

    // ═══════════════════ Node Card ═══════════════════

    private void renderVisibleTreeBatch(SceneCanvas canvas, Font font, List<LayoutNode> visibleNodes, float pt,
                                        RenderOrigin origin) {
        if (visibleNodes.isEmpty()) {
            return;
        }
        RenderSession session = new RenderSession(canvas, pt);
        try {
            for (LayoutNode node : visibleNodes) {
                drawNodeCard(canvas, font, node, session, origin);
            }
        } finally {
            session.finish();
        }
    }

    private void drawNodeCard(SceneCanvas canvas, Font font, LayoutNode ln, RenderSession session, RenderOrigin origin) {
        int x = origin.localX(ln.x), y = origin.localY(ln.y);
        ProcessNode node = ln.processNode;
        if (node == null) return;

        NodeRenderModel model = renderModel(ln);
        boolean hovered = isNodeHovered(ln.pathId);
        boolean tracked = ln.pathId.equals(trackedNodeId);
        boolean externallySelected = Objects.equals(ln.pathId, externalSelectedNodeId);
        boolean recipeNode = model.recipeNode();
        int accent = nodeAccent(ln);
        boolean extensionVisible = model.extensionVisible();
        NodeCardLayout layout = recipeNodeCardLayout(x, y, extensionVisible);

        drawNodeShell(canvas, layout, x, y, hovered, accent, recipeNode, extensionVisible);
        if (zoom < detailLodZoom) {
            return;
        }

        if (tracked) {
            drawOutline(canvas, x - 1, y - 1, nodeW + 2, nodeH + 2, trackedRing);
        }
        if (externallySelected) {
            drawOutline(canvas, x - 2, y - 2, nodeW + 4, nodeH + 4, externalSelectedRing);
        }

        if (recipeNode) {
            drawStepCard(canvas, font, ln, model, layout, session, x, y);
        } else {
            drawLeafCard(canvas, font, ln, model, layout, session, x, y);
        }

        if (ln.originalChildCount > 0) {
            drawCollapseControl(canvas, ln, x, y, accent);
        }
    }

    private void drawNodeShell(SceneCanvas canvas, NodeCardLayout layout, int x, int y, boolean hovered, int accent,
                               boolean recipeNode, boolean extensionVisible) {
        canvas.texture(Background.Outlined.flat, x, y, nodeW, nodeH);
        if (hovered) {
            canvas.fill(x + 3, y + 3, nodeW - 6, nodeH - 6, withAlpha(accent, 0x16));
        }
        if (recipeNode) {
            canvas.texture(Background.flat, layout.recipe().x(), layout.recipe().y(), layout.recipe().w(), layout.recipe().h());
        }
    }

    private void drawStepCard(SceneCanvas canvas, Font font, LayoutNode ln, NodeRenderModel model,
                              NodeCardLayout layout, RenderSession session, int localX, int localY) {
        ProcessNode node = ln.processNode;
        if (node == null) return;
        int slotSize = recipePreviewSlotSize(node.inputs().size(), node.outputs().size(), layout.inputs(), layout.outputs());
        drawMachineZone(canvas, ln, model, layout.machine(), session);
        drawInputZone(canvas, font, node.inputs(), model.inputVisuals(), layout.inputs(), slotSize, session);
        drawFlowArrow(canvas, layout.arrow());
        drawOutputZone(canvas, font, node.outputs(), model.outputVisuals(), layout.outputs(), slotSize, session);
        drawActionZone(canvas, ln, layout, localX, localY);
        if (layout.extension().h() > 0) {
            drawExtensionZone(canvas, font, node, model, false, layout.extension(), session);
        }
    }

    private void drawLeafCard(SceneCanvas canvas, Font font, LayoutNode ln, NodeRenderModel model,
                              NodeCardLayout layout, RenderSession session, int localX, int localY) {
        ProcessNode node = ln.processNode;
        if (node == null) return;
        drawLeafMaterialZone(canvas, font, ln.pathId, node, model, layout.primaryOutput(), layout.candidates(),
                session, localX, localY);
        if (layout.extension().h() > 0) {
            drawExtensionZone(canvas, font, node, model, true, layout.extension(), session);
        }
    }

    private void drawMachineZone(SceneCanvas canvas, LayoutNode layoutNode, NodeRenderModel model,
                                 CardZone zone, RenderSession session) {
        ProcessNode node = layoutNode.processNode;
        MachineInfo displayMachine = getDisplayMachine(layoutNode);
        String machineName = displayMachine == null ? node.machine() : displayMachine.name();
        boolean drawn = drawMachineIcon(canvas, displayMachine, zone.x(), zone.y(), cardMachineSlot, session);
        int candidateCount = model.machines().size();
        int indicatorW = candidateCount > 1 ? 16 : 0;
        int labelX = drawn ? zone.x() + cardMachineSlot + 5 : zone.x();
        int labelW = Math.max(0, zone.w() - (labelX - zone.x()) - indicatorW - 2);
        if (machineName != null && !machineName.isBlank() && labelW > 8) {
            canvas.drawString(cachedTruncate(machineName, labelW, context().font()), labelX, zone.y() + 6, cardMetaText, false);
        }
        if (candidateCount > 1) {
            drawMachineOptionIndicator(canvas, zone.x() + zone.w() - indicatorW + 2, zone.y() + zone.h() - 5,
                    candidateCount - 1);
        }
    }

    private void drawInputZone(SceneCanvas canvas, Font font, List<MaterialSlot> inputs, List<@Nullable SlotVisual> visuals,
                               CardZone zone, int slotSize, RenderSession session) {
        drawSlotGrid(canvas, font, inputs, visuals, zone, slotSize, true, 0xFF88CC88, session);
    }

    private void drawOutputZone(SceneCanvas canvas, Font font, List<MaterialSlot> outputs, List<@Nullable SlotVisual> visuals,
                                CardZone zone, int slotSize, RenderSession session) {
        drawSlotGrid(canvas, font, outputs, visuals, zone, slotSize, true, green, session);
    }

    private void drawLeafMaterialZone(SceneCanvas canvas, Font font, String pathId, ProcessNode node,
                                      NodeRenderModel model, CardZone primaryZone, CardZone candidateZone,
                                      RenderSession session, int localX, int localY) {
        MaterialSlot primary = model.primaryOutput();
        if (primary == null) {
            return;
        }
        if (model.tagLikePrimary()) {
            drawTagLeafMaterialZone(canvas, font, pathId, primary, model.primaryVisual(), session, localX, localY);
            return;
        }
        int accent = nodeAccentColorForSlot(primary);
        SlotVisual primaryVisual = model.primaryVisual();
        int count = primaryVisual == null ? 0 : primaryVisual.count();
        boolean hasCandidates = count > 1;
        int slotSize = hasCandidates ? primaryZone.w() : Math.min(32, Math.max(cardMainSlot, primaryZone.w()));
        int slotX = hasCandidates ? primaryZone.x() : primaryZone.x() + Math.max(0, (primaryZone.w() - slotSize) / 2);
        int slotY = hasCandidates ? primaryZone.y() : primaryZone.y() + Math.max(0, (primaryZone.h() - slotSize) / 2);
        drawMaterialSlot(canvas, font, primary, primaryVisual, slotX, slotY, slotSize, session, true,
                selectedContentCandidateByPath.getOrDefault(pathId, -1));

        int textX = hasCandidates ? candidateZone.x() + 5 : primaryZone.x() + primaryZone.w() + 7;
        int textY = hasCandidates ? candidateZone.y() + 4 : yForCenteredText(candidateZone, 2);
        int textRight = candidateZone.x() + candidateZone.w() - (hasCandidates ? 24 : 4);
        int labelW = Math.max(0, textRight - textX);
        String name = primary.materialName();
        canvas.drawString(cachedTruncate(name, labelW, font), textX, textY, textPrimary, false);

        String amount = primaryAmountText(primary);
        if (!amount.isBlank() && !"1".equals(amount)) {
            canvas.drawString(amount, textX, textY + 10, accent, false);
        } else if (isTagLikeMaterial(primary)) {
            canvas.drawString("tag", textX, textY + 10, cardMetaText, false);
        }

        if (primary.content().isEmpty()) {
            return;
        }
        if (!hasCandidates) {
            return;
        }
        CardZone preview = new CardZone(candidateZone.x() + 42, candidateZone.y() + 12,
                Math.max(0, candidateZone.w() - 66), Math.max(0, candidateZone.h() - 14));
        drawCandidatePreviewGrid(canvas, font, primaryVisual, preview, count, session);
        int[] bounds = new int[] { candidateZone.x() + candidateZone.w() - 19, candidateZone.y() + candidateZone.h() - 18, 16, 16 };
        drawSmallGlyphButton(canvas, bounds[0], bounds[1], bounds[2], bounds[3],
                Objects.equals(contentCandidatePanelNodeId, pathId),
                Icon.Calc.Path.branch, Icon.Calc.Path.branch, 11, 11);
    }

    private static int yForCenteredText(CardZone zone, int lines) {
        int textH = Math.max(1, lines) * 10;
        return zone.y() + Math.max(0, (zone.h() - textH) / 2);
    }

    private void drawTagLeafMaterialZone(SceneCanvas canvas, Font font, String pathId, MaterialSlot slot,
                                         @Nullable SlotVisual slotVisual, RenderSession session,
                                         int localX, int localY) {
        TagLeafCardLayout layout = tagLeafCardLayout(localX, localY);
        CardZone header = layout.header();
        CardZone candidates = layout.candidates();
        CardZone action = layout.action();
        int count = slotVisual == null ? 0 : slotVisual.count();
        boolean hasCandidates = count > 1;
        int selectedIndex = selectedVisualIndex(pathId, slotVisual);

        canvas.texture(Background.flat, header.x(), header.y(), header.w(), header.h());
        int selectedSlotSize = Math.min(20, Math.max(14, header.h() - 4));
        int selectedSlotX = header.x() + 3;
        int selectedSlotY = header.y() + Math.max(0, (header.h() - selectedSlotSize) / 2);
        CirrusSlotGroupRenderer.renderSingle(canvas, selectedSlotX, selectedSlotY, selectedSlotSize);
        drawMaterialSlotContent(canvas, font, slot, slotVisual, selectedSlotX, selectedSlotY, selectedSlotSize, session, false, selectedIndex);

        if (hasCandidates) {
            drawSmallGlyphButton(canvas, action.x(), action.y(), action.w(), action.h(),
                    Objects.equals(contentCandidatePanelNodeId, pathId),
                    Icon.Small.arrowRight, Icon.Small.arrowRightSelected, 5, 6);
            drawTagCandidateGrid(canvas, font, slotVisual, candidates, count, session);
        }
    }

    private void drawTagCandidateGrid(SceneCanvas canvas, Font font, SlotVisual visual, CardZone zone, int candidateCount,
                                      RenderSession session) {
        if (candidateCount <= 0 || zone.w() <= 0 || zone.h() <= 0) {
            return;
        }
        int size = Math.min(tagCandidateCell, Math.max(12, Math.min(zone.w(), zone.h())));
        int columns = Math.max(1, zone.w() / size);
        int rows = Math.max(1, zone.h() / size);
        int maxVisible = Math.max(1, columns * rows);
        int visible = Math.min(candidateCount, maxVisible);
        int shownColumns = Math.min(columns, visible);
        int shownRows = (int) Math.ceil(visible / (double) columns);
        int totalW = shownColumns * size;
        int totalH = shownRows * size;
        int sx = zone.x() + Math.max(0, (zone.w() - totalW) / 2);
        int sy = zone.y() + Math.max(0, (zone.h() - totalH) / 2);
        CirrusSlotGroupRenderer.renderRowMajorCells(canvas, sx, sy, size, visible, columns);
        for (int i = 0; i < visible; i++) {
            int col = i % columns;
            int row = i / columns;
            int cellX = sx + col * size;
            int cellY = sy + row * size;
            drawContentSlot(session, visual, i, cellX + 1, cellY + 1, Math.max(1, size - 2));
        }
        if (candidateCount > visible) {
            String overflow = "+" + fmtNum(candidateCount - visible);
            int textW = font.width(overflow);
            int textX = zone.x() + zone.w() - textW - 1;
            int textY = zone.y() + Math.max(0, (zone.h() - 8) / 2);
            canvas.drawString(overflow, textX, textY, cardMetaText, false);
        }
    }

    private int selectedVisualIndex(String pathId, @Nullable SlotVisual visual) {
        int count = visual == null ? 0 : visual.count();
        if (count <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(count - 1, selectedContentCandidateByPath.getOrDefault(pathId, 0)));
    }

    private void drawSlotGrid(SceneCanvas canvas, Font font, List<MaterialSlot> slots, List<@Nullable SlotVisual> visuals, CardZone zone,
                              int slotSize, boolean amountOverlay, int overflowColor, RenderSession session) {
        if (slots.isEmpty() || zone.w() <= 0 || zone.h() <= 0 || slotSize <= 0) {
            return;
        }
        SlotGridPlan plan = slotGridPlan(slots.size(), zone.w(), zone.h(), slotSize);
        int visible = Math.min(slots.size(), plan.columns() * plan.rows());
        int gridW = plan.columns() * plan.slotSize();
        int gridH = plan.rows() * plan.slotSize();
        int sx = zone.x() + Math.max(0, (zone.w() - gridW) / 2);
        int sy = zone.y() + Math.max(0, (zone.h() - gridH) / 2);
        CirrusSlotGroupRenderer.renderRowMajorCells(canvas, sx, sy, plan.slotSize(), visible, plan.columns());
        for (int i = 0; i < visible; i++) {
            int column = i % plan.columns();
            int row = i / plan.columns();
            int slotX = sx + column * plan.slotSize();
            int slotY = sy + row * plan.slotSize();
            SlotVisual visual = i < visuals.size() ? visuals.get(i) : null;
            drawMaterialSlotContent(canvas, font, slots.get(i), visual, slotX, slotY, plan.slotSize(), session, amountOverlay, -1);
        }
        if (slots.size() > visible) {
            int overflowX = Math.min(zone.x() + zone.w() - 1, sx + gridW + 2);
            int overflowY = sy + Math.max(2, (plan.slotSize() - 7) / 2);
            drawOverflowCount(canvas, font, overflowX, overflowY, slots.size() - visible, overflowColor);
        }
    }

    private void remapSelectedContentCandidatesFromPreferences() {
        selectedContentCandidateByPath.clear();
        if (contentVariantPreferences.isEmpty() || flatNodes.isEmpty()) {
            return;
        }
        for (LayoutNode node : flatNodes) {
            NodeRenderModel model = renderModel(node);
            MaterialSlot slot = model.selectableSlot();
            SlotVisual slotVisual = model.selectableVisual();
            if (slot == null || slotVisual == null || slotVisual.count() <= 1) {
                continue;
            }
            String inputKey = inputKey(slot);
            String variantKey = inputKey == null ? null : contentVariantPreferences.get(inputKey);
            if (variantKey == null) {
                continue;
            }
            int index = visualIndexForVariantKey(slot.content(), variantKey);
            if (index >= 0) {
                selectedContentCandidateByPath.put(node.pathId, index);
            }
        }
    }

    private static @Nullable String inputKey(MaterialSlot slot) {
        return slot.content() instanceof InputContent input ? selectionInputKey(input) : null;
    }

    private static @Nullable String variantKey(MaterialSlot slot, int visualIndex) {
        return selectionVariantKey(slot.content(), visualIndex);
    }

    private static int visualIndexForVariantKey(RecipeContent content, String variantKey) {
        if (!(content instanceof InputContent input) || !input.isEnumerable()) {
            return -1;
        }
        int index = 0;
        for (var variant : input.enumerate()) {
            if (Objects.equals(selectionVariantKey(variant), variantKey)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    static String selectionInputKey(InputContent input) {
        Checks.checkNotNull(input, "input");
        return switch (input) {
            case TagStackContent<?> tag -> "tag:" + tag.type().typeId() + ":" + tag.tagKey().location();
            case StackContent<?, ?> stack -> "stack:" + selectionVariantKey(stack.content());
            case TypedStackListContent<?, ?> list -> "typed_stack_list:" + list.type().typeId() + ":" + selectionVariantListKey(list.stacks());
            case StackListContent list -> "stack_list:" + selectionVariantListKey(list.stacks());
            case ValueContent<?> value -> "value:" + value.type().typeId();
            default -> input.isEnumerable()
                ? "variants:" + selectionVariantListKey(input.enumerate())
                : input.getClass().getName();
        };
    }

    static @Nullable String selectionVariantKey(RecipeContent content, int visualIndex) {
        if (!(content instanceof InputContent input) || !input.isEnumerable()) {
            return null;
        }
        int index = 0;
        for (ContentVariant variant : input.enumerate()) {
            if (index == visualIndex) {
                return selectionVariantKey(variant);
            }
            index++;
        }
        return null;
    }

    static String selectionVariantKey(ContentVariant variant) {
        Checks.checkNotNull(variant, "variant");
        return switch (variant) {
            case ContentStack<?, ?> stack -> selectionStackKey(stack);
            case ContentKind<?> kind -> "kind:" + kind.type().typeId() + ":" + selectionContentKey(kind.type(), kind.value());
            case ContentValue<?> value -> "value:" + value.type().typeId();
            default -> variant.getClass().getName() + ":" + variant;
        };
    }

    private static String selectionVariantListKey(Iterable<? extends ContentVariant> variants) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (ContentVariant variant : variants) {
            if (index++ > 0) {
                builder.append('|');
            }
            builder.append(selectionVariantKey(variant));
        }
        return builder.toString();
    }

    private static final ConcurrentMap<ContentStack<?, ?>, String> selectionStackKeyCache =
            new MapMaker().weakKeys().concurrencyLevel(4).makeMap();

    private static String selectionStackKey(ContentStack<?, ?> stack) {
        // Stacks are read-only after construction, so the built key survives as long as the stack does.
        String cached = selectionStackKeyCache.get(stack);
        if (cached != null) return cached;
        String key = "stack:" + stack.type().typeId() + ":" + selectionContentKey(stack.contentType(), stack.content());
        if (!stack.componentsPatch().isEmpty()) {
            key += ":components:" + Integer.toHexString(stack.componentsPatch().hashCode());
        }
        selectionStackKeyCache.put(stack, key);
        return key;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String selectionContentKey(ContentType type, Object value) {
        ResourceLocation location = type.getLocation(value);
        if (location != null) {
            return location.toString();
        }
        return type.asString(value);
    }

    private NodeRenderModel renderModel(LayoutNode node) {
        if (node == null || node.processNode == null) {
            return emptyRenderModel();
        }
        NodeRenderModel model = node.renderModel;
        if (model != null) {
            return model;
        }
        model = renderModelByNodeId.computeIfAbsent(node.processNode.id(), ignored -> createRenderModel(node, node.processNode));
        node.renderModel = model;
        return model;
    }

    private NodeRenderModel createRenderModel(LayoutNode layoutNode, ProcessNode node) {
        MaterialSlot primary = primaryOutput(node);
        SlotVisual primaryVisual = slotVisual(primary);
        List<@Nullable SlotVisual> inputVisuals = slotVisuals(node.inputs());
        List<@Nullable SlotVisual> outputVisuals = slotVisuals(node.outputs());
        MaterialSlot selectable = selectableContentSlotFromVisuals(node, primaryVisual, inputVisuals, outputVisuals);
        SlotVisual selectableVisual = selectable == primary ? primaryVisual : slotVisual(selectable);
        boolean recipeNode = isRecipeLayoutNode(layoutNode);
        boolean tagLikePrimary = isTagLikeMaterial(primary);
        return new NodeRenderModel(
                machineCandidates(node),
                nodeAccentColor(node),
                dotClr,
                primary,
                primaryVisual,
                selectable,
                selectableVisual,
                inputVisuals,
                outputVisuals,
                recipeNode,
                tagLikePrimary,
                hasExtensionInfo(node, !recipeNode)
        );
    }

    private static NodeRenderModel emptyRenderModel() {
        return new NodeRenderModel(List.of(), cardMetaText, dotClr, null, null, null, null,
                List.of(), List.of(), false, false, false);
    }

    private List<@Nullable SlotVisual> slotVisuals(List<MaterialSlot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        ArrayList<@Nullable SlotVisual> visuals = new ArrayList<>(slots.size());
        for (MaterialSlot slot : slots) {
            visuals.add(slotVisual(slot));
        }
        return Collections.unmodifiableList(visuals);
    }

    private @Nullable SlotVisual slotVisual(@Nullable MaterialSlot slot) {
        if (slot == null || slot.content().isEmpty()) {
            return null;
        }
        RecipeContentVisual visual = visual(slot.content());
        int count = visual.variantCount();
        return count <= 0 ? null : new SlotVisual(slot.content(), visual, count);
    }

    private RecipeContentVisual visual(RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return RecipeContentVisual.empty();
        }
        RecipeContentVisual visual = frameVisualCache.get(content);
        if (visual != null) {
            return visual;
        }
        visual = visualCache.visual(content);
        frameVisualCache.put(content, visual);
        return visual;
    }

    private static int recipePreviewSlotSize(int inputCount, int outputCount, CardZone inputZone, CardZone outputZone) {
        int inputSize = slotGridPlan(Math.max(1, inputCount), inputZone.w(), inputZone.h(), cardMainSlot).slotSize();
        int outputSize = slotGridPlan(Math.max(1, outputCount), outputZone.w(), outputZone.h(), cardMainSlot).slotSize();
        return Math.max(cardRecipeMinSlot, Math.min(inputSize, outputSize));
    }

    private static SlotGridPlan slotGridPlan(int slotCount, int zoneW, int zoneH, int preferredSlotSize) {
        int count = Math.max(1, slotCount);
        int maxSlot = Math.max(cardRecipeMinSlot, Math.min(preferredSlotSize, Math.min(zoneW, zoneH)));
        for (int size = maxSlot; size >= cardRecipeMinSlot; size--) {
            int columns = Math.max(1, zoneW / size);
            int rows = Math.max(1, zoneH / size);
            if (columns * rows >= count) {
                columns = Math.min(columns, count);
                rows = Math.max(1, (int) Math.ceil(count / (double) columns));
                return new SlotGridPlan(size, columns, rows);
            }
        }
        int fallbackColumns = Math.max(1, zoneW / cardRecipeMinSlot);
        int fallbackRows = Math.max(1, zoneH / cardRecipeMinSlot);
        return new SlotGridPlan(cardRecipeMinSlot, fallbackColumns, fallbackRows);
    }

    private static String stripTagPrefix(String text) {
        return text != null && text.startsWith("#") ? text.substring(1) : text;
    }

    private static String stripLeadingTagBreaks(String text) {
        int index = 0;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c) || c == '/' || c == '_') {
                index++;
                continue;
            }
            break;
        }
        return text.substring(index);
    }

    private static int fittingPrefixLength(String text, int maxPxW, Font font) {
        if (text.isEmpty() || maxPxW <= 0) {
            return 0;
        }
        int codePoints = text.codePointCount(0, text.length());
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, mid);
            if (font.width(text.substring(0, end)) <= maxPxW) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.offsetByCodePoints(0, Math.max(0, low));
    }

    private static int preferredTagBreak(String text, int maxExclusive) {
        int limit = Math.max(1, Math.min(maxExclusive, text.length()));
        int best = -1;
        for (int i = 1; i < limit; i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '/' || c == '_' || c == '.') {
                best = i + 1;
            }
        }
        return best >= Math.max(4, limit / 2) ? best : limit;
    }

    private int nodeCardX(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        return node == null ? 0 : node.x;
    }

    private int nodeCardY(String pathId) {
        LayoutNode node = findLayoutNode(pathId);
        return node == null ? 0 : node.y;
    }

    private void drawCandidatePreviewGrid(SceneCanvas canvas, Font font, SlotVisual visual, CardZone zone, int candidateCount,
                                          RenderSession session) {
        if (candidateCount <= 0 || zone.w() <= 0 || zone.h() <= 0) {
            return;
        }
        int size = candidateCount <= 5 ? 14 : 12;
        int gap = 0;
        int columns = Math.max(1, zone.w() / size);
        int rows = Math.max(1, zone.h() / size);
        int visible = Math.min(candidateCount, Math.max(1, columns * rows));
        int shownColumns = Math.min(columns, visible);
        int shownRows = (int) Math.ceil(visible / (double) columns);
        int totalW = shownColumns * size + Math.max(0, shownColumns - 1) * gap;
        int totalH = shownRows * size + Math.max(0, shownRows - 1) * gap;
        int sx = zone.x() + Math.max(0, (zone.w() - totalW) / 2);
        int sy = zone.y() + Math.max(0, (zone.h() - totalH) / 2);
        CirrusSlotGroupRenderer.renderRowMajorCells(canvas, sx, sy, size, visible, columns);
        for (int i = 0; i < visible; i++) {
            int col = i % columns;
            int row = i / columns;
            int cellX = sx + col * (size + gap);
            int cellY = sy + row * (size + gap);
            drawContentSlot(session, visual, i, cellX + 1, cellY + 1, Math.max(1, size - 2));
        }
        if (candidateCount > visible) {
            drawOverflowCount(canvas, font, sx + totalW + 2, sy + Math.max(2, (size - 7) / 2), candidateCount - visible, cardMetaText);
        }
    }

    private void drawFlowArrow(SceneCanvas canvas, CardZone zone) {
        canvas.texture(Icon.arrowRight, zone.x(), zone.y(), zone.w(), zone.h());
    }

    private void drawActionZone(SceneCanvas canvas, LayoutNode ln, NodeCardLayout layout, int localX, int localY) {
        ProcessNode node = ln.processNode;
        if (node == null) return;
        int[] projection = projectionButtonBounds(localX, localY);
        if (node.recipe() != null) {
            drawActionButton(canvas, projection, Objects.equals(hoveredProjectionNodeId, ln.pathId),
                    Icon.setting, Icon.settingSelected, 10, 10);
        }
        if (ln.originalChildCount > 0) {
            drawFocusButton(canvas, focusButtonBounds(localX, localY), isNodeHovered(ln.pathId));
        }
    }

    private void drawActionButton(SceneCanvas canvas, int[] bounds, boolean hovered,
                                  SizedTexture icon, @Nullable SizedTexture hoveredIcon,
                                  int iconW, int iconH) {
        CirrusControlChrome.renderButtonBackground(canvas, bounds[0], bounds[1], bounds[2], bounds[3], false, hovered, true);
        SizedTexture drawIcon = hovered && hoveredIcon != null ? hoveredIcon : icon;
        canvas.texture(drawIcon, bounds[0] + (bounds[2] - iconW) / 2,
                bounds[1] + Math.max(0, (bounds[3] - iconH) / 2 - 1), iconW, iconH);
    }

    private void drawExtensionZone(SceneCanvas canvas, Font font, ProcessNode node, NodeRenderModel model,
                                   boolean leaf, CardZone zone, RenderSession session) {
        int x = zone.x();
        int right = zone.x() + zone.w();
        MaterialSlot primary = primaryOutput(node);
        if (leaf) {
            if (primary != null && (primary.amount() > 1 || primary.chance() < 1.0)) {
                drawMetricPill(canvas, font, x, zone.y(), right, null, primaryAmountText(primary), nodeAccentColor(node));
            }
            return;
        }
        if (node.durationTicks() > 0) {
            x = drawMetricPill(canvas, font, x, zone.y(), right, Icon.Sign.Energy.time, node.durationFormatted(), cardMetaText);
        }
        int machineCount = model.machines().size();
        if (machineCount > 1) {
            drawMachineOptionIndicator(canvas, Math.min(x + 2, right - 14), zone.y() + 5, machineCount - 1);
        }
    }

    private int drawMetricPill(SceneCanvas canvas, Font font, int x, int y, int right,
                               @Nullable ImageTexture icon, String text, int color) {
        if (text == null || text.isBlank() || x >= right - 8) {
            return x;
        }
        int iconW = icon == null ? 0 : 10;
        int maxW = Math.max(12, right - x);
        int w = Math.max(12, Math.min(maxW, iconW + font.width(text) + 6));
        if (icon != null && w >= iconW + 4) {
            canvas.texture(icon, x + 1, y + 2, 9, 9);
        }
        int textX = x + (icon == null ? 2 : 11);
        canvas.drawString(cachedTruncate(text, Math.max(0, w - (textX - x) - 2), font), textX, y + 2, color, false);
        canvas.fill(x + 1, y + 11, Math.max(1, w - 2), 1, withAlpha(color, 0x4F));
        return x + w + 4;
    }

    private void drawMachineOptionIndicator(SceneCanvas canvas, int x, int y, int count) {
        if (count <= 0) {
            return;
        }
        int visible = Math.min(3, count);
        for (int i = 0; i < visible; i++) {
            canvas.fill(x + i * 4, y, 2, 2, withAlpha(cardMetaText, 0xA0));
        }
        if (count > visible) {
            canvas.fill(x + visible * 4, y, 4, 2, withAlpha(cardMetaText, 0xA0));
        }
    }

    private void drawMoreDots(SceneCanvas canvas, int x, int y, int count, int color) {
        int dots = Math.max(1, Math.min(3, count));
        for (int i = 0; i < dots; i++) {
            canvas.fill(x + i * 3, y, 2, 2, withAlpha(color, 0xA0));
        }
    }

    private void drawOverflowCount(SceneCanvas canvas, Font font, int x, int y, int count, int color) {
        if (count <= 0) {
            return;
        }
        String text = "+" + fmtNum(count);
        canvas.drawString(text, x, y, color, false);
    }

    private void drawCollapseControl(SceneCanvas canvas, LayoutNode ln, int x, int y, int accent) {
        int btnX = x + nodeW / 2 - collapseBtnW / 2;
        int btnY = y + nodeH - 1;
        boolean hovered = isCollapseButtonHovered(ln.pathId);
        CirrusControlChrome.renderButtonBackground(canvas, btnX, btnY, collapseBtnW, collapseBtnH, false, hovered, true);
        ImageTexture glyph = ln.collapsed
                ? hovered ? Icon.Small.plusSelected : Icon.Small.plus
                : hovered ? Icon.Small.minusSelected : Icon.Small.minus;
        canvas.texture(glyph, btnX + (collapseBtnW - 5) / 2, btnY + (collapseBtnH - 6) / 2, 5, 6);
    }

    private static @Nullable MaterialSlot primaryOutput(ProcessNode node) {
        return node.outputs().isEmpty() ? null : node.outputs().getFirst();
    }

    private static boolean isSelectableContentSlot(@Nullable MaterialSlot slot, @Nullable SlotVisual visual) {
        if (slot == null || slot.content().isEmpty()) {
            return false;
        }
        return visual == null ? true : visual.count() > 1;
    }

    private static @Nullable MaterialSlot selectableContentSlotFromVisuals(
            ProcessNode node,
            @Nullable SlotVisual primaryVisual,
            List<@Nullable SlotVisual> inputVisuals,
            List<@Nullable SlotVisual> outputVisuals
    ) {
        MaterialSlot primary = primaryOutput(node);
        if (isSelectableContentSlot(primary, primaryVisual)) {
            return primary;
        }
        for (int i = 0; i < node.inputs().size(); i++) {
            SlotVisual visual = i < inputVisuals.size() ? inputVisuals.get(i) : null;
            if (isSelectableContentSlot(node.inputs().get(i), visual)) {
                return node.inputs().get(i);
            }
        }
        for (int i = 0; i < node.outputs().size(); i++) {
            SlotVisual visual = i < outputVisuals.size() ? outputVisuals.get(i) : null;
            if (isSelectableContentSlot(node.outputs().get(i), visual)) {
                return node.outputs().get(i);
            }
        }
        return null;
    }

    private static String primaryName(ProcessNode node) {
        MaterialSlot primary = primaryOutput(node);
        return primary == null ? node.name() : primary.materialName();
    }

    private static boolean isTagLikeMaterial(MaterialSlot slot) {
        return slot != null && slot.materialName() != null && slot.materialName().startsWith("#");
    }

    private static boolean hasExtensionInfo(ProcessNode node, boolean leaf) {
        MaterialSlot primary = primaryOutput(node);
        if (leaf) {
            return primary != null && !isTagLikeMaterial(primary) && (primary.amount() > 1 || primary.chance() < 1.0);
        }
        return node.durationTicks() > 0;
    }

    private static String primaryAmountText(@Nullable MaterialSlot slot) {
        if (slot == null) {
            return "0";
        }
        String text = fmtNum(slot.amount());
        if (slot.chance() < 1.0) {
            text += " " + Math.round(slot.chance() * 100) + "%";
        }
        return text;
    }

    static int[] focusButtonBounds(int nodeX, int nodeY) {
        return new int[] { nodeX + nodeW - focusBtnW - 5, nodeY + 5, focusBtnW, focusBtnH };
    }

    static int[] projectionButtonBounds(int nodeX, int nodeY) {
        int[] focus = focusButtonBounds(nodeX, nodeY);
        return new int[] { focus[0] - focusBtnW - 3, nodeY + 5, focusBtnW, focusBtnH };
    }

    static int[] machineSlotBounds(int nodeX, int nodeY) {
        CardZone zone = recipeNodeCardLayout(nodeX, nodeY).machine();
        return new int[] { zone.x(), zone.y(), zone.w(), zone.h() };
    }

    static int[] contentCandidateButtonBounds(int nodeX, int nodeY) {
        CardZone zone = recipeNodeCardLayout(nodeX, nodeY).candidates();
        return new int[] { zone.x() + zone.w() - 19, zone.y() + zone.h() - 18, 16, 16 };
    }

    private static int[] contentCandidateButtonBounds(LayoutNode node) {
        MaterialSlot slot = node == null || node.processNode == null ? null : primaryOutput(node.processNode);
        if (isTagLikeMaterial(slot)) {
            CardZone zone = tagLeafCardLayout(node.x, node.y).action();
            return new int[] { zone.x(), zone.y(), zone.w(), zone.h() };
        }
        return contentCandidateButtonBounds(node.x, node.y);
    }

    private static NodeCardLayout recipeNodeCardLayout(int x, int y) {
        return recipeNodeCardLayout(x, y, true);
    }

    private static TagLeafCardLayout tagLeafCardLayout(int x, int y) {
        return new TagLeafCardLayout(
                new CardZone(x + 8, y + 8, nodeW - 16, 30),
                new CardZone(x + 10, y + 40, nodeW - 20, 30),
                new CardZone(x + nodeW - 30, y + 10, 18, 18)
        );
    }

    private static NodeCardLayout recipeNodeCardLayout(int x, int y, boolean hasExtension) {
        int focusX = x + nodeW - focusBtnW - 5;
        int contextX = focusX - focusBtnW - 3;
        int recipeTop = y + 29;
        int recipeBottom = hasExtension ? y + 61 : y + nodeH - 8;
        int recipeH = Math.max(24, recipeBottom - recipeTop);
        int slotH = hasExtension ? 22 : 24;
        int slotY = recipeTop + Math.max(0, (recipeH - slotH) / 2);
        int arrowY = recipeTop + Math.max(0, (recipeH - 10) / 2);
        int extensionY = hasExtension ? y + 64 : y + nodeH - 5;
        return new NodeCardLayout(
                new CardZone(x + 6, y + 5, nodeW - 12, 20),
                new CardZone(x + 8, y + 6, Math.max(40, contextX - x - 12), cardMachineSlot),
                new CardZone(contextX, y + 5, nodeW - (contextX - x) - 5, focusBtnH),
                new CardZone(x + 8, recipeTop, nodeW - 16, recipeH),
                new CardZone(x + 10, slotY, 74, slotH),
                new CardZone(x + 89, arrowY, 10, 10),
                new CardZone(x + 105, slotY, 72, slotH),
                new CardZone(x + 12, y + 31, 30, 30),
                new CardZone(x + 48, y + 27, nodeW - 60, 37),
                new CardZone(x + 8, extensionY, nodeW - 16, hasExtension ? cardExtensionH : 0),
                27,
                hasExtension ? 61 : -1,
                3
        );
    }

    static List<String> buildStepMetaLines(ProcessNode node) {
        List<String> lines = new ArrayList<>(2);
        if (node.durationTicks() > 0) {
            lines.add(CirrusLangs.Calc.Tree.durationLine.string(node.durationFormatted()));
        }

        List<String> detail = new ArrayList<>(2);
        int machineCount = machineCandidatesStatic(node).size();
        if (machineCount > 1) {
            detail.add(CirrusLangs.Calc.Tree.machineCount.string(machineCount));
        }
        if (node.outputs().size() > 1) {
            detail.add(CirrusLangs.Calc.Tree.outputCount.string(node.outputs().size()));
        }
        if (!detail.isEmpty()) {
            lines.add(String.join(" · ", detail));
        }
        return lines;
    }

    private static boolean containsWorldPoint(int[] bounds, double wx, double wy) {
        return wx >= bounds[0] && wx <= bounds[0] + bounds[2]
                && wy >= bounds[1] && wy <= bounds[1] + bounds[3];
    }

    private void drawFocusButton(SceneCanvas canvas, int[] bounds, boolean hovered) {
        drawActionButton(canvas, bounds, hovered, Icon.Calc.Path.tree, null, 12, 12);
    }

    // ═══════════════════ Slot Icons ═══════════════════

    private void drawSlotIcon(SceneCanvas canvas, MaterialSlot slot, int ix, int iy, float scale,
                              RenderSession session) {
        SlotVisual visual = slotVisual(slot);
        if (visual != null && visual.count() > 0) {
            drawContentSlot(session, visual, currentVisualIndex(visual), ix, iy, Math.round(16 * scale));
            return;
        }
        ContentStack<?, ?> stack = slot.visualStack();
        if (stack.notEmpty()) {
            submitContentStack(stack, ix, iy, scale, session);
        } else {
            int sz = (int)(16 * scale);
            if (sz > 0) {
                canvas.texture(Sign.Group.item, ix + Math.max(0, (sz - 10) / 2), iy + Math.max(0, (sz - 10) / 2), Math.min(10, sz), Math.min(10, sz));
            }
        }
    }

    private void drawMaterialSlot(SceneCanvas canvas, Font font, @Nullable MaterialSlot slot, int x, int y, int size, RenderSession session, boolean amountOverlay) {
        drawMaterialSlot(canvas, font, slot, slotVisual(slot), x, y, size, session, amountOverlay, -1);
    }

    private void drawMaterialSlot(SceneCanvas canvas, Font font, @Nullable MaterialSlot slot, int x, int y, int size,
                                  RenderSession session, boolean amountOverlay, int visualIndexOverride) {
        drawMaterialSlot(canvas, font, slot, slotVisual(slot), x, y, size, session, amountOverlay, visualIndexOverride);
    }

    private void drawMaterialSlot(SceneCanvas canvas, Font font, @Nullable MaterialSlot slot, @Nullable SlotVisual visual,
                                  int x, int y, int size, RenderSession session, boolean amountOverlay, int visualIndexOverride) {
        CirrusSlotGroupRenderer.renderSingle(canvas, x, y, size);
        drawMaterialSlotContent(canvas, font, slot, visual, x, y, size, session, amountOverlay, visualIndexOverride);
    }

    private void drawMaterialSlotContent(SceneCanvas canvas, Font font, @Nullable MaterialSlot slot, int x, int y, int size,
                                         RenderSession session, boolean amountOverlay, int visualIndexOverride) {
        drawMaterialSlotContent(canvas, font, slot, slotVisual(slot), x, y, size, session, amountOverlay, visualIndexOverride);
    }

    private void drawMaterialSlotContent(SceneCanvas canvas, Font font, @Nullable MaterialSlot slot, @Nullable SlotVisual visual,
                                         int x, int y, int size, RenderSession session, boolean amountOverlay, int visualIndexOverride) {
        if (slot == null) {
            canvas.texture(Sign.Group.item, x + Math.max(2, (size - 10) / 2), y + Math.max(2, (size - 10) / 2), 10, 10);
            return;
        }
        int contentSize = Math.max(1, size - 2);
        if (visual != null && visual.count() > 0) {
            int count = visual.count();
            int visualIndex = visualIndexOverride >= 0 ? Math.min(visualIndexOverride, count - 1) : currentVisualIndex(visual);
            drawContentSlot(session, visual, visualIndex, x + 1, y + 1, contentSize);
        } else {
            drawSlotIcon(canvas, slot, x + 1, y + 1, contentSize / 16.0f, session);
        }
        if (amountOverlay && slot.amount() > 1) {
            String amount = fmtNum(slot.amount());
            int textW = font.width(amount);
            session.addAmountOverlay(amount, x + size - textW - 1, y + size - 7);
        }
        if (slot.isCatalyst() && size >= 14) {
            session.addCatalystOverlay(x + 1, y + 1);
        }
    }

    private void drawContentSlot(RenderSession session, RecipeContent content, int visualIndex, int x, int y, int size) {
        if (content == null || content.isEmpty()) {
            return;
        }
        drawContentSlot(session, visual(content), visualIndex, x, y, size);
    }

    private void drawContentSlot(RenderSession session, SlotVisual visual, int visualIndex, int x, int y, int size) {
        drawContentSlot(session, visual == null ? null : visual.visual(), visualIndex, x, y, size);
    }

    private void drawContentSlot(RenderSession session, RecipeContentVisual visual, int visualIndex, int x, int y, int size) {
        if (visual == null || visual.isEmpty() || size <= 0) {
            return;
        }
        session.addContent(visual, visualIndex, x, y, size);
    }

    private static int currentVisualIndex(SlotVisual visual) {
        return visual == null ? 0 : currentVisualIndex(visual.visual());
    }

    private static int currentVisualIndex(RecipeContentVisual visual) {
        return visual == null || visual.variantCount() <= 1 ? 0 : visual.currentVisualIndex();
    }

    private boolean drawMachineIcon(SceneCanvas canvas, @Nullable MachineInfo machine, int x, int y, int size,
                                    RenderSession session) {
        ContentStack<?, ?> icon = machine == null ? ContentStack.empty() : machine.icon();
        if (icon.notEmpty()) {
            CirrusSlotGroupRenderer.renderSingle(canvas, x, y, size);
            float scale = Math.max(0.5f, Math.min(1.25f, (size - 4) / 16.0f));
            submitContentStack(icon, x + 2, y + 2, scale, session);
            return true;
        }
        return false;
    }

    private void drawContentCandidatePanel(SceneCanvas canvas, Font font, int mouseX, int mouseY, float pt) {
        int[] bounds = contentCandidatePanelBounds();
        MaterialSlot slot = contentCandidatePanelSlot();
        if (bounds == null || slot == null || slot.content().isEmpty()) {
            return;
        }
        SlotVisual visual = slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        if (count <= 1) {
            return;
        }
        int x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        float scale = contentCandidatePanelScale();
        canvas.shadow(x, y + 1, w, h, 2, 2, 6, 0x42000000);

        canvas.pushTransform();
        canvas.translate(x, y);
        canvas.scale(scale);
        canvas.texture(Background.Outlined.flat, 0, 0, contentCandidatePanelW, Math.round(h / scale));
        drawSmallGlyphButton(canvas, contentCandidatePanelW - 19, 4, 14, 14,
                isInContentCandidatePanelClose(mouseX, mouseY), Icon.Small.minus, Icon.Small.minusSelected, 5, 6);

        int columns = contentCandidatePanelColumns(count);
        int totalRows = contentCandidatePanelRows(count);
        int visibleRows = contentCandidatePanelVisibleRows(count, h, scale);
        int maxScrollRow = Math.max(0, totalRows - visibleRows);
        if (contentCandidateScrollRow() > maxScrollRow) {
            setContentCandidateScrollRow(maxScrollRow);
        }
        float displayScrollRow = contentCandidateDisplayScrollRow(maxScrollRow);
        int firstRow = Math.max(0, Math.min(maxScrollRow, (int) Math.floor(displayScrollRow)));
        float rowOffset = (displayScrollRow - firstRow) * (contentCandidateCell + contentCandidateCellGap);
        int renderRows = Math.min(totalRows - firstRow, visibleRows + (rowOffset > 0.01f ? 1 : 0));
        int visible = Math.max(0, Math.min(count - firstRow * columns, renderRows * columns));
        int gridX = contentCandidateGridX;
        int gridY = contentCandidateGridY;
        int gridH = visibleRows * contentCandidateCell + Math.max(0, visibleRows - 1) * contentCandidateCellGap;
        int gridW = columns * contentCandidateCell + Math.max(0, columns - 1) * contentCandidateCellGap;
        int selectedIndex = selectedContentCandidateIndex();
        double localMouseX = (mouseX - x) / (double) scale;
        double localMouseY = (mouseY - y) / (double) scale;
        if (visible > 0) {
            Pos abs = absolutePos();
            canvas.withClip(abs.x() + x + Math.round(gridX * scale), abs.y() + y + Math.round(gridY * scale),
                    Math.round(gridW * scale), Math.round(gridH * scale), () -> {
                RenderSession session = new RenderSession(canvas, pt);
                try {
                    renderContentCandidatePanelCells(
                            canvas,
                            slot,
                            columns,
                            visible,
                            firstRow,
                            rowOffset,
                            selectedIndex,
                            localMouseX,
                            localMouseY,
                            session
                    );
                } finally {
                    session.finish();
                }
            });
        }
        if (contentCandidateNeedsScrollbar(count, h, scale)) {
            drawCandidateScrollbar(canvas, contentCandidatePanelW - candidateScrollbarW - 4,
                    gridY, candidateScrollbarW, visibleRows * contentCandidateCell,
                    displayScrollRow, maxScrollRow, visibleRows, totalRows);
        }
        canvas.popTransform();
    }

    private void renderContentCandidatePanelCells(
            SceneCanvas canvas,
            MaterialSlot slot,
            int columns,
            int visible,
            int firstRow,
            float rowOffset,
            int selectedIndex,
            double localMouseX,
            double localMouseY,
            RenderSession session
    ) {
        SlotVisual slotVisual = slotVisual(slot);
        if (slotVisual == null) {
            return;
        }
        for (int i = 0; i < visible; i++) {
            int visualIndex = firstRow * columns + i;
            int col = i % columns;
            int row = i / columns;
            int cellLocalX = contentCandidateGridX + col * (contentCandidateCell + contentCandidateCellGap);
            int cellLocalY = contentCandidateGridY + row * (contentCandidateCell + contentCandidateCellGap);
            double displayedCellY = cellLocalY - rowOffset;
            int cellX = cellLocalX;
            int cellY = (int) Math.round(displayedCellY);
            int cellSize = contentCandidateCell;
            CirrusSlotGroupRenderer.renderSingle(canvas, cellX, cellY, cellSize);
            boolean hovered = localMouseX >= cellLocalX && localMouseX <= cellLocalX + contentCandidateCell
                    && localMouseY >= displayedCellY && localMouseY <= displayedCellY + contentCandidateCell;
            if (visualIndex != selectedIndex && hovered) {
                canvas.fill(cellX + 1, cellY + 1, Math.max(1, cellSize - 2), Math.max(1, cellSize - 2),
                        withAlpha(slot.category().color(), 0x24));
            }
            int contentInset = 2;
            drawContentSlot(session, slotVisual, visualIndex,
                    cellX + contentInset,
                    cellY + contentInset,
                    Math.max(1, cellSize - contentInset * 2));
            if (visualIndex == selectedIndex) {
                session.addSelectionOverlay(cellX, cellY, cellSize, slot.category().color());
            }
        }
    }

    private static int panelSize(double value, float scale) {
        return Math.round((float) value * scale);
    }

    private @Nullable MaterialSlot contentCandidatePanelSlot() {
        LayoutNode node = contentCandidatePanelNodeId == null ? null : findLayoutNode(contentCandidatePanelNodeId);
        return contentCandidatePanelSlot(node);
    }

    private @Nullable MaterialSlot contentCandidatePanelSlot(@Nullable LayoutNode node) {
        if (node == null || node.processNode == null) {
            return null;
        }
        NodeRenderModel model = renderModel(node);
        MaterialSlot slot = model.selectableSlot();
        SlotVisual visual = model.selectableVisual();
        if (slot == null || visual == null || visual.count() <= 1) {
            return null;
        }
        return slot;
    }

    private @Nullable int[] contentCandidatePanelBounds() {
        LayoutNode node = contentCandidatePanelNodeId == null ? null : findLayoutNode(contentCandidatePanelNodeId);
        MaterialSlot slot = contentCandidatePanelSlot(node);
        if (node == null || node.processNode == null || slot == null) {
            return null;
        }
        float scale = contentCandidatePanelScale();
        SlotVisual visual = slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        int w = scaled(contentCandidatePanelW, scale);
        int h = scaled(contentCandidatePanelBaseHeight(count), scale);
        int maxH = Math.max(4, height() - 8);
        if (h > maxH) {
            h = maxH;
        }
        int vw = viewportW();
        int anchorRight = worldToScreenX(node.x + nodeW + 6);
        int anchorLeft = worldToScreenX(node.x - 6) - w;
        int x = anchorRight + w <= vw - 4 ? anchorRight : anchorLeft;
        x = Math.max(4, Math.min(x, Math.max(4, vw - w - 4)));
        int y = worldToScreenY(node.y + 18);
        y = Math.max(4, Math.min(y, Math.max(4, height() - h - 4)));
        return new int[]{x, y, w, h};
    }

    private boolean isInContentCandidatePanel(double mx, double my) {
        int[] b = contentCandidatePanelBounds();
        return b != null && mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    private boolean isInContentCandidatePanelClose(double mx, double my) {
        int[] b = contentCandidatePanelBounds();
        if (b == null) {
            return false;
        }
        float scale = contentCandidatePanelScale();
        double lx = (mx - b[0]) / scale;
        double ly = (my - b[1]) / scale;
        return lx >= contentCandidatePanelW - 19 && lx <= contentCandidatePanelW - 5
                && ly >= 4 && ly <= 18;
    }

    private int contentCandidateIndexAt(double mx, double my) {
        int[] b = contentCandidatePanelBounds();
        MaterialSlot slot = contentCandidatePanelSlot();
        if (b == null || slot == null || slot.content().isEmpty()) {
            return -1;
        }
        SlotVisual visual = slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        if (count <= 0) {
            return -1;
        }
        float scale = contentCandidatePanelScale();
        double lx = (mx - b[0]) / scale;
        double ly = (my - b[1]) / scale;
        int columns = contentCandidatePanelColumns(count);
        int gridX = contentCandidateGridX;
        int gridY = contentCandidateGridY;
        int visibleRows = contentCandidatePanelVisibleRows(count, b[3], scale);
        int cellStep = contentCandidateCell + contentCandidateCellGap;
        int gridW = columns * contentCandidateCell + Math.max(0, columns - 1) * contentCandidateCellGap;
        int gridH = visibleRows * contentCandidateCell + Math.max(0, visibleRows - 1) * contentCandidateCellGap;
        if (lx < gridX || lx >= gridX + gridW || ly < gridY || ly >= gridY + gridH) {
            return -1;
        }
        int maxScrollRow = Math.max(0, contentCandidatePanelRows(count) - visibleRows);
        float displayScrollRow = contentCandidateDisplayScrollRow(maxScrollRow);
        int firstRow = Math.max(0, Math.min(maxScrollRow, (int) Math.floor(displayScrollRow)));
        float rowOffset = (displayScrollRow - firstRow) * cellStep;
        int col = (int) ((lx - gridX) / cellStep);
        int row = (int) ((ly - gridY + rowOffset) / cellStep);
        if (col < 0 || col >= columns || row < 0) {
            return -1;
        }
        if ((lx - gridX - col * cellStep) >= contentCandidateCell
                || (ly - gridY + rowOffset - row * cellStep) >= contentCandidateCell) {
            return -1;
        }
        int index = (firstRow + row) * columns + col;
        return index >= 0 && index < count ? index : -1;
    }

    private boolean selectContentCandidate(String pathId, int index) {
        LayoutNode node = findLayoutNode(pathId);
        NodeRenderModel model = node == null || node.processNode == null ? null : renderModel(node);
        MaterialSlot slot = model == null ? null : model.selectableSlot();
        SlotVisual visual = model == null ? null : model.selectableVisual();
        if (slot == null || visual == null) {
            return false;
        }
        int count = visual.count();
        if (index < 0 || index >= count) {
            return false;
        }
        int previous = selectedContentCandidateByPath.getOrDefault(pathId, 0);
        selectedContentCandidateByPath.put(pathId, index);
        boolean changed = previous != index;
        String inputKey = changed ? inputKey(slot) : null;
        String variantKey = inputKey == null ? null : variantKey(slot, index);
        if (changed && inputKey != null && variantKey != null) {
            contentVariantPreferences = withPreference(contentVariantPreferences, inputKey, variantKey);
            onContentCandidateSelected.accept(new ContentCandidateSelection(pathId, inputKey, variantKey, index));
        }
        CirrusUiSound.playClickSound();
        return true;
    }

    private static Map<String, String> withPreference(Map<String, String> preferences, String inputKey, String variantKey) {
        LinkedHashMap<String, String> next = new LinkedHashMap<>(preferences);
        next.put(inputKey, variantKey);
        return Map.copyOf(next);
    }

    private int selectedContentCandidateIndex() {
        if (contentCandidatePanelNodeId == null) {
            return 0;
        }
        MaterialSlot slot = contentCandidatePanelSlot();
        SlotVisual visual = slot == null ? null : slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        int selected = selectedContentCandidateByPath.getOrDefault(contentCandidatePanelNodeId, 0);
        return count <= 0 ? 0 : Math.max(0, Math.min(selected, count - 1));
    }

    private int contentCandidateScrollRow() {
        if (contentCandidatePanelNodeId == null) {
            return 0;
        }
        return Math.max(0, contentCandidateScrollRowsByPath.getOrDefault(contentCandidatePanelNodeId, 0));
    }

    private void setContentCandidateScrollRow(int row) {
        if (contentCandidatePanelNodeId != null) {
            contentCandidateScrollRowsByPath.put(contentCandidatePanelNodeId, Math.max(0, row));
        }
    }

    private float contentCandidateDisplayScrollRow(int maxScrollRow) {
        if (contentCandidatePanelNodeId == null) {
            return 0.0f;
        }
        float row = contentCandidateDisplayScrollRowsByPath.getOrDefault(
                contentCandidatePanelNodeId,
                (float) contentCandidateScrollRow()
        );
        row = Math.max(0.0f, Math.min(maxScrollRow, row));
        contentCandidateDisplayScrollRowsByPath.put(contentCandidatePanelNodeId, row);
        return row;
    }

    private void openContentCandidatePanel(String pathId) {
        contentCandidatePanelNodeId = pathId;
        int row = contentCandidateScrollRowsByPath.getOrDefault(pathId, 0);
        contentCandidateDisplayScrollRowsByPath.put(pathId, (float) row);
    }

    private void animateContentCandidateScroll() {
        if (contentCandidatePanelNodeId == null) {
            return;
        }
        MaterialSlot slot = contentCandidatePanelSlot();
        if (slot == null || slot.content().isEmpty()) {
            return;
        }
        SlotVisual visual = slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        if (count <= 0) {
            return;
        }
        int[] bounds = contentCandidatePanelBounds();
        float scale = contentCandidatePanelScale();
        int visibleRows = contentCandidatePanelVisibleRows(count, bounds == null ? 0 : bounds[3], scale);
        int maxScrollRow = Math.max(0, contentCandidatePanelRows(count) - visibleRows);
        int target = Math.max(0, Math.min(maxScrollRow, contentCandidateScrollRow()));
        contentCandidateScrollRowsByPath.put(contentCandidatePanelNodeId, target);
        float current = contentCandidateDisplayScrollRowsByPath.getOrDefault(contentCandidatePanelNodeId, (float) target);
        current = Math.max(0.0f, Math.min(maxScrollRow, current));
        if (Math.abs(target - current) > 0.01f) {
            current += (target - current) * contentCandidateScrollSmooth;
            if (Math.abs(target - current) < 0.01f) {
                current = target;
            }
        } else {
            current = target;
        }
        contentCandidateDisplayScrollRowsByPath.put(contentCandidatePanelNodeId, current);
    }

    private boolean scrollContentCandidatePanel(double scrollY) {
        MaterialSlot slot = contentCandidatePanelSlot();
        if (slot == null || slot.content().isEmpty()) {
            return false;
        }
        SlotVisual visual = slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        if (count <= 0) {
            return false;
        }
        int[] bounds = contentCandidatePanelBounds();
        float scale = contentCandidatePanelScale();
        int visibleRows = contentCandidatePanelVisibleRows(count, bounds == null ? 0 : bounds[3], scale);
        int maxScrollRow = Math.max(0, contentCandidatePanelRows(count) - visibleRows);
        if (maxScrollRow <= 0) {
            return false;
        }
        int current = contentCandidateScrollRow();
        int next = current + Math.round(contentCandidateWheelState.wheelDelta(0, scrollY).y());
        next = Math.max(0, Math.min(maxScrollRow, next));
        if (next == current) {
            return true;
        }
        setContentCandidateScrollRow(next);
        return true;
    }

    private int contentCandidatePanelBaseHeight(int count) {
        int rows = contentCandidatePanelRows(count);
        return contentCandidatePanelHeaderH + 8
                + rows * contentCandidateCell
                + Math.max(0, rows - 1) * contentCandidateCellGap
                + 5;
    }

    private static int contentCandidatePanelColumns(int count) {
        if (count <= 0) {
            return 1;
        }
        int available = contentCandidatePanelW - 12 - candidateScrollbarW - 4;
        int maxColumns = Math.max(1, (available + contentCandidateCellGap) / (contentCandidateCell + contentCandidateCellGap));
        return Math.max(1, Math.min(maxColumns, Math.min(7, count)));
    }

    private static int contentCandidatePanelMaxRenderableCells(int panelHeight, float scale) {
        return contentCandidatePanelColumns(Integer.MAX_VALUE)
                * Math.max(1, contentCandidatePanelVisibleRows(Integer.MAX_VALUE, panelHeight, scale) + 1);
    }

    private static int contentCandidatePanelRows(int count) {
        int columns = contentCandidatePanelColumns(count);
        return Math.max(1, (int) Math.ceil(Math.max(1, count) / (double) columns));
    }

    private static int contentCandidatePanelVisibleRows(int count, int panelHeight, float scale) {
        int rows = contentCandidatePanelRows(count);
        int contentHeight = Math.max(0, panelHeight - panelSize(contentCandidatePanelHeaderH + 8 + 5, scale));
        double localContentHeight = contentHeight / Math.max(0.0001d, scale);
        int rowsThatFit = Math.max(1, (int) Math.floor(
                (localContentHeight + contentCandidateCellGap + 0.001d)
                        / (contentCandidateCell + contentCandidateCellGap)
        ));
        return Math.max(1, Math.min(rows, rowsThatFit));
    }

    private static boolean contentCandidateNeedsScrollbar(int count, int panelHeight, float scale) {
        return contentCandidatePanelRows(count) > contentCandidatePanelVisibleRows(count, panelHeight, scale);
    }

    private void drawCandidateScrollbar(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        float scrollRow,
        int maxScrollRow,
        int visibleRows,
        int totalRows
    ) {
        if (maxScrollRow <= 0 || totalRows <= 0 || height <= 0) {
            return;
        }
        canvas.texture(Background.scroll, x, y, width, height);
        int thumbH = Math.max(10, Math.round(height * (visibleRows / (float) totalRows)));
        int travel = Math.max(0, height - thumbH);
        int thumbY = y + Math.round(travel * (scrollRow / Math.max(1.0f, (float) maxScrollRow)));
        canvas.texture(Icon.scrollBarVertical, x + 1, thumbY, Math.max(1, width - 2), thumbH);
    }

    private float contentCandidatePanelScale() {
        LayoutNode node = contentCandidatePanelNodeId == null ? null : findLayoutNode(contentCandidatePanelNodeId);
        MaterialSlot slot = contentCandidatePanelSlot(node);
        SlotVisual visual = slot == null ? null : slotVisual(slot);
        int count = visual == null ? 1 : visual.count();
        return contentCandidatePanelScale(count, node);
    }

    private float contentCandidatePanelScale(int count, @Nullable LayoutNode anchorNode) {
        float requested = machineCandidatePanelScale(zoom);
        int maxPanelHeight = Math.max(4, height() - 8);
        while (requested > 0.15f
                && anchorNode != null
                && contentCandidatePanelMaxRenderableCells(maxPanelHeight, requested) < Math.min(count, contentCandidatePanelMaxCells)
                && !contentCandidatePanelCanFitSide(anchorNode, requested)) {
            requested *= 0.9f;
        }
        return fitPanelScaleToViewportWidth(requested, contentCandidatePanelW);
    }

    private boolean contentCandidatePanelCanFitSide(LayoutNode node, float scale) {
        int w = scaled(contentCandidatePanelW, scale);
        int vw = viewportW();
        int anchorRight = worldToScreenX(node.x + nodeW + 6);
        int anchorLeft = worldToScreenX(node.x - 6) - w;
        return anchorRight + w <= vw - 4 || anchorLeft >= 4;
    }

    private void drawMachineCandidatePanel(SceneCanvas canvas, Font font, int mouseX, int mouseY, float pt) {
        int[] bounds = machineCandidatePanelBounds();
        if (bounds == null) {
            return;
        }
        LayoutNode layoutNode = findLayoutNode(machineCandidatePanelNodeId);
        if (layoutNode == null || layoutNode.processNode == null) {
            return;
        }
        ProcessNode node = layoutNode.processNode;
        int x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        float scale = machineCandidatePanelScale(node);
        List<MachineInfo> machines = renderModel(layoutNode).machines();
        double localMouseX = (mouseX - x) / (double) scale;
        double localMouseY = (mouseY - y) / (double) scale;

        canvas.shadow(x, y + 1, w, h, 2, 2, 6, 0x42000000);
        canvas.pushTransform();
        canvas.translate(x, y);
        canvas.scale(scale);
        int baseH = machineCandidatePanelBaseHeight(machines.size());
        canvas.texture(Background.Outlined.flat, 0, 0, machineCandidatePanelW, baseH);
        canvas.drawString(String.valueOf(machines.size()), 8, 8, textSecondary, false);
        drawSmallGlyphButton(canvas, machineCandidatePanelW - 19, 4, 14, 14,
                isInMachineCandidatePanelClose(mouseX, mouseY), Icon.Small.minus, Icon.Small.minusSelected, 5, 6);
        RenderSession session = new RenderSession(canvas, pt);

        int gridX = 6;
        int gridY = machineCandidatePanelHeaderH + 3;
        int columns = machineCandidatePanelColumns(machines.size());
        int visible = Math.min(machines.size(), machineCandidatePanelMaxCells());
        if (visible > 0) {
            CirrusSlotGroupRenderer.renderRowMajorCells(canvas, gridX, gridY, machineCandidateCell,
                    visible, columns, nodeAccentColor(node));
        }
        String selectedName = selectedMachineName(layoutNode);
        for (int i = 0; i < visible; i++) {
            int col = i % columns;
            int row = i / columns;
            drawMachineCandidateCell(canvas, node, machines.get(i), selectedName,
                    gridX + col * machineCandidateCell, gridY + row * machineCandidateCell,
                    localMouseX, localMouseY, session);
        }
        if (machines.size() > visible) {
            drawMoreDots(canvas, machineCandidatePanelW - 18, baseH - 9, Math.min(3, machines.size() - visible), cardMetaText);
        }
        session.finish();
        canvas.popTransform();
    }

    private void drawMachineCandidateCell(SceneCanvas canvas, ProcessNode node, MachineInfo machine,
                                          String selectedName, int x, int y,
                                          double localMouseX, double localMouseY, RenderSession session) {
        boolean hovered = localMouseX >= x && localMouseX <= x + machineCandidateCell
                && localMouseY >= y && localMouseY <= y + machineCandidateCell;
        boolean current = Objects.equals(machine.name(), selectedName);
        if (current || hovered) {
            session.addMachineCandidateOverlay(x, y, nodeAccentColor(node), current, hovered);
        }
        ContentStack<?, ?> icon = machine.icon();
        if (icon.notEmpty()) {
            float itemScale = Math.max(0.5f, Math.min(1.25f, (machineCandidateCell - 4) / 16.0f));
            submitContentStack(icon, x + 2, y + 2, itemScale, session);
        }
    }

    private @Nullable int[] machineCandidatePanelBounds() {
        if (machineCandidatePanelNodeId == null) {
            return null;
        }
        LayoutNode node = findLayoutNode(machineCandidatePanelNodeId);
        if (node == null || node.processNode == null) {
            return null;
        }
        float scale = machineCandidatePanelScale(node.processNode);
        int w = scaled(machineCandidatePanelW, scale);
        int h = scaled(machineCandidatePanelBaseHeight(renderModel(node).machines().size()), scale);
        int vw = viewportW();
        int anchorRight = worldToScreenX(node.x + nodeW + 6);
        int anchorLeft = worldToScreenX(node.x - 6) - w;
        int x = anchorRight + w <= vw - 4 ? anchorRight : anchorLeft;
        x = Math.max(4, Math.min(x, Math.max(4, vw - w - 4)));
        int y = worldToScreenY(node.y + 2);
        y = Math.max(4, Math.min(y, Math.max(4, height() - h - 4)));
        return new int[]{x, y, w, h};
    }

    private boolean isInMachineCandidatePanel(double mx, double my) {
        int[] b = machineCandidatePanelBounds();
        return b != null && mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    private boolean isInMachineCandidatePanelClose(double mx, double my) {
        int[] b = machineCandidatePanelBounds();
        if (b == null) {
            return false;
        }
        float scale = machineCandidatePanelScale();
        double lx = (mx - b[0]) / scale;
        double ly = (my - b[1]) / scale;
        return lx >= machineCandidatePanelW - 19 && lx <= machineCandidatePanelW - 5
                && ly >= 4 && ly <= 18;
    }

    private int machineCandidateIndexAt(double mx, double my) {
        int[] b = machineCandidatePanelBounds();
        LayoutNode layoutNode = machineCandidatePanelNodeId == null ? null : findLayoutNode(machineCandidatePanelNodeId);
        if (b == null || layoutNode == null || layoutNode.processNode == null) {
            return -1;
        }
        float scale = machineCandidatePanelScale();
        double lx = (mx - b[0]) / scale;
        double ly = (my - b[1]) / scale;
        List<MachineInfo> candidates = renderModel(layoutNode).machines();
        int columns = machineCandidatePanelColumns(candidates.size());
        int gridX = 6;
        int gridY = machineCandidatePanelHeaderH + 3;
        int visible = Math.min(candidates.size(), machineCandidatePanelMaxCells());
        int rows = machineCandidatePanelRows(candidates.size());
        int gridW = columns * machineCandidateCell;
        int gridH = rows * machineCandidateCell;
        if (lx < gridX || lx >= gridX + gridW || ly < gridY || ly >= gridY + gridH) {
            return -1;
        }
        int col = (int) ((lx - gridX) / machineCandidateCell);
        int row = (int) ((ly - gridY) / machineCandidateCell);
        int index = row * columns + col;
        return index >= 0 && index < visible ? index : -1;
    }

    private boolean selectMachineCandidate(String pathId, int index) {
        LayoutNode layoutNode = findLayoutNode(pathId);
        if (layoutNode == null || layoutNode.processNode == null) {
            return false;
        }
        List<MachineInfo> candidates = renderModel(layoutNode).machines();
        if (index < 0 || index >= candidates.size()) {
            return false;
        }
        selectedMachineByPath.put(pathId, candidates.get(index).name());
        return true;
    }

    private List<MachineInfo> machineCandidates(ProcessNode node) {
        if (node == null) {
            return List.of();
        }
        return machineCandidatesByNodeId.computeIfAbsent(node.id(), ignored -> machineCandidatesStatic(node));
    }

    private static List<MachineInfo> machineCandidatesStatic(ProcessNode node) {
        if (!node.machineOptions().isEmpty()) {
            return dedupeMachineCandidates(node.machineOptions(), node.machine());
        }
        return node.machine().isBlank() ? List.of() : List.of(new MachineInfo(node.machine()));
    }

    private static List<MachineInfo> dedupeMachineCandidates(List<MachineInfo> machines, String preferredName) {
        if (machines.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MachineInfo> deduped = new LinkedHashMap<>();
        for (MachineInfo machine : machines) {
            if (machine == null || machine.name() == null || machine.name().isBlank()) {
                continue;
            }
            String key = machineKey(machine);
            MachineInfo existing = deduped.get(key);
            if (existing == null || shouldReplaceMachineCandidate(existing, machine, preferredName)) {
                deduped.put(key, machine);
            }
        }
        return List.copyOf(deduped.values());
    }

    private static boolean shouldReplaceMachineCandidate(MachineInfo existing, MachineInfo candidate, String preferredName) {
        boolean candidatePreferred = Objects.equals(candidate.name(), preferredName);
        boolean existingPreferred = Objects.equals(existing.name(), preferredName);
        if (candidatePreferred != existingPreferred) {
            return candidatePreferred;
        }
        ContentStack<?, ?> existingIcon = existing.icon();
        ContentStack<?, ?> candidateIcon = candidate.icon();
        return existingIcon.isEmpty() && candidateIcon.notEmpty();
    }

    private static String machineKey(MachineInfo machine) {
        ContentStack<?, ?> icon = machine.icon();
        if (icon.notEmpty() && icon.isItem()) {
            ContentStack<Item, ItemStack> item = icon.cast();
            return item.value().getItemHolder().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElseGet(() -> machine.name().strip().toLowerCase(Locale.ROOT));
        }
        return machine.name().strip().toLowerCase(Locale.ROOT);
    }

    private String selectedMachineName(LayoutNode layoutNode) {
        ProcessNode node = layoutNode.processNode;
        String selected = selectedMachineByPath.get(layoutNode.pathId);
        List<MachineInfo> candidates = renderModel(layoutNode).machines();
        boolean hasNodeMachine = false;
        for (MachineInfo machine : candidates) {
            if (selected != null && Objects.equals(machine.name(), selected)) {
                return selected;
            }
            if (Objects.equals(machine.name(), node.machine())) {
                hasNodeMachine = true;
            }
        }
        return hasNodeMachine || candidates.isEmpty() ? node.machine() : candidates.getFirst().name();
    }

    private int machineCandidatePanelBaseHeight(int machineCount) {
        int rows = machineCandidatePanelRows(machineCount);
        return machineCandidatePanelHeaderH + 8 + rows * machineCandidateCell
                + (machineCount > machineCandidatePanelMaxCells() ? 8 : 4);
    }

    private static int machineCandidatePanelColumns(int count) {
        if (count <= 0) {
            return 1;
        }
        int available = machineCandidatePanelW - 12;
        int maxColumns = Math.max(1, available / machineCandidateCell);
        return Math.max(1, Math.min(maxColumns, count));
    }

    private static int machineCandidatePanelMaxCells() {
        return Math.max(1, machineCandidatePanelColumns(Integer.MAX_VALUE) * machineCandidatePanelMaxRows);
    }

    private static int machineCandidatePanelRows(int count) {
        int visible = Math.max(1, Math.min(count, machineCandidatePanelMaxCells()));
        return Math.max(1, (int) Math.ceil(visible / (double) machineCandidatePanelColumns(count)));
    }

    private float machineCandidatePanelScale() {
        LayoutNode node = machineCandidatePanelNodeId == null ? null : findLayoutNode(machineCandidatePanelNodeId);
        return node == null || node.processNode == null
                ? machineCandidatePanelScale(zoom)
                : machineCandidatePanelScale(node.processNode);
    }

    private float machineCandidatePanelScale(ProcessNode node) {
        return fitPanelScaleToViewport(machineCandidatePanelScale(zoom), machineCandidatePanelW,
                machineCandidatePanelBaseHeight(machineCandidates(node).size()));
    }

    private static float machineCandidatePanelScale(float zoom) {
        if (Float.isNaN(zoom) || Float.isInfinite(zoom)) {
            return 1.0f;
        }
        return Math.max(0.15f, Math.min(3.0f, zoom));
    }

    private float fitPanelScaleToViewportWidth(float requestedScale, int baseW) {
        if (baseW <= 0) {
            return requestedScale;
        }
        int availableW = viewportW() - 8;
        if (availableW > 0) {
            requestedScale = Math.min(requestedScale, availableW / (float) baseW);
        }
        return Math.max(0.15f, requestedScale);
    }

    private float fitPanelScaleToViewport(float requestedScale, int baseW, int baseH) {
        requestedScale = fitPanelScaleToViewportWidth(requestedScale, baseW);
        int availableH = height() - 8;
        if (baseH > 0 && availableH > 0) {
            requestedScale = Math.min(requestedScale, availableH / (float) baseH);
        }
        return Math.max(0.15f, requestedScale);
    }

    private int worldToScreenX(double worldX) {
        return (int) Math.round((worldX - panX) * zoom);
    }

    private int worldToScreenY(double worldY) {
        return (int) Math.round((worldY - panY) * zoom);
    }

    private static int scaled(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private void drawSmallGlyphButton(SceneCanvas canvas, int x, int y, int w, int h, boolean hovered,
                                      SizedTexture icon, SizedTexture hoveredIcon, int iconW, int iconH) {
        CirrusControlChrome.renderButtonBackground(canvas, x, y, w, h, false, hovered, true);
        SizedTexture drawIcon = hovered ? hoveredIcon : icon;
        canvas.texture(drawIcon, x + (w - iconW) / 2, y + Math.max(0, (h - iconH) / 2 - 1), iconW, iconH);
    }

    private void submitItemStack(ItemStack stack, int x, int y, float scale, RenderSession session) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int size = Math.max(1, Math.round(16 * scale));
        ContentStack<?, ?> contentStack = itemStackVisualCache.computeIfAbsent(stack, ContentStack::fromStack);
        if (contentStack.notEmpty()) {
            session.addBaseStack(contentStack, x, y, size);
            return;
        }
        session.deferItem(stack, x, y, scale);
    }

    private void submitFluidStack(FluidStack fluidStack, int x, int y, float scale, RenderSession session) {
        if (fluidStack == null || fluidStack.isEmpty()) {
            return;
        }
        int size = Math.max(1, Math.round(16 * scale));
        ContentStack<?, ?> contentStack = fluidStackVisualCache.computeIfAbsent(fluidStack, ContentStack::fromStack);
        if (contentStack.notEmpty()) {
            session.addBaseStack(contentStack, x, y, size);
            return;
        }
        session.deferFluid(fluidStack, x, y, scale, session.pt);
    }

    private void submitContentStack(ContentStack<?, ?> stack, int x, int y, float scale, RenderSession session) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int size = Math.max(1, Math.round(16 * scale));
        session.addBaseStack(stack, x, y, size);
    }

    // ═══════════════════ Material Panel ═══════════════════

    private static final int searchBarH = materialPanelSearchH;

    private int materialPanelContentTop() {
        return 4 + materialPanelHeaderH + materialPanelSearchH + 3;
    }

    private int panelContentVisibleH() {
        return materialPanelVisibleHeight(height());
    }

    private int materialPanelVisibleHeight(int viewportHeight) {
        int contentTop = materialPanelContentTop();
        return Math.max(0, viewportHeight - contentTop - 4);
    }

    private int materialPanelMaxScroll() {
        return materialPanelMaxScroll(panelContentVisibleH());
    }

    private int materialPanelMaxScroll(int visibleH) {
        return Math.max(0, matFilteredContentH - Math.max(0, visibleH));
    }

    private boolean materialPanelNeedsScrollbar(int visibleH) {
        return materialPanelMaxScroll(visibleH) > 0;
    }

    private boolean isInMaterialPanel(double mx, double my) {
        if (!showMaterialPanel) return false;
        int px = width() - panelW;
        return mx >= px && mx <= width() && my >= 0 && my <= height();
    }

    private @Nullable MaterialEntry materialEntryAt(double mx, double my) {
        if (!showMaterialPanel || !isInMaterialPanel(mx, my)) {
            return null;
        }
        int px = width() - panelW;
        int contentY = materialPanelContentTop();
        int visibleH = panelContentVisibleH();
        if (visibleH <= 0 || my < contentY || my > contentY + visibleH) {
            return null;
        }
        boolean hasScrollbar = materialPanelNeedsScrollbar(visibleH);
        int innerW = panelW - 2 * panelPad - (hasScrollbar ? scrollbarW + 4 : 0);
        double localX = mx - px - panelPad;
        double localY = my - contentY + matScrollY;
        if (localX < 0 || localX > innerW || localY < 0) {
            return null;
        }
        int drawY = 0;
        for (List<MaterialEntry> entries : materialFilteredByCategory.values()) {
            if (entries.isEmpty()) {
                continue;
            }
            drawY += secHdrH + 2;
            for (MaterialEntry entry : entries) {
                if (localY >= drawY && localY <= drawY + entryH) {
                    return entry;
                }
                drawY += entryH + entryGap;
            }
            drawY += 6;
        }
        return null;
    }

    private boolean isInMaterialSearchField(double mx, double my) {
        if (!showMaterialPanel) return false;
        int px = width() - panelW;
        int sfX = px + 8;
        int sfY = 4 + materialPanelHeaderH + 3;
        int sfW = panelW - 16;
        int sfH = materialPanelSearchH - 3;
        return mx >= sfX && mx <= sfX + sfW && my >= sfY && my <= sfY + sfH;
    }

    private boolean isInMaterialSearchClear(double mx, double my) {
        if (!showMaterialPanel || matFilterText.isEmpty()) return false;
        int px = width() - panelW;
        int sfY = 4 + materialPanelHeaderH + 3;
        int clearX = px + panelW - 20;
        return mx >= clearX && mx <= clearX + 10 && my >= sfY + 2 && my <= sfY + 12;
    }

    private boolean isOnScrollbarThumb(double mx, double my) {
        if (!showMaterialPanel) return false;
        int maxScroll = materialPanelMaxScroll();
        if (maxScroll <= 0) return false;
        int px = width() - panelW;
        int contentY = materialPanelContentTop();
        int visibleH = panelContentVisibleH();
        int sbX = px + panelW - scrollbarW - 3;
        int sbH = visibleH - 2;
        int thumbH = Math.max(16, (int) ((float) visibleH / Math.max(1, matFilteredContentH) * sbH));
        float progress = matScrollTargetY / maxScroll;
        int thumbY = contentY + 1 + (int) (progress * (sbH - thumbH));
        return mx >= sbX && mx <= sbX + scrollbarW && my >= thumbY && my <= thumbY + thumbH;
    }

    private void drawMaterialPanel(SceneCanvas canvas, Font font,
                                    int mouseX, int mouseY, float pt) {
        sanitizeMaterialScroll();
        int px = width() - panelW;
        int ph = height();
        canvas.shadow(px - 2, 0, panelW + 2, ph, 4, 2, 8, 0x60000000);
        canvas.texture(Background.frame, px, 0, panelW, ph);
        canvas.texture(Background.inset, px + 4, 4, panelW - 8, ph - 8);

        int headerH = materialPanelHeaderH;
        canvas.texture(Background.Outlined.inset, px + 4, 4, panelW - 8, headerH);
        canvas.texture(Sign.Group.item, px + 6, 6, 3, headerH - 4);
        canvas.drawString(CirrusLangs.Calc.Tree.materialList.string(), px + 14, 11, textPrimary, false);

        // Item count badge
        int totalItems = filteredMaterialEntryCount();
        String badge = String.valueOf(totalItems);
        int badgeW = font.width(badge) + 6;
        int badgeX = px + panelW - badgeW - 8;
        canvas.texture(Background.Outlined.inset, badgeX, 8, badgeW, 12);
        canvas.drawCenteredString(badge, badgeX + badgeW / 2, 11, textPrimary);

        int sfX = px + 8;
        int sfY = 4 + headerH + 3;
        int sfW = panelW - 16;
        int sfH = searchBarH - 3;
        canvas.texture(matSearchActive ? Background.Outlined.inset : Background.flat, sfX, sfY, sfW, sfH);
        canvas.texture(Icon.search, sfX + 3, sfY + 2, 10, 10);
        int textX = sfX + 16;
        int maxTextW = sfW - 28 - (matFilterText.isEmpty() ? 0 : 12);
        if (matFilterText.isEmpty()) {
            canvas.drawString(CirrusLangs.Calc.Tree.materialSearchPlaceholder.string(), textX, sfY + 3, textWeak, false);
        } else {
            String displayText = matFilterText;
            while (font.width(displayText) > maxTextW && displayText.length() > 1) {
                displayText = displayText.substring(1);
            }
            canvas.drawString(displayText, textX, sfY + 3, textPrimary, false);
            int clearX = px + panelW - 20;
            canvas.texture(Icon.delete, clearX, sfY + 2, 10, 10);
            if (matSearchActive && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = textX + font.width(displayText);
                canvas.fill(cursorX, sfY + 2, 1, sfH - 4, textPrimary);
            }
        }

        int contentY = materialPanelContentTop();
        int visibleH = panelContentVisibleH();
        stickyHeaderCat = null;

        Pos absP = absolutePos();
        canvas.pushClip(absP.x() + px + 4, absP.y() + contentY, panelW - 8, visibleH);
        canvas.pushTransform();
        canvas.translate(px + panelPad, contentY - matScrollY);
        RenderSession session = new RenderSession(canvas, pt);
        renderMaterialContent(canvas, font, mouseX - px - panelPad, (int) (mouseY - contentY + matScrollY), session,
                matScrollY, matScrollY + visibleH);
        session.finish();
        canvas.popTransform();
        canvas.popClip();

        // Draw sticky category header ON TOP of the clipped content, in widget-local coords
        boolean hasScrollbar = materialPanelNeedsScrollbar(visibleH);
        int innerW = panelW - 2 * panelPad - (hasScrollbar ? scrollbarW + 4 : 0);
        if (stickyHeaderCat != null) {
            int stickyDrawY = contentY + stickyHeaderOffsetY;
            canvas.texture(Background.inset, px + panelPad, stickyDrawY - 1, innerW, secHdrH + 3);
            drawCategoryHeader(canvas, font, stickyHeaderCat, stickyHeaderCount,
                    px + panelPad, stickyDrawY, innerW);
        }

        int maxScroll = materialPanelMaxScroll(visibleH);
        if (maxScroll > 0) {
            int sbX = px + panelW - scrollbarW - 2;
            int sbY = contentY + 1;
            int sbH = visibleH - 2;
            canvas.texture(Background.scroll, sbX, sbY, scrollbarW, sbH);
            int thumbH = Math.max(16, (int) ((float) visibleH / Math.max(1, matFilteredContentH) * sbH));
            float progress = maxScroll > 0 ? matScrollY / maxScroll : 0;
            int thumbY = sbY + (int) (progress * (sbH - thumbH));
            canvas.texture(Icon.scrollBarVertical, sbX + 1, thumbY, scrollbarW - 2, thumbH);
        }
    }

    private int filteredMaterialEntryCount() {
        return materialFilteredEntryCount;
    }

    private void renderMaterialContent(SceneCanvas canvas, Font font,
                                        int mouseX, int mouseY, RenderSession session,
                                        float visMinY, float visMaxY) {
        int drawY = 0;
        boolean hasScrollbar = materialPanelNeedsScrollbar((int) (visMaxY - visMinY));
        int innerW = panelW - 2 * panelPad - (hasScrollbar ? scrollbarW + 4 : 0);
        hoveredMatName = null;

        for (var catEntry : materialFilteredByCategory.entrySet()) {
            MatCategory cat = catEntry.getKey();
            List<MaterialEntry> entries = catEntry.getValue();
            if (entries.isEmpty()) continue;
            int headerY = drawY;

            // Draw category header (only if visible)
            if (drawY + secHdrH >= visMinY && drawY <= visMaxY) {
                drawCategoryHeader(canvas, font, cat, entries.size(), 0, drawY, innerW);
            }
            drawY += secHdrH + 2;
            for (MaterialEntry entry : entries) {
                if (drawY + entryH >= visMinY && drawY <= visMaxY)
                    drawMaterialEntry(canvas, font, entry, 0, drawY, innerW, session, mouseX, mouseY);
                drawY += entryH + entryGap;
            }
            int sectionEnd = drawY;
            drawY += 6;

            // Track sticky header: category whose header scrolled past the top
            if (headerY < visMinY && sectionEnd > visMinY) {
                stickyHeaderCat = cat;
                stickyHeaderCount = entries.size();
                // Calculate offset for "push up" effect when next section approaches
                int distToEnd = (int) (sectionEnd - visMinY);
                stickyHeaderOffsetY = distToEnd < secHdrH + 6 ? distToEnd - secHdrH - 6 : 0;
            }
        }

        drawY += 2;
        if (drawY + totalsH + 6 >= visMinY && drawY <= visMaxY) {
            canvas.texture(Background.Sign.bar, 4, drawY, Math.max(16, innerW - 8), 2);
            drawY += 6;
            canvas.texture(Background.inset, 0, drawY, innerW, totalsH);
            int txL = 6, txR = innerW - 8;
            int r0 = drawY + 6, r1 = drawY + 20, r2 = drawY + 36;
            canvas.texture(Icon.Sign.Energy.time, txL, r0, 9, 9);
            canvas.drawString(CirrusLangs.Calc.Tree.totalTime.string(), txL + 12, r0, textWeak, false);
            String timeStr = formatDuration(totalTimeTicks);
            canvas.drawString(timeStr, txR - font.width(timeStr), r0, textPrimary, false);
            canvas.texture(Icon.Sign.Amount.available, txL + 1, r2 + 3, 5, 5);
            canvas.drawString(CirrusLangs.Calc.Tree.stepCount.string(), txL + 10, r2, textWeak, false);
            String stepStr = String.valueOf(flatNodes.size());
            canvas.drawString(stepStr, txR - font.width(stepStr), r2, green, false);
        }
    }

    private static ImageTexture categoryMarker(MatCategory cat) {
        return switch (cat) {
            case BYPRODUCT -> Sign.Group.fluid;
            case CATALYST -> Sign.Group.catalyst;
            case EXTERNAL_INPUT, INTERMEDIATE -> Sign.Group.item;
        };
    }

    private void drawCategoryHeader(SceneCanvas canvas, Font font,
                                     MatCategory cat, int count, int x, int y, int w) {
        canvas.texture(Background.Outlined.inset, x, y, w, secHdrH);
        canvas.texture(categoryMarker(cat), x + 2, y + 2, 3, secHdrH - 4);
        canvas.drawString(cat.label.string(), x + 8, y + 4, textSecondary, false);
        String countStr = String.valueOf(count);
        int cw = font.width(countStr);
        canvas.drawString(countStr, x + w - cw - 4, y + 4, textSecondary, false);
    }

    private int drawMaterialQuantityChip(SceneCanvas canvas, Font font,
                                         int x, int y, int right,
                                         String label, String value, int valueColor) {
        if (x >= right - 18 || value == null || value.isBlank()) {
            return x;
        }
        int labelW = font.width(label);
        int rawW = labelW + font.width(value) + 11;
        int chipW = Math.max(24, Math.min(rawW, right - x));
        canvas.texture(Background.flat, x, y, chipW, 12);
        canvas.drawString(label, x + 4, y + 2, textWeak, false);
        int valueX = x + labelW + 7;
        canvas.drawString(cachedTruncate(value, Math.max(0, chipW - (valueX - x) - 3), font),
                valueX, y + 2, valueColor, false);
        return x + chipW + 3;
    }

    private void drawMaterialEntry(SceneCanvas canvas, Font font,
                                    MaterialEntry entry, int x, int y, int w,
                                    RenderSession session, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + entryH;
        if (hovered) hoveredMatName = entry.name();

        canvas.texture(hovered ? Background.Border.light : Background.flat, x, y, w, boxH);

        int pad = 3;
        int slotX = x + pad, slotY = y + pad;
        CirrusSlotGroupRenderer.renderSingle(canvas, slotX, slotY, iconSlotSz);
        int iconX = slotX + (iconSlotSz - panelIconSz) / 2;
        int iconY = slotY + (iconSlotSz - panelIconSz) / 2;
        drawPanelSlotIcon(canvas, entry.refSlot(), iconX, iconY, session);

        int nameX = slotX + iconSlotSz + 4;
        int nameW = w - (nameX - x) - pad;
        String name = cachedTruncate(entry.name(), nameW, font);
        canvas.drawString(name, nameX, y + 4, textPrimary, false);

        int chipX = nameX;
        int chipY = y + 25;
        int right = x + w - pad;
        switch (entry.category()) {
            case EXTERNAL_INPUT -> drawMaterialQuantityChip(canvas, font, chipX, chipY, right,
                    CirrusLangs.Calc.Tree.needShort.string(), fmtNum(entry.needed()), red);
            case INTERMEDIATE -> {
                chipX = drawMaterialQuantityChip(canvas, font, chipX, chipY, right,
                        CirrusLangs.Calc.Tree.producedShort.string(), fmtNum(entry.produced()), green);
                drawMaterialQuantityChip(canvas, font, chipX, chipY, right,
                        entry.missing() > 0
                                ? CirrusLangs.Calc.Tree.missingShort.string()
                                : CirrusLangs.Calc.Tree.needShort.string(),
                        fmtNum(entry.missing() > 0 ? entry.missing() : entry.needed()),
                        entry.missing() > 0 ? red : textSecondary);
            }
            case BYPRODUCT -> {
                chipX = drawMaterialQuantityChip(canvas, font, chipX, chipY, right,
                        CirrusLangs.Calc.Tree.producedShort.string(), fmtNum(entry.produced()), orange);
                if (entry.chance() < 1.0) {
                    drawMaterialQuantityChip(canvas, font, chipX, chipY, right,
                            CirrusLangs.Calc.Tree.chanceShort.string(), (int) (entry.chance() * 100) + "%", orange);
                }
            }
            case CATALYST -> drawMaterialQuantityChip(canvas, font, chipX, chipY, right,
                    CirrusLangs.Calc.Tree.toolShort.string(), fmtNum(entry.needed()), purple);
        }

        int barY = y + boxH;
        int barX = x;
        int barW = w;
        canvas.fill(barX, barY, barW, barH, 0x33000000);
        int barInnerW = Math.max(0, barW - 2);
        if (barInnerW <= 0) return;
        switch (entry.category()) {
            case EXTERNAL_INPUT -> canvas.fill(barX + 1, barY, barInnerW, barH, red);
            case INTERMEDIATE -> {
                long total = Math.max(entry.needed(), entry.produced());
                if (total > 0) {
                    int greenW = (int) (barInnerW * Math.min(1.0, (double) entry.produced() / total));
                    if (greenW > 0) canvas.fill(barX + 1, barY, greenW, barH, green);
                    if (entry.missing() > 0) {
                        int redW = (int) (barInnerW * ((double) entry.missing() / total));
                        canvas.fill(barX + 1 + greenW, barY, Math.max(1, redW), barH, red);
                    }
                }
            }
            case BYPRODUCT -> canvas.fill(barX + 1, barY, barInnerW, barH,
                    withAlpha(orange, entry.chance() < 1.0 ? 0x88 : 0xBB));
            case CATALYST -> canvas.fill(barX + 1, barY, barInnerW, barH, withAlpha(purple, 0x88));
        }
    }

    private void drawPanelSlotIcon(SceneCanvas canvas, MaterialSlot slot, int ix, int iy,
                                   RenderSession session) {
        float iconScale = panelIconSz / 16.0f;
        ContentStack<?, ?> stack = slot.visualStack();
        if (stack.notEmpty()) {
            submitContentStack(stack, ix, iy, iconScale, session);
        } else {
            int c = slot.category().color();
            CirrusSlotGroupRenderer.renderSingle(canvas, ix, iy, panelIconSz);
            canvas.drawCenteredString(slot.category().name().substring(0, 1),
                    ix + panelIconSz / 2, iy + (panelIconSz - 9) / 2, c);
        }
    }

    // ═══════════════════ Hover & Hit Testing ═══════════════════

    private void updateHover(int mouseX, int mouseY) {
        hoveredNodeId = null;
        hoveredCollapseNodeId = null;
        hoveredMachineNodeId = null;
        hoveredProjectionNodeId = null;
        double lx = mouseX, ly = mouseY;
        if (lx < 0 || ly < 0 || lx > width() || ly > height()) return;
        if (showMaterialPanel && isInMaterialPanel(lx, ly)) return;
        if (machineCandidatePanelNodeId != null && isInMachineCandidatePanel(lx, ly)) return;
        if (contentCandidatePanelNodeId != null && isInContentCandidatePanel(lx, ly)) return;
        LayoutNode found = findNodeWithButtonAt(lx, ly);
        if (found != null) {
            hoveredNodeId = found.pathId;
            if (isCollapseButtonHit(found, lx, ly)) {
                hoveredCollapseNodeId = found.pathId;
                return;
            }
            if (isMachineSlotHit(found, lx, ly)) {
                hoveredMachineNodeId = found.pathId;
            }
            if (isProjectionActionHit(found, lx, ly)) {
                hoveredProjectionNodeId = found.pathId;
            }
        }
    }

    private boolean isNodeHovered(String pathId) {
        return Objects.equals(pathId, hoveredNodeId) || Objects.equals(pathId, externalHoveredNodeId);
    }

    private boolean isCollapseButtonHovered(String pathId) {
        return Objects.equals(pathId, hoveredCollapseNodeId);
    }

    private @Nullable LayoutNode findNodeAt(double screenX, double screenY) {
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        int firstLayer = Math.max(0, (int) Math.floor((wy - nodeH) / rowH) - 1);
        int lastLayer = Math.min(maxDepth, (int) Math.floor((wy + nodeH) / rowH) + 1);
        for (int layer = firstLayer; layer <= lastLayer; layer++) {
            List<LayoutNode> nodes = layerNodesByX.getOrDefault(layer, List.of());
            int first = firstNodeEndingAfter(nodes, wx);
            for (int i = first; i < nodes.size(); i++) {
                LayoutNode ln = nodes.get(i);
                if (ln.x > wx) {
                    break;
                }
                if (wx >= ln.x && wx <= ln.x + nodeW && wy >= ln.y && wy <= ln.y + nodeH) {
                    return ln;
                }
            }
        }
        return null;
    }

    private @Nullable LayoutNode findCollapseButtonAt(double screenX, double screenY) {
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        int firstLayer = Math.max(0, (int) Math.floor((wy - nodeH - collapseBtnH) / rowH) - 1);
        int lastLayer = Math.min(maxDepth, (int) Math.floor(wy / rowH) + 1);
        for (int layer = firstLayer; layer <= lastLayer; layer++) {
            List<LayoutNode> nodes = layerNodesByX.getOrDefault(layer, List.of());
            double halfButton = collapseBtnW / 2.0;
            int first = firstNodeCenterAfter(nodes, wx - halfButton);
            for (int i = first; i < nodes.size(); i++) {
                LayoutNode ln = nodes.get(i);
                if (ln.x + nodeW / 2.0 - halfButton > wx) {
                    break;
                }
                if (ln.originalChildCount <= 0) continue;
                double btnX = ln.x + nodeW / 2.0 - collapseBtnW / 2.0;
                double btnY = ln.y + nodeH - 1;
                if (wx >= btnX && wx <= btnX + collapseBtnW && wy >= btnY && wy <= btnY + collapseBtnH) {
                    return ln;
                }
            }
        }
        return null;
    }

    private @Nullable LayoutNode findNodeOrCollapseButtonAt(double screenX, double screenY) {
        LayoutNode hit = findNodeAt(screenX, screenY);
        if (hit != null) {
            return hit;
        }
        return findCollapseButtonAt(screenX, screenY);
    }

    /** Extended hit-test that includes the collapse button area below the node. */
    private @Nullable LayoutNode findNodeWithButtonAt(double screenX, double screenY) {
        return findNodeOrCollapseButtonAt(screenX, screenY);
    }

    private boolean isCollapseButtonHit(LayoutNode node, double screenX, double screenY) {
        if (node == null || node.originalChildCount <= 0) {
            return false;
        }
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        double btnX = node.x + nodeW / 2.0 - collapseBtnW / 2.0;
        double btnY = node.y + nodeH - 1;
        return wx >= btnX && wx <= btnX + collapseBtnW && wy >= btnY && wy <= btnY + collapseBtnH;
    }

    private boolean isMachineSlotHit(LayoutNode node, double screenX, double screenY) {
        if (node == null || node.processNode == null || !isRecipeLayoutNode(node)) {
            return false;
        }
        if (node.processNode.machineOptions().isEmpty() && node.processNode.machine().isBlank()) {
            return false;
        }
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        int[] bounds = machineSlotBounds(node.x, node.y);
        return containsWorldPoint(bounds, wx, wy);
    }

    private boolean isProjectionActionHit(LayoutNode node, double screenX, double screenY) {
        if (node == null || node.processNode == null || node.processNode.recipe() == null) {
            return false;
        }
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        int[] bounds = projectionButtonBounds(node.x, node.y);
        return containsWorldPoint(bounds, wx, wy);
    }

    private boolean isContentCandidateActionHit(LayoutNode node, double screenX, double screenY) {
        NodeRenderModel model = node == null || node.processNode == null ? null : renderModel(node);
        SlotVisual visual = model == null ? null : model.selectableVisual();
        if (model == null || model.selectableSlot() == null || visual == null || visual.count() <= 1) {
            return false;
        }
        double wx = panX + screenX / zoom;
        double wy = panY + screenY / zoom;
        int[] bounds = contentCandidateButtonBounds(node);
        return containsWorldPoint(bounds, wx, wy);
    }

    private static boolean isRecipeLayoutNode(LayoutNode node) {
        return node != null
                && node.processNode != null
                && (node.originalChildCount > 0 || isRecipeProcessNode(node.processNode));
    }

    private static boolean isRecipeProcessNode(ProcessNode node) {
        return node != null && (node.recipe() != null || !node.inputs().isEmpty());
    }

    private @Nullable MachineInfo getDisplayMachine(LayoutNode layoutNode) {
        ProcessNode node = layoutNode.processNode;
        String selectedName = selectedMachineName(layoutNode);
        for (MachineInfo machine : renderModel(layoutNode).machines()) {
            if (Objects.equals(machine.name(), selectedName)) {
                return machine;
            }
        }
        return getDisplayMachine(node, renderModel(layoutNode).machines());
    }

    private @Nullable MachineInfo getDisplayMachine(ProcessNode node, List<MachineInfo> opts) {
        if (opts.isEmpty()) return null;
        if (opts.size() == 1) return opts.get(0);
        for (MachineInfo mi : opts) if (mi.name().equals(node.machine())) return mi;
        return opts.get((int) ((machineCycleTicks / cycleIntervalTicks) % opts.size()));
    }

    // ═══════════════════ Tooltip ═══════════════════

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        updateHover(mouseX, mouseY);
        return treeTooltip(mouseX, mouseY);
    }

    private @Nullable Tooltip treeTooltip(int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
            return null;
        }
        Tooltip tip = Tooltip.create();

        if (machineCandidatePanelNodeId != null && isInMachineCandidatePanel(mouseX, mouseY)) {
            return buildMachineCandidatePanelTooltip(mouseX, mouseY);
        }
        if (contentCandidatePanelNodeId != null && isInContentCandidatePanel(mouseX, mouseY)) {
            return buildContentCandidatePanelTooltip(mouseX, mouseY);
        }

        if (hoveredProjectionNodeId != null) {
            RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
            builder.section(CirrusLangs.Calculator.Database.projection.string(), cardMetaText);
            builder.kv(
                    CirrusLangs.Calculator.Database.effectContext.string(),
                    0xFF888888,
                    CirrusLangs.Calculator.Database.effectSettings.string(),
                    cardMetaText
            );
            tip.add(builder.build());
            return tip;
        }

        if (hoveredMachineNodeId != null) {
            LayoutNode node = findLayoutNode(hoveredMachineNodeId);
            if (node != null && node.processNode != null) {
                return buildMachineTooltip(node);
            }
        }

        if (hoveredCollapseNodeId != null) {
            LayoutNode node = findLayoutNode(hoveredCollapseNodeId);
            if (node != null) {
                return buildCollapseButtonTooltip(node);
            }
        }

        MaterialEntry materialEntry = materialEntryAt(mouseX, mouseY);
        if (materialEntry != null) {
            var b = RichTooltipComponent.builder();
            b.section(materialEntry.name(), materialEntry.refSlot().category().color(), materialEntry.category().color);
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.category), 0xFF888888,
                    materialEntry.category().label.string(), materialEntry.category().color);
            b.divider();
            switch (materialEntry.category()) {
                case EXTERNAL_INPUT -> {
                    b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.required), 0xFF888888,
                            fmtNum(materialEntry.needed()), red);
                    b.bar(1.0f, red);
                }
                case INTERMEDIATE -> {
                    b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.consumed), 0xFF888888,
                            fmtNum(materialEntry.needed()), 0xFF88CC88);
                    b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.produced), 0xFF888888,
                            fmtNum(materialEntry.produced()), 0xFFCC8888);
                    if (materialEntry.missing() > 0) {
                        b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.missing), 0xFFCC4444,
                                fmtNum(materialEntry.missing()), 0xFFFF6666);
                    }
                    float ratio = materialEntry.needed() > 0 ? (float) materialEntry.produced() / materialEntry.needed() : 1.0f;
                    b.bar(Math.min(1.0f, ratio), ratio >= 1.0f ? green : red);
                }
                case BYPRODUCT -> {
                    b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.produced), 0xFF888888,
                            fmtNum(materialEntry.produced()), orange);
                    b.bar((float) materialEntry.chance(), orange);
                }
                case CATALYST -> {
                    b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.usage), 0xFF888888,
                            fmtNum(materialEntry.needed()), purple);
                    b.bar(1.0f, purple);
                }
            }
            tip.add(b.build());
        } else {
            String previewPathId = hoveredNodeId != null ? hoveredNodeId : externalHoveredNodeId;
            if (previewPathId != null) {
                Tooltip nodeTip = buildNodeTooltipForPath(previewPathId, null);
                if (nodeTip != null) {
                    return nodeTip;
                }
            }
        }
        return tip.notEmpty() ? tip : null;
    }

    private Tooltip buildCollapseButtonTooltip(LayoutNode layoutNode) {
        Tooltip tip = Tooltip.create();
        tip.add(ExecutionOverlayUi.tooltipAccentText(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.shortcutHeader)));
        tip.add(clickUsage(layoutNode.collapsed
                ? CirrusLangs.Calc.Tree.expand.component()
                : CirrusLangs.Calc.Tree.collapse.component()));
        tip.add(ExecutionOverlayUi.tooltipBody(
                CirrusLangs.Calc.Tree.childCount,
                fmtNum(layoutNode.originalChildCount)
        ));
        return tip;
    }

    private static MutableComponent clickUsage(Component usage) {
        return Component.empty()
                .append(Component.literal("[")
                        .withColor(ExecutionOverlayUi.tooltipTextSecondary))
                .append(InputConstants.Type.MOUSE.getOrCreate(0).getDisplayName().copy()
                        .withColor(ExecutionOverlayUi.tooltipAccent))
                .append(Component.literal("] ")
                        .withColor(ExecutionOverlayUi.tooltipTextSecondary))
                .append(usage.copy().withColor(ExecutionOverlayUi.tooltipTextSecondary));
    }

    private void appendNodeTooltip(Tooltip tip, LayoutNode layoutNode) {
        ProcessNode node = layoutNode.processNode;
        MaterialSlot primary = primaryOutput(node);
        if (primary != null) {
            TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(primary.content());
            if (stack != null) {
                tip.setStack(stack);
            }
        }
        TooltipComponent preview = ExecutionRecipeTooltipPreview.create(
            node.recipe(),
            node.inputs(),
            node.outputs()
        );
        if (preview != null) {
            tip.add(preview);
        }

        RichTooltipComponent.Builder b = RichTooltipComponent.builder();
        b.section(primaryName(node), nodeAccentColor(node));
        MachineInfo machine = node.inputs().isEmpty() ? null : getDisplayMachine(layoutNode);
        if (machine != null) {
            String machineName = machine.name();
            int machineCount = renderModel(layoutNode).machines().size();
            if (machineCount > 1) {
                machineName += " +" + (machineCount - 1);
            }
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Matrix.machine), 0xFF888888,
                    machineName, nodeAccentColor(node));
        } else if (!node.inputs().isEmpty()) {
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Matrix.machine), 0xFF888888, node.machine(), nodeAccentColor(node));
        }
        if (node.outputs().size() > 1) {
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Matrix.output), 0xFF888888, String.valueOf(node.outputs().size()), green);
        }
        if (node.inputs().size() > 1) {
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Matrix.input), 0xFF888888, String.valueOf(node.inputs().size()), 0xFF88CC88);
        }
        if (node.durationTicks() > 0) {
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Matrix.duration), 0xFF888888, node.durationFormatted(), cardMetaText);
        }
        appendContentCandidateRows(b, primary);
        tip.add(b.build());
    }

    private void appendContentCandidateRows(RichTooltipComponent.Builder b, @Nullable MaterialSlot slot) {
        if (slot == null || slot.content().isEmpty()) {
            return;
        }
        SlotVisual visual = slotVisual(slot);
        int count = visual == null ? 0 : visual.count();
        if (count <= 1) {
            return;
        }
        b.divider(0x664A535C);
        b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.candidates), textSecondary,
                nodeCandidateCountText(count), nodeAccentColorForSlot(slot));
    }

    private static String nodeCandidateCountText(int count) {
        return count <= 0 ? "" : String.valueOf(count);
    }

    private static int nodeAccentColorForSlot(MaterialSlot slot) {
        return slot == null ? cardMetaText : slot.category().color();
    }

    private Tooltip buildMachineTooltip(LayoutNode layoutNode) {
        ProcessNode node = layoutNode.processNode;
        Tooltip tip = Tooltip.create();
        RichTooltipComponent.Builder b = RichTooltipComponent.builder();
        b.section(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.machineCandidates), nodeAccentColor(node));
        List<MachineInfo> machines = renderModel(layoutNode).machines();
        String selected = selectedMachineName(layoutNode);
        if (machines.isEmpty()) {
            b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Matrix.machine), 0xFF888888, node.machine(), nodeAccentColor(node));
        } else {
            int visible = Math.min(8, machines.size());
            for (int i = 0; i < visible; i++) {
                MachineInfo machine = machines.get(i);
                b.kv(
                        Objects.equals(machine.name(), selected)
                                ? ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.current)
                                : ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.candidate),
                        0xFF888888,
                        machine.name(),
                        nodeAccentColor(node)
                );
            }
            if (machines.size() > visible) {
                b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.more), 0xFF888888,
                        String.valueOf(machines.size() - visible), cardMetaText);
            }
        }
        tip.add(b.build());
        return tip;
    }

    private @Nullable Tooltip buildMachineCandidatePanelTooltip(int mouseX, int mouseY) {
        if (isInMachineCandidatePanelClose(mouseX, mouseY)) {
            Tooltip tip = Tooltip.create();
            RichTooltipComponent.Builder b = RichTooltipComponent.builder();
            b.textureIconText(Icon.Small.minus, cardMetaText,
                    ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.close), textPrimary);
            tip.add(b.build());
            return tip;
        }
        int index = machineCandidateIndexAt(mouseX, mouseY);
        LayoutNode layoutNode = machineCandidatePanelNodeId == null ? null : findLayoutNode(machineCandidatePanelNodeId);
        if (layoutNode == null || layoutNode.processNode == null || index < 0) {
            return null;
        }
        List<MachineInfo> machines = renderModel(layoutNode).machines();
        if (index >= machines.size()) {
            return null;
        }
        MachineInfo machine = machines.get(index);
        Tooltip tip = Tooltip.create();
        RichTooltipComponent.Builder b = RichTooltipComponent.builder();
        ContentStack<?, ?> icon = machine.icon();
        if (icon.notEmpty() && icon.isItem()) {
            ContentStack<Item, ItemStack> item = icon.cast();
            tip.setStack(TooltipStack.of(item.value().copy()));
        }
        b.section(machine.name(), nodeAccentColor(layoutNode.processNode));
        b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.clickSet), 0xFF888888,
                machine.name(), textSecondary);
        tip.add(b.build());
        return tip;
    }

    private @Nullable Tooltip buildContentCandidatePanelTooltip(int mouseX, int mouseY) {
        if (isInContentCandidatePanelClose(mouseX, mouseY)) {
            Tooltip tip = Tooltip.create();
            RichTooltipComponent.Builder b = RichTooltipComponent.builder();
            b.textureIconText(Icon.Small.minus, cardMetaText,
                    ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.close), textPrimary);
            tip.add(b.build());
            return tip;
        }
        MaterialSlot slot = contentCandidatePanelSlot();
        if (slot == null || slot.content().isEmpty()) {
            return null;
        }
        int index = contentCandidateIndexAt(mouseX, mouseY);
        if (index >= 0) {
            Tooltip candidateTip = ExecutionOverlayContentHelper.tooltip(slot.content(), index);
            if (candidateTip != null) {
                return candidateTip;
            }
        }
        Tooltip tip = Tooltip.create();
        RichTooltipComponent.Builder b = RichTooltipComponent.builder();
        SlotVisual visual = slotVisual(slot);
        b.section(slot.materialName(), slot.category().color());
        b.kv(ExecutionOverlayUi.tr(CirrusLangs.Calc.Tree.candidates), textSecondary,
                String.valueOf(visual == null ? 0 : visual.count()), slot.category().color());
        tip.add(b.build());
        return tip;
    }

    private static int nodeAccentColor(ProcessNode node) {
        if (node == null || node.outputs().isEmpty()) {
            return cardMetaText;
        }
        return node.outputs().getFirst().category().color();
    }

    private @Nullable LayoutNode findLayoutNode(String pathId) {
        return layoutNodeByPath.get(pathId);
    }

    // ═══════════════════ Utilities ═══════════════════

    private static void drawPanelFrame(SceneCanvas canvas, int x, int y, int w, int h, int fill, int border) {
        canvas.fill(x, y, w, h, fill);
        drawOutline(canvas, x, y, w, h, border);
    }

    private static void drawOutline(SceneCanvas canvas, int x, int y, int w, int h, int color) {
        canvas.fill(x, y, w, 1, color);
        canvas.fill(x, y + h - 1, w, 1, color);
        canvas.fill(x, y, 1, h, color);
        canvas.fill(x + w - 1, y, 1, h, color);
    }

    private static void drawGridGlyph(SceneCanvas canvas, int x, int y, int size, int color) {
        int cell = Math.max(2, (size - 1) / 2);
        int gap = 1;
        canvas.fill(x, y, cell, cell, color);
        canvas.fill(x + cell + gap, y, cell, cell, color);
        canvas.fill(x, y + cell + gap, cell, cell, color);
        canvas.fill(x + cell + gap, y + cell + gap, cell, cell, color);
    }

    private static void drawHomeGlyph(SceneCanvas canvas, int x, int y, int size, int color) {
        int roofY = y;
        int bodyY = y + size / 2;
        int right = x + size;
        int mid = x + size / 2;
        canvas.line(x, bodyY, mid, roofY, 1.25f, color);
        canvas.line(mid, roofY, right, bodyY, 1.25f, color);
        canvas.fill(x + 1, bodyY, size - 2, size / 2 + 1, color);
        canvas.fill(mid - 1, y + size - 3, 2, 3, 0xFF555555);
    }

    private static String fmtNum(long v) {
        if (v < 0) v = -v;
        if (v < 1_000) return String.valueOf(v);
        if (v < 1_000_000) return String.format("%.1fk", v / 1_000.0);
        if (v < 1_000_000_000) return String.format("%.1fM", v / 1_000_000.0);
        return String.format("%.1fG", v / 1_000_000_000.0);
    }

    private static String truncate(String text, int maxPxW, Font font) {
        if (maxPxW <= 10) return "";
        if (font.width(text) <= maxPxW) return text;
        String ell = "..";
        int ellW = font.width(ell);
        int available = maxPxW - ellW;
        if (available <= 0) {
            return ell;
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (font.width(text.substring(0, mid)) <= available) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return (low <= 0 ? "" : text.substring(0, low)) + ell;
    }

    private String cachedTruncate(String text, int maxPxW, Font font) {
        long key = ((long) text.hashCode() << 32) | (maxPxW & 0xFFFFFFFFL);
        return truncationCache.computeIfAbsent(key, k -> truncate(text, maxPxW, font));
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static String formatDuration(long ticks) {
        double seconds = ticks / 20.0;
        if (seconds < 60) return String.format("%.1fs (%dt)", seconds, ticks);
        double minutes = seconds / 60;
        if (minutes < 60) return String.format("%.1fm (%dt)", minutes, ticks);
        return String.format("%.1fh (%dt)", minutes / 60, ticks);
    }
    //endregion
}
