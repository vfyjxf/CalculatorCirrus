package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.process.MaterialCategories;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.process.RecipeGraphBuilder;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeCanvasWidgetContractTest {

    @BeforeAll
    static void bootstrapRenderers() {
        ContentRegistryBuilder contentRegistry = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(contentRegistry);
        internal.registerContentVariant(contentRegistry);
        internal.registerContentResolver(contentRegistry);
        ContentApiImpl.attach(new ContentApiImpl(contentRegistry));
        RecipeContentRendererTestBootstrap.ensureInitialized();
    }

    @Test
    void togglingMaterialPanelDoesNotResetViewport() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setViewport(120, 240, 0.75f);

        widget.toggleMaterialPanel();

        assertEquals(120, widget.getPanX());
        assertEquals(240, widget.getPanY());
        assertEquals(0.75f, widget.getZoom());
    }

    @Test
    void materialPanelUsesCirrusPixelPalette() {
        TreeCanvasWidget.MaterialPanelPalette palette = TreeCanvasWidget.debugMaterialPanelPalette();

        assertEquals(0xFFCFCFCF, palette.baseFill());
        assertEquals(0xFF555555, palette.shadow());
        assertEquals(0xFFFFFFFF, palette.highlight());
        assertEquals(0xFFE6E6E6, palette.frame());
    }

    @Test
    void minimapChromeRemovesTitleAndTrackedGlow() {
        assertEquals(false, TreeCanvasWidget.debugMinimapShowsTitle());
        assertEquals(false, TreeCanvasWidget.debugTrackedLinksUseGlow());
        assertEquals(128, TreeCanvasWidget.debugMinimapWidth());
        assertEquals(78, TreeCanvasWidget.debugMinimapHeight());
    }

    @Test
    void stepCardsUseCompactChromeAndForkActionButton() {
        TreeCanvasWidget.StepCardChrome chrome = TreeCanvasWidget.debugStepCardChrome();

        assertFalse(chrome.usesDrawnSeparator());
        assertFalse(TreeCanvasWidget.debugStepCardsRenderTierByDefault());
        assertEquals(true, chrome.focusButtonUsesTreeGlyph());
        assertEquals(6, chrome.sectionGap());
        assertEquals(true, chrome.hasIntrinsicZone());
        assertEquals(true, chrome.hasExtensionZone());
        assertEquals(true, chrome.rendersRecipeFlow());
        assertEquals(true, chrome.leafShowsCandidates());
        assertEquals(false, chrome.leafRendersMachineSlot());
        assertEquals(false, chrome.rendersMachinePlaceholder());
        assertTrue(chrome.usesSingleCardBackground());
        assertFalse(chrome.usesDividerLines());
        assertTrue(chrome.machineOptionIndicatorAvoidsText());
        assertTrue(chrome.extensionUsesIconMetrics());
        assertTrue(chrome.hasProjectionAction());
        assertFalse(chrome.usesLeftAccentStrip());
        assertTrue(chrome.collapseChildCountRenderedInTooltip());
        assertEquals(188, TreeCanvasWidget.debugNodeWidth());
        assertEquals(82, TreeCanvasWidget.debugNodeHeight());
        assertTrue(TreeCanvasWidget.debugRecipePreviewUsesUnifiedSlotGroups());
        assertTrue(TreeCanvasWidget.debugSingleMaterialLeafUsesCompactLayout());
        assertTrue(TreeCanvasWidget.debugRecipePreviewSlotSize(9, 1) > 0);
        assertTrue(TreeCanvasWidget.debugRecipePreviewSlotSize(1, 3) > 0);
    }

    @Test
    void recipeNodeCardLayoutHasStableExtensibleZones() {
        TreeCanvasWidget.NodeCardLayout layout = TreeCanvasWidget.debugRecipeNodeCardLayout();

        assertTrue(layout.intrinsic().y() < layout.recipe().y());
        assertTrue(layout.recipe().y() < layout.extension().y());
        assertTrue(layout.machine().w() > 0);
        assertTrue(layout.machine().w() > layout.inputs().w());
        assertTrue(layout.outputs().x() > layout.arrow().x());
        assertTrue(layout.actions().x() > layout.outputs().x());
        assertTrue(layout.extension().y() > layout.outputs().y() + layout.outputs().h());
        assertTrue(layout.candidates().w() > layout.primaryOutput().w());
        assertTrue(layout.intrinsicDividerY() > layout.intrinsic().y());
        assertTrue(layout.dividerY() > layout.outputs().y() + layout.outputs().h());
        assertEquals(3, layout.actionGap());
    }

    @Test
    void tagLeafLayoutUsesTopPreviewRightTagBottomCandidatesAndUpperRightAction() {
        TreeCanvasWidget.TagLeafCardLayout layout = TreeCanvasWidget.debugTagLeafCardLayout();

        assertTrue(layout.header().y() < layout.candidates().y());
        assertTrue(layout.header().w() > layout.action().w());
        assertTrue(layout.candidates().y() > layout.header().y() + layout.header().h());
        assertTrue(layout.candidates().w() >= layout.header().w() - 4);
        assertTrue(layout.action().x() > layout.header().x());
        assertTrue(layout.action().y() >= layout.header().y());
        assertTrue(layout.action().y() < layout.candidates().y());
        assertEquals(16, TreeCanvasWidget.debugTagCandidateCellSize());
        assertTrue(layout.header().h() >= 18);
        assertTrue(layout.candidates().h() > 0);
    }

    @Test
    void minimapViewportIndicatorAlwaysClampedInsideContentArea() {
        TreeCanvasWidget.MinimapViewportRect rect = TreeCanvasWidget.debugClampMinimapViewportRect(
                10, 20, 48, 32,
                8, 16, 64, 42
        );

        assertEquals(true, rect.x() >= 10);
        assertEquals(true, rect.y() >= 20);
        assertEquals(true, rect.x() + rect.w() <= 58);
        assertEquals(true, rect.y() + rect.h() <= 52);
    }

    @Test
    void materialPanelSearchIsPersistentAndContentStartsBelowIt() {
        TreeCanvasWidget widget = new TreeCanvasWidget();

        assertEquals(true, TreeCanvasWidget.debugMaterialPanelSearchAlwaysVisible());
        assertEquals(45, widget.debugMaterialPanelContentTop());
    }

    @Test
    void preferredRootNodeIsUsedInsteadOfFirstSink() {
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "side/sink",
                "Side Sink",
                "Inventory",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("side", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "source/raw",
                "Raw",
                "Inventory",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("raw", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "target/craft",
                "Target Craft",
                "Crafting Table",
                List.of(new ProcessNode.MaterialSlot("raw", 1, MaterialCategories.ITEM)),
                List.of(new ProcessNode.MaterialSlot("target", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "source/raw",
                "target/craft",
                "raw",
                1,
                MaterialCategories.ITEM
        ));
        graph.preferredRootNodeId("target/craft");

        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(graph);
        TreeCanvasWidget.LayoutDebugInfo layout = widget.debugLayoutInfo();

        assertEquals("target/craft", layout.targetNodeId());
        assertEquals(2, layout.layoutNodes());
        assertEquals(1, layout.rootChildCount());
    }

    @Test
    void largeRootStartsCompactBeforeExplicitExpansion() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "target/large",
                "Large Target",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("target", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        for (int i = 0; i < 48; i++) {
            String sourceId = "source/" + i;
            graph.addNode(new ProcessNode(
                    sourceId,
                    "Source " + i,
                    "Inventory",
                    List.of(),
                    List.of(new ProcessNode.MaterialSlot("raw_" + i, 1, MaterialCategories.ITEM)),
                    0,
                    List.of(),
                    null
            ));
            graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                    sourceId,
                    "target/large",
                    "raw_" + i,
                    1,
                    MaterialCategories.ITEM
            ));
        }
        graph.preferredRootNodeId("target/large");

        widget.setGraph(graph);
        TreeCanvasWidget.LayoutDebugInfo layout = widget.debugLayoutInfo();

        assertFalse(widget.isForceExpandAll());
        assertTrue(layout.layoutNodes() <= 2);
        assertTrue(layout.rootCollapsed() || layout.rootChildCount() > 0);
    }

    @Test
    void expandAllDisablesAutoCollapseForDeepComplexTree() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "target/root",
                "Root",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("root", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        String parent = "target/root";
        for (int i = 0; i < 14; i++) {
            String child = "step/" + i;
            graph.addNode(new ProcessNode(
                    child,
                    "Step " + i,
                    "Crafting Table",
                    List.of(),
                    List.of(new ProcessNode.MaterialSlot("mat_" + i, 1, MaterialCategories.ITEM)),
                    0,
                    List.of(),
                    null
            ));
            graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                    child,
                    parent,
                    "mat_" + i,
                    1,
                    MaterialCategories.ITEM
            ));
            parent = child;
        }
        graph.preferredRootNodeId("target/root");

        widget.setGraph(graph);
        assertTrue(widget.getCollapsedNodePaths().size() > 0);

        widget.expandAllNodes();

        assertTrue(widget.isForceExpandAll());
        assertEquals(0, widget.getCollapsedNodePaths().size());
        assertEquals(15, widget.debugLayoutNodeCount());
    }

    @Test
    void expandAllDoesNotDuplicateSharedDependencySubtrees() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "target/root",
                "Root",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("root", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "branch/a",
                "Branch A",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("a", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "branch/b",
                "Branch B",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("b", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "shared/base",
                "Shared Base",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("shared", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        String parent = "shared/base";
        for (int i = 0; i < 8; i++) {
            String child = "shared/deep/" + i;
            graph.addNode(new ProcessNode(
                    child,
                    "Shared Deep " + i,
                    "Crafting Table",
                    List.of(),
                    List.of(new ProcessNode.MaterialSlot("deep_" + i, 1, MaterialCategories.ITEM)),
                    0,
                    List.of(),
                    null
            ));
            graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                    child,
                    parent,
                    "deep_" + i,
                    1,
                    MaterialCategories.ITEM
            ));
            parent = child;
        }
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "branch/a",
                "target/root",
                "a",
                1,
                MaterialCategories.ITEM
        ));
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "branch/b",
                "target/root",
                "b",
                1,
                MaterialCategories.ITEM
        ));
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "shared/base",
                "branch/a",
                "shared",
                1,
                MaterialCategories.ITEM
        ));
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "shared/base",
                "branch/b",
                "shared",
                1,
                MaterialCategories.ITEM
        ));
        graph.preferredRootNodeId("target/root");

        widget.setGraph(graph);
        widget.expandAllNodes();

        assertTrue(widget.isForceExpandAll());
        assertEquals(graph.nodeCount() + 1, widget.debugLayoutNodeCount());
        assertEquals(0, widget.getCollapsedNodePaths().size());
    }

    @Test
    void expandAllSharedDagScalesWithDependencyEdgesNotPathTree() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(processNode("target/root", "root"));

        int layers = 7;
        int width = 4;
        for (int layer = 0; layer < layers; layer++) {
            for (int index = 0; index < width; index++) {
                graph.addNode(processNode("layer/" + layer + "/" + index, "mat_" + layer + "_" + index));
            }
        }
        for (int index = 0; index < width; index++) {
            graph.addEdge(edge("layer/0/" + index, "target/root", "mat_0_" + index));
        }
        for (int layer = 1; layer < layers; layer++) {
            for (int parent = 0; parent < width; parent++) {
                for (int child = 0; child < width; child++) {
                    graph.addEdge(edge(
                            "layer/" + layer + "/" + child,
                            "layer/" + (layer - 1) + "/" + parent,
                            "mat_" + layer + "_" + child
                    ));
                }
            }
        }
        graph.preferredRootNodeId("target/root");

        long started = System.nanoTime();
        widget.setGraph(graph);
        widget.expandAllNodes();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        int naivePathTreeLowerBound = 1;
        int frontier = width;
        for (int layer = 0; layer < layers; layer++) {
            naivePathTreeLowerBound += frontier;
            frontier *= width;
        }

        assertTrue(widget.isForceExpandAll());
        assertEquals(graph.edgeCount() + 1, widget.debugLayoutNodeCount(),
                "expanded shared DAG should materialize one layout node per dependency edge plus the root");
        assertTrue(widget.debugLayoutNodeCount() < naivePathTreeLowerBound / 10,
                "expanded DAG layoutNodes=" + widget.debugLayoutNodeCount()
                        + " naivePathTreeLowerBound=" + naivePathTreeLowerBound
                        + " elapsedMs=" + elapsedMs);
        assertEquals(0, widget.debugRenderModelCacheSize());
        assertEquals(0, widget.getCollapsedNodePaths().size());
    }

    @Test
    void cyclicProcessGraphStopsAtCycleTerminus() {
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "target/a",
                "Target A",
                "Crafting Table",
                List.of(new ProcessNode.MaterialSlot("b", 1, MaterialCategories.ITEM)),
                List.of(new ProcessNode.MaterialSlot("a", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "step/b",
                "Step B",
                "Crafting Table",
                List.of(new ProcessNode.MaterialSlot("a", 1, MaterialCategories.ITEM)),
                List.of(new ProcessNode.MaterialSlot("b", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "step/b",
                "target/a",
                "b",
                1,
                MaterialCategories.ITEM
        ));
        graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                "target/a",
                "step/b",
                "a",
                1,
                MaterialCategories.ITEM
        ));
        graph.preferredRootNodeId("target/a");

        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(graph);
        widget.expandAllNodes();

        assertTrue(widget.debugLayoutNodeCount() <= 3);
        assertEquals(0, widget.getCollapsedNodePaths().size());
    }

    @Test
    void materialPanelScrollbarOnlyAppearsWhenFilteredContentOverflows() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(materialSearchGraph());
        widget.toggleMaterialPanel();

        assertFalse(widget.debugMaterialPanelNeedsScrollbar(10_000));
        assertTrue(widget.debugMaterialPanelNeedsScrollbar(120));

        widget.setMatFilter("missing>10");

        assertFalse(widget.debugMaterialPanelNeedsScrollbar(10_000));
    }

    @Test
    void materialPanelSearchFiltersAliasesAndNumericPredicates() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(materialSearchGraph());
        widget.toggleMaterialPanel();

        assertEquals(4, widget.debugMaterialEntryCount());

        widget.setMatFilter("need>=128");
        assertEquals(1, widget.debugFilteredMaterialEntryCount());

        widget.setMatFilter("missing>10");
        assertEquals(1, widget.debugFilteredMaterialEntryCount());

        widget.setMatFilter("catalyst");
        assertEquals(1, widget.debugFilteredMaterialEntryCount());

        widget.setMatFilter("out");
        assertEquals(3, widget.debugFilteredMaterialEntryCount());
    }

    @Test
    void materialPanelHotPathUsesCachedFilterResults() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(largeMaterialSearchGraph(180));
        widget.toggleMaterialPanel();

        int entryCount = widget.debugMaterialEntryCount();
        assertTrue(entryCount > 100);

        widget.setMatFilter("need>=64");
        int firstEvaluationCount = widget.debugMaterialFilterEvaluationCount();
        assertEquals(entryCount, firstEvaluationCount);
        int firstFiltered = widget.debugFilteredMaterialEntryCount();

        for (int i = 0; i < 50; i++) {
            assertEquals(firstFiltered, widget.debugFilteredMaterialEntryCount());
            assertTrue(widget.debugMaterialFilteredCategoryCount() > 0);
        }

        assertEquals(firstEvaluationCount, widget.debugMaterialFilterEvaluationCount());
    }

    @Test
    void releaseGraphDropsProcessGraphAndLayoutReferences() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        WeakReference<ProcessGraph> graphRef = attachLargeGraph(widget);

        assertTrue(widget.debugRetainsGraphForTests());
        assertTrue(widget.debugLayoutNodeCount() > 0);
        assertTrue(widget.debugMaterialEntryCount() > 0);

        widget.releaseGraph();

        assertFalse(widget.debugRetainsGraphForTests());
        assertEquals(0, widget.debugLayoutNodeCount());
        assertEquals(0, widget.debugMaterialEntryCount());
        assertEquals(0, widget.debugRenderModelCacheSize());
        assertEquals(0, widget.debugMinimapScratchLengthForTests());
        assertCollected(graphRef);
    }

    @Test
    void treeRenderingLimitsNodesAndConnectionsToViewportWindow() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "target/root",
                "Root",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("root", 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        ));
        for (int i = 0; i < 24; i++) {
            String sourceId = "source/" + i;
            graph.addNode(new ProcessNode(
                    sourceId,
                    "Source " + i,
                    "Inventory",
                    List.of(),
                    List.of(new ProcessNode.MaterialSlot("raw_" + i, 1, MaterialCategories.ITEM)),
                    0,
                    List.of(),
                    null
            ));
            graph.addEdge(new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                    sourceId,
                    "target/root",
                    "raw_" + i,
                    1,
                    MaterialCategories.ITEM
            ));
        }
        graph.preferredRootNodeId("target/root");

        widget.setGraph(graph);
        widget.expandAllNodes();
        widget.setViewport(0, 0, 1.0f);

        assertTrue(widget.debugLayoutInfo().layoutNodes() > 8);
        assertTrue(widget.debugVisibleNodeCount(220, 180) < widget.debugLayoutInfo().layoutNodes());
        assertTrue(widget.debugVisibleConnectionCount(220, 180) < widget.debugLayoutInfo().layoutLinks());
    }

    @Test
    void machineSlotOpensCustomCandidatePanel() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "craft/smoker",
                "Smoker",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("Smoker", 16, MaterialCategories.ITEM)),
                0,
                List.of(
                        new ProcessNode.MachineInfo("Crafting Table"),
                        new ProcessNode.MachineInfo("Inventory Crafting")
                ),
                null
        ));
        widget.setGraph(graph);

        assertTrue(TreeCanvasWidget.debugMachineCandidatePanelUsesCustomRendering());
        assertEquals(3, TreeCanvasWidget.debugMachineCandidatePanelMaxRows());
        assertEquals(0.2f, TreeCanvasWidget.debugMachineCandidatePanelScale(0.2f), 0.0001f);
        assertEquals(1.25f, TreeCanvasWidget.debugMachineCandidatePanelScale(1.25f), 0.0001f);
        assertEquals(2.0f, TreeCanvasWidget.debugMachineCandidatePanelScale(2.0f), 0.0001f);
        assertTrue(widget.debugOpenMachineCandidatePanelForPath("root"));
        assertTrue(widget.debugMachineCandidatePanelVisible());
        assertTrue(widget.debugSelectMachineCandidateForPath("root", 1));
        assertEquals("Inventory Crafting", widget.debugSelectedMachineName("root"));
    }

    @Test
    void duplicateMachineCandidatesAreCollapsedBeforeRendering() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "craft/smoker",
                "Smoker",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("Smoker", 16, MaterialCategories.ITEM)),
                0,
                List.of(
                        new ProcessNode.MachineInfo("Crafting Table", Items.CRAFTING_TABLE.getDefaultInstance()),
                        new ProcessNode.MachineInfo("Crafting Table", Items.CRAFTING_TABLE.getDefaultInstance()),
                        new ProcessNode.MachineInfo("Inventory Crafting")
                ),
                null
        ));
        widget.setGraph(graph);

        assertEquals(2, widget.debugMachineCandidateCount("root"));
        assertEquals(List.of("Crafting Table", "Inventory Crafting"), widget.debugMachineCandidateNames("root"));
        assertTrue(widget.debugOpenMachineCandidatePanelForPath("root"));
        assertTrue(widget.debugSelectMachineCandidateForPath("root", 1));
        assertEquals("Inventory Crafting", widget.debugSelectedMachineName("root"));
    }

    @Test
    void machineCandidateSelectionOverlayRendersAfterDeferredMachineItems() {
        assertTrue(TreeCanvasWidget.debugMachineCandidateSelectionOverlayRendersAfterIcons());
        assertTrue(TreeCanvasWidget.debugMachineTooltipsUseTextOnlyMachineRows());
    }

    @Test
    void materialCandidatesUseCompactPopupInsteadOfScrollableTooltip() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance()
                ),
                2
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "material/planks",
                "#minecraft:planks",
                "",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:planks",
                        2,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                0,
                List.of(),
                null
        ));
        widget.setGraph(graph);

        assertEquals(21, TreeCanvasWidget.debugContentCandidatePanelMaxCells());
        assertTrue(widget.debugOpenContentCandidatePanelForPath("root"));
        assertTrue(widget.debugContentCandidatePanelVisible());
        assertTrue(widget.debugSelectContentCandidateForPath("root", 2));
        assertEquals(2, widget.debugSelectedContentCandidateIndex("root"));
    }

    @Test
    void contentCandidateSelectionEmitsVariantPreference() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance()
                ),
                2
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "material/planks",
                "#minecraft:planks",
                "",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:planks",
                        2,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                0,
                List.of(),
                null
        ));
        AtomicReference<TreeCanvasWidget.ContentCandidateSelection> selected = new AtomicReference<>();
        widget.setOnContentCandidateSelected(selected::set);
        widget.setGraph(graph);

        assertTrue(widget.debugSelectContentCandidateForPath("root", 1));

        assertEquals("root", selected.get().pathId());
        assertEquals(TreeCanvasWidget.selectionInputKey(alternatives), selected.get().inputKey());
        assertEquals(TreeCanvasWidget.selectionVariantKey(alternatives, 1), selected.get().variantKey());
        assertEquals(1, selected.get().visualIndex());
    }

    @Test
    void contentCandidateSelectionDoesNotRerouteWhenChoosingCurrentCandidate() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance()
                ),
                2
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "material/planks",
                "#minecraft:planks",
                "",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:planks",
                        2,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                0,
                List.of(),
                null
        ));
        AtomicInteger selected = new AtomicInteger();
        widget.setOnContentCandidateSelected(ignored -> selected.incrementAndGet());
        widget.setGraph(graph);

        assertTrue(widget.debugSelectContentCandidateForPath("root", 0));
        assertEquals(0, selected.get());

        assertTrue(widget.debugSelectContentCandidateForPath("root", 2));
        assertEquals(1, selected.get());

        assertTrue(widget.debugSelectContentCandidateForPath("root", 2));
        assertEquals(1, selected.get());
    }

    @Test
    void materialCandidatePanelFallsBackToInputCandidateSlot() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance()
                ),
                2
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "material/planks",
                "#minecraft:planks",
                "",
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:planks",
                        2,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                List.of(ProcessNode.MaterialSlot.item(Items.STICK.getDefaultInstance(), 1)),
                0,
                List.of(),
                null
        ));
        widget.setGraph(graph);

        assertEquals(3, widget.debugContentCandidateVisualCount("root"));
        assertTrue(widget.debugOpenContentCandidatePanelForPath("root"));
        assertTrue(widget.debugContentCandidatePanelVisible());
    }

    @Test
    void materialCandidatesUseContentFilledGridWhenManyAlternativesExist() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance(),
                        Items.JUNGLE_PLANKS.getDefaultInstance(),
                        Items.ACACIA_PLANKS.getDefaultInstance(),
                        Items.DARK_OAK_PLANKS.getDefaultInstance(),
                        Items.MANGROVE_PLANKS.getDefaultInstance(),
                        Items.CHERRY_PLANKS.getDefaultInstance(),
                        Items.CRIMSON_PLANKS.getDefaultInstance(),
                        Items.WARPED_PLANKS.getDefaultInstance(),
                        Items.OAK_LOG.getDefaultInstance(),
                        Items.BIRCH_LOG.getDefaultInstance(),
                        Items.SPRUCE_LOG.getDefaultInstance(),
                        Items.JUNGLE_LOG.getDefaultInstance(),
                        Items.ACACIA_LOG.getDefaultInstance(),
                        Items.DARK_OAK_LOG.getDefaultInstance(),
                        Items.MANGROVE_LOG.getDefaultInstance(),
                        Items.CHERRY_LOG.getDefaultInstance(),
                        Items.CRIMSON_STEM.getDefaultInstance(),
                        Items.WARPED_STEM.getDefaultInstance(),
                        Items.BAMBOO_BLOCK.getDefaultInstance(),
                        Items.STRIPPED_OAK_LOG.getDefaultInstance()
                ),
                1
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "material/wood",
                "#minecraft:logs",
                "",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:logs",
                        1,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                0,
                List.of(),
                null
        ));
        widget.setGraph(graph);

        widget.debugSetSize(220, 180);
        assertTrue(widget.debugOpenContentCandidatePanelForPath("root"));
        assertFalse(widget.debugContentCandidateNeedsScrollbar("root"));
    }

    @Test
    void materialCandidatePopupScrollUsesTargetAndDisplayRows() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance(),
                        Items.JUNGLE_PLANKS.getDefaultInstance(),
                        Items.ACACIA_PLANKS.getDefaultInstance(),
                        Items.DARK_OAK_PLANKS.getDefaultInstance(),
                        Items.MANGROVE_PLANKS.getDefaultInstance(),
                        Items.CHERRY_PLANKS.getDefaultInstance(),
                        Items.CRIMSON_PLANKS.getDefaultInstance(),
                        Items.WARPED_PLANKS.getDefaultInstance(),
                        Items.OAK_LOG.getDefaultInstance(),
                        Items.BIRCH_LOG.getDefaultInstance(),
                        Items.SPRUCE_LOG.getDefaultInstance(),
                        Items.JUNGLE_LOG.getDefaultInstance(),
                        Items.ACACIA_LOG.getDefaultInstance(),
                        Items.DARK_OAK_LOG.getDefaultInstance(),
                        Items.MANGROVE_LOG.getDefaultInstance(),
                        Items.CHERRY_LOG.getDefaultInstance(),
                        Items.CRIMSON_STEM.getDefaultInstance(),
                        Items.WARPED_STEM.getDefaultInstance(),
                        Items.BAMBOO_BLOCK.getDefaultInstance(),
                        Items.STRIPPED_OAK_LOG.getDefaultInstance()
                ),
                1
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "material/wood",
                "#minecraft:logs",
                "",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:logs",
                        1,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                0,
                List.of(),
                null
        ));
        widget.setGraph(graph);

        widget.debugSetSize(220, 80);
        assertTrue(widget.debugOpenContentCandidatePanelForPath("root"));
        assertTrue(widget.debugContentCandidateNeedsScrollbar("root"));
        assertTrue(widget.debugScrollContentCandidatePanel(-1));
        assertEquals(1, widget.debugContentCandidateScrollRow("root"));
        assertEquals(0.0f, widget.debugContentCandidateDisplayScrollRow("root"));

        widget.debugAnimateSmooth();

        assertTrue(widget.debugContentCandidateDisplayScrollRow("root") > 0.0f);
        assertTrue(widget.debugContentCandidateDisplayScrollRow("root") < 1.0f);
    }

    @Test
    void candidatePanelsStayInsideViewportAtLargeZoom() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        InputContent alternatives = InputContent.fromTypedStackList(
                CirrusTypes.Contents.ITEM_STACK,
                List.of(
                        Items.OAK_PLANKS.getDefaultInstance(),
                        Items.BIRCH_PLANKS.getDefaultInstance(),
                        Items.SPRUCE_PLANKS.getDefaultInstance(),
                        Items.JUNGLE_PLANKS.getDefaultInstance(),
                        Items.ACACIA_PLANKS.getDefaultInstance(),
                        Items.DARK_OAK_PLANKS.getDefaultInstance(),
                        Items.MANGROVE_PLANKS.getDefaultInstance(),
                        Items.CHERRY_PLANKS.getDefaultInstance(),
                        Items.CRIMSON_PLANKS.getDefaultInstance(),
                        Items.WARPED_PLANKS.getDefaultInstance(),
                        Items.OAK_LOG.getDefaultInstance(),
                        Items.BIRCH_LOG.getDefaultInstance(),
                        Items.SPRUCE_LOG.getDefaultInstance(),
                        Items.JUNGLE_LOG.getDefaultInstance(),
                        Items.ACACIA_LOG.getDefaultInstance(),
                        Items.DARK_OAK_LOG.getDefaultInstance(),
                        Items.MANGROVE_LOG.getDefaultInstance(),
                        Items.CHERRY_LOG.getDefaultInstance(),
                        Items.CRIMSON_STEM.getDefaultInstance(),
                        Items.WARPED_STEM.getDefaultInstance(),
                        Items.BAMBOO_BLOCK.getDefaultInstance(),
                        Items.STRIPPED_OAK_LOG.getDefaultInstance()
                ),
                1
        );
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "craft/large-panel",
                "Large Panel",
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(
                        "#minecraft:logs",
                        1,
                        MaterialCategories.ITEM,
                        1.0,
                        false,
                        alternatives
                )),
                0,
                List.of(
                        new ProcessNode.MachineInfo("Crafting Table", Items.CRAFTING_TABLE.getDefaultInstance()),
                        new ProcessNode.MachineInfo("Furnace", Items.FURNACE.getDefaultInstance()),
                        new ProcessNode.MachineInfo("Smoker", Items.SMOKER.getDefaultInstance())
                ),
                null
        ));
        widget.setGraph(graph);
        widget.debugSetSize(220, 180);
        widget.setViewport(0, 0, 3.0f);

        assertTrue(widget.debugOpenContentCandidatePanelForPath("root"));
        int[] contentBounds = widget.debugContentCandidatePanelBounds();
        assertTrue(widget.debugContentCandidatePanelScale() < 3.0f);
        assertTrue(contentBounds[0] >= 4);
        assertTrue(contentBounds[1] >= 4);
        assertTrue(contentBounds[0] + contentBounds[2] <= 216);
        assertTrue(contentBounds[1] + contentBounds[3] <= 176);
        assertFalse(widget.debugContentCandidateNeedsScrollbar("root"));

        assertTrue(widget.debugOpenMachineCandidatePanelForPath("root"));
        int[] machineBounds = widget.debugMachineCandidatePanelBounds();
        assertTrue(widget.debugMachineCandidatePanelScale() < 3.0f);
        assertTrue(machineBounds[0] >= 4);
        assertTrue(machineBounds[1] >= 4);
        assertTrue(machineBounds[0] + machineBounds[2] <= 216);
        assertTrue(machineBounds[1] + machineBounds[3] <= 176);
    }

    private static ProcessGraph materialSearchGraph() {
        ProcessNode source = new ProcessNode(
                "source/cobblestone",
                "Cobblestone Source",
                "Inventory",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("Cobblestone", 64, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        );
        ProcessNode craft = new ProcessNode(
                "craft/furnace",
                "Furnace",
                "Crafting Table",
                List.of(
                        new ProcessNode.MaterialSlot("Cobblestone", 128, MaterialCategories.ITEM),
                        new ProcessNode.MaterialSlot("Hammer", 1, MaterialCategories.CATALYST, true)
                ),
                List.of(
                        new ProcessNode.MaterialSlot("Furnace", 16, MaterialCategories.ITEM),
                        new ProcessNode.MaterialSlot(
                                "Gravel",
                                2,
                                MaterialCategories.ITEM,
                                0.5,
                                false,
                                RecipeContent.empty()
                        )
                ),
                0,
                List.of(new ProcessNode.MachineInfo("Crafting Table")),
                null
        );
        return RecipeGraphBuilder.build(List.of(source, craft));
    }

    private static ProcessNode processNode(String id, String outputName) {
        return new ProcessNode(
                id,
                outputName,
                "Crafting Table",
                List.of(),
                List.of(new ProcessNode.MaterialSlot(outputName, 1, MaterialCategories.ITEM)),
                0,
                List.of(),
                null
        );
    }

    private static dev.vfyjxf.cirrus.ui.process.ProcessEdge edge(String from, String to, String material) {
        return new dev.vfyjxf.cirrus.ui.process.ProcessEdge(
                from,
                to,
                material,
                1,
                MaterialCategories.ITEM
        );
    }

    private static ProcessGraph largeMaterialSearchGraph(int count) {
        java.util.ArrayList<ProcessNode.MaterialSlot> inputs = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            inputs.add(new ProcessNode.MaterialSlot("raw_" + i, 32 + i, MaterialCategories.ITEM));
        }
        ProcessNode node = new ProcessNode(
                "craft/large",
                "Large Craft",
                "Crafting Table",
                inputs,
                List.of(new ProcessNode.MaterialSlot("result", 1, MaterialCategories.ITEM)),
                20,
                List.of(new ProcessNode.MachineInfo("Crafting Table")),
                null
        );
        return RecipeGraphBuilder.build(List.of(node));
    }

    private static WeakReference<ProcessGraph> attachLargeGraph(TreeCanvasWidget widget) {
        ProcessGraph graph = largeMaterialSearchGraph(256);
        WeakReference<ProcessGraph> ref = new WeakReference<>(graph);
        widget.setGraph(graph);
        return ref;
    }

    private static void assertCollected(WeakReference<?> ref) {
        for (int i = 0; i < 20 && ref.get() != null; i++) {
            System.gc();
            System.runFinalization();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertEquals(null, ref.get());
    }
}
