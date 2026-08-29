package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.content.render.BatchableStackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRoutingRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.FileRuleRepository;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollection;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollectionKey;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleCollectionScope;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfilePreset;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleAction;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleChannel;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleCondition;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleStage;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfileSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleRepositoryView;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.SourcePolicyRuleSpec;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.TagExpr;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.UnresolvedInputMode;
import dev.vfyjxf.cloudlib.api.search.SearchExpression;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCheckboxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusRowWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusText;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusWrappedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.factory.Lists;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textMuted;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textSecondary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsFlexStart;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexBasisPercent;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexNoWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.maxWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

public final class RulesPage extends CirrusScrollablePageWidget {
    private static final int listMinWidth = 124;
    private static final int inspectorMinWidth = 132;
    private static final int detailKeyWidth = 42;
    private static final int detailRowHeight = 18;
    private static final int profileMetricHeight = 24;
    private static final int ruleHeaderHeight = 18;
    private static final int ruleSentenceHeight = 16;
    private static final int infoTextPadding = 1;
    private static final int infoTextLineGap = 1;
    private static final int infoTextLineHeight = 9;
    private static final int ruleListRowHeight = 36;
    private static final int formRowHeight = 18;
    private static final int actionButtonHeight = 18;
    private static final int simpleHintHeight = 20;
    private static final int simpleFooterGap = 2;
    private static final int shortButtonWidth = 34;
    private static final int iconButtonWidth = 18;
    private static final int materialIconSize = 18;
    private static final int clauseMaterialPreviewLimit = 6;
    private static final int clauseMaterialDetailColumns = 8;
    private static final int clauseMaterialGap = 1;
    private static final int materialPickerPanelMinWidth = 176;
    private static final int materialPickerPanelMaxWidth = 220;
    private static final int materialPickerPanelOuterPadding = 4;
    private static final int materialPickerControlsMinWidth = 160;
    private static final int materialPickerResultsMinWidth = 150;
    private static final int materialPickerControlsInnerPadding = 4;
    private static final int materialPickerControlsBodyGap = 3;
    private static final int materialPickerSectionPadding = 4;
    private static final int materialPickerSectionTitleHeight = 10;
    private static final int materialPickerSectionTitleGap = 3;
    private static final int materialPickerGridCellSize = 22;
    private static final int materialPickerGridGap = 1;
    private static final int materialPickerGridPadding = 2;
    private static final int materialPickerGridViewportInset = 3;
    private static final int materialPickerGridMinHeight = 128;
    private static final int materialPickerGridScrollbarWidth = 6;
    private static final int materialPickerGridScrollGap = 2;
    private static final int materialPickerGridScrollSpeed = 20;
    private static final float materialPickerGridScrollSmooth = 0.35f;
    private static final int materialPickerGridMinThumbHeight = 14;
    private static final String defaultScoreKey = "inventoryShortage";

    private enum SimpleRuleKind {
        reject,
        penalty,
        reward,
        stockSensitive
    }

    private RuleRepositoryView view;
    private final boolean persistenceEnabled;
    private final WidgetGroup<Widget> columns;
    private final WidgetGroup<Widget> list;
    private final WidgetGroup<Widget> inspector;
    private RuleDefinition selectedRule;
    private RuleCollectionKey selectedCollection;
    private boolean rebuildScheduled;
    private boolean inspectorRebuildScheduled;
    private boolean creatingRule;

    private String draftNewRuleName = "";
    private SimpleRuleKind draftSimpleKind = SimpleRuleKind.penalty;
    private String draftSimpleMaterial = "minecraft:stone";
    private String draftSimpleStrength = "1";
    private RuleChannel draftSimpleChannel = RuleChannel.any;
    private boolean materialPickerOpen;
    private int expandedClauseIndex = -1;
    private String materialPickerQuery = "";
    private List<RuleIngredientCatalog.Entry> materialPickerEntries = List.of();
    private final Set<String> pickedMaterials = new LinkedHashSet<>();
    private final List<Set<String>> materialPickerUndoStack = new ArrayList<>();
    private CirrusFittedTextWidget materialPickerSummaryWidget;
    private float materialPickerScrollY;
    private float materialPickerTargetScrollY;
    private int materialPickerLastSelectionAnchor = -1;
    private boolean advancedBuilderOpen;
    private String draftSourceTag = "cirrus:quality/junk";
    private String draftMaterial = "minecraft:stone";
    private String draftScoreKey = defaultScoreKey;
    private String draftScoreAmount = "1";
    private SourcePolicyAction draftSourceAction = SourcePolicyAction.excludeMatch;
    private RuleChannel draftScoreChannel = RuleChannel.any;
    private boolean draftSourceOverride;

    public RulesPage() {
        this(loadConfiguredView(), true);
    }

    public RulesPage(RuleRepositoryView view) {
        this(view, false);
    }

    private RulesPage(RuleRepositoryView view, boolean persistenceEnabled) {
        this.view = view;
        this.persistenceEnabled = persistenceEnabled;

        columns = new WidgetGroup<>();
        applyColumnsLayout(false);
        addWidget(columns);

        list = new WidgetGroup<>();
        applyListLayout(false);
        columns.addWidget(list);

        inspector = new WidgetGroup<>();
        applyInspectorLayout(false);
        columns.addWidget(inspector);

        selectRule(view.activeRuleOrDefault(), findCollectionKey(view.activeRuleOrDefault()), false);
        rebuild();
    }

    public void setView(RuleRepositoryView view) {
        this.view = view;
        selectRule(view.activeRuleOrDefault(), findCollectionKey(view.activeRuleOrDefault()), false);
        requestRebuild();
    }

    public RuleDefinition debugActiveRule() {
        return ActiveRoutingRule.activeRule();
    }

    public RuleDefinition debugSelectedRule() {
        return selectedRule;
    }

    public int debugRuleCount() {
        return view.visibleCollections().stream().mapToInt(collection -> collection.rules().size()).sum();
    }

    public int debugSelectedComposableRuleCount() {
        RuleDefinition rule = selectedRule == null ? view.activeRuleOrDefault() : selectedRule;
        return rule == null ? 0 : rule.sourcePolicy().rules().size() + rule.clauses().size();
    }

    public boolean debugCanEditSelectedRule() {
        return canEditSelected();
    }

    public int debugPlayerRuleCount() {
        return view.playerCollection().rules().size();
    }

    public int debugSelectedSourceRuleCount() {
        return selectedRule == null ? 0 : selectedRule.sourcePolicy().rules().size();
    }

    public int debugSelectedScoreRuleCount() {
        return selectedRule == null ? 0 : selectedRule.clauses().size();
    }

    public int debugSelectedClauseMaterialCount(int index) {
        if (selectedRule == null || index < 0 || index >= selectedRule.clauses().size()) {
            return 0;
        }
        RuleSpec clause = selectedRule.clauses().get(index);
        return clause.condition() instanceof RuleCondition.Material m ? m.ids().size() + m.tags().size() : 0;
    }

    public void debugCreatePlayerRule(String name) {
        createPlayerRule(name);
    }

    public void debugCopySelectedToPlayer() {
        copySelectedToPlayer();
    }

    public void debugRemoveSelectedRule() {
        removeSelectedRule();
    }

    public void debugRenameSelectedRule(String name) {
        renameSelectedRule(name);
    }

    public void debugSetSelectedPreset(RuleProfilePreset preset) {
        updateSelectedProfile(profile -> profileWithPreset(profile, preset));
    }

    public void debugAddSourceFilter(String tag) {
        addSourceFilter(tag, SourcePolicyAction.excludeMatch, false);
    }

    public void debugAddScoreRule(String material, double amount) {
        addScoreRule(material, RuleChannel.any, defaultScoreKey, amount);
    }

    public void debugToggleSelectedClause(int index) {
        toggleClause(index);
    }

    public void debugRemoveSelectedClause(int index) {
        removeClause(index);
    }

    public boolean debugAdvancedBuilderOpen() {
        return advancedBuilderOpen;
    }

    public int debugExpandedClauseIndex() {
        return expandedClauseIndex;
    }

    public int debugClauseMaterialPreviewLimit() {
        return clauseMaterialPreviewLimit;
    }

    public void debugToggleClauseDetails(int index) {
        toggleClauseDetails(index);
    }

    public void debugOpenAdvancedBuilder() {
        advancedBuilderOpen = true;
        requestInspectorRebuild();
    }

    public void debugAddSimpleRejectRule(String material) {
        addRejectRule(material, RuleChannel.any);
    }

    public void debugAddSimpleStockSensitiveRule(String material, double amount) {
        addCoverageRule(material, RuleChannel.any, defaultScoreKey, amount);
    }

    public boolean debugCreatingRule() {
        return creatingRule;
    }

    public boolean debugMaterialPickerOpen() {
        return materialPickerOpen;
    }

    public int debugMaterialPickerCandidateCount() {
        ensureMaterialPickerEntries();
        return materialPickerEntries.size();
    }

    public int debugVisibleMaterialPickerCandidateCount() {
        return visibleMaterialEntries().size();
    }

    public int debugFilteredMaterialPickerCandidateCount() {
        return filteredMaterialEntries().size();
    }

    public int debugPickedMaterialCount() {
        return pickedMaterials.size();
    }

    public float debugMaterialPickerScrollY() {
        return materialPickerTargetScrollY;
    }

    public void debugSetMaterialPickerScrollY(float scrollY) {
        materialPickerScrollY = Math.max(0f, scrollY);
        materialPickerTargetScrollY = materialPickerScrollY;
    }

    public void debugSetMaterialPickerQuery(String query) {
        updateMaterialPickerQuery(query);
    }

    public boolean debugUndoMaterialPickerSelection() {
        return undoMaterialPickerSelection();
    }

    public void debugOpenCreateRuleEditor() {
        openCreateRuleEditor();
    }

    public void debugOpenMaterialPicker() {
        openMaterialPicker();
    }

    public void debugPickMaterial(String material) {
        if (!materialPickerOpen) {
            openMaterialPicker();
        }
        togglePickedMaterial(material, true);
    }

    public void debugSelectFilteredMaterials() {
        if (!materialPickerOpen) {
            openMaterialPicker();
        }
        selectFilteredMaterials();
    }

    public void debugClearPickedMaterials() {
        clearPickedMaterials();
    }

    public void debugApplyPickedMaterials() {
        applyPickedMaterials();
    }

    public String debugMaterialPickerPresentation() {
        return materialPickerOpen ? "virtual_grid" : "";
    }

    public int debugMaterialPickerGridColumnsForWidth(int width) {
        return materialPickerGridColumnsForWidth(width);
    }

    public int debugMaterialPickerGridGap() {
        return materialPickerGridGap;
    }

    public int debugMaterialPickerGridViewportInset() {
        return materialPickerGridViewportInset;
    }

    public int debugMaterialPickerGridVisibleCapacity(int width, int height) {
        int viewportWidth = Math.max(1, width - materialPickerGridViewportInset * 2 - materialPickerGridScrollbarWidth - materialPickerGridScrollGap);
        int viewportHeight = Math.max(1, height - materialPickerGridViewportInset * 2);
        int columns = materialPickerGridColumnsForWidth(viewportWidth);
        int visibleRows = Math.max(1, (viewportHeight + materialPickerGridGap) / materialPickerGridStride());
        return columns * visibleRows;
    }

    public int debugMaterialPickerControlsMinHeight() {
        return materialPickerControlsMinHeight();
    }

    public int debugMaterialPickerControlsBodyMinHeight() {
        return materialPickerControlsBodyMinHeight();
    }

    public void debugSelectMaterialPickerGridRange(int from, int to, boolean selected) {
        pushMaterialPickerUndoSnapshot();
        if (!setPickedMaterialRange(filteredMaterialEntries(), from, to, selected)) {
            dropLastMaterialPickerUndoSnapshot();
        }
    }

    public static int ruleListMinWidth() {
        return listMinWidth;
    }

    public static int materialPickerPanelMinWidth() {
        return materialPickerPanelMinWidth;
    }

    public static int materialPickerPanelOuterPadding() {
        return materialPickerPanelOuterPadding;
    }

    public static int materialPickerControlsMinWidth() {
        return materialPickerControlsMinWidth;
    }

    public static int materialPickerControlsInnerPadding() {
        return materialPickerControlsInnerPadding;
    }

    public static int ruleInspectorMinWidth() {
        return inspectorMinWidth;
    }

    public static int detailRowHeight() {
        return detailRowHeight;
    }

    public static int profileMetricHeight() {
        return profileMetricHeight;
    }

    public static int ruleSentenceHeight() {
        return ruleSentenceHeight;
    }

    private void rebuild() {
        rebuildScheduled = false;
        inspectorRebuildScheduled = false;
        applyColumnsLayout(materialPickerOpen);
        applyListLayout(materialPickerOpen);
        applyInspectorLayout(materialPickerOpen);
        list.clear();
        inspector.clear();
        pageScrollingEnabled(!materialPickerOpen);
        if (materialPickerOpen) {
            buildMaterialPicker();
            return;
        }
        buildList();
        rebuildInspector();
    }

    private void requestRebuild() {
        if (!lifecycle().mounted()) {
            rebuild();
            return;
        }
        if (rebuildScheduled) {
            return;
        }
        rebuildScheduled = true;
        scene().nextTick(this::rebuild);
    }

    private void requestInspectorRebuild() {
        if (!lifecycle().mounted()) {
            rebuildInspector();
            return;
        }
        if (rebuildScheduled || inspectorRebuildScheduled) {
            return;
        }
        inspectorRebuildScheduled = true;
        scene().nextTick(() -> {
            inspectorRebuildScheduled = false;
            rebuildInspector();
        });
    }

    private void buildList() {
        list.addWidget(listActions());
        for (RuleCollection collection : view.visibleCollections()) {
            if (collection.rules().isEmpty()) {
                continue;
            }
            CirrusSectionWidget section = section(sectionTitle(collection.key()));
            list.addWidget(section);
            for (RuleDefinition rule : collection.rules()) {
                section.addContent(ruleRow(collection.key(), rule));
            }
        }
    }

    private void buildMaterialPicker() {
        ensureMaterialPickerEntries();
        list.addWidget(materialPickerControls());
        inspector.addWidget(materialPickerResults());
    }

    private Widget materialPickerControls() {
        CirrusSectionWidget controls = section(CirrusLangs.Calculator.Rules.materialPicker);
        controls.useStyle(
            widthOf(TaffyDimension.percent(1f)),
            flexShrink(0),
            minWidth(materialPickerControlsMinWidth),
            minHeight(materialPickerControlsMinHeight()),
            overflow(Overflow.HIDDEN)
        );
        controls.body().useStyle(
            gap(materialPickerControlsBodyGap),
            padding(materialPickerControlsInnerPadding),
            boxSizingBorderBox(),
            minHeight(materialPickerControlsBodyMinHeight())
        );
        WidgetGroup<Widget> actions = new WidgetGroup<>();
        actions.useStyle(flexRow(), flexWrap(), alignItemsCenter(), gap(3), minHeight(actionButtonHeight), minWidth(0));
        actions.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionBack,
            CirrusTextures.Icon.Small.arrowLeft,
            CirrusTextures.Icon.Small.arrowLeftSelected,
            this::closeMaterialPicker
        ));
        CirrusFittedTextWidget selected = new CirrusFittedTextWidget(
            CirrusLangs.Calculator.Rules.materialSelected.component(pickedMaterials.size()),
            textMuted
        );
        selected.textPadding(1, 1);
        selected.useStyle(flexGrow(1), minWidth(72), heightOf(actionButtonHeight));
        actions.addWidget(selected);
        actions.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionAdd,
            CirrusTextures.Icon.Small.plus,
            CirrusTextures.Icon.Small.plusSelected,
            this::applyPickedMaterials
        ).enabled(!pickedMaterials.isEmpty()));
        controls.addContent(actions);
        controls.addContent(simpleKindRow());
        controls.addContent(simpleScopeRow());
        if (draftSimpleKind != SimpleRuleKind.reject) {
            controls.addContent(textInputRow(CirrusLangs.Calculator.Rules.fieldStrength, draftSimpleStrength, value -> draftSimpleStrength = value, 12));
        }

        SearchBoxWidget search = new SearchBoxWidget()
            .setText(materialPickerQuery)
            .setPlaceholder(CirrusLangs.Calculator.Rules.materialSearchPlaceholder)
            .highlighter(RuleMaterialSearch::highlight)
            .suggestionViewport(this::scrollViewportSceneBounds)
            .suggestionSource(RuleMaterialSearch.suggestionSource(materialPickerEntries))
            .onQueryChanged(this::updateMaterialPickerQuery);
        search.useStyle(heightOf(actionButtonHeight), minWidth(0));
        controls.addContent(search);

        WidgetGroup<Widget> bulk = new WidgetGroup<>();
        bulk.useStyle(
            flexColumn(),
            gap(3),
            minHeight(actionButtonHeight * 2 + 3),
            minWidth(0),
            widthOf(TaffyDimension.percent(1f))
        );
        bulk.addWidget(panelButton(76, CirrusLangs.Calculator.Rules.actionSelectFiltered, this::selectFilteredMaterials));
        bulk.addWidget(panelButton(44, CirrusLangs.Calculator.Rules.actionClear, this::clearPickedMaterials));
        controls.addContent(bulk);
        controls.addContent(materialPickerSummary());
        return controls;
    }

    private void applyListLayout(boolean materialPickerLayout) {
        list.useStyle(
            flexColumn(),
            gap(3),
            flexGrow(1),
            flexShrink(1),
            flexBasisPercent(materialPickerLayout ? 0.28f : 0.31f),
            minWidth(materialPickerLayout ? materialPickerPanelMinWidth : listMinWidth),
            maxWidth(materialPickerLayout ? materialPickerPanelMaxWidth : 192),
            minHeight(0),
            padding(materialPickerLayout ? materialPickerPanelOuterPadding : 0),
            boxSizingBorderBox(),
            overflow(Overflow.HIDDEN)
        );
    }

    private void applyInspectorLayout(boolean materialPickerLayout) {
        inspector.useStyle(
            flexColumn(),
            gap(4),
            flexGrow(materialPickerLayout ? 3 : 2),
            flexShrink(1),
            flexBasisPercent(materialPickerLayout ? 0.66f : 0.64f),
            minWidth(materialPickerLayout ? materialPickerResultsMinWidth : inspectorMinWidth),
            minHeight(0),
            overflow(Overflow.HIDDEN)
        );
    }

    private void applyColumnsLayout(boolean materialPickerLayout) {
        columns.useStyle(
            flexRow(),
            materialPickerLayout ? flexNoWrap() : flexWrap(),
            gap(5),
            flexGrow(1),
            minHeight(0),
            minWidth(0),
            overflow(Overflow.HIDDEN)
        );
    }

    private int materialPickerControlsMinHeight() {
        return materialPickerSectionPadding * 2
            + materialPickerSectionTitleHeight
            + materialPickerSectionTitleGap
            + materialPickerControlsBodyMinHeight();
    }

    private int materialPickerControlsBodyMinHeight() {
        int rows = materialPickerControlsRowCount();
        return materialPickerControlsInnerPadding * 2
            + rows * actionButtonHeight
            + Math.max(0, rows - 1) * materialPickerControlsBodyGap;
    }

    private int materialPickerControlsRowCount() {
        return draftSimpleKind == SimpleRuleKind.reject ? 8 : 9;
    }

    private Widget materialPickerSummary() {
        CirrusFittedTextWidget summary = new CirrusFittedTextWidget(materialPickerSummaryText(), textMuted);
        summary.textPadding(1, 1);
        summary.useStyle(minWidth(0), heightOf(actionButtonHeight));
        materialPickerSummaryWidget = summary;
        return summary;
    }

    private Component materialPickerSummaryText() {
        List<RuleIngredientCatalog.Entry> filtered = filteredMaterialEntries();
        return CirrusLangs.Calculator.Rules.materialVisible.component(filtered.size() + " / " + materialPickerEntries.size());
    }

    private void updateMaterialPickerQuery(String query) {
        String value = query == null ? "" : query;
        if (value.equals(materialPickerQuery)) {
            return;
        }
        materialPickerQuery = value;
        materialPickerScrollY = 0f;
        materialPickerTargetScrollY = 0f;
        if (materialPickerSummaryWidget != null) {
            materialPickerSummaryWidget.text(materialPickerSummaryText());
        }
        requestMaterialPickerResultsRebuild();
    }

    private void requestMaterialPickerResultsRebuild() {
        if (!materialPickerOpen) {
            requestInspectorRebuild();
            return;
        }
        if (!lifecycle().mounted()) {
            rebuildMaterialPickerResults();
            return;
        }
        if (rebuildScheduled || inspectorRebuildScheduled) {
            return;
        }
        inspectorRebuildScheduled = true;
        scene().nextTick(() -> {
            inspectorRebuildScheduled = false;
            rebuildMaterialPickerResults();
        });
    }

    private void rebuildMaterialPickerResults() {
        inspectorRebuildScheduled = false;
        inspector.clear();
        inspector.addWidget(materialPickerResults());
    }

    private Widget materialPickerResults() {
        CirrusSectionWidget section = section(CirrusLangs.Calculator.Rules.materialPickerResults);
        section.useStyle(flexGrow(1), minHeight(materialPickerGridMinHeight), overflow(Overflow.HIDDEN));
        section.body().useStyle(flexGrow(1), minHeight(0), overflow(Overflow.HIDDEN));
        List<RuleIngredientCatalog.Entry> entries = visibleMaterialEntries();
        if (entries.isEmpty()) {
            section.addContent(CirrusText.muted(CirrusLangs.Calculator.Rules.empty));
            return section;
        }
        MaterialPickerGridWidget grid = new MaterialPickerGridWidget(entries);
        grid.useStyle(
            widthOf(TaffyDimension.percent(1f)),
            flexGrow(1),
            minWidth(0),
            minHeight(materialPickerGridMinHeight),
            overflow(Overflow.HIDDEN)
        );
        section.addContent(grid);
        return section;
    }

    private Widget listActions() {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(actionButtonHeight), minWidth(0), overflow(Overflow.HIDDEN));
        if (!creatingRule) {
            row.addWidget(iconButton(
                iconButtonWidth,
                CirrusLangs.Calculator.Rules.actionNew,
                CirrusTextures.Icon.Small.plus,
                CirrusTextures.Icon.Small.plusSelected,
                this::openCreateRuleEditor
            ));
            row.addWidget(button(shortButtonWidth, CirrusLangs.Calculator.Rules.actionCopy, this::copySelectedToPlayer)
                .enabled(selectedRule != null));
            row.addWidget(iconButton(
                iconButtonWidth,
                CirrusLangs.Calculator.Rules.actionRemove,
                CirrusTextures.Icon.delete,
                CirrusTextures.Icon.deleteSelected,
                this::removeSelectedRule
            ).enabled(canRemoveSelectedRule()));
            return row;
        }

        CirrusTextInputWidget name = new CirrusTextInputWidget()
            .setText(draftNewRuleName)
            .setPlaceholder(CirrusLangs.Calculator.Rules.newNamePlaceholder)
            .maxLength(48)
            .onTextChanged(value -> draftNewRuleName = value)
            .onEnterPressed(this::createPlayerRule);
        name.useStyle(flexGrow(1), minWidth(48), heightOf(actionButtonHeight));
        row.addWidget(name);
        row.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionNew,
            CirrusTextures.Icon.Small.plus,
            CirrusTextures.Icon.Small.plusSelected,
            this::createPlayerRule
        ));
        row.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionBack,
            CirrusTextures.Icon.Small.arrowLeft,
            CirrusTextures.Icon.Small.arrowLeftSelected,
            this::closeCreateRuleEditor
        ));
        return row;
    }

    private Component sectionTitle(RuleCollectionKey key) {
        return Component.literal(key.scope().name().toLowerCase(Locale.ROOT) + " / " + key.id());
    }

    private Widget ruleRow(RuleCollectionKey collection, RuleDefinition rule) {
        String displayName = ruleDisplayName(rule);
        CirrusRowWidget row = CirrusRowWidget.selectable(ruleListRowHeight, 0)
            .selected(() -> selectedRule != null && selectedRule.ruleId().equals(rule.ruleId()))
            .onPress(ignored -> {
                selectRule(rule, collection, true);
                requestInspectorRebuild();
                CirrusUiSound.playClickSound();
            });
        row.onHoverTooltip((mouseX, mouseY) -> detailTooltip(Component.literal(displayName), Component.literal(rule.ruleId().toString())));
        row.useStyle(heightOf(ruleListRowHeight), minWidth(0), flexGrow(1));

        WidgetGroup<Widget> body = new WidgetGroup<>();
        body.useStyle(flexColumn(), gap(2), padding(4), boxSizingBorderBox(), flexGrow(1), minWidth(0), minHeight(0), overflow(Overflow.HIDDEN));
        CirrusFittedTextWidget name = new CirrusFittedTextWidget(Component.literal(displayName), textPrimary);
        name.textPadding(1, 0);
        name.useStyle(heightOf(12), minWidth(0));
        body.addWidget(name);
        String meta = compactRuleListMeta(rule);
        CirrusFittedTextWidget sub = new CirrusFittedTextWidget(Component.literal(meta), textMuted);
        sub.textPadding(1, 0);
        sub.useStyle(heightOf(11), minWidth(0));
        body.addWidget(sub);
        row.addWidget(body);
        return row;
    }

    private void rebuildInspector() {
        inspectorRebuildScheduled = false;
        inspector.clear();
        RuleDefinition rule = selectedRule == null ? view.activeRuleOrDefault() : selectedRule;
        if (rule == null) {
            inspector.addWidget(CirrusText.muted(CirrusLangs.Calculator.Rules.empty));
            return;
        }
        selectedRule = rule;

        CirrusSectionWidget summary = section(CirrusLangs.Calculator.Rules.sectionSummary);
        summary.addContent(detailRow(CirrusLangs.Calculator.Rules.fieldName, rule.name()));
        summary.addContent(detailRow(CirrusLangs.Calculator.Rules.fieldId, compactId(rule.ruleId().toString())));
        summary.addContent(detailRow(CirrusLangs.Calculator.Rules.fieldCollection, selectedCollectionLabel()));
        summary.addContent(detailRow(CirrusLangs.Calculator.Rules.fieldEditable, state(canEditSelected())));
        if (!canEditSelected()) {
            summary.addContent(readOnlyActions());
        }
        inspector.addWidget(summary);

        CirrusSectionWidget profile = section(CirrusLangs.Calculator.Rules.sectionProfile);
        if (canEditSelected()) {
            profile.addContent(profilePresetRow(rule));
            profile.addContent(profileUnresolvedRow(rule));
            profile.addContent(profileNumberRow(CirrusLangs.Calculator.Rules.fieldShortlist, rule.profile().shortlistK(), 1, 32,
                value -> updateSelectedProfile(profileSpec -> profileWithShortlist(profileSpec, value.intValue()))));
            profile.addContent(profileNumberRow(CirrusLangs.Calculator.Rules.fieldRollback, rule.profile().rollbackWidth(), 0, 32,
                value -> updateSelectedProfile(profileSpec -> profileWithRollback(profileSpec, value.intValue()))));
        }
        WidgetGroup<Widget> metrics = new WidgetGroup<>();
        metrics.useStyle(flexRow(), flexWrap(), gap(3), minWidth(0), alignItemsFlexStart());
        metrics.addWidget(metric(CirrusLangs.Calculator.Rules.fieldPreset, rule.profile().preset().name(), compactEnumName(rule.profile().preset().name())));
        metrics.addWidget(metric(CirrusLangs.Calculator.Rules.fieldUnresolved, rule.profile().unresolvedInputMode().name(), compactEnumName(rule.profile().unresolvedInputMode().name())));
        metrics.addWidget(metric(CirrusLangs.Calculator.Rules.fieldTieEps, compactDecimal(rule.profile().nearTieEpsilon())));
        metrics.addWidget(metric(CirrusLangs.Calculator.Rules.fieldSoftAmp, compactDecimal(rule.profile().amplificationSoftThreshold())));
        metrics.addWidget(metric(CirrusLangs.Calculator.Rules.fieldHardAmp, compactDecimal(rule.profile().amplificationHardThreshold())));
        profile.addContent(metrics);
        inspector.addWidget(profile);

        if (canEditSelected()) {
            inspector.addWidget(builderSection());
        }

        if (!canEditSelected() || advancedBuilderOpen) {
            CirrusSectionWidget sourcePolicy = section(CirrusLangs.Calculator.Rules.sectionSourcePolicy);
            if (rule.sourcePolicy().rules().isEmpty()) {
                sourcePolicy.addContent(CirrusText.muted(CirrusLangs.Calculator.Rules.noSourceFilters));
            } else {
                int policyIndex = 0;
                for (SourcePolicyRuleSpec spec : rule.sourcePolicy().rules()) {
                    sourcePolicy.addContent(policyCard(spec, policyIndex++));
                }
            }
            inspector.addWidget(sourcePolicy);
        }

        CirrusSectionWidget clauses = section(CirrusLangs.Calculator.Rules.sectionClauses);
        if (rule.clauses().isEmpty()) {
            clauses.addContent(CirrusText.muted(CirrusLangs.Calculator.Rules.noClauses));
        } else {
            int clauseIndex = 0;
            for (RuleSpec spec : rule.clauses()) {
                clauses.addContent(clauseCard(spec, clauseIndex++));
            }
        }
        inspector.addWidget(clauses);
    }

    private CirrusSectionWidget builderSection() {
        CirrusSectionWidget builder = section(CirrusLangs.Calculator.Rules.sectionBuilder);
        if (advancedBuilderOpen) {
            builder.addContent(advancedHeaderRow());
            builder.addContent(textInputRow(CirrusLangs.Calculator.Rules.fieldSourceTag, draftSourceTag, value -> draftSourceTag = value, 120));
            builder.addContent(sourceActionRow());
            builder.addContent(sourceOverrideRow());
            builder.addContent(textInputRow(CirrusLangs.Calculator.Rules.fieldMaterial, draftMaterial, value -> draftMaterial = value, 120));
            builder.addContent(textInputRow(CirrusLangs.Calculator.Rules.fieldScoreKey, draftScoreKey, value -> draftScoreKey = value, 48));
            builder.addContent(scoreActionRow());
            return builder;
        }

        builder.addContent(simpleKindRow());
        builder.addContent(simpleMaterialRow());
        builder.addContent(simpleScopeRow());
        if (draftSimpleKind != SimpleRuleKind.reject) {
            builder.addContent(textInputRow(CirrusLangs.Calculator.Rules.fieldStrength, draftSimpleStrength, value -> draftSimpleStrength = value, 12));
        }
        builder.addContent(simpleActionRow());
        return builder;
    }

    private Widget advancedHeaderRow() {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(actionButtonHeight), minWidth(0), overflow(Overflow.HIDDEN));
        CirrusFittedTextWidget label = new CirrusFittedTextWidget(CirrusLangs.Calculator.Rules.advancedEditor.component(), textMuted);
        label.useStyle(flexGrow(1), minWidth(0), heightOf(actionButtonHeight));
        row.addWidget(label);
        row.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionBack,
            CirrusTextures.Icon.Small.arrowLeft,
            CirrusTextures.Icon.Small.arrowLeftSelected,
            () -> {
                advancedBuilderOpen = false;
                requestInspectorRebuild();
            }
        ));
        return row;
    }

    private Widget simpleKindRow() {
        CirrusChoiceBoxWidget<SimpleRuleKind> choice = new CirrusChoiceBoxWidget<SimpleRuleKind>(92)
            .option(SimpleRuleKind.reject, CirrusLangs.Calculator.Rules.simpleReject)
            .option(SimpleRuleKind.penalty, CirrusLangs.Calculator.Rules.simplePenalty)
            .option(SimpleRuleKind.reward, CirrusLangs.Calculator.Rules.simpleReward)
            .option(SimpleRuleKind.stockSensitive, CirrusLangs.Calculator.Rules.simpleStock)
            .value(draftSimpleKind)
            .onChanged(value -> {
                draftSimpleKind = value == null ? SimpleRuleKind.penalty : value;
                if (materialPickerOpen) {
                    requestRebuild();
                } else {
                    requestInspectorRebuild();
                }
            });
        return formRow(CirrusLangs.Calculator.Rules.fieldType, choice);
    }

    private Widget simpleMaterialRow() {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(formRowHeight), minWidth(0), overflow(Overflow.HIDDEN));
        Widget preview = materialPreviewSlot(draftSimpleMaterial);
        preview.useStyle(sizeOf(formRowHeight, formRowHeight), flexShrink(0));
        row.addWidget(preview);

        CirrusFittedTextWidget label = new CirrusFittedTextWidget(Component.literal(materialDisplayLabel(draftSimpleMaterial)), textPrimary);
        label.textPadding(1, 1);
        label.useStyle(flexGrow(1), minWidth(0), heightOf(formRowHeight));
        row.addWidget(label);

        row.addWidget(button(40, CirrusLangs.Calculator.Rules.actionPick, this::openMaterialPicker));
        row.onHoverTooltip((mouseX, mouseY) -> detailTooltip(
            CirrusLangs.Calculator.Rules.fieldMaterial.component(),
            Component.literal(draftSimpleMaterial)
        ));
        return formRow(CirrusLangs.Calculator.Rules.fieldMaterial, row);
    }

    private Widget simpleScopeRow() {
        CirrusChoiceBoxWidget<RuleChannel> choice = new CirrusChoiceBoxWidget<RuleChannel>(64)
            .option(RuleChannel.any, CirrusLangs.Calculator.Rules.scopeAny)
            .option(RuleChannel.input, CirrusLangs.Calculator.Rules.scopeInput)
            .option(RuleChannel.output, CirrusLangs.Calculator.Rules.scopeOutput)
            .value(draftSimpleChannel)
            .onChanged(value -> draftSimpleChannel = value == null ? RuleChannel.any : value);
        return formRow(CirrusLangs.Calculator.Rules.fieldScope, choice);
    }

    private Widget simpleActionRow() {
        WidgetGroup<Widget> footer = new WidgetGroup<>();
        footer.useStyle(
            flexColumn(),
            gap(simpleFooterGap),
            heightOf(simpleHintHeight + simpleFooterGap + actionButtonHeight),
            minWidth(0),
            overflow(Overflow.HIDDEN)
        );

        CirrusWrappedTextWidget hint = new CirrusWrappedTextWidget(simpleRuleHint(), textMuted)
            .padding(1)
            .maxLines(2);
        hint.useStyle(heightOf(simpleHintHeight), minWidth(0), flexShrink(0));
        footer.addWidget(hint);

        WidgetGroup<Widget> actions = new WidgetGroup<>();
        actions.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(actionButtonHeight), minWidth(0), overflow(Overflow.HIDDEN));
        Widget spacer = new Widget();
        spacer.useStyle(flexGrow(1), minWidth(0), heightOf(actionButtonHeight));
        actions.addWidget(spacer);
        actions.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionAdvanced,
            CirrusTextures.Icon.setting,
            CirrusTextures.Icon.settingSelected,
            () -> {
                advancedBuilderOpen = true;
                requestInspectorRebuild();
            }
        ));
        actions.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionAdd,
            CirrusTextures.Icon.Small.plus,
            CirrusTextures.Icon.Small.plusSelected,
            this::addSimpleRule
        ));
        footer.addWidget(actions);
        return footer;
    }

    private Component simpleRuleHint() {
        return switch (draftSimpleKind) {
            case reject -> CirrusLangs.Calculator.Rules.hintReject.component();
            case penalty -> CirrusLangs.Calculator.Rules.hintPenalty.component();
            case reward -> CirrusLangs.Calculator.Rules.hintReward.component();
            case stockSensitive -> CirrusLangs.Calculator.Rules.hintStock.component();
        };
    }

    private Widget sourceActionRow() {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(formRowHeight), minWidth(0), overflow(Overflow.HIDDEN));
        CirrusChoiceBoxWidget<SourcePolicyAction> action = new CirrusChoiceBoxWidget<SourcePolicyAction>(68)
            .option(SourcePolicyAction.excludeMatch, CirrusLangs.Calculator.Rules.actionExcludeShort)
            .option(SourcePolicyAction.includeMatch, CirrusLangs.Calculator.Rules.actionIncludeShort)
            .option(SourcePolicyAction.allowOnlyMatch, CirrusLangs.Calculator.Rules.actionOnlyShort)
            .value(draftSourceAction)
            .onChanged(value -> draftSourceAction = value);
        row.addWidget(action);
        row.addWidget(button(30, CirrusLangs.Calculator.Rules.actionAdd, () ->
            addSourceFilter(draftSourceTag, draftSourceAction, draftSourceOverride)));
        return formRow(CirrusLangs.Calculator.Rules.fieldAction, row);
    }

    private Widget sourceOverrideRow() {
        WidgetGroup<Widget> override = new WidgetGroup<>();
        override.useStyle(flexRow(), alignItemsCenter(), heightOf(formRowHeight), minWidth(0), overflow(Overflow.HIDDEN));
        CirrusCheckboxWidget checkbox = new CirrusCheckboxWidget(() -> draftSourceOverride, value -> draftSourceOverride = value);
        checkbox.useStyle(sizeOf(CirrusCheckboxWidget.defaultSize(), CirrusCheckboxWidget.defaultSize()), flexShrink(0));
        override.addWidget(checkbox);
        return formRow(CirrusLangs.Calculator.Rules.fieldOverride, override);
    }

    private Widget scoreActionRow() {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(formRowHeight), minWidth(0), overflow(Overflow.HIDDEN));
        CirrusChoiceBoxWidget<RuleChannel> channel = new CirrusChoiceBoxWidget<RuleChannel>(50)
            .option(RuleChannel.any, Component.literal("any"))
            .option(RuleChannel.input, Component.literal("in"))
            .option(RuleChannel.output, Component.literal("out"))
            .value(draftScoreChannel)
            .onChanged(value -> draftScoreChannel = value);
        row.addWidget(channel);
        CirrusTextInputWidget amount = new CirrusTextInputWidget()
            .setText(draftScoreAmount)
            .maxLength(12)
            .onTextChanged(value -> draftScoreAmount = value);
        amount.useStyle(widthOf(38), heightOf(formRowHeight), flexShrink(1), minWidth(28));
        row.addWidget(amount);
        row.addWidget(button(30, CirrusLangs.Calculator.Rules.actionAdd, () ->
            addScoreRule(draftMaterial, draftScoreChannel, draftScoreKey, parseDouble(draftScoreAmount, 1.0d))));
        return formRow(CirrusLangs.Calculator.Rules.fieldAmount, row);
    }

    private Widget readOnlyActions() {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), heightOf(formRowHeight), minWidth(0), overflow(Overflow.HIDDEN));
        CirrusFittedTextWidget hint = new CirrusFittedTextWidget(CirrusLangs.Calculator.Rules.readOnly.component(), textMuted);
        hint.useStyle(flexGrow(1), minWidth(0), heightOf(formRowHeight));
        row.addWidget(hint);
        row.addWidget(button(44, CirrusLangs.Calculator.Rules.actionCopy, this::copySelectedToPlayer));
        return row;
    }

    private Widget profilePresetRow(RuleDefinition rule) {
        CirrusChoiceBoxWidget<RuleProfilePreset> choice = new CirrusChoiceBoxWidget<RuleProfilePreset>(102)
            .option(RuleProfilePreset.standardDefault, Component.literal("standard"))
            .option(RuleProfilePreset.standardExecution, Component.literal("execution"))
            .option(RuleProfilePreset.assuredBoundedDefault, Component.literal("bounded"))
            .option(RuleProfilePreset.assuredProvenCompat, Component.literal("proven"))
            .value(rule.profile().preset())
            .onChanged(value -> updateSelectedProfile(profile -> profileWithPreset(profile, value)));
        return formRow(CirrusLangs.Calculator.Rules.fieldPreset, choice);
    }

    private Widget profileUnresolvedRow(RuleDefinition rule) {
        CirrusChoiceBoxWidget<UnresolvedInputMode> choice = new CirrusChoiceBoxWidget<UnresolvedInputMode>(102)
            .option(UnresolvedInputMode.strict, Component.literal("strict"))
            .option(UnresolvedInputMode.directSupplyOnNoSource, Component.literal("direct"))
            .option(UnresolvedInputMode.directSupplyOnNoSourceOrSafeExhausted, Component.literal("safe"))
            .option(UnresolvedInputMode.directSupplyOnNoSourceOrSafeOrCycleExhausted, Component.literal("cycle"))
            .value(rule.profile().unresolvedInputMode())
            .onChanged(value -> updateSelectedProfile(profile -> profileWithUnresolved(profile, value)));
        return formRow(CirrusLangs.Calculator.Rules.fieldUnresolved, choice);
    }

    private Widget profileNumberRow(LangEntry label, int value, int min, int max, Consumer<Long> setter) {
        NumberInputWidget input = NumberInputWidget.create(value)
            .setRange(min, max)
            .setScrollStep(1)
            .onValueChanged(setter::accept);
        input.useStyle(sizeOf(48, formRowHeight), flexShrink(0));
        return formRow(label, input);
    }

    private Widget formRow(LangEntry key, Widget value) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(4), heightOf(formRowHeight), minWidth(0), overflow(Overflow.HIDDEN));
        CirrusFittedTextWidget keyWidget = new CirrusFittedTextWidget(key.component(), textMuted);
        keyWidget.useStyle(widthOf(detailKeyWidth), flexShrink(0), heightOf(formRowHeight));
        row.addWidget(keyWidget);
        value.useStyle(flexGrow(1), minWidth(0));
        row.addWidget(value);
        return row;
    }

    private Widget textInputRow(LangEntry key, String value, Consumer<String> setter, int maxLength) {
        CirrusTextInputWidget input = new CirrusTextInputWidget()
            .setText(value)
            .maxLength(maxLength)
            .onTextChanged(setter);
        input.useStyle(heightOf(formRowHeight), minWidth(0));
        return formRow(key, input);
    }

    private Widget detailRow(LangEntry key, String value) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        Component keyText = key.component();
        Component valueText = Component.literal(value);
        int rowHeight = Math.max(
            detailRowHeight,
            Math.max(wrappedTextHeight(keyText, 8, 2, detailRowHeight), wrappedTextHeight(valueText, 18, 4, detailRowHeight))
        );
        row.useStyle(flexRow(), alignItemsFlexStart(), gap(4), minHeight(rowHeight), minWidth(0));
        CirrusWrappedTextWidget keyWidget = wrappedInfoText(keyText, textMuted, 2);
        keyWidget.useStyle(widthOf(detailKeyWidth), flexShrink(0), minHeight(rowHeight));
        row.addWidget(keyWidget);
        CirrusWrappedTextWidget valueWidget = wrappedInfoText(valueText, textPrimary, 4);
        valueWidget.useStyle(flexGrow(1), minWidth(0), minHeight(rowHeight));
        row.addWidget(valueWidget);
        row.onHoverTooltip((mouseX, mouseY) -> detailTooltip(keyText, valueText));
        return row;
    }

    private Widget metric(LangEntry key, String value) {
        return metric(key, value, value);
    }

    private Widget metric(LangEntry key, String fullValue, String visibleValue) {
        WidgetGroup<Widget> card = new WidgetGroup<>();
        Component keyText = key.component();
        Component valueText = Component.literal(visibleValue);
        int labelHeight = wrappedTextHeight(keyText, 11, 2, 10);
        int valueHeight = wrappedTextHeight(valueText, 10, 2, 11);
        int cardHeight = Math.max(profileMetricHeight, labelHeight + valueHeight + 5);
        card.useStyle(
            flexColumn(),
            gap(1),
            flexBasisPercent(0.31f),
            flexGrow(1),
            flexShrink(1),
            minWidth(42),
            minHeight(cardHeight),
            padding(2),
            boxSizingBorderBox(),
            background(CirrusTextures.Background.Outlined.flat)
        );
        card.onHoverTooltip((mouseX, mouseY) -> detailTooltip(keyText, Component.literal(fullValue)));

        CirrusWrappedTextWidget label = wrappedInfoText(keyText, textMuted, 2);
        label.useStyle(minHeight(labelHeight), minWidth(0));
        card.addWidget(label);
        CirrusWrappedTextWidget value = wrappedInfoText(valueText, textPrimary, 2);
        value.useStyle(minHeight(valueHeight), minWidth(0));
        card.addWidget(value);
        return card;
    }

    private Widget policyCard(SourcePolicyRuleSpec spec, int index) {
        WidgetGroup<Widget> card = ruleCard();
        card.addWidget(cardHeader(
            CirrusLangs.Calculator.Rules.sourceFilter.component(),
            spec.enabled(),
            compactId(spec.ruleId().toString()),
            () -> toggleSourcePolicy(index),
            () -> removeSourcePolicy(index)
        ));
        card.addWidget(compactPair(CirrusLangs.Calculator.Rules.fieldWhen, tagExpressionLabel(spec.when())));
        card.addWidget(compactPair(CirrusLangs.Calculator.Rules.fieldAction, sourceActionLabel(spec)));
        return card;
    }

    private Widget clauseCard(RuleSpec spec, int index) {
        WidgetGroup<Widget> card = ruleCard();
        card.onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            toggleClauseDetails(index);
            CirrusUiSound.playClickSound();
            return EventDispatch.consumed;
        });
        Component title = clauseTitle(spec);
        card.addWidget(clauseHeader(title, spec, index));
        if (spec.condition() instanceof RuleCondition.Material) {
            card.addWidget(materialConditionRow(spec));
        } else {
            card.addWidget(compactPair(CirrusLangs.Calculator.Rules.fieldWhen, conditionLabel(spec.condition())));
            card.addWidget(effectSummaryRow(spec.actions()));
        }
        if (expandedClauseIndex == index) {
            card.addWidget(clauseDetailPanel(spec));
        }
        return card;
    }

    private Widget materialConditionRow(RuleSpec spec) {
        RuleCondition.Material condition = (RuleCondition.Material) spec.condition();
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), flexWrap(), alignItemsCenter(), gap(clauseMaterialGap), minWidth(0), minHeight(materialIconSize));
        row.onHoverTooltip((mouseX, mouseY) -> clauseTooltip(spec));

        row.addWidget(effectIconBadge(spec.actions()));
        List<String> materials = materialTokens(condition);
        int visible = Math.min(clauseMaterialPreviewLimit, materials.size());
        for (int i = 0; i < visible; i++) {
            Widget preview = materialPreviewSlot(materials.get(i));
            preview.useStyle(sizeOf(materialIconSize, materialIconSize), flexShrink(0));
            row.addWidget(preview);
        }
        int hidden = materials.size() - visible;
        if (hidden > 0) {
            CirrusFittedTextWidget more = new CirrusFittedTextWidget(Component.literal(hiddenMaterialLabel(hidden)), textSecondary);
            more.align(CirrusFittedTextWidget.Align.center);
            more.textPadding(0, 0);
            more.useStyle(widthOf(16), heightOf(materialIconSize), flexShrink(0), background(CirrusTextures.Background.Outlined.flat));
            more.onHoverTooltip((mouseX, mouseY) -> clauseTooltip(spec));
            row.addWidget(more);
        }
        Widget amount = effectAmountBadge(spec.actions());
        if (amount != null) {
            row.addWidget(amount);
        }
        row.addWidget(channelBadge(condition.channel()));
        return row;
    }

    private Widget clauseDetailPanel(RuleSpec spec) {
        WidgetGroup<Widget> panel = new WidgetGroup<>();
        panel.useStyle(
            flexColumn(),
            gap(3),
            padding(3),
            boxSizingBorderBox(),
            minWidth(0),
            background(CirrusTextures.Background.inset)
        );
        panel.onHoverTooltip((mouseX, mouseY) -> clauseTooltip(spec));
        panel.addWidget(clauseVisualSummary(spec));
        if (spec.condition() instanceof RuleCondition.Material m) {
            panel.addWidget(materialDetailGrid(m));
        } else {
            panel.addWidget(compactPair(CirrusLangs.Calculator.Rules.fieldWhen, conditionLabel(spec.condition())));
        }
        return panel;
    }

    private Widget clauseVisualSummary(RuleSpec spec) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), flexWrap(), alignItemsCenter(), gap(2), minWidth(0), minHeight(actionButtonHeight));
        row.addWidget(effectIconBadge(spec.actions()));
        Widget amount = effectAmountBadge(spec.actions());
        if (amount != null) {
            row.addWidget(amount);
        }
        RuleChannel channel = spec.condition() instanceof RuleCondition.Material m ? m.channel() : RuleChannel.any;
        row.addWidget(channelBadge(channel));
        if (spec.condition() instanceof RuleCondition.Material m) {
            row.addWidget(countBadge(materialTokens(m).size()));
        }
        row.onHoverTooltip((mouseX, mouseY) -> clauseTooltip(spec));
        return row;
    }

    private Widget materialDetailGrid(RuleCondition.Material condition) {
        WidgetGroup<Widget> grid = new WidgetGroup<>();
        List<String> materials = materialTokens(condition);
        int rows = Math.max(1, (materials.size() + clauseMaterialDetailColumns - 1) / clauseMaterialDetailColumns);
        int gridHeight = rows * materialIconSize + Math.max(0, rows - 1) * clauseMaterialGap + 4;
        grid.useStyle(
            flexRow(),
            flexWrap(),
            gap(clauseMaterialGap),
            padding(1),
            boxSizingBorderBox(),
            minWidth(0),
            minHeight(gridHeight),
            background(CirrusTextures.Background.Outlined.flat)
        );
        grid.onHoverTooltip((mouseX, mouseY) -> detailTooltip(
            conditionLabel(condition),
            CirrusLangs.Calculator.Rules.materialSelected.component(materials.size())
        ));
        for (String material : materials) {
            Widget preview = materialPreviewSlot(material);
            preview.useStyle(sizeOf(materialIconSize, materialIconSize), flexShrink(0));
            grid.addWidget(preview);
        }
        return grid;
    }

    private Widget channelBadge(RuleChannel channel) {
        Component label = switch (channel) {
            case any -> CirrusLangs.Calculator.Rules.scopeAny.component();
            case input -> CirrusLangs.Calculator.Rules.scopeInput.component();
            case output -> CirrusLangs.Calculator.Rules.scopeOutput.component();
        };
        CirrusFittedTextWidget badge = new CirrusFittedTextWidget(Component.literal(channelShortLabel(channel)), textSecondary);
        badge.align(CirrusFittedTextWidget.Align.center);
        badge.textPadding(0, 0);
        badge.useStyle(widthOf(22), heightOf(materialIconSize), flexShrink(0), background(CirrusTextures.Background.Outlined.flat));
        badge.onHoverTooltip((mouseX, mouseY) -> Tooltip.create().add(label));
        return badge;
    }

    private Widget countBadge(int count) {
        CirrusFittedTextWidget badge = new CirrusFittedTextWidget(Component.literal(String.valueOf(count)), textPrimary);
        badge.align(CirrusFittedTextWidget.Align.center);
        badge.textPadding(0, 0);
        badge.useStyle(widthOf(22), heightOf(materialIconSize), flexShrink(0), background(CirrusTextures.Background.Outlined.flat));
        badge.onHoverTooltip((mouseX, mouseY) -> Tooltip.create().add(Component.literal(String.valueOf(count))));
        return badge;
    }

    private Widget effectIconBadge(List<RuleAction> actions) {
        TextureIconWidget icon = new TextureIconWidget(effectIconTexture(actions), materialIconSize);
        icon.useStyle(sizeOf(materialIconSize, materialIconSize), flexShrink(0), background(CirrusTextures.Background.Outlined.flat));
        icon.onHoverTooltip((mouseX, mouseY) -> Tooltip.create().add(effectLabel(actions)));
        return icon;
    }

    private Widget effectAmountBadge(List<RuleAction> actions) {
        String amount = effectAmountText(actions);
        if (amount.isBlank()) {
            return null;
        }
        CirrusFittedTextWidget badge = new CirrusFittedTextWidget(Component.literal(amount), textPrimary);
        badge.align(CirrusFittedTextWidget.Align.center);
        badge.textPadding(0, 0);
        badge.useStyle(widthOf(24), heightOf(materialIconSize), flexShrink(0), background(CirrusTextures.Background.Outlined.flat));
        badge.onHoverTooltip((mouseX, mouseY) -> Tooltip.create().add(effectLabel(actions)));
        return badge;
    }

    private Widget effectSummaryRow(List<RuleAction> actions) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), minWidth(0), minHeight(ruleSentenceHeight));
        row.addWidget(new TextureIconWidget(effectIconTexture(actions), ruleSentenceHeight));
        CirrusFittedTextWidget value = new CirrusFittedTextWidget(effectLabel(actions), textSecondary);
        value.textPadding(1, 1);
        value.useStyle(flexGrow(1), minWidth(0), heightOf(ruleSentenceHeight));
        row.addWidget(value);
        return row;
    }

    private SizedTexture effectIconTexture(List<RuleAction> actions) {
        if (rejectOf(actions).isPresent()) {
            return CirrusTextures.Icon.pause;
        }
        return scoreOf(actions)
            .map(adjust -> {
                if (adjust.scale() == RuleAction.ScoreAdjust.ScoreScale.coverageShortage) {
                    return CirrusTextures.Icon.Sign.Amount.available;
                }
                return adjust.boost() ? CirrusTextures.Icon.Small.plus : CirrusTextures.Icon.Small.minus;
            })
            .orElse(CirrusTextures.Icon.Small.minus);
    }

    private WidgetGroup<Widget> ruleCard() {
        WidgetGroup<Widget> card = new WidgetGroup<>();
        card.useStyle(
            flexColumn(),
            gap(2),
            minWidth(0),
            padding(4),
            boxSizingBorderBox(),
            background(CirrusTextures.Background.Outlined.flat)
        );
        return card;
    }

    private Widget clauseHeader(Component title, RuleSpec spec, int index) {
        WidgetGroup<Widget> row = cardHeader(
            title,
            spec.enabled(),
            compactId(spec.id().toString()),
            () -> toggleClause(index),
            () -> removeClause(index)
        );
        row.addWidget(iconButton(
            iconButtonWidth,
            CirrusLangs.Calculator.Rules.actionDetails,
            expandedClauseIndex == index ? CirrusTextures.Icon.arrowDown : CirrusTextures.Icon.arrowRight,
            expandedClauseIndex == index ? CirrusTextures.Icon.arrowDownSelected : CirrusTextures.Icon.arrowRight,
            () -> toggleClauseDetails(index)
        ));
        return row;
    }

    private WidgetGroup<Widget> cardHeader(Component title, boolean enabled, String id, Runnable toggle, Runnable remove) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        int rowHeight = wrappedTextHeight(title, 22, 2, ruleHeaderHeight);
        row.useStyle(flexRow(), alignItemsFlexStart(), gap(3), minWidth(0), minHeight(rowHeight));
        row.onHoverTooltip((mouseX, mouseY) -> detailTooltip(title, Component.literal(id)));
        CirrusWrappedTextWidget name = wrappedInfoText(title, textPrimary, 2);
        name.useStyle(flexGrow(1), minWidth(0), minHeight(rowHeight));
        row.addWidget(name);
        if (canEditSelected()) {
            row.addWidget(toggleIconButton(enabled, toggle));
            row.addWidget(iconButton(
                iconButtonWidth,
                CirrusLangs.Calculator.Rules.actionRemove,
                CirrusTextures.Icon.delete,
                CirrusTextures.Icon.deleteSelected,
                remove
            ));
        } else {
            CirrusFittedTextWidget state = new CirrusFittedTextWidget(
                enabled ? CirrusLangs.Calculator.Rules.enabled.component() : CirrusLangs.Calculator.Rules.disabled.component(),
                enabled ? textSecondary : textMuted
            );
            state.useStyle(widthOf(24), flexShrink(0), heightOf(ruleHeaderHeight));
            row.addWidget(state);
        }
        return row;
    }

    private Widget compactPair(LangEntry lead, Component value) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        Component leadText = lead.component();
        int rowHeight = Math.max(
            ruleSentenceHeight,
            Math.max(wrappedTextHeight(leadText, 7, 2, ruleSentenceHeight), wrappedTextHeight(value, 18, 3, ruleSentenceHeight))
        );
        row.useStyle(flexRow(), gap(4), alignItemsFlexStart(), minWidth(0), minHeight(rowHeight));
        CirrusWrappedTextWidget leadWidget = wrappedInfoText(leadText, textMuted, 2);
        leadWidget.useStyle(widthOf(32), flexShrink(0), minHeight(rowHeight));
        row.addWidget(leadWidget);
        CirrusWrappedTextWidget valueWidget = wrappedInfoText(value, textSecondary, 3);
        valueWidget.useStyle(flexGrow(1), minWidth(0), minHeight(rowHeight));
        row.addWidget(valueWidget);
        row.onHoverTooltip((mouseX, mouseY) -> detailTooltip(leadText, value));
        return row;
    }

    private CirrusButtonWidget button(int width, LangEntry label, Runnable action) {
        CirrusButtonWidget button = new CirrusButtonWidget(width, actionButtonHeight)
            .label(label.component())
            .onPress(action)
            .tooltip(() -> Tooltip.create().add(label.component()))
            .clickSound(true);
        button.useStyle(flexShrink(0));
        return button;
    }

    private CirrusButtonWidget compactButton(int width, LangEntry label, Runnable action) {
        CirrusButtonWidget button = button(width, label, action)
            .compressLabelToBaseWidth(true);
        button.useStyle(flexShrink(1), minWidth(0), maxWidth(width));
        return button;
    }

    private CirrusButtonWidget panelButton(int width, LangEntry label, Runnable action) {
        CirrusButtonWidget button = button(width, label, action);
        button.useStyle(widthOf(TaffyDimension.percent(1f)), minWidth(width), flexShrink(0));
        return button;
    }

    private CirrusButtonWidget iconButton(
        int width,
        LangEntry tooltip,
        SizedTexture icon,
        SizedTexture hoverIcon,
        Runnable action
    ) {
        return new CirrusButtonWidget(width, actionButtonHeight)
            .icon(icon, hoverIcon)
            .onPress(action)
            .tooltip(() -> Tooltip.create().add(tooltip.component()))
            .clickSound(true);
    }

    private CirrusButtonWidget toggleIconButton(boolean enabled, Runnable action) {
        return new CirrusButtonWidget(iconButtonWidth, actionButtonHeight)
            .iconSupplier(() -> enabled ? CirrusTextures.Icon.pause : CirrusTextures.Icon.play)
            .onPress(action)
            .tooltip(() -> Tooltip.create().add((enabled
                ? CirrusLangs.Calculator.Rules.enabled
                : CirrusLangs.Calculator.Rules.disabled).component()))
            .clickSound(true);
    }

    private CirrusSectionWidget section(LangEntry title) {
        return section(title.component());
    }

    private CirrusSectionWidget section(Component title) {
        CirrusSectionWidget section = new CirrusSectionWidget(title).chrome(CirrusSectionWidget.Chrome.outlinedFlat);
        section.titleWidget().textPadding(2, 1);
        section.body().useStyle(gap(2), minWidth(0), minHeight(0));
        return section;
    }

    private void selectRule(RuleDefinition rule, RuleCollectionKey collection, boolean activate) {
        boolean changed = selectedRule == null || rule == null || !selectedRule.ruleId().equals(rule.ruleId());
        selectedRule = rule;
        selectedCollection = collection == null ? findCollectionKey(rule) : collection;
        if (changed) {
            advancedBuilderOpen = false;
            materialPickerOpen = false;
            pickedMaterials.clear();
            expandedClauseIndex = -1;
        }
        if (activate && rule != null && selectedCollection != null) {
            view = new RuleRepositoryView(
                view.defaultCollection(),
                view.playerCollection(),
                view.worldCollection(),
                view.readableWorldCollections(),
                new dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRuleSelection(selectedCollection, rule.ruleId())
            );
            ActiveRoutingRule.setActiveRule(rule);
            persistView();
        }
    }

    private void createPlayerRule() {
        createPlayerRule(draftNewRuleName);
    }

    private void createPlayerRule(String name) {
        RuleDefinition base = DefaultRuleDefinitions.standardExecution();
        String cleanName = name == null || name.isBlank() ? "New rule" : name.trim();
        draftNewRuleName = "";
        RuleDefinition rule = new RuleDefinition(
            uniqueRuleId(cleanName),
            cleanName,
            base.profile(),
            base.sourcePolicy(),
            base.clauses(),
            true
        );
        addRule(view.playerCollection().key(), rule);
        selectRule(rule, view.playerCollection().key(), true);
        creatingRule = false;
        requestRebuild();
    }

    private void openCreateRuleEditor() {
        creatingRule = true;
        if (draftNewRuleName.isBlank()) {
            draftNewRuleName = "";
        }
        requestRebuild();
    }

    private void closeCreateRuleEditor() {
        creatingRule = false;
        draftNewRuleName = "";
        requestRebuild();
    }

    private void removeSelectedRule() {
        if (!canRemoveSelectedRule()) {
            return;
        }
        RuleCollectionKey removedCollection = selectedCollection;
        Namespace removedId = selectedRule.ruleId();
        updateCollection(removedCollection, rules -> rules.removeIf(rule -> rule.ruleId().equals(removedId)));

        RuleDefinition nextRule = firstSelectableRule();
        RuleCollectionKey nextCollection = findCollectionKey(nextRule);
        selectedRule = null;
        selectedCollection = null;
        creatingRule = false;
        selectRule(nextRule, nextCollection, true);
        requestRebuild();
    }

    private void copySelectedToPlayer() {
        if (selectedRule == null) {
            return;
        }
        String name = selectedRule.name() + " copy";
        RuleDefinition copy = new RuleDefinition(
            uniqueRuleId(name),
            name,
            selectedRule.profile(),
            selectedRule.sourcePolicy(),
            selectedRule.clauses(),
            true
        );
        addRule(view.playerCollection().key(), copy);
        selectRule(copy, view.playerCollection().key(), true);
        requestRebuild();
    }

    private void renameSelectedRule(String name) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        String clean = name == null || name.isBlank() ? selectedRule.name() : name.trim();
        replaceSelected(new RuleDefinition(
            selectedRule.ruleId(),
            clean,
            selectedRule.profile(),
            selectedRule.sourcePolicy(),
            selectedRule.clauses(),
            true
        ));
    }

    private void updateSelectedProfile(java.util.function.UnaryOperator<RuleProfileSpec> updater) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        replaceSelected(new RuleDefinition(
            selectedRule.ruleId(),
            selectedRule.name(),
            updater.apply(selectedRule.profile()),
            selectedRule.sourcePolicy(),
            selectedRule.clauses(),
            true
        ));
    }

    private void openMaterialPicker() {
        if (!canEditSelected()) {
            return;
        }
        materialPickerOpen = true;
        ensureMaterialPickerEntries();
        pickedMaterials.clear();
        materialPickerUndoStack.clear();
        materialPickerScrollY = 0f;
        materialPickerTargetScrollY = 0f;
        materialPickerLastSelectionAnchor = -1;
        if (draftSimpleMaterial != null && !draftSimpleMaterial.isBlank()) {
            pickedMaterials.add(draftSimpleMaterial);
        }
        requestRebuild();
    }

    private void closeMaterialPicker() {
        materialPickerOpen = false;
        pickedMaterials.clear();
        materialPickerUndoStack.clear();
        materialPickerScrollY = 0f;
        materialPickerTargetScrollY = 0f;
        materialPickerLastSelectionAnchor = -1;
        requestRebuild();
    }

    private void ensureMaterialPickerEntries() {
        if (materialPickerEntries.isEmpty()) {
            materialPickerEntries = RuleIngredientCatalog.itemEntries();
        }
    }

    private List<RuleIngredientCatalog.Entry> visibleMaterialEntries() {
        return filteredMaterialEntries();
    }

    private List<RuleIngredientCatalog.Entry> filteredMaterialEntries() {
        ensureMaterialPickerEntries();
        SearchExpression expression = RuleMaterialSearch.parse(materialPickerQuery);
        ArrayList<RuleIngredientCatalog.Entry> filtered = new ArrayList<>();
        for (RuleIngredientCatalog.Entry entry : materialPickerEntries) {
            boolean matches = expression == null ? entry.matches(materialPickerQuery) : entry.matches(expression);
            if (!matches) {
                continue;
            }
            filtered.add(entry);
        }
        return List.copyOf(filtered);
    }

    private void togglePickedMaterial(String material, boolean selected) {
        if (setPickedMaterial(material, selected)) {
            requestRebuild();
        }
    }

    private boolean setPickedMaterial(String material, boolean selected) {
        String clean = material == null ? "" : material.trim();
        if (clean.isBlank()) {
            return false;
        }
        if (selected) {
            return pickedMaterials.add(clean);
        }
        return pickedMaterials.remove(clean);
    }

    private boolean setPickedMaterialRange(List<RuleIngredientCatalog.Entry> entries, int from, int to, boolean selected) {
        if (entries.isEmpty()) {
            return false;
        }
        int start = Math.clamp(Math.min(from, to), 0, entries.size() - 1);
        int end = Math.clamp(Math.max(from, to), 0, entries.size() - 1);
        boolean changed = false;
        for (int i = start; i <= end; i++) {
            changed |= setPickedMaterial(entries.get(i).material(), selected);
        }
        return changed;
    }

    private void selectFilteredMaterials() {
        pushMaterialPickerUndoSnapshot();
        boolean changed = false;
        for (RuleIngredientCatalog.Entry entry : filteredMaterialEntries()) {
            changed |= pickedMaterials.add(entry.material());
        }
        if (!changed) {
            dropLastMaterialPickerUndoSnapshot();
        }
        requestRebuild();
    }

    private void clearPickedMaterials() {
        if (pickedMaterials.isEmpty()) {
            return;
        }
        pushMaterialPickerUndoSnapshot();
        pickedMaterials.clear();
        requestRebuild();
    }

    private void pushMaterialPickerUndoSnapshot() {
        Set<String> snapshot = new LinkedHashSet<>(pickedMaterials);
        if (!materialPickerUndoStack.isEmpty() && materialPickerUndoStack.getLast().equals(snapshot)) {
            return;
        }
        materialPickerUndoStack.add(snapshot);
        if (materialPickerUndoStack.size() > 24) {
            materialPickerUndoStack.removeFirst();
        }
    }

    private void dropLastMaterialPickerUndoSnapshot() {
        if (!materialPickerUndoStack.isEmpty()) {
            materialPickerUndoStack.removeLast();
        }
    }

    private boolean undoMaterialPickerSelection() {
        if (materialPickerUndoStack.isEmpty()) {
            return false;
        }
        Set<String> snapshot = materialPickerUndoStack.removeLast();
        pickedMaterials.clear();
        pickedMaterials.addAll(snapshot);
        requestRebuild();
        return true;
    }

    private void applyPickedMaterials() {
        if (pickedMaterials.isEmpty()) {
            return;
        }
        ArrayList<String> materials = new ArrayList<>(pickedMaterials);
        draftSimpleMaterial = materials.getFirst();
        materialPickerOpen = false;
        pickedMaterials.clear();
        addSimpleRules(materials);
    }

    private void addSourceFilter(String tag, SourcePolicyAction action, boolean override) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        String cleanTag = normalizeTag(tag);
        if (cleanTag.isBlank()) {
            return;
        }
        ArrayList<SourcePolicyRuleSpec> rules = new ArrayList<>();
        selectedRule.sourcePolicy().rules().forEach(rules::add);
        rules.add(new SourcePolicyRuleSpec(
            uniqueChildId("source", cleanTag),
            true,
            TagExpr.has(cleanTag),
            action == null ? SourcePolicyAction.excludeMatch : action,
            override
        ));
        replaceSelected(withSourcePolicy(selectedRule, new SourcePolicy(
            selectedRule.sourcePolicy().policyId(),
            selectedRule.sourcePolicy().version() + 1,
            Lists.immutable.ofAll(rules)
        )));
    }

    private void addSimpleRule() {
        addSimpleRules(List.of(draftSimpleMaterial));
    }

    private void addSimpleRules(List<String> materials) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        List<String> cleanMaterials = normalizeMaterials(materials);
        if (cleanMaterials.isEmpty()) {
            return;
        }
        double strength = Math.abs(parseDouble(draftSimpleStrength, 1.0d));
        RuleSpec clause = simpleClause(cleanMaterials, strength);
        if (clause == null) {
            return;
        }
        replaceSelected(withClauses(selectedRule, mergeMaterialClause(selectedRule.clauses().castToList(), clause)));
    }

    private RuleSpec simpleClause(List<String> materials, double strength) {
        List<String> cleanMaterials = normalizeMaterials(materials);
        if (cleanMaterials.isEmpty()) {
            return null;
        }
        RuleChannel channel = draftSimpleChannel == null ? RuleChannel.any : draftSimpleChannel;
        String idSeed = cleanMaterials.size() == 1 ? cleanMaterials.getFirst() : cleanMaterials.getFirst() + "_" + cleanMaterials.size();
        return switch (draftSimpleKind) {
            case reject -> new RuleSpec(
                uniqueChildId("reject", idSeed),
                "",
                true,
                RuleStage.candidate,
                RuleCondition.Material.ofTokens(channel, cleanMaterials, null, false),
                List.of(new RuleAction.Reject(""))
            );
            case penalty -> new RuleSpec(
                uniqueChildId("score", idSeed),
                "",
                true,
                RuleStage.candidate,
                RuleCondition.Material.ofTokens(channel, cleanMaterials, null, false),
                List.of(RuleAction.ScoreAdjust.penalize(defaultScoreKey, strength))
            );
            case reward -> new RuleSpec(
                uniqueChildId("score", idSeed),
                "",
                true,
                RuleStage.candidate,
                RuleCondition.Material.ofTokens(channel, cleanMaterials, null, false),
                List.of(RuleAction.ScoreAdjust.boost(defaultScoreKey, strength))
            );
            case stockSensitive -> new RuleSpec(
                uniqueChildId("stock", idSeed),
                "",
                true,
                RuleStage.candidate,
                RuleCondition.Material.ofTokens(channel, cleanMaterials, null, false),
                List.of(new RuleAction.ScoreAdjust(defaultScoreKey, strength, RuleAction.ScoreAdjust.ScoreScale.coverageShortage, false))
            );
        };
    }

    private List<RuleSpec> mergeMaterialClause(List<RuleSpec> current, RuleSpec incoming) {
        ArrayList<RuleSpec> clauses = new ArrayList<>(current);
        for (int i = 0; i < clauses.size(); i++) {
            RuleSpec existing = clauses.get(i);
            if (!canMergeMaterialClause(existing, incoming)) {
                continue;
            }
            RuleCondition.Material existingMaterial = (RuleCondition.Material) existing.condition();
            RuleCondition.Material incomingMaterial = (RuleCondition.Material) incoming.condition();
            List<String> mergedMaterials = mergeMaterials(materialTokens(existingMaterial), materialTokens(incomingMaterial));
            if (mergedMaterials.equals(materialTokens(existingMaterial))) {
                return clauses;
            }
            RuleCondition.Material mergedCondition = RuleCondition.Material.ofTokens(
                existingMaterial.channel(),
                mergedMaterials,
                null,
                false
            );
            clauses.set(i, new RuleSpec(
                existing.id(),
                existing.label(),
                existing.enabled(),
                RuleStage.candidate,
                mergedCondition,
                existing.actions()
            ));
            return clauses;
        }
        clauses.add(incoming);
        return clauses;
    }

    private boolean canMergeMaterialClause(RuleSpec existing, RuleSpec incoming) {
        if (!(existing.condition() instanceof RuleCondition.Material existingMaterial)
            || !(incoming.condition() instanceof RuleCondition.Material incomingMaterial)) {
            return false;
        }
        if (!existing.enabled() || !incoming.enabled()) {
            return false;
        }
        if (!existingMaterial.channel().equals(incomingMaterial.channel())) {
            return false;
        }
        if (!existingMaterial.amount().equals(incomingMaterial.amount())) {
            return false;
        }
        if (existingMaterial.allSlots() != incomingMaterial.allSlots()) {
            return false;
        }
        return actionShapesEqual(existing.actions(), incoming.actions());
    }

    private static List<String> normalizeMaterials(List<String> materials) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (materials != null) {
            for (String material : materials) {
                String clean = material == null ? "" : material.trim();
                if (!clean.isBlank()) {
                    out.add(clean);
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<String> mergeMaterials(List<String> left, List<String> right) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (left != null) {
            out.addAll(left);
        }
        if (right != null) {
            out.addAll(right);
        }
        return List.copyOf(out);
    }

    private RuleDefinition withClauses(RuleDefinition base, List<RuleSpec> clauses) {
        return new RuleDefinition(
            base.ruleId(),
            base.name(),
            base.profile(),
            base.sourcePolicy(),
            Lists.immutable.ofAll(clauses),
            true
        );
    }

    private void addRejectRule(String material, RuleChannel channel) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        String cleanMaterial = material == null ? "" : material.trim();
        if (cleanMaterial.isBlank()) {
            return;
        }
        RuleSpec clause = new RuleSpec(
            uniqueChildId("reject", cleanMaterial),
            "",
            true,
            RuleStage.candidate,
            RuleCondition.Material.ofTokens(
                channel == null ? RuleChannel.any : channel,
                List.of(cleanMaterial),
                null,
                false
            ),
            List.of(new RuleAction.Reject(""))
        );
        replaceSelected(withClauses(selectedRule, mergeMaterialClause(selectedRule.clauses().castToList(), clause)));
    }

    private void addScoreRule(String material, RuleChannel channel, String scoreKey, double amount) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        String cleanMaterial = material == null ? "" : material.trim();
        String cleanScore = scoreKey == null || scoreKey.isBlank() ? defaultScoreKey : scoreKey.trim();
        if (cleanMaterial.isBlank()) {
            return;
        }
        RuleAction.ScoreAdjust action = amount < 0
            ? RuleAction.ScoreAdjust.boost(cleanScore, -amount)
            : RuleAction.ScoreAdjust.penalize(cleanScore, amount);
        RuleSpec clause = new RuleSpec(
            uniqueChildId("score", cleanMaterial),
            "",
            true,
            RuleStage.candidate,
            RuleCondition.Material.ofTokens(
                channel == null ? RuleChannel.any : channel,
                List.of(cleanMaterial),
                null,
                false
            ),
            List.of(action)
        );
        replaceSelected(withClauses(selectedRule, mergeMaterialClause(selectedRule.clauses().castToList(), clause)));
    }

    private void addCoverageRule(String material, RuleChannel channel, String scoreKey, double amount) {
        if (!canEditSelected() || selectedRule == null) {
            return;
        }
        String cleanMaterial = material == null ? "" : material.trim();
        String cleanScore = scoreKey == null || scoreKey.isBlank() ? defaultScoreKey : scoreKey.trim();
        if (cleanMaterial.isBlank()) {
            return;
        }
        RuleSpec clause = new RuleSpec(
            uniqueChildId("stock", cleanMaterial),
            "",
            true,
            RuleStage.candidate,
            RuleCondition.Material.ofTokens(
                channel == null ? RuleChannel.any : channel,
                List.of(cleanMaterial),
                null,
                false
            ),
            List.of(new RuleAction.ScoreAdjust(cleanScore, amount, RuleAction.ScoreAdjust.ScoreScale.coverageShortage, false))
        );
        replaceSelected(withClauses(selectedRule, mergeMaterialClause(selectedRule.clauses().castToList(), clause)));
    }

    private void toggleSourcePolicy(int index) {
        if (!canEditSelected() || selectedRule == null || index < 0 || index >= selectedRule.sourcePolicy().rules().size()) {
            return;
        }
        ArrayList<SourcePolicyRuleSpec> rules = new ArrayList<>();
        selectedRule.sourcePolicy().rules().forEach(rules::add);
        SourcePolicyRuleSpec spec = rules.get(index);
        rules.set(index, new SourcePolicyRuleSpec(spec.ruleId(), !spec.enabled(), spec.when(), spec.action(), spec.override()));
        replaceSelected(withSourcePolicy(selectedRule, new SourcePolicy(
            selectedRule.sourcePolicy().policyId(),
            selectedRule.sourcePolicy().version() + 1,
            Lists.immutable.ofAll(rules)
        )));
    }

    private void removeSourcePolicy(int index) {
        if (!canEditSelected() || selectedRule == null || index < 0 || index >= selectedRule.sourcePolicy().rules().size()) {
            return;
        }
        ArrayList<SourcePolicyRuleSpec> rules = new ArrayList<>();
        selectedRule.sourcePolicy().rules().forEach(rules::add);
        rules.remove(index);
        replaceSelected(withSourcePolicy(selectedRule, new SourcePolicy(
            selectedRule.sourcePolicy().policyId(),
            selectedRule.sourcePolicy().version() + 1,
            Lists.immutable.ofAll(rules)
        )));
    }

    private void toggleClause(int index) {
        if (!canEditSelected() || selectedRule == null || index < 0 || index >= selectedRule.clauses().size()) {
            return;
        }
        ArrayList<RuleSpec> clauses = new ArrayList<>();
        selectedRule.clauses().forEach(clauses::add);
        RuleSpec spec = clauses.get(index);
        clauses.set(index, new RuleSpec(spec.id(), spec.label(), !spec.enabled(), spec.stage(), spec.condition(), spec.actions()));
        replaceSelected(new RuleDefinition(selectedRule.ruleId(), selectedRule.name(), selectedRule.profile(), selectedRule.sourcePolicy(), Lists.immutable.ofAll(clauses), true));
    }

    private void toggleClauseDetails(int index) {
        if (selectedRule == null || index < 0 || index >= selectedRule.clauses().size()) {
            expandedClauseIndex = -1;
            return;
        }
        expandedClauseIndex = expandedClauseIndex == index ? -1 : index;
        requestInspectorRebuild();
    }

    private void removeClause(int index) {
        if (!canEditSelected() || selectedRule == null || index < 0 || index >= selectedRule.clauses().size()) {
            return;
        }
        ArrayList<RuleSpec> clauses = new ArrayList<>();
        selectedRule.clauses().forEach(clauses::add);
        clauses.remove(index);
        if (expandedClauseIndex == index) {
            expandedClauseIndex = -1;
        } else if (expandedClauseIndex > index) {
            expandedClauseIndex--;
        }
        replaceSelected(new RuleDefinition(selectedRule.ruleId(), selectedRule.name(), selectedRule.profile(), selectedRule.sourcePolicy(), Lists.immutable.ofAll(clauses), true));
    }

    private void addRule(RuleCollectionKey key, RuleDefinition rule) {
        updateCollection(key, rules -> rules.add(rule));
        persistView();
    }

    private void replaceSelected(RuleDefinition next) {
        if (selectedCollection == null) {
            return;
        }
        updateCollection(selectedCollection, rules -> {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).ruleId().equals(selectedRule.ruleId())) {
                    rules.set(i, next);
                    return;
                }
            }
        });
        selectedRule = next;
        ActiveRoutingRule.setActiveRule(next);
        view = new RuleRepositoryView(
            view.defaultCollection(),
            view.playerCollection(),
            view.worldCollection(),
            view.readableWorldCollections(),
            new dev.vfyjxf.cirrus.api.recipe.routing.rule.ActiveRuleSelection(selectedCollection, next.ruleId())
        );
        persistView();
        requestRebuild();
    }

    private void updateCollection(RuleCollectionKey key, Consumer<ArrayList<RuleDefinition>> updater) {
        ArrayList<RuleDefinition> rules = new ArrayList<>(collectionByKey(key).rules());
        updater.accept(rules);
        RuleCollection updated = new RuleCollection(key, rules);
        if (key.equals(view.playerCollection().key())) {
            view = new RuleRepositoryView(view.defaultCollection(), updated, view.worldCollection(), view.readableWorldCollections(), view.activeSelection());
        } else if (key.equals(view.worldCollection().key())) {
            view = new RuleRepositoryView(view.defaultCollection(), view.playerCollection(), updated, view.readableWorldCollections(), view.activeSelection());
        }
    }

    private RuleCollection collectionByKey(RuleCollectionKey key) {
        if (key.equals(view.playerCollection().key())) {
            return view.playerCollection();
        }
        if (key.equals(view.worldCollection().key())) {
            return view.worldCollection();
        }
        return view.defaultCollection();
    }

    private boolean canEditSelected() {
        return selectedRule != null && selectedRule.editable() && selectedCollection != null && isWritable(selectedCollection);
    }

    private boolean canRemoveSelectedRule() {
        return canEditSelected() && collectionByKey(selectedCollection).rules().size() > 0;
    }

    private boolean isWritable(RuleCollectionKey key) {
        return key.equals(view.playerCollection().key()) || key.equals(view.worldCollection().key());
    }

    private RuleDefinition firstSelectableRule() {
        RuleDefinition active = view.activeRuleOrDefault();
        if (findCollectionKey(active) != null) {
            return active;
        }
        for (RuleCollection collection : view.visibleCollections()) {
            if (!collection.rules().isEmpty()) {
                return collection.rules().getFirst();
            }
        }
        return DefaultRuleDefinitions.standardExecution();
    }

    private RuleCollectionKey findCollectionKey(RuleDefinition rule) {
        if (rule == null) {
            return null;
        }
        for (RuleCollection collection : view.visibleCollections()) {
            for (RuleDefinition candidate : collection.rules()) {
                if (candidate.ruleId().equals(rule.ruleId())) {
                    return collection.key();
                }
            }
        }
        return null;
    }

    private String selectedCollectionLabel() {
        if (selectedCollection == null) {
            return "";
        }
        return selectedCollection.scope().name().toLowerCase(Locale.ROOT) + "/" + selectedCollection.id();
    }

    private RuleDefinition withSourcePolicy(RuleDefinition rule, SourcePolicy sourcePolicy) {
        return new RuleDefinition(rule.ruleId(), rule.name(), rule.profile(), sourcePolicy, rule.clauses(), true);
    }

    private RuleProfileSpec profileWithPreset(RuleProfileSpec profile, RuleProfilePreset preset) {
        RuleProfileSpec base = RuleProfileSpec.fromPreset(profile.profileId(), preset == null ? profile.preset() : preset, profile.unresolvedInputMode());
        return new RuleProfileSpec(
            profile.profileId(),
            base.preset(),
            profile.unresolvedInputMode(),
            base.shortlistK(),
            base.rollbackWidth(),
            base.nearTieEpsilon(),
            base.amplificationSoftThreshold(),
            base.amplificationHardThreshold(),
            base.liftMultiSemanticChildShortages()
        );
    }

    private RuleProfileSpec profileWithUnresolved(RuleProfileSpec profile, UnresolvedInputMode mode) {
        return new RuleProfileSpec(
            profile.profileId(),
            profile.preset(),
            mode == null ? profile.unresolvedInputMode() : mode,
            profile.shortlistK(),
            profile.rollbackWidth(),
            profile.nearTieEpsilon(),
            profile.amplificationSoftThreshold(),
            profile.amplificationHardThreshold(),
            profile.liftMultiSemanticChildShortages()
        );
    }

    private RuleProfileSpec profileWithShortlist(RuleProfileSpec profile, int shortlist) {
        return new RuleProfileSpec(
            profile.profileId(),
            profile.preset(),
            profile.unresolvedInputMode(),
            Math.max(1, shortlist),
            profile.rollbackWidth(),
            profile.nearTieEpsilon(),
            profile.amplificationSoftThreshold(),
            profile.amplificationHardThreshold(),
            profile.liftMultiSemanticChildShortages()
        );
    }

    private RuleProfileSpec profileWithRollback(RuleProfileSpec profile, int rollback) {
        return new RuleProfileSpec(
            profile.profileId(),
            profile.preset(),
            profile.unresolvedInputMode(),
            profile.shortlistK(),
            Math.max(0, rollback),
            profile.nearTieEpsilon(),
            profile.amplificationSoftThreshold(),
            profile.amplificationHardThreshold(),
            profile.liftMultiSemanticChildShortages()
        );
    }

    private Namespace uniqueRuleId(String name) {
        String base = "routing/rule/player/" + slug(name);
        String candidate = base;
        int index = 2;
        while (ruleExists(Namespace.of("cirrus", candidate))) {
            candidate = base + "_" + index++;
        }
        return Namespace.of("cirrus", candidate);
    }

    private Namespace uniqueChildId(String prefix, String value) {
        String base = selectedRule == null ? prefix : compactId(selectedRule.ruleId().toString()) + "/" + prefix + "/" + slug(value);
        String candidate = base;
        int index = 2;
        while (ruleExists(Namespace.of("cirrus", candidate))) {
            candidate = base + "_" + index++;
        }
        return Namespace.of("cirrus", candidate);
    }

    private boolean ruleExists(Namespace id) {
        for (RuleCollection collection : view.visibleCollections()) {
            for (RuleDefinition rule : collection.rules()) {
                if (rule.ruleId().equals(id)) {
                    return true;
                }
                for (SourcePolicyRuleSpec spec : rule.sourcePolicy().rules()) {
                    if (spec.ruleId().equals(id)) {
                        return true;
                    }
                }
                for (RuleSpec spec : rule.clauses()) {
                    if (spec.id().equals(id)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void persistView() {
        if (!persistenceEnabled) {
            return;
        }
        try {
            FileRuleRepository repository = new FileRuleRepository(defaultRepositoryRoot());
            repository.saveCollection(view.playerCollection());
            repository.saveCollection(view.worldCollection());
            repository.saveActiveSelection(currentWorldId(), view.activeSelection());
        } catch (Exception ignored) {
            // The editor remains usable in-memory if the file backend is unavailable.
        }
    }

    private Component tagExpressionLabel(TagExpr expression) {
        return Component.literal(switch (expression) {
            case TagExpr.Has has -> "#" + has.tag();
            case TagExpr.AllOf all -> "all " + all.expressions().size();
            case TagExpr.AnyOf any -> "any " + any.expressions().size();
            case TagExpr.NoneOf none -> "none " + none.expressions().size();
            case TagExpr.Not ignored -> "not";
        });
    }

    private Component conditionLabel(RuleCondition condition) {
        if (condition instanceof RuleCondition.Material material) {
            List<String> values = materialTokens(material);
            return switch (material.channel()) {
                case input -> CirrusLangs.Calculator.Rules.conditionInputMaterial.component(materialSummary(values));
                case output -> CirrusLangs.Calculator.Rules.conditionOutputMaterial.component(materialSummary(values));
                case any -> CirrusLangs.Calculator.Rules.conditionAnyMaterial.component(materialSummary(values));
            };
        }
        return CirrusLangs.Calculator.Rules.conditionAlways.component();
    }

    private Component clauseTitle(RuleSpec spec) {
        if (!spec.label().isBlank()) {
            return Component.literal(spec.label());
        }
        if (spec.actions().stream().anyMatch(a -> a instanceof RuleAction.Reject)) {
            return CirrusLangs.Calculator.Rules.simpleReject.component();
        }
        return scoreOf(spec.actions())
            .map(adjust -> {
                if (adjust.scale() == RuleAction.ScoreAdjust.ScoreScale.coverageShortage) {
                    return CirrusLangs.Calculator.Rules.simpleStock.component();
                }
                return adjust.boost()
                    ? CirrusLangs.Calculator.Rules.simpleReward.component()
                    : CirrusLangs.Calculator.Rules.simplePenalty.component();
            })
            .orElse(CirrusLangs.Calculator.Rules.simplePenalty.component());
    }

    private Component effectLabel(List<RuleAction> actions) {
        if (rejectOf(actions).isPresent()) {
            return CirrusLangs.Calculator.Rules.effectReject.component();
        }
        return scoreOf(actions)
            .map(adjust -> {
                String decimals = compactDecimal(Math.abs(adjust.amount()));
                if (adjust.scale() == RuleAction.ScoreAdjust.ScoreScale.coverageShortage) {
                    return CirrusLangs.Calculator.Rules.effectStock.component(decimals);
                }
                return adjust.boost()
                    ? CirrusLangs.Calculator.Rules.effectReward.component(decimals)
                    : CirrusLangs.Calculator.Rules.effectPenalty.component(decimals);
            })
            .orElse(Component.literal(""));
    }

    private static String effectAmountText(List<RuleAction> actions) {
        if (rejectOf(actions).isPresent()) {
            return "";
        }
        return scoreOf(actions)
            .map(adjust -> {
                String decimals = compactDecimal(Math.abs(adjust.amount()));
                if (adjust.scale() == RuleAction.ScoreAdjust.ScoreScale.coverageShortage) {
                    return decimals;
                }
                return adjust.boost() ? "+" + decimals : "-" + decimals;
            })
            .orElse("");
    }

    private static String channelShortLabel(RuleChannel channel) {
        return switch (channel) {
            case any -> "*";
            case input -> "in";
            case output -> "out";
        };
    }

    private static String hiddenMaterialLabel(int hidden) {
        return hidden <= 99 ? String.valueOf(Math.max(0, hidden)) : "...";
    }

    private Tooltip clauseTooltip(RuleSpec spec) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(clauseTitle(spec));
        tooltip.add(conditionLabel(spec.condition()));
        tooltip.add(effectLabel(spec.actions()));
        if (spec.condition() instanceof RuleCondition.Material m) {
            tooltip.add(CirrusLangs.Calculator.Rules.materialSelected.component(materialTokens(m).size()));
        }
        tooltip.add(Component.literal(spec.id().toString()));
        return tooltip;
    }

    private Component sourceActionLabel(SourcePolicyRuleSpec spec) {
        String value = switch (spec.action()) {
            case excludeMatch -> "exclude";
            case includeMatch -> spec.override() ? "include+" : "include";
            case allowOnlyMatch -> "only";
        };
        return Component.literal(value);
    }

    private static String state(boolean value) {
        return value
            ? CirrusLangs.Calculator.Rules.enabled.string()
            : CirrusLangs.Calculator.Rules.disabled.string();
    }

    private static String normalizeTag(String value) {
        if (value == null) {
            return "";
        }
        String tag = value.trim();
        if (tag.startsWith("#")) {
            tag = tag.substring(1);
        }
        if (tag.startsWith("tag:")) {
            tag = tag.substring("tag:".length());
        }
        return tag.trim();
    }

    private static String slug(String value) {
        String slug = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/.-]+", "_");
        slug = slug.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "rule" : slug;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String signed(double value) {
        return value >= 0 ? "+" + compactDecimal(value) : compactDecimal(value);
    }

    private static String compactDecimal(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        String decimal = String.format(Locale.ROOT, "%.3f", value)
            .replaceFirst("\\.?0+$", "");
        if (decimal.equals("-0") || decimal.isBlank()) {
            return "0";
        }
        if (decimal.length() <= 6) {
            return decimal;
        }
        return String.format(Locale.ROOT, "%.2g", value);
    }

    private static String compactEnumName(String value) {
        String words = value
            .replace('_', ' ')
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .toLowerCase(Locale.ROOT)
            .strip();
        if (words.length() <= 14) {
            return words;
        }
        String[] parts = words.split("\\s+");
        if (parts.length <= 2) {
            return words;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isBlank()) {
                out.append(Character.toUpperCase(parts[i].charAt(0))).append(' ');
            }
        }
        out.append(parts[parts.length - 1]);
        return out.toString();
    }

    private static String compactRuleListMeta(RuleDefinition rule) {
        String preset = switch (rule.profile().preset()) {
            case standardDefault -> "def";
            case standardExecution -> "exec";
            case assuredBoundedDefault -> "bound";
            case assuredProvenCompat -> "prove";
        };
        return preset + " " + rule.sourcePolicy().rules().size() + "+" + rule.clauses().size();
    }

    private static String compactId(String value) {
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < value.length()) {
            return value.substring(slash + 1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            return value.substring(colon + 1);
        }
        return value;
    }

    private static String ruleDisplayName(RuleDefinition rule) {
        if (rule == null) {
            return "";
        }
        String name = rule.name() == null ? "" : rule.name().trim();
        return name.isBlank() ? compactId(rule.ruleId().toString()) : name;
    }

    private Widget materialPreviewSlot(String material) {
        Widget preview = materialPreviewSlot(itemStackForMaterial(material));
        String label = materialDisplayLabel(material);
        String id = material == null ? "" : material.trim();
        preview.onHoverTooltip((mouseX, mouseY) -> detailTooltip(Component.literal(label), Component.literal(id)));
        return preview;
    }

    private Widget materialPreviewSlot(ItemStack stack) {
        return new MaterialIconWidget(stack);
    }

    private String materialDisplayLabel(String material) {
        String clean = material == null ? "" : material.trim();
        if (clean.isBlank()) {
            return "";
        }
        ensureMaterialPickerEntries();
        for (RuleIngredientCatalog.Entry entry : materialPickerEntries) {
            if (entry.material().equals(clean)) {
                return entry.label();
            }
        }
        ItemStack stack = itemStackForMaterial(clean);
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        return compactId(clean);
    }

    private String materialSummary(List<String> materials) {
        if (materials == null || materials.isEmpty()) {
            return "";
        }
        String first = materialDisplayLabel(materials.getFirst());
        if (materials.size() == 1) {
            return first;
        }
        return first + " +" + (materials.size() - 1);
    }

    /** Reconstructs the display tokens for a material condition (ids raw, tags prefixed with {@code #}). */
    private static List<String> materialTokens(RuleCondition.Material condition) {
        List<String> tokens = new ArrayList<>(condition.ids());
        for (String tag : condition.tags()) {
            tokens.add("#" + tag);
        }
        return List.copyOf(tokens);
    }

    private static java.util.Optional<RuleAction.Reject> rejectOf(List<RuleAction> actions) {
        return actions.stream()
            .filter(a -> a instanceof RuleAction.Reject)
            .map(a -> (RuleAction.Reject) a)
            .findFirst();
    }

    private static java.util.Optional<RuleAction.ScoreAdjust> scoreOf(List<RuleAction> actions) {
        return actions.stream()
            .filter(a -> a instanceof RuleAction.ScoreAdjust)
            .map(a -> (RuleAction.ScoreAdjust) a)
            .findFirst();
    }

    /** True when the two action lists share the same shape (both Reject or both ScoreAdjust with matching shape). */
    private static boolean actionShapesEqual(List<RuleAction> left, List<RuleAction> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            RuleAction a = left.get(i);
            RuleAction b = right.get(i);
            if (a instanceof RuleAction.Reject && b instanceof RuleAction.Reject) {
                continue;
            }
            if (a instanceof RuleAction.ScoreAdjust sa && b instanceof RuleAction.ScoreAdjust sb) {
                if (!sa.component().equals(sb.component())
                    || sa.scale() != sb.scale()
                    || sa.boost() != sb.boost()) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    private static CirrusWrappedTextWidget wrappedInfoText(Component text, int color, int maxLines) {
        return new CirrusWrappedTextWidget(text, color)
            .padding(infoTextPadding)
            .lineGap(infoTextLineGap)
            .maxLines(maxLines);
    }

    private static int wrappedTextHeight(Component text, int charsPerLine, int maxLines, int minHeight) {
        int lines = estimatedWrappedLines(text == null ? "" : text.getString(), charsPerLine, maxLines);
        int textHeight = infoTextPadding * 2
            + lines * infoTextLineHeight
            + Math.max(0, lines - 1) * infoTextLineGap;
        return Math.max(minHeight, textHeight);
    }

    private static int estimatedWrappedLines(String value, int charsPerLine, int maxLines) {
        int width = Math.max(1, charsPerLine);
        int cap = Math.max(1, maxLines);
        if (value == null || value.isBlank()) {
            return 1;
        }
        int lines = 0;
        String[] paragraphs = value.split("\\R", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                lines++;
            } else {
                lines += Math.max(1, (paragraph.length() + width - 1) / width);
            }
            if (lines >= cap) {
                return cap;
            }
        }
        return Math.clamp(lines, 1, cap);
    }

    private static ItemStack itemStackForMaterial(String material) {
        String clean = material == null ? "" : material.trim();
        if (clean.startsWith("item:")) {
            clean = clean.substring("item:".length());
        }
        if (clean.isBlank() || clean.startsWith("#") || clean.startsWith("tag:") || clean.startsWith("fluid:")) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(clean);
            return BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static Tooltip detailTooltip(Component key, Component value) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(key);
        tooltip.add(value);
        return tooltip;
    }

    private static int materialPickerGridStride() {
        return materialPickerGridCellSize + materialPickerGridGap;
    }

    private static int materialPickerGridColumnsForWidth(int width) {
        int usable = Math.max(1, width - materialPickerGridPadding * 2);
        return Math.max(1, (usable + materialPickerGridGap) / materialPickerGridStride());
    }

    private static int materialPickerGridRows(int count, int columns) {
        if (count <= 0) {
            return 0;
        }
        return (count + Math.max(1, columns) - 1) / Math.max(1, columns);
    }

    private static int materialPickerGridContentHeight(int count, int columns) {
        int rows = materialPickerGridRows(count, columns);
        if (rows <= 0) {
            return materialPickerGridPadding * 2;
        }
        return materialPickerGridPadding * 2
            + rows * materialPickerGridCellSize
            + Math.max(0, rows - 1) * materialPickerGridGap;
    }

    private static RuleRepositoryView defaultView() {
        RuleCollection defaults = DefaultRuleDefinitions.defaultCollection();
        RuleCollection player = new RuleCollection(new RuleCollectionKey(RuleCollectionScope.player, "player"), List.of());
        RuleCollection world = new RuleCollection(new RuleCollectionKey(RuleCollectionScope.world, "current"), List.of());
        RuleRepositoryView view = new RuleRepositoryView(
            defaults,
            player,
            world,
            List.of(),
            DefaultRuleDefinitions.defaultActiveSelection()
        );
        ActiveRoutingRule.applyView(view);
        return view;
    }

    private static RuleRepositoryView loadConfiguredView() {
        try {
            FileRuleRepository repository = new FileRuleRepository(defaultRepositoryRoot());
            RuleRepositoryView view = repository.loadView(currentWorldId(), ExecutionOverlayClientConfig.calculatorRulesReadOtherWorlds());
            ActiveRoutingRule.applyView(view);
            return view;
        } catch (RuntimeException ignored) {
            return defaultView();
        }
    }

    private static Path defaultRepositoryRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("cirrus").resolve("rules");
    }

    private static String currentWorldId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "global";
        }
        return minecraft.level.dimension().location().toString().replace(':', '_').replace('/', '_');
    }

    private final class MaterialPickerGridWidget extends Widget {
        private final List<RuleIngredientCatalog.Entry> entries;
        private float scrollY;
        private float targetScrollY;
        private int dragButton = -1;
        private int dragAnchor = -1;
        private int lastDragIndex = -1;
        private boolean dragSelectionState;
        private boolean selectionChanged;
        private boolean scrollbarDragging;
        private boolean undoSnapshotPushed;
        private final ScrollState wheelScrollState = CirrusScroll.longVerticalState(materialPickerGridScrollSpeed);

        private MaterialPickerGridWidget(List<RuleIngredientCatalog.Entry> entries) {
            this.entries = List.copyOf(entries);
            this.scrollY = materialPickerScrollY;
            this.targetScrollY = materialPickerTargetScrollY;
            setFocusable(true);
            onKeyPressed((input, context) -> {
                if (input.isCtrlDown() && input.key().getValue() == GLFW.GLFW_KEY_Z) {
                    return undoMaterialPickerSelection() ? EventDispatch.consumed : EventDispatch.pass;
                }
                return EventDispatch.pass;
            });
            onMouseScrolled((mouseX, mouseY, scrollX, wheelY, context) -> {
                GridMetrics metrics = metrics();
                if (metrics.maxScrollY() <= 0f || wheelY == 0) {
                    return EventDispatch.pass;
                }
                scrollBy(wheelScrollState.wheelDelta(0, wheelY).y(), metrics);
                return EventDispatch.consumed;
            });
            onMouseClicked((input, context) -> {
                var local = input.mouseRelative(this);
                GridMetrics metrics = metrics();
                if (input.isLeftClick() && metrics.hasScrollbar() && isOverScrollbar(local.x(), local.y(), metrics)) {
                    scrollbarDragging = true;
                    scrollToScrollbarMouse(local.y(), metrics);
                    return EventDispatch.consumed;
                }
                scrollbarDragging = false;
                if (!input.isLeftClick() && !input.isRightClick()) {
                    return EventDispatch.pass;
                }
                int index = indexAt(local.x(), local.y(), metrics);
                if (index < 0) {
                    return EventDispatch.pass;
                }
                beginSelectionGesture(index, input.isRightClick(), input.isShiftDown());
                return EventDispatch.consumed;
            });
            onMouseDragged((input, deltaX, deltaY, context) -> {
                var local = input.mouseRelative(this);
                GridMetrics metrics = metrics();
                if (scrollbarDragging) {
                    if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
                        scrollbarDragging = false;
                        return EventDispatch.pass;
                    }
                    scrollToScrollbarMouse(local.y(), metrics);
                    return EventDispatch.consumed;
                }
                if (dragButton < 0 || (dragButton == 0 && !input.isLeftClick()) || (dragButton == 1 && !input.isRightClick())) {
                    return EventDispatch.pass;
                }
                int index = indexAt(local.x(), local.y(), metrics);
                if (index < 0 || index == lastDragIndex) {
                    return EventDispatch.consumed;
                }
                applyDraggedSelection(index, input.isShiftDown());
                return EventDispatch.consumed;
            });
            onMouseReleased((input, context) -> {
                if (scrollbarDragging) {
                    scrollbarDragging = false;
                    return EventDispatch.consumed;
                }
                if (dragButton >= 0) {
                    dragButton = -1;
                    dragAnchor = -1;
                    lastDragIndex = -1;
                    if (selectionChanged) {
                        selectionChanged = false;
                        requestRebuild();
                    } else if (undoSnapshotPushed) {
                        dropLastMaterialPickerUndoSnapshot();
                    }
                    undoSnapshotPushed = false;
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
            onMouseLeave((mouseX, mouseY, context) -> {
                scrollbarDragging = false;
                if (dragButton >= 0) {
                    dragButton = -1;
                    dragAnchor = -1;
                    lastDragIndex = -1;
                    if (selectionChanged) {
                        selectionChanged = false;
                        requestRebuild();
                    } else if (undoSnapshotPushed) {
                        dropLastMaterialPickerUndoSnapshot();
                    }
                    undoSnapshotPushed = false;
                }
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            super.renderInternal(canvas, mouseX, mouseY, partialTicks);
            if (width() <= 0 || height() <= 0) {
                return;
            }
            GridMetrics metrics = metrics();
            targetScrollY = Math.clamp(targetScrollY, 0f, metrics.maxScrollY());
            scrollY = Math.clamp(scrollY, 0f, metrics.maxScrollY());
            if (Math.abs(targetScrollY - scrollY) < 0.5f) {
                scrollY = targetScrollY;
            } else {
                scrollY += (targetScrollY - scrollY) * materialPickerGridScrollSmooth;
            }
            rememberScroll();
            canvas.texture(CirrusTextures.Background.inset, 0, 0, width(), height());
            if (entries.isEmpty()) {
                return;
            }

            var origin = localToScene(materialPickerGridViewportInset, materialPickerGridViewportInset);
            canvas.pushClip(
                (int) origin.x,
                (int) origin.y,
                metrics.viewportWidth(),
                metrics.viewportHeight()
            );
            renderVisibleCells(canvas, metrics, mouseX, mouseY, partialTicks);
            canvas.popClip();

            renderScrollbar(canvas, metrics);
        }

        @Override
        public Tooltip hoverTooltip(int mouseX, int mouseY) {
            GridMetrics metrics = metrics();
            int index = indexAt(mouseX, mouseY, metrics);
            if (index < 0) {
                return super.hoverTooltip(mouseX, mouseY);
            }
            RuleIngredientCatalog.Entry entry = entries.get(index);
            Tooltip tooltip = Tooltip.create();
            tooltip.add(Component.literal(entry.label()));
            tooltip.add(Component.literal(entry.material()));
            tooltip.add(pickedMaterials.contains(entry.material())
                ? CirrusLangs.Calculator.Rules.enabled.component()
                : CirrusLangs.Calculator.Rules.disabled.component());
            return tooltip;
        }

        int entryCount() {
            return entries.size();
        }

        private void renderVisibleCells(SceneCanvas canvas, GridMetrics metrics, int mouseX, int mouseY, float partialTicks) {
            int firstRow = firstVisibleRow(metrics);
            int lastRow = lastVisibleRow(metrics);
            if (lastRow < firstRow) {
                return;
            }
            int hoverIndex = indexAt(mouseX, mouseY, metrics);
            ArrayList<BatchableStackRenderer.Entry<Item, ItemStack>> itemBatch = new ArrayList<>();
            ArrayList<CellOverlay> overlays = new ArrayList<>();
            for (int row = firstRow; row <= lastRow; row++) {
                for (int column = 0; column < metrics.columns(); column++) {
                    int index = row * metrics.columns() + column;
                    if (index >= entries.size()) {
                        break;
                    }
                    int x = cellX(column);
                    int y = Math.round(cellY(row) - scrollY);
                    RuleIngredientCatalog.Entry entry = entries.get(index);
                    boolean selected = pickedMaterials.contains(entry.material());
                    boolean hovered = index == hoverIndex;
                    if (selected || hovered) {
                        CirrusControlChrome.renderButtonBackground(canvas, x, y, materialPickerGridCellSize, materialPickerGridCellSize, selected, hovered, true);
                    }
                    int slotX = x + 2;
                    int slotY = y + 2;
                    canvas.texture(CirrusTextures.Background.Slot.dark, slotX, slotY, materialIconSize, materialIconSize);
                    ContentStack<Item, ItemStack> stack = ContentStack.fromStack(entry.stack(), 1);
                    if (!stack.isEmpty()) {
                        itemBatch.add(new BatchableStackRenderer.Entry<>(stack, slotX + 1, slotY + 1, 16, 16));
                    }
                    if (selected || hovered) {
                        overlays.add(new CellOverlay(x, y, selected, hovered));
                    }
                }
            }
            if (!itemBatch.isEmpty()) {
                canvas.flushBatch();
                ItemStackRenderer.instance.renderBatch(canvas.layeredGraphics(), itemBatch, partialTicks);
                canvas.flushBatch();
            }
            for (CellOverlay overlay : overlays) {
                renderCellOverlay(canvas, overlay);
            }
        }

        private void renderCellOverlay(SceneCanvas canvas, CellOverlay overlay) {
            if (overlay.selected()) {
                var icon = overlay.hovered()
                    ? CirrusTextures.Toggle.Checked.Small.selectHover
                    : CirrusTextures.Toggle.Checked.Small.select;
                canvas.texture(
                    icon,
                    overlay.x() + materialPickerGridCellSize - icon.width() - 1,
                    overlay.y() + materialPickerGridCellSize - icon.height() - 1,
                    icon.width(),
                    icon.height()
                );
            } else if (overlay.hovered()) {
                var icon = CirrusTextures.Toggle.Checked.Small.unselectHover;
                canvas.texture(
                    icon,
                    overlay.x() + materialPickerGridCellSize - icon.width() - 1,
                    overlay.y() + materialPickerGridCellSize - icon.height() - 1,
                    icon.width(),
                    icon.height()
                );
            }
        }

        private void renderScrollbar(SceneCanvas canvas, GridMetrics metrics) {
            if (!metrics.hasScrollbar()) {
                return;
            }
            int x = scrollbarX(metrics);
            int y = materialPickerGridViewportInset;
            int trackHeight = metrics.viewportHeight();
            canvas.texture(CirrusTextures.Background.scroll, x, y, materialPickerGridScrollbarWidth, trackHeight);
            int thumbHeight = thumbHeight(metrics);
            int maxThumbY = Math.max(0, trackHeight - thumbHeight);
            int thumbY = y + (maxThumbY <= 0 ? 0 : (int) (scrollY / metrics.maxScrollY() * maxThumbY));
            canvas.texture(CirrusTextures.Icon.scrollBarVertical, x, thumbY, materialPickerGridScrollbarWidth, thumbHeight);
        }

        private void beginSelectionGesture(int index, boolean rightClick, boolean shiftDown) {
            dragButton = rightClick ? 1 : 0;
            dragAnchor = shiftDown && materialPickerLastSelectionAnchor >= 0 ? materialPickerLastSelectionAnchor : index;
            lastDragIndex = index;
            selectionChanged = false;
            undoSnapshotPushed = true;
            pushMaterialPickerUndoSnapshot();
            if (shiftDown && !rightClick && materialPickerLastSelectionAnchor >= 0) {
                selectionChanged |= setPickedMaterialRange(entries, dragAnchor, index, true);
            } else {
                dragSelectionState = rightClick || !pickedMaterials.contains(entries.get(index).material());
                if (rightClick) {
                    dragSelectionState = false;
                }
                selectionChanged |= setPickedMaterial(entries.get(index).material(), dragSelectionState);
            }
            materialPickerLastSelectionAnchor = index;
            CirrusUiSound.playClickSound();
        }

        private void applyDraggedSelection(int index, boolean shiftDown) {
            if (shiftDown && dragAnchor >= 0 && dragButton == 0) {
                selectionChanged |= setPickedMaterialRange(entries, dragAnchor, index, true);
            } else {
                selectionChanged |= setPickedMaterial(entries.get(index).material(), dragSelectionState);
            }
            lastDragIndex = index;
        }

        private void scrollBy(float amount, GridMetrics metrics) {
            targetScrollY = Math.clamp(targetScrollY + amount, 0f, metrics.maxScrollY());
            rememberScroll();
        }

        private void scrollToScrollbarMouse(double localY, GridMetrics metrics) {
            if (metrics.maxScrollY() <= 0f) {
                scrollY = 0f;
                targetScrollY = 0f;
                rememberScroll();
                return;
            }
            int thumbHeight = thumbHeight(metrics);
            int track = Math.max(1, metrics.viewportHeight() - thumbHeight);
            float progress = (float) ((localY - materialPickerGridViewportInset - thumbHeight * 0.5f) / track);
            scrollY = Math.clamp(progress, 0f, 1f) * metrics.maxScrollY();
            targetScrollY = scrollY;
            rememberScroll();
        }

        private void rememberScroll() {
            materialPickerScrollY = scrollY;
            materialPickerTargetScrollY = targetScrollY;
        }

        private int thumbHeight(GridMetrics metrics) {
            if (!metrics.hasScrollbar() || metrics.contentHeight() <= 0) {
                return metrics.viewportHeight();
            }
            return Math.min(
                metrics.viewportHeight(),
                Math.max(materialPickerGridMinThumbHeight, (int) ((float) metrics.viewportHeight() / metrics.contentHeight() * metrics.viewportHeight()))
            );
        }

        private boolean isOverScrollbar(double localX, double localY, GridMetrics metrics) {
            return metrics.hasScrollbar()
                && localX >= scrollbarX(metrics)
                && localX < scrollbarX(metrics) + materialPickerGridScrollbarWidth
                && localY >= materialPickerGridViewportInset
                && localY < materialPickerGridViewportInset + metrics.viewportHeight();
        }

        private int indexAt(double localX, double localY, GridMetrics metrics) {
            if (localX < materialPickerGridViewportInset
                || localX >= materialPickerGridViewportInset + metrics.viewportWidth()
                || localY < materialPickerGridViewportInset
                || localY >= materialPickerGridViewportInset + metrics.viewportHeight()) {
                return -1;
            }
            int stride = materialPickerGridStride();
            int relX = (int) localX - materialPickerGridViewportInset - materialPickerGridPadding;
            int relY = (int) (localY - materialPickerGridViewportInset + scrollY) - materialPickerGridPadding;
            if (relX < 0 || relY < 0) {
                return -1;
            }
            int column = relX / stride;
            int row = relY / stride;
            int xInCell = relX - column * stride;
            int yInCell = relY - row * stride;
            if (column < 0 || column >= metrics.columns() || xInCell >= materialPickerGridCellSize || yInCell >= materialPickerGridCellSize) {
                return -1;
            }
            int index = row * metrics.columns() + column;
            return index >= 0 && index < entries.size() ? index : -1;
        }

        private int firstVisibleRow(GridMetrics metrics) {
            int stride = materialPickerGridStride();
            int row = (int) Math.floor((scrollY - materialPickerGridPadding) / stride) - 1;
            return Math.clamp(row, 0, Math.max(0, metrics.rows() - 1));
        }

        private int lastVisibleRow(GridMetrics metrics) {
            int stride = materialPickerGridStride();
            int row = (int) Math.floor((scrollY + metrics.viewportHeight() - materialPickerGridPadding) / stride) + 1;
            return Math.clamp(row, 0, Math.max(0, metrics.rows() - 1));
        }

        private int cellX(int column) {
            return materialPickerGridViewportInset + materialPickerGridPadding + column * materialPickerGridStride();
        }

        private int cellY(int row) {
            return materialPickerGridViewportInset + materialPickerGridPadding + row * materialPickerGridStride();
        }

        private int scrollbarX(GridMetrics metrics) {
            return materialPickerGridViewportInset + metrics.viewportWidth() + materialPickerGridScrollGap;
        }

        private GridMetrics metrics() {
            int viewportHeight = Math.max(1, height() - materialPickerGridViewportInset * 2);
            int viewWidth = Math.max(1, width() - materialPickerGridViewportInset * 2);
            int columns = materialPickerGridColumnsForWidth(viewWidth);
            int contentHeight = materialPickerGridContentHeight(entries.size(), columns);
            boolean hasScrollbar = contentHeight > viewportHeight;
            if (hasScrollbar) {
                viewWidth = Math.max(
                    1,
                    width() - materialPickerGridViewportInset * 2 - materialPickerGridScrollbarWidth - materialPickerGridScrollGap
                );
                columns = materialPickerGridColumnsForWidth(viewWidth);
                contentHeight = materialPickerGridContentHeight(entries.size(), columns);
                hasScrollbar = contentHeight > viewportHeight;
            }
            int rows = materialPickerGridRows(entries.size(), columns);
            float maxScroll = Math.max(0f, contentHeight - viewportHeight);
            return new GridMetrics(viewWidth, viewportHeight, columns, rows, contentHeight, maxScroll, hasScrollbar);
        }

        private record GridMetrics(int viewportWidth, int viewportHeight, int columns, int rows, int contentHeight, float maxScrollY, boolean hasScrollbar) {
        }

        private record CellOverlay(int x, int y, boolean selected, boolean hovered) {
        }
    }

    private static final class TextureIconWidget extends Widget {
        private final SizedTexture icon;

        private TextureIconWidget(SizedTexture icon, int size) {
            this.icon = icon;
            useStyle(sizeOf(size, size), flexShrink(0));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            super.renderInternal(canvas, mouseX, mouseY, partialTicks);
            if (icon == null) {
                return;
            }
            int x = Math.max(0, (width() - icon.width()) / 2);
            int y = Math.max(0, (height() - icon.height()) / 2);
            canvas.texture(icon, x, y, icon.width(), icon.height());
        }
    }

    private static final class MaterialIconWidget extends Widget {
        private final ItemStack stack;

        private MaterialIconWidget(ItemStack stack) {
            this.stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
            useStyle(sizeOf(materialIconSize, materialIconSize), flexShrink(0));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            super.renderInternal(canvas, mouseX, mouseY, partialTicks);
            CirrusTextures.Background.Slot.dark.render(canvas.layeredGraphics(), 0, 0, width(), height());
            if (!stack.isEmpty()) {
                ItemStackRenderer.instance.renderAt(canvas.layeredGraphics(), stack, 1, 1, Math.max(1, width() - 2), Math.max(1, height() - 2), partialTicks);
            }
        }
    }
}
