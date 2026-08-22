package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisual;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentVisualCache;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.database.CustomRecipeDatabase;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseKind;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseRecipeRef;
import dev.vfyjxf.cirrus.api.recipe.database.FileRecipeDatabaseRepository;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseFailureRegistry;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseRecord;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseResult;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseWorkspace;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.BooleanProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.CalculatorProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ChoiceProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.DatabaseProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.LongProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextChoice;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextScope;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.InputSchemaPut;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.OutputSchemaPut;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionSchemaPut;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionPluginReport;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionReport;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.SchemaEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridSize;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemaKey;
import dev.vfyjxf.cloudlib.api.search.SearchField;
import dev.vfyjxf.cloudlib.api.search.SearchExpression;
import dev.vfyjxf.cloudlib.api.search.SearchOperator;
import dev.vfyjxf.cloudlib.api.search.SearchParser;
import dev.vfyjxf.cloudlib.api.search.SearchTerm;
import dev.vfyjxf.cloudlib.api.search.SearchParseException;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusUiAsync;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeTooltipPreview;
import dev.vfyjxf.cirrus.ui.execution.widget.ExecutionRecipeLayoutPreviewWidget;
import dev.vfyjxf.cirrus.ui.execution.widget.JeiRecipePreviewWidget;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorTargetRecipeState;
import dev.vfyjxf.cirrus.ui.widget.calculator.RecipeDatabaseKeyMappings;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotsWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.RecipeDatabaseSuggestionSource;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestion;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestionSource;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCardWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCheckboxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusRowWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusText;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusWrappedTextWidget;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.util.Checks;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textMuted;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsFlexStart;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayDefault;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexNoWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexWrap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.justifyCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.maxWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizePercent;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.zIndexOf;

public final class DatabasePage extends CirrusScrollablePageWidget {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Database");
    private static final int controlHeight = 18;
    private static final int toolbarIconButtonMinSize = 20;
    private static final int toolbarIconButtonInset = 2;
    private static final int databaseChoiceWidth = 78;
    private static final int createDatabaseButtonWidth = toolbarButtonWidth(
        CirrusTextures.Icon.Small.plus,
        CirrusTextures.Icon.Small.plusSelected
    );
    private static final int createDatabaseButtonHeight = toolbarButtonHeight(
        CirrusTextures.Icon.Small.plus,
        CirrusTextures.Icon.Small.plusSelected
    );
    private static final int createDatabaseEditorWidth = 118;
    private static final int createDatabaseNameFieldMinWidth = 58;
    private static final int deleteDatabaseButtonWidth = toolbarButtonWidth(
        CirrusTextures.Icon.delete,
        CirrusTextures.Icon.deleteSelected
    );
    private static final int deleteDatabaseButtonHeight = toolbarButtonHeight(
        CirrusTextures.Icon.delete,
        CirrusTextures.Icon.deleteSelected
    );
    private static final int sourcesButtonWidth = toolbarButtonWidth(
        CirrusTextures.Icon.databaseSelect,
        CirrusTextures.Icon.databaseUnselect
    );
    private static final int sourcesButtonHeight = toolbarButtonHeight(
        CirrusTextures.Icon.databaseSelect,
        CirrusTextures.Icon.databaseUnselect
    );
    private static final int viewButtonWidth = toolbarButtonWidth(
        CirrusTextures.Toggle.Switch.Calc.gridOn,
        CirrusTextures.Toggle.Switch.Calc.listOn
    );
    private static final int viewButtonHeight = toolbarButtonHeight(
        CirrusTextures.Toggle.Switch.Calc.gridOn,
        CirrusTextures.Toggle.Switch.Calc.listOn
    );
    private static final int sortButtonWidth = toolbarButtonWidth(
        CirrusTextures.Icon.Calc.Sort.number,
        CirrusTextures.Icon.Calc.Sort.numberPressed,
        CirrusTextures.Icon.Calc.Sort.id,
        CirrusTextures.Icon.Calc.Sort.idPressed,
        CirrusTextures.Icon.databaseUnselect,
        CirrusTextures.Icon.databaseSelect,
        CirrusTextures.Icon.contextUnselect,
        CirrusTextures.Icon.contextSelect
    );
    private static final int sortButtonHeight = toolbarButtonHeight(
        CirrusTextures.Icon.Calc.Sort.number,
        CirrusTextures.Icon.Calc.Sort.numberPressed,
        CirrusTextures.Icon.Calc.Sort.id,
        CirrusTextures.Icon.Calc.Sort.idPressed,
        CirrusTextures.Icon.databaseUnselect,
        CirrusTextures.Icon.databaseSelect,
        CirrusTextures.Icon.contextUnselect,
        CirrusTextures.Icon.contextSelect
    );
    private static final int sortDirectionButtonWidth = toolbarButtonWidth(
        CirrusTextures.Icon.sortAscending,
        CirrusTextures.Icon.sortDescending
    );
    private static final int sortDirectionButtonHeight = toolbarButtonHeight(
        CirrusTextures.Icon.sortAscending,
        CirrusTextures.Icon.sortDescending
    );
    private static final int sectionHeaderHeight = 16;
    private static final int sectionHeaderPaddingLeft = 6;
    private static final int sectionHeaderPaddingRight = 4;
    private static final int sourceSectionBodyPadding = 4;
    private static final int resultSectionBodyPadding = 3;
    private static final int resultCategoryHeaderHeight = 16;
    private static final int resultCategoryGroupPadding = 3;
    private static final int resultCategoryHeaderPaddingLeft = 5;
    private static final int resultCategoryHeaderPaddingRight = 4;
    private static final int resultCategoryCountWidth = 30;
    private static final int resultCategoryCountHeight = 14;
    private static final int resultCategoryCountPadding = 3;
    private static final int resultCategoryToggleSize = 14;
    private static final int resultCategoryToggleGap = 3;
    private static final int deleteConfirmPopupWidth = 156;
    private static final int deleteConfirmPopupHeight = 72;
    private static final int systemSourceRowMinWidth = 92;
    private static final int systemSourceRowMaxWidth = 210;
    private static final int systemSourceLayoutMaxWidth = 210;
    private static final int systemSourceGridGap = 4;
    private static final int systemSourceRowPadding = 2;
    private static final int systemSourceControlHeight = 34;
    private static final int systemSourceControlGap = 2;
    private static final int systemSourceLabelPaddingLeft = 3;
    private static final int systemSourceLabelPaddingRight = 2;
    private static final int systemSourceGroupHeaderHeight = 20;
    private static final int systemSourceGroupPadding = 3;
    private static final int tooltipEffectLimit = 4;
    private static final int gridCardPreferredWidth = 118;
    private static final int gridCardMinWidth = 104;
    private static final int gridCardHeight = 42;
    private static final int gridGap = 3;
    private static final float gridCardBasisPercent = 0.48f;
    private static final int gridBatchColumns = 2;
    private static final int listRowHeight = 32;
    private static final int listRowGap = 2;
    private static final int listRowHorizontalPadding = 3;
    private static final int listOutputSlotLimit = 3;
    private static final int listOutputSlotGap = 1;
    private static final int resultCardActionButtonSize = 18;
    private static final int resultCardActionButtonGap = 2;
    private static final int resultCardActionButtonInset = 2;
    private static final int resultCardActionColumnWidth =
        resultCardActionButtonSize * 2 + resultCardActionButtonGap + resultCardActionButtonInset * 2;
    private static final int detailContentSlotLimit = 10;
    private static final int detailCardPadding = 5;
    private static final int detailIconButtonSize = 18;
    private static final int detailKvHeight = 18;
    private static final int detailKvKeyWidth = 72;
    private static final int detailContextRowHeight = 20;
    private static final int detailEffectRowHeight = 32;
    private static final int detailEffectGroupHeaderHeight = 14;
    private static final int detailEffectAmountWidth = 58;
    private static final int detailProjectionControlPanelWidth = 164;
    private static final int detailProjectionControlPanelHeight = 126;
    private static final int detailProjectionControlScrollbarWidth = 6;
    private static final int detailProjectionPluginRowHeight = 28;
    private static final int detailProjectionWorkspaceMinHeight = 116;
    private static final int detailProjectionRowPadding = 3;
    private static final int detailProjectionPanelGap = 8;
    private static final int detailProjectionControlMarginLeft = 3;
    private static final int systemRecipeSelectionRowHeight = 28;
    private static final int systemRecipeSelectionRowGap = 2;
    private static final int systemRecipeSelectionRowPadding = 3;
    private static final int systemRecipeSelectionActionGap = 4;
    private static final int systemRecipeActionButtonWidth = 20;

    private RecipeDatabaseIndex index;
    private RecipeDatabaseIndex systemCatalogIndex;
    private RecipeDatabaseWorkspace workspace;
    private final @Nullable FileRecipeDatabaseRepository repository;
    private final List<RecipeSystem<?>> systems;
    private final Map<Namespace, List<Namespace>> systemTags;
    private final List<ProjectionPlugin> projectionPlugins;
    private final List<ProjectionContextOption<?>> projectionContextOptions;
    private final ExecutionContext globalProjectionContext;
    private final @Nullable CalculatorProjectionContext calculatorProjectionContext;
    private final @Nullable Runnable recipeDetailBackAction;
    private final DatabasePageChrome pageChrome;
    private final Map<Namespace, Object> projectionContextValues = new LinkedHashMap<>();
    private Map<String, List<RecipeEntry<?>>> explicitEntries;
    private final boolean databaseScopedIndex;
    private String activeDatabaseId;
    private final WidgetGroup<Widget> toolbar;
    private final SearchBoxWidget searchBox;
    private final CirrusChoiceBoxWidget<String> databaseChoice;
    private final WidgetGroup<Widget> results;
    private final CirrusButtonWidget createDatabaseButton;
    private final WidgetGroup<Widget> createDatabaseEditor;
    private final CirrusTextInputWidget createDatabaseNameField;
    private final CirrusButtonWidget confirmCreateDatabaseButton;
    private final CirrusButtonWidget deleteDatabaseButton;
    private CirrusButtonWidget confirmDeleteDatabaseButton;
    private final WidgetGroup<Widget> deleteConfirmPopup;
    private CirrusFittedTextWidget deleteConfirmDatabaseLabel;
    private final CirrusButtonWidget sourcesButton;
    private final CirrusButtonWidget viewButton;
    private final CirrusButtonWidget sortButton;
    private final CirrusButtonWidget sortDirectionButton;
    private ViewMode viewMode = ViewMode.grid;
    private SortMode sortMode = SortMode.relevance;
    private SortDirection sortDirection = SortDirection.ascending;
    private boolean createDatabaseEditorOpen;
    private boolean sourcesPageOpen;
    private @Nullable String pendingDeleteDatabaseId;
    private @Nullable Namespace selectedSystemRecipeSource;
    private @Nullable RecipeDatabaseRecord selectedRecipeDetail;
    private @Nullable Namespace selectedProjectionPluginId;
    private @Nullable Boolean systemRecipeDragSelection;
    private boolean systemRecipeDragged;
    private boolean activeIndexDirty;
    private boolean rebuildActiveIndexScheduled;
    private boolean applyQueryScheduled;
    private boolean asyncIndexBuildEnabled;
    private long indexBuildRequestId;
    private @Nullable CompletableFuture<IndexBuildResult> pendingIndexBuild;
    private @Nullable DatabaseIndexBuildProgress indexBuildProgress;
    private @Nullable LabelWidget indexProgressStatusLabel;
    private String indexProgressStatusText = "";
    private long queryRequestId;
    private @Nullable CompletableFuture<QueryBuildResult> pendingQueryBuild;
    private @Nullable QueryBuildResult lastAppliedResult;
    private boolean preserveScrollOnNextApply;
    private boolean restoreSavedScrollOnNextApply;
    private @Nullable ScrollSnapshot pendingScrollRestore;
    private int pendingScrollRestoreTicks;
    private final Map<String, ScrollSnapshot> savedScrollPositions = new LinkedHashMap<>();
    private final Set<String> collapsedResultCategories = new LinkedHashSet<>();
    private int visibleResultCount;
    private int visibleSystemRecipeSelectionCount;
    private int visibleRecipeDetailEffectCount;
    private int visibleRecipeDetailPluginCount;
    private int visibleRecipeDetailSectionCount;
    private boolean visibleRecipeDetailJeiPreview;
    private boolean projectionEffectsMerged = true;
    private Map<Namespace, List<ProjectionContextOption<?>>> visibleProjectionOptionsByPlugin = Map.of();

    private static int toolbarButtonWidth(SizedTexture... textures) {
        int width = toolbarIconButtonMinSize;
        for (SizedTexture texture : textures) {
            width = Math.max(width, texture.width() + toolbarIconButtonInset * 2);
        }
        return width;
    }

    private static int toolbarButtonHeight(SizedTexture... textures) {
        int height = toolbarIconButtonMinSize;
        for (SizedTexture texture : textures) {
            height = Math.max(height, texture.height() + toolbarIconButtonInset * 2);
        }
        return height;
    }

    public DatabasePage() {
        this(buildContextFromApi());
    }

    public DatabasePage(RecipeDatabaseIndex index) {
        this(fixedContext(index));
    }

    public DatabasePage(
        RecipeDatabaseWorkspace workspace,
        List<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags
    ) {
        this(workspaceContext(workspace, systems, systemTags, Map.of(), null));
    }

    public static DatabasePage calculatorProjection(
        RecipeEntry<?> recipe,
        int times,
        long version,
        Runnable recipeDetailBackAction
    ) {
        RecipeDatabaseIndex index = calculatorProjectionIndex(recipe);
        DatabaseContext baseContext = ExecutionOverlayClientConfig.calculatorProjectionUsesGlobalDatabase()
            ? buildContextFromApi().withFixedIndex(index)
            : calculatorProjectionLocalContext(index);
        return calculatorProjection(baseContext, recipe, times, version, recipeDetailBackAction);
    }

    public static DatabasePage calculatorProjection(
        RecipeDatabaseIndex index,
        RecipeEntry<?> recipe,
        int times,
        long version,
        Runnable recipeDetailBackAction
    ) {
        return calculatorProjection(fixedContext(index), recipe, times, version, recipeDetailBackAction);
    }

    private static DatabasePage calculatorProjection(
        DatabaseContext baseContext,
        RecipeEntry<?> recipe,
        int times,
        long version,
        Runnable recipeDetailBackAction
    ) {
        DatabaseContext context = baseContext.withCalculatorProjectionContext(new CalculatorProjectionContext(
            Checks.checkNotNull(recipe, "recipe"),
            times,
            version
        ))
            .withRecipeDetailBackAction(Checks.checkNotNull(recipeDetailBackAction, "recipeDetailBackAction"))
            .withPageChrome(DatabasePageChrome.projectionDetail);
        return new DatabasePage(context);
    }

    private DatabasePage(DatabaseContext context) {
        this.workspace = Checks.checkNotNull(context.workspace(), "workspace");
        this.repository = context.repository();
        this.systems = context.systems();
        this.systemTags = context.systemTags();
        this.projectionPlugins = context.projectionPlugins().stream()
            .sorted(Comparator.comparing(plugin -> plugin.id().toString()))
            .toList();
        this.projectionContextOptions = context.projectionContextOptions().stream()
            .sorted(Comparator.comparing(option -> option.id().toString()))
            .toList();
        this.globalProjectionContext = context.globalProjectionContext();
        this.calculatorProjectionContext = context.calculatorProjectionContext();
        this.recipeDetailBackAction = context.recipeDetailBackAction();
        this.pageChrome = context.pageChrome();
        setTickable(true);
        for (ProjectionPlugin plugin : projectionPlugins) {
            projectionContextValues.put(plugin.id(), plugin.enabledKey().defaultValue());
        }
        for (ProjectionContextOption<?> option : projectionContextOptions) {
            projectionContextValues.put(option.id(), option.defaultValue());
        }
        this.selectedProjectionPluginId = null;
        this.databaseScopedIndex = context.databaseScopedIndex();
        this.asyncIndexBuildEnabled = databaseScopedIndex && context.asyncIndexBuild() && pageChrome == DatabasePageChrome.databaseBrowser;
        this.explicitEntries = !databaseScopedIndex
            ? copyExplicitEntries(context.explicitEntries())
            : asyncIndexBuildEnabled
            ? copyExplicitEntries(context.explicitEntries())
            : resolveExplicitEntries(workspace, systems, context.explicitEntries());
        if (databaseScopedIndex && asyncIndexBuildEnabled) {
            this.systemCatalogIndex = RecipeDatabaseIndex.ofRecords(List.of());
            this.index = RecipeDatabaseIndex.ofRecords(List.of());
        } else if (databaseScopedIndex) {
            RecipeDatabaseIndex catalogIndex = buildSystemCatalogIndex(systems, systemTags);
            this.systemCatalogIndex = catalogIndex;
            this.index = workspace.activeDatabase().builtin()
                ? catalogIndex
                : buildActiveIndex(workspace, systems, systemTags, explicitEntries, catalogIndex);
        } else {
            this.systemCatalogIndex = RecipeDatabaseIndex.ofRecords(List.of());
            this.index = Checks.checkNotNull(context.index(), "index");
        }
        this.activeDatabaseId = workspace.activeDatabaseId();

        onMouseClicked((input, bubble) -> {
            settlePageScrollForInteraction();
            return EventDispatch.pass;
        }, false);
        onMouseReleased((input, bubble) -> {
            settlePageScrollForInteraction();
            return EventDispatch.pass;
        }, false);

        toolbar = new WidgetGroup<>();
        toolbar.useStyle(flexRow(), gap(4), minWidth(0), zIndexOf(40), alignItemsCenter());
        searchBox = new SearchBoxWidget()
            .setPlaceholder(CirrusLangs.Calculator.Database.search)
            .tooltip(this::searchTooltip)
            .suggestionViewport(this::scrollViewportSceneBounds)
            .suggestionSource(new RecipeDatabaseSuggestionSource(index))
            .onQueryChanged(ignored -> requestApplyQuery())
            .onEnterPressed(ignored -> applyQuery());
        searchBox.useStyle(flexGrow(1), flexShrink(1), heightOf(controlHeight), minWidth(82), zIndexOf(60));
        toolbar.addWidget(searchBox);

        databaseChoice = new CirrusChoiceBoxWidget<String>(databaseChoiceWidth)
            .tooltip(this::databaseChoiceTooltip)
            .onChanged(this::setActiveDatabase);
        for (CustomRecipeDatabase database : workspace.customDatabases()) {
            databaseChoice.option(database.id(), displayName(database));
        }
        databaseChoice.value(activeDatabaseId);
        databaseChoice.useStyle(sizeOf(databaseChoiceWidth, controlHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(databaseChoice);

        createDatabaseButton = new CirrusButtonWidget(createDatabaseButtonWidth, createDatabaseButtonHeight)
            .icon(CirrusTextures.Icon.Small.plus, CirrusTextures.Icon.Small.plusSelected)
            .tooltip(this::createDatabaseTooltip)
            .clickSound(true)
            .selected(() -> createDatabaseEditorOpen)
            .onPress(this::toggleCreateDatabaseEditor);
        createDatabaseButton.useStyle(sizeOf(createDatabaseButtonWidth, createDatabaseButtonHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(createDatabaseButton);

        createDatabaseEditor = new WidgetGroup<>();
        createDatabaseEditor.useStyle(
            flexRow(),
            alignItemsCenter(),
            gap(2),
            widthOf(createDatabaseEditorWidth),
            minWidth(createDatabaseNameFieldMinWidth + createDatabaseButtonWidth + 2),
            flexShrink(1),
            displayNone(),
            zIndexOf(20)
        );
        createDatabaseNameField = new CirrusTextInputWidget()
            .setPlaceholder(CirrusLangs.Calculator.Database.createNamePlaceholder)
            .maxLength(48)
            .tooltip(this::createDatabaseNameTooltip)
            .onTextChanged(ignored -> updateCreateDatabaseConfirmState())
            .onEnterPressed(ignored -> confirmCreateDatabase());
        createDatabaseNameField.useStyle(
            heightOf(controlHeight),
            minWidth(createDatabaseNameFieldMinWidth),
            flexGrow(1),
            flexShrink(1),
            zIndexOf(20)
        );
        createDatabaseEditor.addWidget(createDatabaseNameField);

        confirmCreateDatabaseButton = new CirrusButtonWidget(createDatabaseButtonWidth, createDatabaseButtonHeight)
            .icon(CirrusTextures.Icon.finished)
            .tooltip(this::confirmCreateDatabaseTooltip)
            .clickSound(true)
            .onPress((Runnable) this::confirmCreateDatabase);
        confirmCreateDatabaseButton.useStyle(sizeOf(createDatabaseButtonWidth, createDatabaseButtonHeight), flexShrink(0), zIndexOf(20));
        createDatabaseEditor.addWidget(confirmCreateDatabaseButton);
        toolbar.addWidget(createDatabaseEditor);
        setCreateDatabaseEditorOpen(false);

        deleteDatabaseButton = new CirrusButtonWidget(deleteDatabaseButtonWidth, deleteDatabaseButtonHeight)
            .icon(CirrusTextures.Icon.delete, CirrusTextures.Icon.deleteSelected)
            .tooltip(this::deleteDatabaseTooltip)
            .clickSound(true)
            .selected(this::deleteConfirmPendingForActive)
            .onPress(this::requestDeleteActiveDatabase);
        deleteDatabaseButton.useStyle(sizeOf(deleteDatabaseButtonWidth, deleteDatabaseButtonHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(deleteDatabaseButton);

        sourcesButton = new CirrusButtonWidget(sourcesButtonWidth, sourcesButtonHeight)
            .iconSupplier(() -> sourcesPageOpen || selectedSystemRecipeSource != null
                ? CirrusTextures.Icon.databaseSelect
                : CirrusTextures.Icon.databaseUnselect)
            .tooltip(this::sourcesTooltip)
            .clickSound(true)
            .selected(() -> sourcesPageOpen || selectedSystemRecipeSource != null)
            .onPress(this::openSourcesPage);
        sourcesButton.useStyle(sizeOf(sourcesButtonWidth, sourcesButtonHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(sourcesButton);

        viewButton = new CirrusButtonWidget(viewButtonWidth, viewButtonHeight)
            .iconSupplier(() -> viewMode == ViewMode.grid
                ? CirrusTextures.Toggle.Switch.Calc.gridOn
                : CirrusTextures.Toggle.Switch.Calc.listOn)
            .tooltip(this::viewTooltip)
            .clickSound(true)
            .onPress(this::toggleViewMode);
        viewButton.useStyle(sizeOf(viewButtonWidth, viewButtonHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(viewButton);

        sortButton = new CirrusButtonWidget(sortButtonWidth, sortButtonHeight)
            .icon(sortMode.icon(), sortMode.hoverIcon())
            .tooltip(this::sortTooltip)
            .clickSound(true)
            .onPress(this::cycleSortMode);
        sortButton.useStyle(sizeOf(sortButtonWidth, sortButtonHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(sortButton);

        sortDirectionButton = new CirrusButtonWidget(sortDirectionButtonWidth, sortDirectionButtonHeight)
            .iconSupplier(() -> sortDirection == SortDirection.ascending
                ? CirrusTextures.Icon.sortAscending
                : CirrusTextures.Icon.sortDescending)
            .tooltip(this::sortDirectionTooltip)
            .clickSound(true)
            .onPress(this::toggleSortDirection);
        sortDirectionButton.useStyle(sizeOf(sortDirectionButtonWidth, sortDirectionButtonHeight), flexShrink(0), zIndexOf(20));
        toolbar.addWidget(sortDirectionButton);
        if (browserChromeVisible()) {
            addWidget(toolbar);
        } else {
            toolbar.useStyle(displayNone());
            toolbar.setVisible(false).setActive(false).setInteractive(false);
        }

        results = addVerticalList(3);
        deleteConfirmPopup = createDeleteConfirmPopup();
        addWidget(deleteConfirmPopup);
        syncControls();
        if (asyncIndexBuildEnabled) {
            startIndexBuild(true);
        } else {
            applyQuery();
        }
    }

    private WidgetGroup<Widget> createDeleteConfirmPopup() {
        WidgetGroup<Widget> popup = new WidgetGroup<>();
        popup.useStyle(
            positionAbsolute(),
            sizePercent(1f),
            justifyCenter(),
            alignItemsCenter(),
            displayNone(),
            zIndexOf(90)
        );
        popup.onMouseClick((input, clickCount, context) -> EventDispatch.consumed);

        WidgetGroup<Widget> dialog = new WidgetGroup<>();
        dialog.useStyle(
            flexColumn(),
            gap(4),
            padding(6),
            widthOf(deleteConfirmPopupWidth),
            heightOf(deleteConfirmPopupHeight),
            background(CirrusTextures.Background.Outlined.inset),
            zIndexOf(91)
        );
        dialog.addWidget(new CirrusFittedTextWidget(CirrusLangs.Calculator.Database.deleteConfirm.component(), textPrimary))
            .useStyle(heightOf(10), minWidth(0));
        deleteConfirmDatabaseLabel = dialog.addWidget(new CirrusFittedTextWidget(Component.empty(), textMuted));
        deleteConfirmDatabaseLabel.useStyle(heightOf(10), minWidth(0));

        WidgetGroup<Widget> actions = new WidgetGroup<>();
        actions.useStyle(flexRow(), gap(4), justifyCenter(), alignItemsCenter(), minWidth(0));
        CirrusButtonWidget cancel = new CirrusButtonWidget(52, controlHeight)
            .label(CirrusLangs.Calculator.Database.deleteCancel.component())
            .tooltip(this::cancelDeleteDatabaseTooltip)
            .clickSound(true)
            .onPress(this::cancelDeleteActiveDatabase);
        cancel.useStyle(sizeOf(52, controlHeight), flexShrink(0));
        actions.addWidget(cancel);

        confirmDeleteDatabaseButton = new CirrusButtonWidget(52, controlHeight)
            .label(CirrusLangs.Calculator.Database.deleteConfirm.component())
            .icon(CirrusTextures.Icon.finished)
            .tooltip(this::confirmDeleteDatabaseTooltip)
            .clickSound(true)
            .onPress(this::confirmDeleteActiveDatabase);
        confirmDeleteDatabaseButton.useStyle(sizeOf(52, controlHeight), flexShrink(0));
        actions.addWidget(confirmDeleteDatabaseButton);
        dialog.addWidget(actions);
        popup.addWidget(dialog);
        return popup;
    }

    public void setIndex(RecipeDatabaseIndex index) {
        this.index = Checks.checkNotNull(index, "index");
        searchBox.suggestionSource(new RecipeDatabaseSuggestionSource(index));
        applyQuery();
    }

    @Override
    public void tick() {
        super.tick();
        applyCompletedIndexBuild();
        applyCompletedQueryBuild();
        applyPendingScrollRestore();
    }

    public int debugVisibleResultCount() {
        return visibleResultCount;
    }

    public String debugQuery() {
        return searchBox.text();
    }

    public void debugSetQuery(String query) {
        searchBox.setText(query);
        applyQuery();
    }

    public void debugCompletePendingQuery() {
        CompletableFuture<QueryBuildResult> future = pendingQueryBuild;
        if (future == null) {
            return;
        }
        pendingQueryBuild = null;
        applyQueryResult(future.join());
    }

    public int debugResultCategoryCount() {
        VirtualizedResultSectionsWidget widget = debugResultSectionsWidget();
        return widget == null ? 0 : widget.debugCategoryCount();
    }

    public int debugResultCardCount() {
        VirtualizedResultSectionsWidget widget = debugResultSectionsWidget();
        return widget == null ? 0 : widget.debugCardCount();
    }

    public int debugResultSectionsContentHeight() {
        VirtualizedResultSectionsWidget widget = debugResultSectionsWidget();
        return widget == null ? 0 : widget.debugContentHeight();
    }

    public int debugSourceGroupsContentHeight() {
        VirtualizedSourceGroupsWidget widget = debugSourceGroupsWidget();
        return widget == null ? 0 : widget.debugContentHeight();
    }

    public int debugCollapsedResultCategoryCount() {
        return collapsedResultCategories.size();
    }

    public boolean debugResultCategoryCollapsed(int index) {
        VirtualizedResultSectionsWidget widget = debugResultSectionsWidget();
        return widget != null && widget.debugCategoryCollapsed(index);
    }

    public int debugResultCategoryY(int index) {
        VirtualizedResultSectionsWidget widget = debugResultSectionsWidget();
        return widget == null ? -1 : widget.debugCategoryY(index);
    }

    public void debugToggleResultCategory(int index) {
        VirtualizedResultSectionsWidget widget = debugResultSectionsWidget();
        if (widget != null) {
            widget.debugToggleCategory(index);
        }
    }

    private @Nullable VirtualizedResultSectionsWidget debugResultSectionsWidget() {
        for (Widget child : results.children()) {
            if (child instanceof VirtualizedResultSectionsWidget widget) {
                return widget;
            }
        }
        return null;
    }

    private @Nullable VirtualizedSourceGroupsWidget debugSourceGroupsWidget() {
        for (Widget child : results.children()) {
            if (child instanceof CirrusSectionWidget section) {
                for (Widget sectionChild : section.body().children()) {
                    if (sectionChild instanceof VirtualizedSourceGroupsWidget widget) {
                        return widget;
                    }
                }
            }
        }
        return null;
    }

    public int debugSearchSuggestionCount() {
        return searchBox.debugSuggestionCount();
    }

    public String debugViewMode() {
        return viewMode.name();
    }

    public String debugSortMode() {
        return sortMode.name();
    }

    public String debugSortDirection() {
        return sortDirection.name();
    }

    public String debugActiveDatabaseId() {
        return activeDatabaseId;
    }

    public int debugCustomDatabaseCount() {
        return workspace.customDatabases().size();
    }

    public int debugActiveDatabaseRecipeCount() {
        return workspace.activeDatabase().recipes().size();
    }

    public int debugIndexRecordCount() {
        return index.records().size();
    }

    public int debugSystemCatalogRecordCount() {
        return systemCatalogIndex.records().size();
    }

    public boolean debugDatabaseScopedIndex() {
        return databaseScopedIndex;
    }

    public boolean debugActiveDatabaseBuiltin() {
        return workspace.activeDatabase().builtin();
    }

    public boolean debugActiveDatabaseEditable() {
        return activeDatabaseEditable();
    }

    public boolean debugBrowserToolbarVisible() {
        return toolbar.visible();
    }

    public boolean debugSearchBoxVisible() {
        return searchBox.visible();
    }

    public boolean debugDatabaseChoiceVisible() {
        return databaseChoice.visible();
    }

    public boolean debugCreateDatabaseButtonVisible() {
        return createDatabaseButton.visible();
    }

    public boolean debugDeleteDatabaseConfirmVisible() {
        return deleteConfirmPendingForActive();
    }

    public boolean debugDeleteDatabaseEnabled() {
        return deleteDatabaseButton.enabled();
    }

    public boolean debugDeleteDatabaseVisible() {
        return deleteDatabaseButton.visible();
    }

    public boolean debugSourcesButtonEnabled() {
        return sourcesButton.enabled();
    }

    public boolean debugSourcesButtonVisible() {
        return sourcesButton.visible();
    }

    public boolean debugViewButtonVisible() {
        return viewButton.visible();
    }

    public boolean debugSortButtonVisible() {
        return sortButton.visible();
    }

    public boolean debugSortDirectionButtonVisible() {
        return sortDirectionButton.visible();
    }

    public int debugSystemRecipeSelectionCount() {
        return visibleSystemRecipeSelectionCount;
    }

    public boolean debugRecipeDetailOpen() {
        return selectedRecipeDetail != null;
    }

    public int debugRecipeDetailEffectCount() {
        return visibleRecipeDetailEffectCount;
    }

    public int debugRecipeDetailPluginCount() {
        return visibleRecipeDetailPluginCount;
    }

    public int debugRecipeDetailSectionCount() {
        return visibleRecipeDetailSectionCount;
    }

    public boolean debugRecipeDetailUsesJeiPreview() {
        return visibleRecipeDetailJeiPreview;
    }

    public boolean debugProjectionEffectsMerged() {
        return projectionEffectsMerged;
    }

    public void debugSetProjectionEffectsMerged(boolean merged) {
        setProjectionEffectsMerged(merged);
    }

    public @Nullable String debugSelectedRecipeDetail() {
        return selectedRecipeDetail == null ? null : recipeLabel(selectedRecipeDetail);
    }

    public @Nullable String debugSelectedProjectionPlugin() {
        return selectedProjectionPluginId == null ? null : selectedProjectionPluginId.toString();
    }

    public void debugSelectProjectionPlugin(String pluginId) {
        Namespace id = parseNamespace(pluginId);
        if (id != null) {
            selectProjectionPlugin(id);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void debugSetProjectionContextOption(String optionId, Object value) {
        Namespace id = parseNamespace(optionId);
        if (id == null) {
            return;
        }
        for (ProjectionContextOption option : projectionContextOptions) {
            if (option.id().equals(id)) {
                setProjectionContextValue(option, value);
                return;
            }
        }
    }

    public @Nullable String debugSelectedSystemRecipeSource() {
        return selectedSystemRecipeSource == null ? null : selectedSystemRecipeSource.toString();
    }

    public int debugActiveDatabaseSystemSourceCount() {
        return workspace.activeDatabase().sources().size();
    }

    public int debugEnabledDatabaseSystemSourceCount() {
        return workspace.activeDatabase().enabledSources().size();
    }

    public void debugToggleSystemSource(String sourceId) {
        Namespace source = parseNamespace(sourceId);
        if (source != null) {
            toggleSystemSource(source);
        }
    }

    public void debugSetSystemSourceEnabled(String sourceId, boolean enabled) {
        Namespace source = parseNamespace(sourceId);
        if (source != null) {
            setSystemSourceEnabled(source, enabled);
        }
    }

    public void debugSetSystemRecipeSelected(String sourceId, String recipeId, boolean selected) {
        DatabaseRecipeRef ref = parseRecipeRef(sourceId, recipeId);
        if (ref != null) {
            setRecipeSelected(ref, selected);
        }
    }

    public void debugSetVisibleSystemRecipesSelected(String sourceId, boolean selected) {
        Namespace source = parseNamespace(sourceId);
        if (source == null) {
            return;
        }
        try {
            setRecipeSelectionsSelected(systemRecipeSelections(source), selected);
        } catch (SearchParseException ignored) {
        }
    }

    public void debugOpenSystemRecipeSource(String sourceId) {
        Namespace source = parseNamespace(sourceId);
        if (source != null) {
            openSystemRecipeSource(source);
        }
    }

    public void debugLeaveSystemRecipeSource() {
        leaveSystemRecipeSource();
    }

    public void debugOpenSourcesPage() {
        openSourcesPage();
    }

    public void debugLeaveSourcesPage() {
        leaveSourcesPage();
    }

    public boolean debugSourcesPageOpen() {
        return sourcesPageOpen;
    }

    public void debugOpenFirstResultDetail() {
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(searchBox.text()));
            if (!matches.isEmpty()) {
                openRecipeDetail(matches.getFirst().record());
            }
        } catch (SearchParseException ignored) {
        }
    }

    public void debugLeaveRecipeDetail() {
        closeRecipeDetail();
    }

    public void debugRefreshProjectionPreservingScroll() {
        preserveScrollOnNextApply = true;
        applyQuery();
    }

    public boolean debugActiveDatabaseContainsRecipe(String sourceId, String recipeId) {
        DatabaseRecipeRef ref = parseRecipeRef(sourceId, recipeId);
        return ref != null && workspace.activeDatabase().contains(ref);
    }

    public void debugSetActiveDatabase(String databaseId) {
        setActiveDatabase(databaseId);
    }

    public boolean openRecipe(RecipeEntry<?> recipe) {
        RecipeDatabaseRecord record = findRecipeRecord(index, recipe);
        if (record == null) {
            record = findRecipeRecord(systemCatalogIndex, recipe);
        }
        if (record == null) {
            return false;
        }
        openRecipeDetail(record, false);
        return true;
    }

    public ProjectionContextScope debugProjectionContextScope() {
        return projectionContext().get(ProjectionContextKeys.scope);
    }

    public String debugProjectionContextDatabaseId() {
        return projectionContext().get(ProjectionContextKeys.database).databaseId();
    }

    public boolean debugProjectionContextHasCalculatorRecipe() {
        return projectionContext().get(ProjectionContextKeys.calculator).hasRecipe();
    }

    public void debugRequestDeleteActiveDatabase() {
        requestDeleteActiveDatabase();
    }

    public void debugConfirmDeleteActiveDatabase() {
        confirmDeleteActiveDatabase();
    }

    public boolean debugCreateDatabaseEditorOpen() {
        return createDatabaseEditorOpen;
    }

    public String debugCreateDatabaseName() {
        return createDatabaseNameField.text();
    }

    public boolean debugCreateDatabaseConfirmEnabled() {
        return confirmCreateDatabaseButton.enabled();
    }

    public void debugOpenCreateDatabaseEditor() {
        setCreateDatabaseEditorOpen(true);
    }

    public void debugCloseCreateDatabaseEditor() {
        setCreateDatabaseEditorOpen(false);
    }

    public void debugSetCreateDatabaseName(String name) {
        createDatabaseNameField.setText(name);
        updateCreateDatabaseConfirmState();
    }

    public void debugConfirmCreateDatabaseName() {
        confirmCreateDatabase();
    }

    public void debugSetViewMode(String mode) {
        viewMode = ViewMode.valueOf(mode);
        syncControls();
        applyQuery();
    }

    public void debugSetSortMode(String mode) {
        sortMode = SortMode.valueOf(mode);
        syncControls();
        applyQuery();
    }

    public void debugSetSortDirection(String direction) {
        sortDirection = SortDirection.valueOf(direction);
        syncControls();
        applyQuery();
    }

    public List<String> debugViewTooltipLines() {
        return debugTooltipLines(viewTooltip());
    }

    public Tooltip debugViewTooltip() {
        return viewTooltip();
    }

    public Tooltip debugFirstResultTooltip() {
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(searchBox.text()));
            if (matches.isEmpty()) {
                return Tooltip.create();
            }
            return recipeTooltip(matches.getFirst().record());
        } catch (SearchParseException ignored) {
            return Tooltip.create();
        }
    }

    public Tooltip debugFirstResultActionTooltip(String action) {
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(searchBox.text()));
            if (matches.isEmpty()) {
                return Tooltip.create();
            }
            return recipeActionTooltip(matches.getFirst().record(), ResultCardAction.valueOf(action));
        } catch (IllegalArgumentException ignored) {
            return Tooltip.create();
        }
    }

    public void debugActivateFirstResultCard(String action) {
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(searchBox.text()));
            if (!matches.isEmpty()) {
                activateRecipeCard(matches.getFirst().record(), ResultCardAction.valueOf(action), false);
            }
        } catch (SearchParseException ignored) {
        }
    }

    public void debugOpenFirstResultCardDefault() {
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(searchBox.text()));
            if (!matches.isEmpty()) {
                openRecipeDetail(matches.getFirst().record(), false);
            }
        } catch (SearchParseException ignored) {
        }
    }

    public @Nullable String debugFirstResultSystemGroup() {
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(searchBox.text()));
            return matches.isEmpty() ? null : systemGroupKey(matches.getFirst().record());
        } catch (SearchParseException ignored) {
            return null;
        }
    }

    public List<String> debugSortTooltipLines() {
        return debugTooltipLines(sortTooltip());
    }

    public Tooltip debugSortTooltip() {
        return sortTooltip();
    }

    public List<String> debugCreateDatabaseTooltipLines() {
        return debugTooltipLines(createDatabaseTooltip());
    }

    public List<String> debugDatabaseChoiceTooltipLines() {
        return debugTooltipLines(databaseChoiceTooltip());
    }

    public static List<String> debugTooltipLines(Tooltip tooltip) {
        ArrayList<String> lines = new ArrayList<>();
        for (TooltipEntry entry : tooltip.flatEntries()) {
            switch (entry) {
                case TooltipEntry.TextEntry text -> lines.add(text.text().getString());
                case TooltipEntry.DynamicEntry dynamic -> lines.add(dynamic.provider().get().getString());
                case TooltipEntry.ComponentEntry ignored -> {
                }
            }
        }
        return List.copyOf(lines);
    }

    public String debugSortTooltipModeState() {
        return sortMode.name() + "->" + nextSortMode().name();
    }

    public String debugViewTooltipModeState() {
        return viewMode.name() + "->" + nextViewMode().name();
    }

    public static int viewModeButtonWidth() {
        return viewButtonWidth;
    }

    public static int viewModeButtonHeight() {
        return viewButtonHeight;
    }

    public static int databaseChoiceWidth() {
        return databaseChoiceWidth;
    }

    public static int createDatabaseButtonWidth() {
        return createDatabaseButtonWidth;
    }

    public static int createDatabaseButtonHeight() {
        return createDatabaseButtonHeight;
    }

    public static int deleteDatabaseButtonWidth() {
        return deleteDatabaseButtonWidth;
    }

    public static int deleteDatabaseButtonHeight() {
        return deleteDatabaseButtonHeight;
    }

    public static int createDatabaseEditorWidth() {
        return createDatabaseEditorWidth;
    }

    public static int createDatabaseNameFieldMinWidth() {
        return createDatabaseNameFieldMinWidth;
    }

    public static int sortModeButtonWidth() {
        return sortButtonWidth;
    }

    public static int sortModeButtonHeight() {
        return sortButtonHeight;
    }

    public static int sortDirectionButtonWidth() {
        return sortDirectionButtonWidth;
    }

    public static int sortDirectionButtonHeight() {
        return sortDirectionButtonHeight;
    }

    public static int sourcesButtonWidth() {
        return sourcesButtonWidth;
    }

    public static int sourcesButtonHeight() {
        return sourcesButtonHeight;
    }

    public static int sectionHeaderHeight() {
        return sectionHeaderHeight;
    }

    public static int sectionHeaderPaddingLeft() {
        return sectionHeaderPaddingLeft;
    }

    public static int sectionHeaderPaddingRight() {
        return sectionHeaderPaddingRight;
    }

    public static int sourceSectionBodyPadding() {
        return sourceSectionBodyPadding;
    }

    public static int resultSectionBodyPadding() {
        return resultSectionBodyPadding;
    }

    public static int resultCategoryHeaderHeight() {
        return resultCategoryHeaderHeight;
    }

    public static int resultCategoryGroupPadding() {
        return resultCategoryGroupPadding;
    }

    public static int resultCategoryHeaderPaddingLeft() {
        return resultCategoryHeaderPaddingLeft;
    }

    public static int resultCategoryHeaderPaddingRight() {
        return resultCategoryHeaderPaddingRight;
    }

    public static int resultCategoryCountWidth() {
        return resultCategoryCountWidth;
    }

    public static int resultCategoryCountHeight() {
        return resultCategoryCountHeight;
    }

    public static int resultCategoryCountPadding() {
        return resultCategoryCountPadding;
    }

    public static int systemSourceRowMinWidth() {
        return systemSourceRowMinWidth;
    }

    public static int systemSourceRowMaxWidth() {
        return systemSourceRowMaxWidth;
    }

    public static int systemSourceControlHeight() {
        return systemSourceControlHeight;
    }

    public static int systemSourceControlGap() {
        return systemSourceControlGap;
    }

    public static int systemSourceLabelPaddingLeft() {
        return systemSourceLabelPaddingLeft;
    }

    public static int systemSourceLabelPaddingRight() {
        return systemSourceLabelPaddingRight;
    }

    public static int gridCardPreferredWidth() {
        return gridCardPreferredWidth;
    }

    public static int gridCardMinWidth() {
        return gridCardMinWidth;
    }

    public static int gridCardHeight() {
        return gridCardHeight;
    }

    public static int gridGap() {
        return gridGap;
    }

    public static float gridCardBasisPercent() {
        return gridCardBasisPercent;
    }

    public static int gridBatchColumns() {
        return gridBatchColumns;
    }

    public static int listRowHeight() {
        return listRowHeight;
    }

    public static int listRowGap() {
        return listRowGap;
    }

    public static int listRowHorizontalPadding() {
        return listRowHorizontalPadding;
    }

    public static int listOutputSlotLimit() {
        return listOutputSlotLimit;
    }

    public static int listOutputStripWidth(int count) {
        return RecipeContentSlotsWidget.stripWidth(count, listOutputSlotLimit, listOutputSlotGap);
    }

    public static int resultCardActionButtonSize() {
        return resultCardActionButtonSize;
    }

    public static int resultCardActionColumnWidth() {
        return resultCardActionColumnWidth;
    }

    public static int detailIconButtonSize() {
        return detailIconButtonSize;
    }

    public static int detailEffectRowHeight() {
        return detailEffectRowHeight;
    }

    public static int detailProjectionControlPanelWidth() {
        return detailProjectionControlPanelWidth;
    }

    public static int detailProjectionControlPanelHeight() {
        return detailProjectionControlPanelHeight;
    }

    public static int detailProjectionRowPadding() {
        return detailProjectionRowPadding;
    }

    public static int detailProjectionPanelGap() {
        return detailProjectionPanelGap;
    }

    public static int detailProjectionControlMarginLeft() {
        return detailProjectionControlMarginLeft;
    }

    public static int systemRecipeSelectionRowHeight() {
        return systemRecipeSelectionRowHeight;
    }

    public static int systemRecipeSelectionRowGap() {
        return systemRecipeSelectionRowGap;
    }

    public static int systemRecipeSelectionRowPadding() {
        return systemRecipeSelectionRowPadding;
    }

    public static int systemSourceGroupHeaderHeight() {
        return systemSourceGroupHeaderHeight;
    }

    public static int systemSourceGroupPadding() {
        return systemSourceGroupPadding;
    }

    public static int debugSystemSourceRowWidth(String sourceId) {
        return systemSourceRowWidth(Namespace.parse(sourceId));
    }

    public static int systemRecipeSelectionActionGap() {
        return systemRecipeSelectionActionGap;
    }

    public static int systemRecipeActionButtonWidth() {
        return systemRecipeActionButtonWidth;
    }

    public static int systemRecipeSelectionListHeight(int count) {
        if (count <= 0) {
            return 0;
        }
        return count * systemRecipeSelectionRowHeight + Math.max(0, count - 1) * systemRecipeSelectionRowGap;
    }

    private void applyQuery() {
        boolean preserveScroll = preserveScrollOnNextApply && selectedRecipeDetail != null;
        String targetScrollKey = scrollPositionKey();
        ScrollSnapshot scrollToRestore = preserveScroll
            ? currentScrollSnapshot()
            : restoreSavedScrollOnNextApply ? savedScrollPositions.get(targetScrollKey) : null;
        preserveScrollOnNextApply = false;
        restoreSavedScrollOnNextApply = false;
        long requestId = ++queryRequestId;
        clearPendingScrollRestore();
        clearResults();
        resetScroll();
        if (selectedRecipeDetail != null) {
            addRecipeDetailPage(selectedRecipeDetail);
            restoreScroll(scrollToRestore);
            return;
        }
        if (selectedSystemRecipeSource != null) {
            addSystemRecipeSourcePage(selectedSystemRecipeSource);
            restoreScroll(scrollToRestore);
            return;
        }
        if (sourcesPageOpen) {
            addSourceOverviewPage();
            restoreScroll(scrollToRestore);
            return;
        }
        if (shouldBuildQueryAsync()) {
            startQueryBuild(requestId, scrollToRestore);
            return;
        }
        QueryBuildResult queryResult = buildQueryResult(
            requestId,
            index,
            searchBox.text(),
            sortMode,
            sortDirection,
            scrollToRestore
        );
        applyQueryResult(queryResult);
    }

    private boolean shouldBuildQueryAsync() {
        return lifecycle().mounted();
    }

    private void startQueryBuild(long requestId, @Nullable ScrollSnapshot scrollToRestore) {
        RecipeDatabaseIndex indexSnapshot = index;
        String querySnapshot = searchBox.text();
        SortMode sortModeSnapshot = sortMode;
        SortDirection sortDirectionSnapshot = sortDirection;
        pendingQueryBuild = CirrusUiAsync.recipeTask(() -> buildQueryResult(
            requestId,
            indexSnapshot,
            querySnapshot,
            sortModeSnapshot,
            sortDirectionSnapshot,
            scrollToRestore
        ));
        if (shouldShowIndexProgress()) {
            showIndexProgressStatus();
        }
    }

    private static QueryBuildResult buildQueryResult(
        long requestId,
        RecipeDatabaseIndex index,
        String query,
        SortMode sortMode,
        SortDirection sortDirection,
        @Nullable ScrollSnapshot scrollToRestore
    ) {
        if (shouldWaitForMoreSearchInput(query)) {
            return QueryBuildResult.results(
                requestId,
                query,
                sortMode,
                sortDirection,
                0,
                List.of(),
                scrollToRestore
            );
        }
        try {
            List<RecipeDatabaseResult> matches = sort(index.search(query), sortMode, sortDirection);
            return QueryBuildResult.results(
                requestId,
                query,
                sortMode,
                sortDirection,
                matches.size(),
                buildResultBuckets(matches, sortMode),
                scrollToRestore
            );
        } catch (SearchParseException ex) {
            return QueryBuildResult.invalid(requestId, query, sortMode, sortDirection, scrollToRestore);
        }
    }

    private static boolean shouldWaitForMoreSearchInput(String query) {
        if (query == null || query.isBlank() || !query.equals(query.stripTrailing())) {
            return false;
        }
        String token = trailingSearchToken(query);
        if (token.isBlank()) {
            return false;
        }
        if (isPrefixOnlySearchToken(token)) {
            return true;
        }
        if (token.equals("!") || token.equals("-") || token.equals("(")) {
            return true;
        }
        String body = token.startsWith("!") || token.startsWith("-") ? token.substring(1) : token;
        if (body.isBlank()) {
            return true;
        }
        SearchField prefixField = body.length() == 1 ? CirrusSearchFields.SET.byJeiPrefix(body.charAt(0)) : null;
        if (prefixField != null) {
            return true;
        }
        if (CirrusSearchFields.SET.isKnownAlias(body) && query.charAt(query.length() - 1) != ' ') {
            return true;
        }
        int colon = body.indexOf(':');
        if (colon > 0 && colon == body.length() - 1 && CirrusSearchFields.SET.isKnownAlias(body.substring(0, colon))) {
            return true;
        }
        if (colon > 0 && CirrusSearchFields.SET.isKnownAlias(body.substring(0, colon))) {
            String value = body.substring(colon + 1);
            if (shouldWaitForSearchValue(value)) {
                return true;
            }
        } else if (shouldWaitForPlainSearchToken(body)) {
            return true;
        }
        if (body.endsWith(".") && hasKnownFieldAliasPrefix(body)) {
            return true;
        }
        return incompleteAmountModifier(body);
    }

    private static boolean isPrefixOnlySearchToken(String token) {
        String body = token.startsWith("!") || token.startsWith("-") ? token.substring(1) : token;
        if (body.isBlank()) {
            return true;
        }
        if (body.length() == 1 && CirrusSearchFields.SET.byJeiPrefix(body.charAt(0)) != null) {
            return true;
        }
        return switch (body) {
            case "@", "$", "#", "&", "%", "in:", "out:", "cat:", "byproduct:", "bp:", "by:", "name:", "id:", "mod:", "system:", "db:", "machine:" -> true;
            default -> false;
        };
    }

    private static boolean shouldWaitForPlainSearchToken(String body) {
        if (body.isBlank() || body.equals("*") || incompleteAmountModifier(body)) {
            return true;
        }
        if (body.indexOf(':') >= 0 || body.indexOf('*') >= 0 || body.indexOf('?') >= 0) {
            return false;
        }
        return meaningfulSearchChars(body) < 2;
    }

    private static boolean shouldWaitForSearchValue(String value) {
        if (value.isBlank() || value.equals("*") || incompleteAmountModifier(value)) {
            return true;
        }
        if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0) {
            return false;
        }
        return meaningfulSearchChars(value) < 2;
    }

    private static int meaningfulSearchChars(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (Character.isLetterOrDigit(c)) {
                count++;
            }
        }
        return count;
    }

    private static String trailingSearchToken(String query) {
        int end = query.length();
        while (end > 0 && Character.isWhitespace(query.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(query.charAt(start - 1)) && query.charAt(start - 1) != '(') {
            start--;
        }
        return query.substring(start, end);
    }

    private static boolean hasKnownFieldAliasPrefix(String value) {
        for (String prefix : List.of("in.", "out.", "cat.", "byproduct.", "bp.", "by.", "ingredient.", "result.", "results.")) {
            if (value.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean incompleteAmountModifier(String body) {
        if (!body.startsWith("*")) {
            return false;
        }
        String suffix = body.substring(1).trim();
        return suffix.isEmpty()
            || suffix.equals(">")
            || suffix.equals("<")
            || suffix.equals("=")
            || suffix.equals(">=")
            || suffix.equals("<=");
    }

    private void applyCompletedQueryBuild() {
        CompletableFuture<QueryBuildResult> future = pendingQueryBuild;
        if (future == null || !future.isDone()) {
            return;
        }
        pendingQueryBuild = null;
        QueryBuildResult result;
        try {
            result = future.join();
        } catch (CompletionException ignored) {
            return;
        }
        applyQueryResult(result);
    }

    // saturated theme orange: the result cards are light-background with
    // dark text, so the match color must out-contrast both plus the blue
    // selection fill used elsewhere
    private static final int matchHighlightColor = dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.orange;
    private List<String> matchNeedles = List.of();
    private int matchNeedlesVersion;

    /** Positive text needles of the current query, used to highlight matches in result labels. */
    private static List<String> extractMatchNeedles(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        SearchExpression expression;
        try {
            expression = CirrusSearchFields.parser().parse(query);
        } catch (RuntimeException ignored) {
            return List.of();
        }
        LinkedHashSet<String> needles = new LinkedHashSet<>();
        collectMatchNeedles(expression, needles);
        return List.copyOf(needles);
    }

    private static void collectMatchNeedles(SearchExpression expression, LinkedHashSet<String> needles) {
        switch (expression) {
            case SearchExpression.Term term -> {
                SearchTerm search = term.term();
                if (search.operator() != SearchOperator.contains && search.operator() != SearchOperator.exact) {
                    return;
                }
                SearchField field = search.field();
                if (field.numeric() || field == CirrusSearchFields.mod || field == CirrusSearchFields.system
                    || field == CirrusSearchFields.db || field == CirrusSearchFields.profile || field == CirrusSearchFields.plan) {
                    return;
                }
                String value = search.value();
                if (!value.isBlank() && value.indexOf('*') < 0 && value.indexOf('?') < 0) {
                    needles.add(value.toLowerCase(Locale.ROOT));
                }
            }
            case SearchExpression.And and -> {
                for (SearchExpression child : and.terms()) {
                    collectMatchNeedles(child, needles);
                }
            }
            case SearchExpression.Or or -> {
                for (SearchExpression child : or.terms()) {
                    collectMatchNeedles(child, needles);
                }
            }
            default -> {
            }
        }
    }

    private void applyQueryResult(QueryBuildResult result) {
        applyQueryResult(result, true);
    }

    private void applyQueryResult(QueryBuildResult result, boolean resetScrollPosition) {
        if (result.requestId() != queryRequestId
            || selectedRecipeDetail != null
            || selectedSystemRecipeSource != null
            || sourcesPageOpen
            || !result.query().equals(searchBox.text())
            || result.sortMode() != sortMode
            || result.sortDirection() != sortDirection) {
            return;
        }
        clearPendingScrollRestore();
        clearResults();
        lastAppliedResult = null;
        matchNeedles = result.invalidQuery() ? List.of() : extractMatchNeedles(result.query());
        matchNeedlesVersion++;
        if (resetScrollPosition) {
            resetScroll();
        }
        if (result.invalidQuery()) {
            addStatus(CirrusLangs.Calculator.Database.invalidQuery.component(), 0xFFFF7777);
            visibleResultCount = 0;
            visibleSystemRecipeSelectionCount = 0;
            visibleRecipeDetailEffectCount = 0;
            visibleRecipeDetailPluginCount = 0;
            visibleRecipeDetailSectionCount = 0;
            restoreScroll(result.scrollToRestore());
            return;
        }
        visibleSystemRecipeSelectionCount = 0;
        visibleRecipeDetailEffectCount = 0;
        visibleRecipeDetailPluginCount = 0;
        visibleRecipeDetailSectionCount = 0;
        visibleResultCount = result.visibleResultCount();
        if (result.visibleResultCount() == 0) {
            addStatus(CirrusLangs.Calculator.Database.empty.component(), textMuted);
            restoreScroll(result.scrollToRestore());
            return;
        }
        addResultBuckets(result.buckets());
        lastAppliedResult = result;
        restoreScroll(result.scrollToRestore());
    }

    private void rememberScrollPosition() {
        settlePageScrollForInteraction();
        savedScrollPositions.put(scrollPositionKey(), currentScrollSnapshot());
    }

    private ScrollSnapshot currentScrollSnapshot() {
        return new ScrollSnapshot(scrollState().targetScrollX(), scrollState().targetScrollY());
    }

    private void requestApplyQueryWithSavedScroll() {
        restoreSavedScrollOnNextApply = true;
        requestApplyQuery();
    }

    private void restoreScroll(@Nullable ScrollSnapshot scroll) {
        if (scroll != null) {
            pendingScrollRestore = scroll;
            pendingScrollRestoreTicks = 8;
            applyPendingScrollRestore();
        }
    }

    private void applyPendingScrollRestore() {
        ScrollSnapshot scroll = pendingScrollRestore;
        if (scroll == null) {
            return;
        }
        scrollState().jumpTo(scroll.x(), scroll.y());
        viewport().setContentOffset(scrollState().scrollX(), scrollState().scrollY());
        boolean restored = Math.abs(scrollState().targetScrollX() - scroll.x()) < 0.5f
            && Math.abs(scrollState().targetScrollY() - scroll.y()) < 0.5f;
        if (restored || --pendingScrollRestoreTicks <= 0) {
            pendingScrollRestore = null;
            pendingScrollRestoreTicks = 0;
        }
    }

    private void clearPendingScrollRestore() {
        pendingScrollRestore = null;
        pendingScrollRestoreTicks = 0;
    }

    private void settlePageScrollForInteraction() {
        if (!scrollState().isAnimating()) {
            return;
        }
        float x = scrollState().scrollX();
        float y = scrollState().scrollY();
        scrollState().jumpTo(x, y);
        viewport().setContentOffset(x, y);
    }

    private String scrollPositionKey() {
        if (selectedRecipeDetail != null) {
            return "detail:" + activeDatabaseId + ":" + recipeScrollKey(selectedRecipeDetail);
        }
        if (selectedSystemRecipeSource != null) {
            return "source-recipes:" + activeDatabaseId + ":" + selectedSystemRecipeSource;
        }
        if (sourcesPageOpen) {
            return "sources:" + activeDatabaseId;
        }
        return "results:" + activeDatabaseId
            + ":" + viewMode
            + ":" + sortMode
            + ":" + sortDirection
            + ":" + searchBox.text();
    }

    private static String recipeScrollKey(RecipeDatabaseRecord record) {
        ResourceLocation id = record.recipe().id();
        return record.recipe().systemId() + ":" + (id == null ? Integer.toHexString(record.recipe().hashCode()) : id.toString());
    }

    private CirrusSectionWidget resultSection(Component title) {
        CirrusSectionWidget section = new CirrusSectionWidget(title).chrome(CirrusSectionWidget.Chrome.outlinedFlat);
        styleSectionHeader(section);
        return section;
    }

    private void styleSectionHeader(CirrusSectionWidget section) {
        section.titleWidget().textPadding(sectionHeaderPaddingLeft, sectionHeaderPaddingRight);
        section.titleWidget().useStyle(
            widthOf(TaffyDimension.percent(1f)),
            heightOf(sectionHeaderHeight),
            minWidth(0),
            background(CirrusTextures.Background.Border.dark)
        );
    }

    private void addRecipeDetailPage(RecipeDatabaseRecord record) {
        visibleResultCount = 0;
        visibleSystemRecipeSelectionCount = 0;
        ProjectionReport report = projectionReport(record.projection());
        visibleRecipeDetailEffectCount = report == null ? 0 : report.effects().size();
        visibleProjectionOptionsByPlugin = relevantProjectionOptionsByPlugin(record.projection(), report);
        List<ProjectionPlugin> detailPlugins = relevantProjectionPlugins(record.projection(), report);
        visibleRecipeDetailPluginCount = detailPlugins.size();
        visibleRecipeDetailSectionCount = 0;
        visibleRecipeDetailJeiPreview = false;

        ensureSelectedProjectionPlugin(record.projection(), report, detailPlugins);
        WidgetGroup<Widget> shell = new WidgetGroup<>();
        shell.useStyle(
            flexColumn(),
            gap(3),
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            flexShrink(0)
        );
        results.addWidget(shell);

        addRecipeDetailHeader(shell, record, report);
        addRecipePreviewSection(shell, record, report);
        addRecipeProjectionSection(shell, record.projection(), report, detailPlugins);
    }

    private void addRecipeDetailHeader(WidgetGroup<Widget> parent, RecipeDatabaseRecord record, @Nullable ProjectionReport report) {
        CirrusCardWidget card = detailCard();
        parent.addWidget(card);
        visibleRecipeDetailSectionCount++;

        WidgetGroup<Widget> header = new WidgetGroup<>();
        header.useStyle(flexRow(), gap(4), minWidth(0), alignItemsCenter());
        CirrusButtonWidget back = backButton(this::closeRecipeDetail);
        back.useStyle(sizeOf(detailIconButtonSize, detailIconButtonSize), flexShrink(0));
        header.addWidget(back);

        WidgetGroup<Widget> title = new WidgetGroup<>();
        title.useStyle(flexColumn(), gap(1), flexGrow(1), minWidth(0));
        title.addWidget(new CirrusFittedTextWidget(CirrusLangs.Calculator.Database.details.component(shortRecipeLabel(record)), textPrimary).textPadding(1, 1))
            .useStyle(heightOf(11), minWidth(0));
        title.addWidget(new CirrusFittedTextWidget(Component.literal(recipeLabel(record)), textMuted).textPadding(1, 1))
            .useStyle(heightOf(10), minWidth(0));
        header.addWidget(title);
        card.addContent(header);
        card.addContent(detailMetadataStrip(record, report));
    }

    private void addRecipePreviewSection(WidgetGroup<Widget> parent, RecipeDatabaseRecord record, @Nullable ProjectionReport report) {
        CirrusCardWidget card = detailCard(CirrusLangs.Calculator.Database.preview.component());
        parent.addWidget(card);
        visibleRecipeDetailSectionCount++;
        JeiRecipePreviewWidget jeiPreview = JeiRecipePreviewWidget.create(record.recipe());
        if (jeiPreview != null) {
            visibleRecipeDetailJeiPreview = true;
            card.addContent(jeiPreview);
            return;
        }
        Widget groupedPreview = recipeContentPreviewGroups(record.recipe(), record.projection(), null);
        if (recipeContentPreviewHasContent(record.projection(), null)) {
            card.addContent(groupedPreview);
            return;
        }
        card.addContent(new CirrusFittedTextWidget(CirrusLangs.Calculator.Database.empty.component(), textMuted).textPadding(2, 1))
            .useStyle(heightOf(14), minWidth(0));
    }

    private Widget detailMetadataStrip(RecipeDatabaseRecord record, @Nullable ProjectionReport report) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), flexWrap(), alignItemsCenter(), gap(3), minWidth(0));
        row.addWidget(detailMetadataChip(
            Component.literal(record.database().name()),
            CirrusLangs.Calculator.Database.database.component(),
            Component.literal(record.database().name()),
            86,
            54,
            true
        ));
        row.addWidget(detailMetadataChip(
            Component.literal(shortNamespace(record.recipe().systemId())),
            CirrusLangs.Calculator.Database.system.component(),
            Component.literal(record.recipe().systemId().toString()),
            128,
            92,
            true
        ));
        boolean runnable = report != null && report.runnable();
        row.addWidget(detailMetadataChip(
            (runnable
                ? CirrusLangs.Calculator.Database.effectRunnableYes
                : CirrusLangs.Calculator.Database.effectRunnableNo).component(),
            CirrusLangs.Calculator.Database.effectRunnable.component(),
            (runnable
                ? CirrusLangs.Calculator.Database.effectRunnableYes
                : CirrusLangs.Calculator.Database.effectRunnableNo).component(),
            38,
            34,
            false
        ));
        return row;
    }

    private Widget detailMetadataChip(
        Component value,
        Component title,
        Component tooltipValue,
        int baseWidth,
        int minChipWidth,
        boolean grow
    ) {
        CirrusFittedTextWidget chip = new CirrusFittedTextWidget(value, textMuted);
        chip.textPadding(4, 1);
        if (grow) {
            chip.useStyle(
                heightOf(13),
                widthOf(baseWidth),
                flexGrow(1),
                flexShrink(1),
                minWidth(minChipWidth),
                background(CirrusTextures.Background.inset)
            );
        } else {
            chip.useStyle(
                heightOf(13),
                widthOf(baseWidth),
                minWidth(minChipWidth),
                flexShrink(0),
                background(CirrusTextures.Background.inset)
            );
        }
        chip.onHoverTooltip((mouseX, mouseY) -> {
            Tooltip tooltip = Tooltip.create();
            tooltip.add(title, Tooltip.title);
            tooltip.add(tooltipValue);
            return tooltip;
        });
        return chip;
    }

    private void addRecipeProjectionSection(
        WidgetGroup<Widget> parent,
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report,
        List<ProjectionPlugin> detailPlugins
    ) {
        ProjectionPlugin selected = selectedProjectionPlugin(detailPlugins);

        CirrusCardWidget card = detailCard(CirrusLangs.Calculator.Database.projection.component());
        parent.addWidget(card);
        visibleRecipeDetailSectionCount++;

        WidgetGroup<Widget> workspace = new WidgetGroup<>();
        workspace.useStyle(
            flexRow(),
            flexNoWrap(),
            alignItemsStretch(),
            gap(detailProjectionPanelGap),
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            minHeight(detailProjectionWorkspaceMinHeight)
        );
        card.addContent(workspace);

        ProjectionPluginReport selectedReport = selected == null
            ? null
            : report == null ? ProjectionPluginReport.empty(selected) : report.pluginReport(selected);
        Widget overview = projectionOverviewPanel(projection, report, selected, selectedReport);
        overview.useStyle(flexGrow(1), flexShrink(1), minWidth(0), minHeight(detailProjectionWorkspaceMinHeight));
        workspace.addWidget(overview);

        Widget controls = projectionControlPanel(projection, report, detailPlugins, selected);
        controls.useStyle(
            widthOf(detailProjectionControlPanelWidth),
            maxWidth(detailProjectionControlPanelWidth),
            minWidth(detailProjectionControlPanelWidth),
            heightOf(detailProjectionControlPanelHeight),
            flexGrow(0),
            flexShrink(0),
            marginLeft(detailProjectionControlMarginLeft)
        );
        workspace.addWidget(controls);
    }

    private Widget projectionOverviewPanel(
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report,
        @Nullable ProjectionPlugin selected,
        @Nullable ProjectionPluginReport selectedReport
    ) {
        WidgetGroup<Widget> panel = detailSubPanel(CirrusLangs.Calculator.Database.effects.component());
        if (selected != null) {
            panel.addWidget(projectionSelectedHeader(selected, selectedReport));
        }

        ProjectionRecipeViewWidget projectionView = ProjectionRecipeViewWidget.create(report == null ? projection : report.projection());
        if (projectionView != null) {
            panel.addWidget(projectionView);
        }

        if (selectedReport == null || selectedReport.effects().isEmpty()) {
            panel.addWidget(new CirrusWrappedTextWidget(CirrusLangs.Calculator.Database.effectsNone, textMuted).padding(2).maxLines(2))
                .useStyle(heightOf(20), minWidth(0));
        } else {
            panel.addWidget(projectionEffectMergeRow());
            panel.addWidget(effectSummaryStrip(selectedReport));
            addEffectGroup(panel, CirrusLangs.Calculator.Database.detailInputs.component(), effectRowsForChannel(selectedReport, EffectChannel.input));
            addEffectGroup(panel, CirrusLangs.Calculator.Database.detailOutputs.component(), effectRowsForChannel(selectedReport, EffectChannel.output));
            addEffectGroup(panel, CirrusLangs.Calculator.Database.effects.component(), rawEffectRows(uncategorizedEffects(selectedReport)));
        }
        return panel;
    }

    private Widget projectionSelectedHeader(ProjectionPlugin selected, @Nullable ProjectionPluginReport selectedReport) {
        WidgetGroup<Widget> header = new WidgetGroup<>();
        header.useStyle(flexRow(), alignItemsCenter(), gap(3), minWidth(0), minHeight(20));

        CirrusWrappedTextWidget label = new CirrusWrappedTextWidget(Component.translatable(selected.labelKey()), textPrimary)
            .padding(1)
            .maxLines(2);
        label.useStyle(flexGrow(1), minWidth(0), minHeight(20));
        header.addWidget(label);

        if (selectedReport != null) {
            int effectCount = selectedReport.effectCount();
            CirrusFittedTextWidget count = new CirrusFittedTextWidget(Component.literal(String.valueOf(effectCount)),
                effectCount == 0 ? textMuted : ExecutionOverlayUi.tooltipAccent)
                .align(CirrusFittedTextWidget.Align.center);
            count.useStyle(
                widthOf(22),
                heightOf(14),
                flexShrink(0),
                background(CirrusTextures.Background.inset)
            );
            count.onHoverTooltip((mouseX, mouseY) -> {
                Tooltip tooltip = Tooltip.create();
                tooltip.add(CirrusLangs.Calculator.Database.effects.component(), Tooltip.title);
                tooltip.add(CirrusLangs.Calculator.Database.pluginEffects.component(effectCount));
                return tooltip;
            });
            header.addWidget(count);
        }

        return header;
    }

    private Widget projectionControlPanel(
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report,
        List<ProjectionPlugin> detailPlugins,
        @Nullable ProjectionPlugin selected
    ) {
        WidgetGroup<Widget> viewport = detailSubPanel(CirrusLangs.Calculator.Database.effectSettings.component());
        viewport.useStyle(overflow(Overflow.HIDDEN), paddingRight(detailProjectionControlScrollbarWidth + 2));

        ScrollState scrollState = CirrusScroll.longVerticalState(10)
            .smooth(true)
            .smoothSpeed(0.35f)
            .trackTexture(CirrusTextures.Background.scroll)
            .thumbTexture(CirrusTextures.Icon.scrollBarVertical)
            .scrollbarWidth(detailProjectionControlScrollbarWidth)
            .minThumbSize(14)
            .viewportInset(2, 1, 2, 0);
        scrollState.showScrollbar(detailPlugins.size() > 3 || (selected != null && optionsForPlugin(selected, projection, report).size() > 3));
        viewport.useEffect(UIEffects.scrollable(scrollState));

        if (detailPlugins.isEmpty()) {
            viewport.addWidget(new CirrusWrappedTextWidget(CirrusLangs.Calculator.Database.pluginsNone, textMuted).padding(2).maxLines(2))
                .useStyle(heightOf(22), minWidth(0));
            return viewport;
        }

        viewport.addWidget(projectionPluginPanel(report, detailPlugins));
        if (selected != null) {
            viewport.addWidget(projectionConfigurationPanel(selected, projection, report));
        }
        return viewport;
    }

    private Widget projectionEffectMergeRow() {
        Widget row = projectionSwitchRow(
            CirrusLangs.Calculator.Database.effectsMerge.component(),
            () -> projectionEffectsMerged,
            this::setProjectionEffectsMerged,
            this::projectionEffectMergeTooltip
        );
        row.useStyle(minWidth(0), padding(detailProjectionRowPadding), boxSizingBorderBox());
        return row;
    }

    private Widget projectionPluginPanel(@Nullable ProjectionReport report, List<ProjectionPlugin> detailPlugins) {
        WidgetGroup<Widget> panel = detailSubPanel(CirrusLangs.Calculator.Database.plugins.component());
        WidgetGroup<Widget> pluginColumn = new WidgetGroup<>();
        pluginColumn.useStyle(flexColumn(), gap(2), minWidth(0));
        for (ProjectionPlugin plugin : detailPlugins) {
            pluginColumn.addWidget(projectionPluginRow(plugin, report == null ? ProjectionPluginReport.empty(plugin) : report.pluginReport(plugin)));
        }
        panel.addWidget(pluginColumn);
        return panel;
    }

    private Widget projectionConfigurationPanel(
        ProjectionPlugin selected,
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report
    ) {
        WidgetGroup<Widget> parameters = detailSubPanel(CirrusLangs.Calculator.Database.parameters.component());
        List<ProjectionContextOption<?>> options = optionsForPlugin(selected, projection, report);
        if (options.isEmpty()) {
            parameters.addWidget(new CirrusWrappedTextWidget(CirrusLangs.Calculator.Database.parametersNone, textMuted).padding(2).maxLines(2))
                .useStyle(heightOf(20), minWidth(0));
        } else {
            for (ProjectionContextOption<?> option : options) {
                parameters.addWidget(projectionContextOptionRow(option));
            }
        }
        return parameters;
    }

    private Widget projectionContextOptionRow(ProjectionContextOption<?> option) {
        if (option instanceof BooleanProjectionContextOption booleanOption) {
            Widget row = projectionSwitchRow(
                Component.translatable(booleanOption.labelKey()),
                () -> booleanOption.normalize(projectionContextValues.get(booleanOption.id())),
                enabled -> setProjectionContextValue(booleanOption, enabled),
                () -> projectionContextOptionTooltip(booleanOption)
            );
            row.useStyle(minWidth(0), padding(detailProjectionRowPadding), boxSizingBorderBox());
            return row;
        }
        if (option instanceof LongProjectionContextOption longOption) {
            WidgetGroup<Widget> row = projectionContextBaseRow(longOption);
            NumberInputWidget input = NumberInputWidget.create(longOption.normalize(projectionContextValues.get(longOption.id())))
                .setRange(longOption.min(), longOption.max())
                .setScrollStep(longOption.step())
                .onValueChanged(value -> setProjectionContextValue(longOption, value));
            input.useStyle(sizeOf(48, 14), flexShrink(0));
            input.onHoverTooltip((mouseX, mouseY) -> projectionContextOptionTooltip(longOption));
            row.addWidget(input);
            return row;
        }
        if (option instanceof ChoiceProjectionContextOption<?> choiceOption) {
            return projectionContextChoiceRow(choiceOption);
        }
        return detailKvRow(Component.translatable(option.labelKey()), Component.literal(String.valueOf(option.defaultValue())));
    }

    private Widget projectionPluginRow(ProjectionPlugin plugin, ProjectionPluginReport pluginReport) {
        CirrusRowWidget row = CirrusRowWidget.selectable(detailProjectionPluginRowHeight, 4)
            .selected(() -> plugin.id().equals(selectedProjectionPluginId))
            .onPress(input -> selectProjectionPlugin(plugin.id()));
        row.useStyle(minWidth(0), padding(detailProjectionRowPadding), boxSizingBorderBox());
        row.onHoverTooltip((mouseX, mouseY) -> projectionPluginTooltip(plugin, pluginReport));

        CirrusWrappedTextWidget label = new CirrusWrappedTextWidget(Component.translatable(plugin.labelKey()), textPrimary)
            .padding(1)
            .maxLines(2);
        label.useStyle(flexGrow(1), minHeight(18), minWidth(0));
        row.addWidget(label);

        int effectCount = pluginReport.effectCount();
        CirrusFittedTextWidget effects = new CirrusFittedTextWidget(
            Component.literal(String.valueOf(effectCount)),
            effectCount == 0 ? textMuted : ExecutionOverlayUi.tooltipAccent
        );
        effects.textPadding(1, 1);
        effects.useStyle(widthOf(18), heightOf(12), flexShrink(0), background(CirrusTextures.Background.inset));
        effects.onHoverTooltip((mouseX, mouseY) -> projectionPluginTooltip(plugin, pluginReport));
        row.addWidget(effects);

        dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide toggle = new dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide(
            () -> projectionPluginEnabled(plugin),
            value -> setProjectionPluginEnabled(plugin, value),
            () -> projectionPluginTooltip(plugin, pluginReport)
        );
        toggle.useStyle(sizeOf(20, 13), flexShrink(0));
        row.addWidget(toggle);

        return row;
    }

    private Widget projectionSwitchRow(
        Component label,
        java.util.function.BooleanSupplier value,
        java.util.function.Consumer<Boolean> setter,
        java.util.function.Supplier<Tooltip> tooltipSupplier
    ) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(
            flexRow(),
            alignItemsCenter(),
            gap(6),
            minHeight(detailContextRowHeight),
            minWidth(0),
            overflow(Overflow.HIDDEN)
        );
        java.util.function.Supplier<Tooltip> tooltip = tooltipSupplier == null
            ? () -> Tooltip.create()
            : tooltipSupplier;
        CirrusWrappedTextWidget rowLabel = new CirrusWrappedTextWidget(label, textPrimary)
            .padding(1)
            .maxLines(2);
        rowLabel.useStyle(flexGrow(1), minWidth(0), minHeight(18));
        rowLabel.onHoverTooltip((mouseX, mouseY) -> tooltip.get());
        row.addWidget(rowLabel);

        dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide toggle = new dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide(
            value == null ? () -> false : value,
            setter == null ? ignored -> {} : setter,
            tooltip
        );
        toggle.useStyle(sizeOf(20, 13), flexShrink(0));
        row.addWidget(toggle);
        row.onHoverTooltip((mouseX, mouseY) -> tooltip.get());
        return row;
    }

    private WidgetGroup<Widget> detailSubPanel(Component title) {
        WidgetGroup<Widget> panel = new WidgetGroup<>();
        panel.useStyle(
            flexColumn(),
            gap(2),
            padding(3),
            minWidth(0),
            background(CirrusTextures.Background.Outlined.flat)
        );
        panel.addWidget(sectionCaption(title));
        return panel;
    }

    private Widget effectSummaryStrip(ProjectionPluginReport report) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(4), minWidth(0), heightOf(16));
        row.addWidget(detailTinyMetric(
            CirrusLangs.Calculator.Database.effectInputChanges.component(report.inputChangeCount()),
            report.changesInputs() ? ExecutionOverlayUi.tooltipAccent : textMuted
        ));
        row.addWidget(detailTinyMetric(
            CirrusLangs.Calculator.Database.effectOutputChanges.component(report.outputChangeCount()),
            report.changesOutputs() ? ExecutionOverlayUi.tooltipAccent : textMuted
        ));
        return row;
    }

    private Widget detailTinyMetric(Component label, int color) {
        CirrusFittedTextWidget widget = new CirrusFittedTextWidget(label, color);
        widget.textPadding(2, 1);
        widget.useStyle(heightOf(14), flexGrow(1), minWidth(0));
        return widget;
    }

    private Widget sectionCaption(Component label) {
        CirrusFittedTextWidget caption = new CirrusFittedTextWidget(label, textMuted);
        caption.textPadding(2, 1);
        caption.useStyle(heightOf(11), minWidth(0));
        return caption;
    }

    private CirrusCardWidget detailCard() {
        CirrusCardWidget card = new CirrusCardWidget();
        card.useStyle(
            padding(detailCardPadding),
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            flexShrink(0)
        );
        return card;
    }

    private CirrusCardWidget detailCard(Component title) {
        CirrusCardWidget card = new CirrusCardWidget(title);
        card.useStyle(
            padding(detailCardPadding),
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            flexShrink(0)
        );
        return card;
    }

    private Widget detailKvRow(Component key, Component value) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(4), heightOf(detailKvHeight), minWidth(0));
        CirrusFittedTextWidget keyWidget = new CirrusFittedTextWidget(key, textMuted);
        keyWidget.textPadding(2, 1);
        keyWidget.useStyle(widthOf(detailKvKeyWidth), heightOf(detailKvHeight), flexShrink(0));
        row.addWidget(keyWidget);
        CirrusFittedTextWidget valueWidget = new CirrusFittedTextWidget(value, textPrimary);
        valueWidget.textPadding(1, 2);
        valueWidget.useStyle(flexGrow(1), heightOf(detailKvHeight), minWidth(0));
        row.addWidget(valueWidget);
        return row;
    }

    private CirrusButtonWidget backButton(Runnable action) {
        return new CirrusButtonWidget(detailIconButtonSize, detailIconButtonSize)
            .icon(CirrusTextures.Icon.Small.arrowLeft, CirrusTextures.Icon.Small.arrowLeftSelected)
            .tooltip(this::allResultsTooltip)
            .clickSound(true)
            .onPress(action);
    }

    private WidgetGroup<Widget> projectionContextBaseRow(ProjectionContextOption<?> option) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(
            flexRow(),
            alignItemsCenter(),
            gap(6),
            minHeight(detailContextRowHeight),
            minWidth(0),
            padding(detailProjectionRowPadding),
            boxSizingBorderBox()
        );
        row.onHoverTooltip((mouseX, mouseY) -> projectionContextOptionTooltip(option));
        CirrusWrappedTextWidget label = new CirrusWrappedTextWidget(Component.translatable(option.labelKey()), textPrimary)
            .padding(1)
            .maxLines(2);
        label.useStyle(flexGrow(1), minHeight(18), minWidth(0));
        row.addWidget(label);
        return row;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Widget projectionContextChoiceRow(ChoiceProjectionContextOption<?> option) {
        ChoiceProjectionContextOption rawOption = option;
        WidgetGroup<Widget> row = projectionContextBaseRow(option);
        CirrusChoiceBoxWidget<Object> choice = new CirrusChoiceBoxWidget<>(82);
        for (Object rawChoice : rawOption.choices()) {
            ProjectionContextChoice contextChoice = (ProjectionContextChoice) rawChoice;
            choice.option(contextChoice.value(), Component.translatable(contextChoice.labelKey()));
        }
        choice.value(rawOption.normalize(projectionContextValues.get(rawOption.id())));
        choice.tooltip(() -> projectionContextOptionTooltip(option));
        choice.onChanged(value -> setProjectionContextValue(rawOption, value));
        choice.useStyle(sizeOf(82, controlHeight), flexShrink(0));
        row.addWidget(choice);
        return row;
    }

    private <T> void setProjectionContextValue(ProjectionContextOption<T> option, T value) {
        projectionContextValues.put(option.id(), option.normalize(value));
        preserveScrollOnNextApply = true;
        requestApplyQuery();
    }

    private void setProjectionEffectsMerged(boolean merged) {
        if (projectionEffectsMerged == merged) {
            return;
        }
        projectionEffectsMerged = merged;
        preserveScrollOnNextApply = true;
        requestApplyQuery();
    }

    private boolean projectionPluginEnabled(ProjectionPlugin plugin) {
        Object value = projectionContextValues.get(plugin.id());
        return value instanceof Boolean enabled ? enabled : plugin.enabledKey().defaultValue();
    }

    private void setProjectionPluginEnabled(ProjectionPlugin plugin, boolean enabled) {
        projectionContextValues.put(plugin.id(), enabled);
        preserveScrollOnNextApply = true;
        requestApplyQuery();
    }

    private void selectProjectionPlugin(Namespace pluginId) {
        for (ProjectionPlugin plugin : projectionPlugins) {
            if (plugin.id().equals(pluginId)) {
                selectedProjectionPluginId = pluginId;
                preserveScrollOnNextApply = true;
                requestApplyQuery();
                return;
            }
        }
    }

    private @Nullable ProjectionPlugin selectedProjectionPlugin() {
        return selectedProjectionPlugin(projectionPlugins);
    }

    private @Nullable ProjectionPlugin selectedProjectionPlugin(List<ProjectionPlugin> candidates) {
        if (selectedProjectionPluginId != null) {
            for (ProjectionPlugin plugin : candidates) {
                if (plugin.id().equals(selectedProjectionPluginId)) {
                    return plugin;
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private void ensureSelectedProjectionPlugin(
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report,
        List<ProjectionPlugin> detailPlugins
    ) {
        if (detailPlugins.isEmpty()) {
            selectedProjectionPluginId = null;
            return;
        }
        if (report != null) {
            for (ProjectionEffect effect : report.effects()) {
                ProjectionPlugin effectPlugin = effect.source().plugin();
                if (effectPlugin.id().equals(selectedProjectionPluginId) && containsPlugin(detailPlugins, effectPlugin.id())) {
                    return;
                }
            }
            for (ProjectionEffect effect : report.effects()) {
                ProjectionPlugin effectPlugin = effect.source().plugin();
                for (ProjectionPlugin plugin : detailPlugins) {
                    if (plugin.id().equals(effectPlugin.id())) {
                        selectedProjectionPluginId = plugin.id();
                        return;
                    }
                }
            }
        }
        if (selectedProjectionPluginId != null && containsPlugin(detailPlugins, selectedProjectionPluginId)) {
            return;
        }
        for (ProjectionPlugin plugin : detailPlugins) {
            if (!optionsForPlugin(plugin, projection, report).isEmpty()) {
                selectedProjectionPluginId = plugin.id();
                return;
            }
        }
        selectedProjectionPluginId = detailPlugins.getFirst().id();
    }

    private @Nullable ProjectionPlugin projectionPluginById(Namespace id) {
        for (ProjectionPlugin plugin : projectionPlugins) {
            if (plugin.id().equals(id)) {
                return plugin;
            }
        }
        return null;
    }

    private static boolean containsPlugin(List<ProjectionPlugin> plugins, Namespace id) {
        for (ProjectionPlugin plugin : plugins) {
            if (plugin.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private List<ProjectionContextOption<?>> optionsForPlugin(ProjectionPlugin plugin) {
        ArrayList<ProjectionContextOption<?>> out = new ArrayList<>();
        for (ProjectionContextOption<?> option : projectionContextOptions) {
            if (option.plugin().id().equals(plugin.id())) {
                out.add(option);
            }
        }
        return List.copyOf(out);
    }

    private List<ProjectionContextOption<?>> optionsForPlugin(
        ProjectionPlugin plugin,
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report
    ) {
        if (projection == null) {
            return List.of();
        }
        return visibleProjectionOptionsByPlugin.getOrDefault(plugin.id(), List.of());
    }

    private Map<Namespace, List<ProjectionContextOption<?>>> relevantProjectionOptionsByPlugin(
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report
    ) {
        if (projection == null || projectionPlugins.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Namespace, List<ProjectionContextOption<?>>> out = new LinkedHashMap<>();
        for (ProjectionPlugin plugin : projectionPlugins) {
            if (!projectionMentionsPlugin(projection, plugin)) {
                continue;
            }
            ArrayList<ProjectionContextOption<?>> relevantOptions = new ArrayList<>();
            for (ProjectionContextOption<?> option : optionsForPlugin(plugin)) {
                if (projectionContextOptionRelevant(option, projection, report)) {
                    relevantOptions.add(option);
                }
            }
            out.put(plugin.id(), List.copyOf(relevantOptions));
        }
        return Map.copyOf(out);
    }

    private List<ProjectionPlugin> relevantProjectionPlugins(
        @Nullable RecipeProjection projection,
        @Nullable ProjectionReport report
    ) {
        if (projection == null || projectionPlugins.isEmpty()) {
            return List.of();
        }
        ArrayList<ProjectionPlugin> out = new ArrayList<>();
        for (ProjectionPlugin plugin : projectionPlugins) {
            if (projectionPluginRelevant(plugin, projection, report)) {
                out.add(plugin);
            }
        }
        return List.copyOf(out);
    }

    private boolean projectionPluginRelevant(
        ProjectionPlugin plugin,
        RecipeProjection projection,
        @Nullable ProjectionReport report
    ) {
        if (!projectionMentionsPlugin(projection, plugin)) {
            return false;
        }
        if (reportHasPluginEffects(report, plugin)) {
            return true;
        }
        if (projectionReportHasPluginEffects(projection, plugin, Map.of(plugin.id(), true))) {
            return true;
        }
        return !visibleProjectionOptionsByPlugin.getOrDefault(plugin.id(), List.of()).isEmpty();
    }

    private boolean projectionContextOptionRelevant(
        ProjectionContextOption<?> option,
        RecipeProjection projection,
        @Nullable ProjectionReport report
    ) {
        if (!projectionMentionsPlugin(projection, option.plugin())) {
            return false;
        }
        ProjectionPluginReport currentReport = report == null
            ? ProjectionPluginReport.empty(option.plugin())
            : report.pluginReport(option.plugin());
        for (Object value : optionProbeValues(option)) {
            ProjectionReport probe = projectionReport(projection, projectionContext(Map.of(
                option.plugin().id(), true,
                option.id(), value
            )));
            if (probe != null && !probe.pluginReport(option.plugin()).effects().equals(currentReport.effects())) {
                return true;
            }
        }
        return false;
    }

    private List<Object> optionProbeValues(ProjectionContextOption<?> option) {
        Object current = projectionContextValues.get(option.id());
        ArrayList<Object> values = new ArrayList<>();
        if (option instanceof BooleanProjectionContextOption booleanOption) {
            values.add(!booleanOption.normalize(current));
        } else if (option instanceof LongProjectionContextOption longOption) {
            long normalized = longOption.normalize(current);
            addDistinctProbe(values, normalized + longOption.step(), longOption);
            addDistinctProbe(values, normalized - longOption.step(), longOption);
            addDistinctProbe(values, longOption.max(), longOption);
            addDistinctProbe(values, longOption.min(), longOption);
        } else if (option instanceof ChoiceProjectionContextOption<?> choiceOption) {
            Object normalized = choiceOption.normalize(current);
            for (ProjectionContextChoice<?> choice : choiceOption.choices()) {
                if (!java.util.Objects.equals(choice.value(), normalized)) {
                    values.add(choice.value());
                }
            }
        }
        return List.copyOf(values);
    }

    private static void addDistinctProbe(List<Object> values, long raw, LongProjectionContextOption option) {
        long normalized = option.normalize(raw);
        if (!values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private boolean projectionReportHasPluginEffects(
        RecipeProjection projection,
        ProjectionPlugin plugin,
        Map<Namespace, Object> overrides
    ) {
        ProjectionReport probe = projectionReport(projection, projectionContext(overrides));
        return reportHasPluginEffects(probe, plugin);
    }

    private static boolean reportHasPluginEffects(@Nullable ProjectionReport report, ProjectionPlugin plugin) {
        return report != null && report.pluginReport(plugin).hasEffects();
    }

    private static boolean projectionMentionsPlugin(RecipeProjection projection, ProjectionPlugin plugin) {
        for (var clause : projection.clauses().allClauses()) {
            if (clause.plugin().id().equals(plugin.id())) {
                return true;
            }
        }
        for (ClausedContent<InputContent> input : projection.inputs()) {
            for (var clause : input.clauses().allClauses()) {
                if (clause.plugin().id().equals(plugin.id())) {
                    return true;
                }
            }
        }
        for (ClausedContent<OutputContent> output : projection.outputs()) {
            for (var clause : output.clauses().allClauses()) {
                if (clause.plugin().id().equals(plugin.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ProjectionEffect> effectsForPlugin(@Nullable ProjectionReport report, ProjectionPlugin plugin) {
        if (report == null) {
            return List.of();
        }
        ArrayList<ProjectionEffect> out = new ArrayList<>();
        for (ProjectionEffect effect : report.effects()) {
            if (effect.source().plugin().id().equals(plugin.id())) {
                out.add(effect);
            }
        }
        return List.copyOf(out);
    }

    private static List<ProjectionEffect> effectsForChannel(ProjectionPluginReport report, EffectChannel channel) {
        ArrayList<ProjectionEffect> out = new ArrayList<>();
        for (ProjectionEffect effect : report.effects()) {
            if (effectChannel(effect) == channel) {
                out.add(effect);
            }
        }
        return List.copyOf(out);
    }

    private static List<ProjectionEffect> uncategorizedEffects(ProjectionPluginReport report) {
        ArrayList<ProjectionEffect> out = new ArrayList<>();
        for (ProjectionEffect effect : report.effects()) {
            if (!isContentEffect(effect)) {
                out.add(effect);
            }
        }
        return List.copyOf(out);
    }

    private Tooltip projectionContextOptionTooltip(ProjectionContextOption<?> option) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(Component.translatable(option.labelKey()), Tooltip.title);
        if (!option.descriptionKey().isBlank()) {
            tooltip.add(Component.translatable(option.descriptionKey()));
        }
        if (option instanceof BooleanProjectionContextOption booleanOption) {
            boolean enabled = booleanOption.normalize(projectionContextValues.get(booleanOption.id()));
            tooltip.add(CirrusLangs.Calculator.Settings.switchState.component(
                (enabled
                    ? CirrusLangs.Calculator.Settings.switchOn
                    : CirrusLangs.Calculator.Settings.switchOff).string()
            ));
        }
        return tooltip;
    }

    private Tooltip projectionPluginTooltip(ProjectionPlugin plugin, ProjectionPluginReport report) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(Component.translatable(plugin.labelKey()), Tooltip.title);
        if (!plugin.descriptionKey().isBlank()) {
            tooltip.add(Component.translatable(plugin.descriptionKey()));
        }
        tooltip.add(CirrusLangs.Calculator.Settings.switchState.component(
            (projectionPluginEnabled(plugin)
                ? CirrusLangs.Calculator.Settings.switchOn
                : CirrusLangs.Calculator.Settings.switchOff).string()
        ));
        tooltip.add(CirrusLangs.Calculator.Database.pluginEffects.component(report.effectCount()));
        tooltip.add(CirrusLangs.Calculator.Database.effectInputChanges.component(report.inputChangeCount()));
        tooltip.add(CirrusLangs.Calculator.Database.effectOutputChanges.component(report.outputChangeCount()));
        return tooltip;
    }

    private void addSourceOverviewPage() {
        visibleResultCount = 0;
        visibleSystemRecipeSelectionCount = 0;
        visibleRecipeDetailEffectCount = 0;
        visibleRecipeDetailPluginCount = 0;
        visibleRecipeDetailSectionCount = 0;
        if (!databaseScopedIndex || systems.isEmpty()) {
            addStatus(CirrusLangs.Calculator.Database.empty.component(), textMuted);
            return;
        }
        CirrusSectionWidget section = resultSection(CirrusLangs.Calculator.Database.sources.component());
        results.addWidget(section);
        section.body().useStyle(
            flexColumn(),
            gap(4),
            padding(sourceSectionBodyPadding),
            minWidth(0),
            boxSizingBorderBox(),
            background(CirrusTextures.Background.flat)
        );
        WidgetGroup<Widget> header = new WidgetGroup<>();
        header.useStyle(flexRow(), alignItemsCenter(), gap(4), minWidth(0));
        CirrusButtonWidget back = backButton(this::leaveSourcesPage);
        back.useStyle(sizeOf(detailIconButtonSize, detailIconButtonSize), flexShrink(0));
        header.addWidget(back);
        CirrusFittedTextWidget active = new CirrusFittedTextWidget(
            CirrusLangs.Calculator.Database.active.component(displayName(workspace.activeDatabase()).getString()),
            textMuted
        ).textPadding(2, 1);
        active.useStyle(flexGrow(1), heightOf(12), minWidth(0));
        header.addWidget(active);
        section.body().addWidget(header);
        VirtualizedSourceGroupsWidget groups = new VirtualizedSourceGroupsWidget(groupedSourcesByRoot(searchBox.text()));
        groups.useStyle(
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            flexShrink(0)
        );
        section.body().addWidget(groups);
    }

    private Map<String, List<Namespace>> groupedSourcesByRoot() {
        return groupedSourcesByRoot("");
    }

    private Map<String, List<Namespace>> groupedSourcesByRoot(String query) {
        LinkedHashMap<String, List<Namespace>> groups = new LinkedHashMap<>();
        String[] tokens = systemQueryTokens(query);
        for (RecipeSystem<?> system : sortedSystems()) {
            Namespace source = system.systemId();
            if (!matchesSystemQuery(source, tokens)) {
                continue;
            }
            groups.computeIfAbsent(source.root(), ignored -> new ArrayList<>()).add(source);
        }
        return groups;
    }

    private static String[] systemQueryTokens(String query) {
        if (query == null || query.isBlank()) {
            return new String[0];
        }
        String[] raw = query.toLowerCase(Locale.ROOT).split("\\s+");
        ArrayList<String> tokens = new ArrayList<>(raw.length);
        for (String token : raw) {
            String normalized = normalizeSystemQueryToken(token);
            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
        }
        return tokens.toArray(String[]::new);
    }

    private static String normalizeSystemQueryToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String value = token.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("-") || value.startsWith("!")) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            value = value.substring(colon + 1);
        } else if (!value.isEmpty() && "@$#&%".indexOf(value.charAt(0)) >= 0) {
            value = value.substring(1);
        }
        return value;
    }

    private static boolean matchesSystemQuery(Namespace source, String[] tokens) {
        if (tokens.length == 0) {
            return true;
        }
        String full = source.toString().toLowerCase(Locale.ROOT);
        String root = source.root().toLowerCase(Locale.ROOT);
        String path = source.path().toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!full.contains(token) && !root.contains(token) && !path.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static int systemSourceRowWidth(Namespace source) {
        int pathWidth = estimatedNamespaceTextWidth(source.path())
            + systemSourceLabelPaddingLeft
            + systemSourceLabelPaddingRight
            + systemSourceRowPadding * 2;
        int controlsWidth = estimatedNamespaceTextWidth(source.root())
            + systemSourceLabelPaddingLeft
            + 1
            + CirrusCheckboxWidget.defaultSize()
            + 16
            + 2 * 2
            + systemSourceRowPadding * 2;
        return Math.clamp(
            Math.max(pathWidth, controlsWidth),
            systemSourceRowMinWidth,
            systemSourceRowMaxWidth
        );
    }

    private static int estimatedNamespaceTextWidth(String value) {
        return value == null ? 0 : value.length() * 6;
    }

    final class VirtualizedSourceGroupsWidget extends WidgetGroup<Widget> {
        private static final int groupGap = 6;
        private static final int headerGap = 2;
        private static final int sourceRowGap = systemSourceGridGap;
        private static final int sourceRowInnerPadding = 3;
        private static final int groupHeaderPadding = 3;
        private static final int groupBulkButtonWidth = 18;
        private static final int groupBulkButtonHeight = 14;
        private static final int groupToggleWidth = 18;
        private static final int groupToggleHeight = 14;
        private static final int openButtonWidth = 16;
        private static final int openButtonHeight = 14;
        private final List<SourceGroupData> groups;
        private final LinkedHashSet<String> collapsedGroups = new LinkedHashSet<>();

        private VirtualizedSourceGroupsWidget(Map<String, List<Namespace>> groupedSources) {
            ArrayList<SourceGroupData> out = new ArrayList<>(groupedSources.size());
            for (Map.Entry<String, List<Namespace>> entry : groupedSources.entrySet()) {
                out.add(new SourceGroupData(entry.getKey(), List.copyOf(entry.getValue())));
            }
            this.groups = List.copyOf(out);
            useStyle(
                flexColumn(),
                gap(groupGap),
                minWidth(0),
                padding(systemSourceGroupPadding),
                boxSizingBorderBox(),
                flexShrink(0)
            );
            rebuild();
        }

        private void rebuild() {
            clear();
            for (SourceGroupData group : groups) {
                addWidget(createGroupWidget(group));
            }
        }

        private WidgetGroup<Widget> createGroupWidget(SourceGroupData group) {
            WidgetGroup<Widget> container = new WidgetGroup<>();
            container.useStyle(flexColumn(), gap(headerGap), minWidth(0), flexShrink(0));
            container.addWidget(createGroupHeader(group));

            WidgetGroup<Widget> rows = new WidgetGroup<>();
            rows.useStyle(flexColumn(), gap(sourceRowGap), minWidth(0), flexShrink(0));
            if (collapsedGroups.contains(group.root())) {
                rows.useStyle(displayNone());
                rows.setVisible(false).setActive(false).setInteractive(false);
            }
            for (Namespace source : group.sources()) {
                rows.addWidget(createSourceRow(source));
            }
            container.addWidget(rows);
            return container;
        }

        private CirrusRowWidget createGroupHeader(SourceGroupData group) {
            CirrusRowWidget header = CirrusRowWidget.flat(systemSourceGroupHeaderHeight, 4);
            header.useStyle(
                widthOf(TaffyDimension.percent(1f)),
                heightOf(systemSourceGroupHeaderHeight),
                minWidth(0),
                padding(groupHeaderPadding),
                boxSizingBorderBox(),
                flexShrink(0)
            );
            CirrusFittedTextWidget title = new CirrusFittedTextWidget(Component.literal(group.root()), textPrimary);
            title.textPadding(1, 1);
            title.useStyle(flexGrow(1), heightOf(10), minWidth(0));
            header.addWidget(title);

            CirrusFittedTextWidget count = new CirrusFittedTextWidget(Component.literal(String.valueOf(group.sources().size())), textMuted);
            count.textPadding(1, 1);
            count.useStyle(flexShrink(0), heightOf(10), minWidth(0));
            header.addWidget(count);

            CirrusButtonWidget bulk = new CirrusButtonWidget(groupBulkButtonWidth, groupBulkButtonHeight)
                .iconSupplier(() -> sourceGroupAllEnabled(group.sources())
                    ? CirrusTextures.Icon.delete
                    : CirrusTextures.Icon.Small.plus)
                .tooltip(() -> sourceGroupBulkTooltip(group.root(), group.sources()))
                .clickSound(true)
                .onPress(() -> toggleSystemSourceGroup(group.root()));
            bulk.useStyle(sizeOf(groupBulkButtonWidth, groupBulkButtonHeight), flexShrink(0));
            header.addWidget(bulk);

            CirrusButtonWidget toggle = new CirrusButtonWidget(groupToggleWidth, groupToggleHeight)
                .iconSupplier(() -> collapsedGroups.contains(group.root())
                    ? CirrusTextures.Icon.Small.arrowRight
                    : CirrusTextures.Icon.arrowDown)
                .tooltip(() -> sourceGroupTooltip(group.root(), group.sources()))
                .clickSound(true)
                .onPress(() -> toggleSourceGroupCollapsed(group.root()));
            toggle.useStyle(sizeOf(groupToggleWidth, groupToggleHeight), flexShrink(0));
            header.addWidget(toggle);
            return header;
        }

        private CirrusRowWidget createSourceRow(Namespace source) {
            CirrusRowWidget row = CirrusRowWidget.selectable(systemSourceControlHeight, 4);
            row.useStyle(
                widthOf(TaffyDimension.percent(1f)),
                heightOf(systemSourceControlHeight),
                minWidth(0),
                padding(sourceRowInnerPadding),
                boxSizingBorderBox(),
                flexShrink(0)
            );
            row.selected(() -> workspace.activeDatabase().sourceEnabled(source));
            row.onPress(ignored -> toggleSystemSource(source));

            WidgetGroup<Widget> labels = new WidgetGroup<>();
            labels.useStyle(flexColumn(), gap(0), flexGrow(1), minWidth(0));

            CirrusFittedTextWidget path = new CirrusFittedTextWidget(Component.literal(source.path()), textPrimary);
            path.textPadding(1, 1);
            path.useStyle(heightOf(11), minWidth(0));
            labels.addWidget(path);

            CirrusFittedTextWidget root = new CirrusFittedTextWidget(Component.literal(source.root()), textMuted);
            root.textPadding(1, 1);
            root.useStyle(heightOf(10), minWidth(0));
            labels.addWidget(root);
            row.addWidget(labels);

            CirrusCheckboxWidget checkbox = new CirrusCheckboxWidget(
                () -> workspace.activeDatabase().sourceEnabled(source),
                selected -> setSystemSourceEnabled(source, selected)
            );
            checkbox.useStyle(sizeOf(CirrusCheckboxWidget.defaultSize(), CirrusCheckboxWidget.defaultSize()), flexShrink(0));
            row.addWidget(checkbox);

            CirrusButtonWidget open = new CirrusButtonWidget(openButtonWidth, openButtonHeight)
                .icon(CirrusTextures.Icon.Small.arrowRight, CirrusTextures.Icon.Small.arrowRightSelected)
                .tooltip(() -> sourceRecipePageTooltip(source))
                .clickSound(true)
                .onPress(() -> openSystemRecipeSource(source));
            open.useStyle(sizeOf(openButtonWidth, openButtonHeight), flexShrink(0));
            row.addWidget(open);
            return row;
        }

        public int debugGroupCount() {
            return groups.size();
        }

        public int debugSourceCount() {
            int count = 0;
            for (SourceGroupData group : groups) {
                count += group.sources().size();
            }
            return count;
        }

        public int debugContentHeight() {
            return contentHeight();
        }

        void debugSetSize(int width, int height) {
            setSize(width, height);
            rebuild();
        }

        private int contentHeight() {
            int total = 0;
            for (SourceGroupData group : groups) {
                if (total > 0) {
                    total += groupGap;
                }
                total += systemSourceGroupHeaderHeight + headerGap;
                if (!collapsedGroups.contains(group.root())) {
                    int sourceCount = group.sources().size();
                    if (sourceCount > 0) {
                        total += sourceCount * systemSourceControlHeight + Math.max(0, sourceCount - 1) * sourceRowGap;
                    }
                }
            }
            return total + systemSourceGroupPadding * 2;
        }

        private Tooltip sourceGroupTooltip(String root, List<Namespace> sources) {
            Tooltip tooltip = Tooltip.create();
            tooltip.add(ExecutionOverlayUi.tooltipTitleText(root));
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sourcesDesc));
            tooltip.add(ExecutionOverlayUi.tooltipAccentText(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.active, String.valueOf(sources.size()))
            ));
            return tooltip;
        }

        private Tooltip sourceGroupBulkTooltip(String root, List<Namespace> sources) {
            boolean allEnabled = sourceGroupAllEnabled(sources);
            Tooltip tooltip = Tooltip.create();
            tooltip.add(ExecutionOverlayUi.tooltipTitle(
                allEnabled
                    ? CirrusLangs.Calculator.Database.bulkClear
                    : CirrusLangs.Calculator.Database.bulkSelect
            ));
            tooltip.add(ExecutionOverlayUi.tooltipBody(
                allEnabled
                    ? CirrusLangs.Calculator.Database.bulkClearDesc
                    : CirrusLangs.Calculator.Database.bulkSelectDesc
            ));
            tooltip.add(ExecutionOverlayUi.tooltipAccentText(root));
            return tooltip;
        }

        private boolean sourceGroupAllEnabled(List<Namespace> sources) {
            if (sources.isEmpty()) {
                return false;
            }
            CustomRecipeDatabase active = workspace.activeDatabase();
            for (Namespace source : sources) {
                if (!active.sourceEnabled(source)) {
                    return false;
                }
            }
            return true;
        }

        private void toggleSourceGroupCollapsed(String root) {
            if (!collapsedGroups.remove(root)) {
                collapsedGroups.add(root);
            }
            rememberScrollPosition();
            requestApplyQueryWithSavedScroll();
        }

        private record SourceGroupData(String root, List<Namespace> sources) {
        }
    }

    private void addSystemRecipeSourcePage(Namespace source) {
        visibleResultCount = 0;
        visibleSystemRecipeSelectionCount = 0;
        systemRecipeDragSelection = null;
        systemRecipeDragged = false;
        if (!databaseScopedIndex) {
            visibleSystemRecipeSelectionCount = 0;
            return;
        }

        List<SystemRecipeSelection> selections;
        try {
            selections = systemRecipeSelections(source);
        } catch (SearchParseException ex) {
            addStatus(CirrusLangs.Calculator.Database.invalidQuery.component(), 0xFFFF7777);
            return;
        }

        CirrusSectionWidget section = resultSection(Component.literal(source.toString()));
        results.addWidget(section);
        section.body().useStyle(flexColumn(), gap(2), minWidth(0));

        WidgetGroup<Widget> header = new WidgetGroup<>();
        header.useStyle(flexRow(), gap(3), minWidth(0), alignItemsCenter());
        CirrusButtonWidget back = backButton(this::leaveSystemRecipeSource);
        back.useStyle(sizeOf(detailIconButtonSize, detailIconButtonSize), flexShrink(0));
        header.addWidget(back);
        header.addWidget(new Widget().useStyle(flexGrow(1), minWidth(0)));
        header.addWidget(systemRecipeBulkButton(selections, true));
        header.addWidget(systemRecipeBulkButton(selections, false));
        section.body().addWidget(header);

        visibleSystemRecipeSelectionCount = selections.size();
        if (selections.isEmpty()) {
            section.body().addWidget(CirrusText.label(
                CirrusLangs.Calculator.Database.empty.component(),
                textMuted
            ));
            return;
        }
        SystemRecipeSelectionListWidget list = new SystemRecipeSelectionListWidget(selections);
        list.useStyle(
            widthOf(TaffyDimension.percent(1f)),
            heightOf(systemRecipeSelectionListHeight(selections.size())),
            minWidth(0),
            flexShrink(0)
        );
        section.body().addWidget(list);
    }

    private CirrusButtonWidget systemRecipeBulkButton(List<SystemRecipeSelection> selections, boolean selected) {
        CirrusButtonWidget button = new CirrusButtonWidget(systemRecipeActionButtonWidth, controlHeight)
            .icon(
                selected ? CirrusTextures.Icon.Small.plus : CirrusTextures.Icon.delete,
                selected ? CirrusTextures.Icon.Small.plusSelected : CirrusTextures.Icon.deleteSelected
            )
            .tooltip(() -> systemRecipeBulkTooltip(selected))
            .clickSound(true)
            .onPress(() -> setRecipeSelectionsSelected(selections, selected));
        button.useStyle(sizeOf(systemRecipeActionButtonWidth, controlHeight), flexShrink(0));
        return button;
    }

    final class SystemRecipeSelectionListWidget extends Widget {
        private final List<SystemRecipeSelection> selections;
        private final RecipeContentVisualCache visualCache = RecipeContentVisualCache.create();

        private SystemRecipeSelectionListWidget(List<SystemRecipeSelection> selections) {
            this.selections = selections;
            onMouseClicked((input, context) -> {
                if (input.isLeftClick()) {
                    systemRecipeDragSelection = null;
                    systemRecipeDragged = false;
                }
                return EventDispatch.pass;
            });
            onMouseDragged((input, deltaX, deltaY, context) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                SystemRecipeSelection selection = selectionAt(input.mouseRelative(this).y());
                if (selection == null) {
                    return EventDispatch.pass;
                }
                boolean selected = systemRecipeDragSelection != null
                    ? systemRecipeDragSelection
                    : !workspace.activeDatabase().contains(selection.ref());
                systemRecipeDragSelection = selected;
                systemRecipeDragged = true;
                applyRecipeSelection(selection.ref(), selected, false);
                return EventDispatch.consumed;
            });
            onMouseReleased((input, context) -> {
                if (input.isLeftClick()) {
                    systemRecipeDragSelection = null;
                }
                return EventDispatch.pass;
            });
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                if (systemRecipeDragged) {
                    systemRecipeDragged = false;
                    context.consume();
                    return EventDispatch.consumed;
                }
                var local = input.mouseRelative(this);
                SystemRecipeSelection selection = selectionAt(local.y());
                if (selection == null) {
                    return EventDispatch.pass;
                }
                boolean selected = !workspace.activeDatabase().contains(selection.ref());
                applyRecipeSelection(selection.ref(), selected, true);
                context.consume();
                return EventDispatch.consumed;
            });
        }

        int selectionCount() {
            return selections.size();
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            super.renderInternal(canvas, mouseX, mouseY, partialTicks);
            if (selections.isEmpty() || width() <= 0 || height() <= 0) {
                return;
            }
            int first = firstVisibleRow(canvas);
            int last = lastVisibleRow(canvas);
            if (last < first) {
                return;
            }
            Font font = context().font();
            CustomRecipeDatabase active = workspace.activeDatabase();
            for (int index = first; index <= last; index++) {
                renderRowChrome(canvas, active, selections.get(index), index, mouseX, mouseY);
            }
            renderRowTextBatch(canvas, font, first, last);
            renderContentBatch(canvas, first, last, partialTicks);
        }

        @Override
        public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
            int row = rowIndexAt(mouseY);
            if (row < 0 || row >= selections.size()) {
                return super.hoverTooltip(mouseX, mouseY);
            }
            SystemRecipeSelection selection = selections.get(row);
            RowLayout layout = rowLayout(selection, row);
            Tooltip outputsTooltip = RecipeContentSlotsWidget.tooltipAt(
                selection.outputPreviews(),
                mouseX - layout.outputX(),
                mouseY - layout.outputY(),
                listOutputSlotLimit,
                listOutputSlotGap
            );
            if (outputsTooltip != null) {
                return outputsTooltip;
            }
            return systemRecipeSelectionTooltip(selection.record(), selection.ref());
        }

        private void renderRowChrome(
            SceneCanvas canvas,
            CustomRecipeDatabase active,
            SystemRecipeSelection selection,
            int index,
            int mouseX,
            int mouseY
        ) {
            int rowY = rowTop(index);
            boolean rowHovered = mouseX >= 0 && mouseX < width() && mouseY >= rowY && mouseY < rowY + systemRecipeSelectionRowHeight;
            boolean selected = active.contains(selection.ref()) || active.sourceEnabled(selection.ref().system());
            CirrusControlChrome.renderButtonBackground(canvas, 0, rowY, width(), systemRecipeSelectionRowHeight, selected, rowHovered, true);

            int actionX = Math.max(systemRecipeSelectionRowPadding, width() - systemRecipeSelectionRowPadding - systemRecipeActionButtonWidth);
            int actionY = rowY + Math.max(0, (systemRecipeSelectionRowHeight - controlHeight) / 2);
            boolean actionHovered = rowHovered
                && mouseX >= actionX
                && mouseX < actionX + systemRecipeActionButtonWidth
                && mouseY >= actionY
                && mouseY < actionY + controlHeight;
            renderAction(canvas, actionX, actionY, active.contains(selection.ref()), actionHovered);
        }

        private void renderRowTextBatch(SceneCanvas canvas, Font font, int first, int last) {
            canvas.textBatch(text -> {
                for (int index = first; index <= last; index++) {
                    SystemRecipeSelection selection = selections.get(index);
                    RowLayout layout = rowLayout(selection, index);
                    int textY = layout.rowY() + 4;
                    text.drawStringClipped(selection.fittedLabel(font, layout.textWidth()), layout.textX(), textY, layout.textWidth(), textPrimary, false);
                    text.drawStringClipped(
                        fitText(font, systemRecipeSelectionStatus(selection.ref()).getString(), layout.textWidth()),
                        layout.textX(),
                        textY + 11,
                        layout.textWidth(),
                        systemRecipeSelectionStatusColor(selection.ref()),
                        false
                    );
                }
            });
        }

        private void renderContentBatch(SceneCanvas canvas, int first, int last, float partialTicks) {
            if (first > last) {
                return;
            }
            for (int index = first; index <= last; index++) {
                SystemRecipeSelection selection = selections.get(index);
                RowLayout layout = rowLayout(selection, index);
                if (layout.outputWidth() <= 0) {
                    continue;
                }
                RecipeContentSlotsWidget.renderBackgrounds(
                    canvas,
                    selection.outputPreviews(),
                    layout.outputX(),
                    layout.outputY(),
                    listOutputSlotLimit,
                    listOutputSlotGap,
                    true
                );
            }
            BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch.create(canvas.layeredGraphics(), partialTicks);
            for (int index = first; index <= last; index++) {
                SystemRecipeSelection selection = selections.get(index);
                RowLayout layout = rowLayout(selection, index);
                if (layout.outputWidth() <= 0) {
                    continue;
                }
                RecipeContentSlotsWidget.submitContents(
                    batch,
                    visualCache,
                    selection.outputPreviews(),
                    layout.outputX(),
                    layout.outputY(),
                    listOutputSlotLimit,
                    listOutputSlotGap,
                    true
                );
            }
            batch.flush();
            Font font = context().font();
            canvas.textBatch(text -> {
                for (int index = first; index <= last; index++) {
                    SystemRecipeSelection selection = selections.get(index);
                    RowLayout layout = rowLayout(selection, index);
                    RecipeContentSlotsWidget.drawOverflowIndicator(
                        text,
                        font,
                        selection.outputPreviews(),
                        layout.outputX(),
                        layout.outputY(),
                        listOutputSlotLimit,
                        listOutputSlotGap
                    );
                }
            });
        }

        private RowLayout rowLayout(SystemRecipeSelection selection, int index) {
            int rowY = rowTop(index);
            int actionX = Math.max(systemRecipeSelectionRowPadding, width() - systemRecipeSelectionRowPadding - systemRecipeActionButtonWidth);
            int textX = systemRecipeSelectionRowPadding;
            int outputWidth = listOutputStripWidth(selection.outputPreviews().size());
            int outputX = outputWidth == 0
                ? actionX - systemRecipeSelectionActionGap
                : Math.max(textX, actionX - systemRecipeSelectionActionGap - outputWidth);
            int textWidth = Math.max(0, outputX - textX - systemRecipeSelectionActionGap);
            int outputY = rowY + Math.max(0, (systemRecipeSelectionRowHeight - RecipeContentSlotWidget.slotSize) / 2);
            return new RowLayout(rowY, textX, textWidth, outputX, outputY, outputWidth);
        }

        private void renderAction(SceneCanvas canvas, int x, int y, boolean selected, boolean hovered) {
            CirrusControlChrome.renderButtonBackground(canvas, x, y, systemRecipeActionButtonWidth, controlHeight, false, hovered, true);
            SizedTexture icon = selected
                ? hovered ? CirrusTextures.Icon.Small.minusSelected : CirrusTextures.Icon.Small.minus
                : hovered ? CirrusTextures.Icon.Small.plusSelected : CirrusTextures.Icon.Small.plus;
            int iconX = x + CirrusControlChrome.centeredIconX(systemRecipeActionButtonWidth, icon.width());
            int iconY = y + CirrusControlChrome.centeredIconY(controlHeight, icon.height(), false);
            canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
        }

        private record RowLayout(int rowY, int textX, int textWidth, int outputX, int outputY, int outputWidth) {
        }

        private int firstVisibleRow(SceneCanvas canvas) {
            var clip = canvas.currentClip();
            if (clip == null) {
                return 0;
            }
            var origin = localToScene(0, 0);
            int localTop = Math.max(0, (int) Math.floor(clip.y() - origin.y()));
            return Math.clamp(localTop / rowStride() - 1, 0, selections.size() - 1);
        }

        private int lastVisibleRow(SceneCanvas canvas) {
            var clip = canvas.currentClip();
            if (clip == null) {
                return selections.size() - 1;
            }
            var origin = localToScene(0, 0);
            int localBottom = Math.min(height(), (int) Math.ceil(clip.bottom() - origin.y()));
            return Math.clamp(localBottom / rowStride() + 1, 0, selections.size() - 1);
        }

        private @Nullable SystemRecipeSelection selectionAt(double localY) {
            int row = rowIndexAt(localY);
            return row < 0 ? null : selections.get(row);
        }

        private int rowIndexAt(double localY) {
            if (localY < 0) {
                return -1;
            }
            int stride = rowStride();
            int row = (int) (localY / stride);
            if (row < 0 || row >= selections.size()) {
                return -1;
            }
            int yInRow = (int) (localY - (double) row * stride);
            return yInRow >= 0 && yInRow < systemRecipeSelectionRowHeight ? row : -1;
        }

        private int rowTop(int index) {
            return index * rowStride();
        }

        private int rowStride() {
            return systemRecipeSelectionRowHeight + systemRecipeSelectionRowGap;
        }

    }

    private List<SystemRecipeSelection> systemRecipeSelections(Namespace source) throws SearchParseException {
        ArrayList<RecipeDatabaseResult> filtered = new ArrayList<>();
        for (RecipeDatabaseResult candidate : systemCatalogIndex.search(searchBox.text(), source)) {
            if (source.equals(candidate.record().recipe().systemId())) {
                filtered.add(candidate);
            }
        }

        ArrayList<SystemRecipeSelection> selections = new ArrayList<>();
        for (RecipeDatabaseResult candidate : sort(filtered)) {
            DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(candidate.record().recipe());
            if (ref != null) {
                selections.add(new SystemRecipeSelection(candidate.record(), ref));
            }
        }
        return List.copyOf(selections);
    }

    private List<RecipeSystem<?>> sortedSystems() {
        return systems.stream()
            .sorted(Comparator.comparing(system -> system.systemId().toString()))
            .toList();
    }

    private void addResultSections(List<RecipeDatabaseResult> records) {
        addResultBuckets(buildResultBuckets(records, sortMode));
    }

    private void addResultBuckets(List<ResultCategoryBucket> buckets) {
        if (!buckets.isEmpty()) {
            VirtualizedResultSectionsWidget widget = new VirtualizedResultSectionsWidget(
                CirrusLangs.Calculator.Database.results.component(),
                buckets
            );
            widget.useStyle(
                widthOf(TaffyDimension.percent(1f)),
                heightOf(widget.contentHeight()),
                minWidth(0),
                flexShrink(0)
            );
            results.addWidget(widget);
        }
    }

    private static List<ResultCategoryBucket> buildResultBuckets(List<RecipeDatabaseResult> records, SortMode sortMode) {
        LinkedHashMap<String, ResultCategoryBucket> groups = new LinkedHashMap<>();
        for (RecipeDatabaseResult result : records) {
            ResultCategory category = resultCategory(result.record(), sortMode);
            groups.computeIfAbsent(category.stableKey(), ignored -> new ResultCategoryBucket(category.stableKey(), category.title(), new ArrayList<>()))
                .records()
                .add(result);
        }
        return List.copyOf(groups.values());
    }

    private ResultCategory resultCategory(RecipeDatabaseRecord record) {
        return resultCategory(record, sortMode);
    }

    private static ResultCategory resultCategory(RecipeDatabaseRecord record, SortMode sortMode) {
        return switch (sortMode) {
            case system -> {
                String key = systemGroupKey(record);
                yield new ResultCategory("system:" + key, CirrusLangs.Calculator.Database.systemGroup.component(key));
            }
            case database -> new ResultCategory("database:" + record.database().id(), Component.literal(record.database().name()));
            default -> {
                String key = naturalResultCategoryStatic(record);
                yield new ResultCategory("category:" + key, CirrusLangs.Calculator.Database.systemGroup.component(key));
            }
        };
    }

    private String naturalResultCategory(RecipeDatabaseRecord record) {
        List<Namespace> tags = record.systemTags();
        if (!tags.isEmpty()) {
            return tags.getFirst().toString();
        }
        return record.recipe().systemId().root();
    }

    private static String naturalResultCategoryStatic(RecipeDatabaseRecord record) {
        List<Namespace> tags = record.systemTags();
        if (!tags.isEmpty()) {
            return tags.getFirst().toString();
        }
        return record.recipe().systemId().root();
    }

    private record ResultCategory(String stableKey, Component title) {
    }

    private record ResultCategoryBucket(String stableKey, Component title, ArrayList<RecipeDatabaseResult> records) {
    }

    private int resultBatchHeight(int count) {
        if (count <= 0) {
            return 0;
        }
        if (viewMode == ViewMode.list) {
            return count * listRowHeight + Math.max(0, count - 1) * listRowGap;
        }
        int rows = (count + gridBatchColumns - 1) / gridBatchColumns;
        return rows * gridCardHeight + Math.max(0, rows - 1) * gridGap;
    }

    private int visualIndex(int visualCount) {
        if (visualCount <= 1) {
            return 0;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        return (int) ((tick / 20L) % visualCount);
    }

    private static String fitText(Font font, String value, int available) {
        if (available <= 0 || value == null || value.isBlank()) {
            return "";
        }
        if (font.width(value) <= available) {
            return value;
        }
        int ellipsisWidth = font.width("...");
        if (available <= ellipsisWidth) {
            return "";
        }
        return font.plainSubstrByWidth(value, Math.max(0, available - ellipsisWidth)).stripTrailing() + "...";
    }

    public final class VirtualizedResultSectionsWidget extends Widget {
        private static final int sectionTitleOuterHeight = 0;
        private static final int categoryOuterPadding = resultSectionBodyPadding;
        private static final int categoryInnerPadding = resultCategoryGroupPadding;
        private static final int categoryHeaderGap = 2;
        private static final int categoryGap = 3;
        private static final int initialPlannedCardCapacity = 96;

        private final Component title;
        private final List<VirtualResultCategory> categories;
        private final int contentHeight;
        private final ResultCard[] cardCache;
        private final RecipeContentVisualCache visualCache = RecipeContentVisualCache.create();
        private final RenderPlan renderPlan = new RenderPlan();
        private @Nullable PressedResultCardTarget pressedTarget;
        private boolean pressedTargetDragged;

        private VirtualizedResultSectionsWidget(Component title, List<ResultCategoryBucket> buckets) {
            this.title = title;
            ArrayList<VirtualResultCategory> out = new ArrayList<>(buckets.size());
            int cardOffset = 0;
            int y = sectionTitleOuterHeight;
            for (ResultCategoryBucket bucket : buckets) {
                boolean collapsed = collapsedResultCategories.contains(bucket.stableKey());
                int height = categoryHeight(bucket.records().size(), collapsed);
                out.add(new VirtualResultCategory(bucket.stableKey(), bucket.title(), List.copyOf(bucket.records()), cardOffset, y, height, collapsed));
                cardOffset += bucket.records().size();
                y += height + categoryGap;
            }
            this.categories = List.copyOf(out);
            this.contentHeight = this.categories.isEmpty() ? 0 : y - categoryGap;
            this.cardCache = new ResultCard[cardOffset];
            onMouseClicked((input, context) -> {
                settlePageScrollForInteraction();
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                var local = input.mouseRelative(this);
                VirtualResultCategory category = categoryToggleAt(local.x(), local.y());
                if (category != null) {
                    deferAfterMouseDispatch(() -> toggleResultCategory(category));
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
                ResultHit hit = hitAt(local.x(), local.y());
                if (hit == null) {
                    return EventDispatch.pass;
                }
                pressedTarget = new PressedResultCardTarget(
                    hit.globalIndex(),
                    actionAt(hit, local.x(), local.y()),
                    RecipeDatabaseKeyMappings.openRecipeInCalculatorPressed(input.key())
                );
                pressedTargetDragged = false;
                context.consume();
                return EventDispatch.consumed;
            });
            onMouseDragged((input, deltaX, deltaY, context) -> {
                if (pressedTarget == null) {
                    return EventDispatch.pass;
                }
                if (Math.abs(deltaX) >= 1.0 || Math.abs(deltaY) >= 1.0) {
                    pressedTargetDragged = true;
                }
                return EventDispatch.pass;
            });
            onMouseReleased((input, context) -> {
                settlePageScrollForInteraction();
                if (pressedTarget == null) {
                    return EventDispatch.pass;
                }
                PressedResultCardTarget pressed = pressedTarget;
                pressedTarget = null;
                if (!input.isLeftClick() || pressedTargetDragged) {
                    pressedTargetDragged = false;
                    return EventDispatch.pass;
                }
                pressedTargetDragged = false;
                var local = input.mouseRelative(this);
                ResultHit hit = hitAt(local.x(), local.y());
                if (hit == null || !pressed.matches(hit.globalIndex(), actionAt(hit, local.x(), local.y()))) {
                    return EventDispatch.pass;
                }
                ResultCard card = card(hit);
                if (pressed.action() == null) {
                    deferAfterMouseDispatch(() -> openRecipeDetail(card.record(), true));
                } else {
                    deferAfterMouseDispatch(() -> activateRecipeCard(card.record(), pressed.action(), pressed.openRecipeInCalculator(), true));
                }
                context.consume();
                return EventDispatch.consumed;
            });
            onMouseLeave((mouseX, mouseY, context) -> {
                pressedTarget = null;
                pressedTargetDragged = false;
            });
        }

        private int contentHeight() {
            return contentHeight;
        }

        public int debugContentHeight() {
            return contentHeight;
        }

        public int debugCategoryCount() {
            return categories.size();
        }

        public int debugCardCount() {
            return cardCache.length;
        }

        public boolean debugCategoryCollapsed(int index) {
            return index >= 0 && index < categories.size() && categories.get(index).collapsed();
        }

        public int debugCategoryY(int index) {
            return index >= 0 && index < categories.size() ? categories.get(index).y() : -1;
        }

        public void debugToggleCategory(int index) {
            if (index >= 0 && index < categories.size()) {
                toggleResultCategory(categories.get(index));
            }
        }

        public int debugFirstCategoryY() {
            return categories.isEmpty() ? -1 : categories.getFirst().y();
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            LocalClip clip = localClip(canvas);
            if (categories.isEmpty() || width() <= 0 || height() <= 0 || clip.isEmpty()) {
                return;
            }
            canvas.withClip(sceneClipRect(canvas, clip), () -> renderClipped(canvas, clip, mouseX, mouseY, partialTicks));
        }

        private void renderClipped(SceneCanvas canvas, LocalClip clip, int mouseX, int mouseY, float partialTicks) {
            RenderPlan plan = renderPlan.prepare(clip);
            if (plan.hasVisibleSectionHeader() || plan.hasVisibleCategories() || plan.hasVisibleCards()) {
                renderChrome(canvas, plan, mouseX, mouseY);
                renderSlotBackgrounds(canvas, plan);
                if (plan.hasVisibleCards()) {
                    BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch.create(canvas.layeredGraphics(), partialTicks);
                    submitVisibleSlotContents(batch, plan);
                    batch.flush();
                }
                renderText(canvas, plan);
                renderActions(canvas, plan, mouseX, mouseY);
            }
        }

        private void renderChrome(SceneCanvas canvas, RenderPlan plan, int mouseX, int mouseY) {
            if (plan.hasVisibleSectionHeader()) {
                renderSectionChrome(canvas);
            }
            for (int i = plan.firstCategory(); i <= plan.lastCategory(); i++) {
                VirtualResultCategory category = categories.get(i);
                if (intersects(plan.clip(), category.headerY(), resultCategoryHeaderHeight)) {
                    renderCategoryChrome(canvas, category);
                }
            }
            for (int i = 0; i < plan.cardCount(); i++) {
                PlannedCard card = plan.card(i);
                renderCardBackground(canvas, card, mouseX, mouseY);
            }
        }

        @Override
        public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
            ResultHit hit = hitAt(mouseX, mouseY);
            if (hit == null) {
                return super.hoverTooltip(mouseX, mouseY);
            }
            @Nullable ResultCardAction action = actionAt(hit, mouseX, mouseY);
            ResultCard card = card(hit);
            if (action != null) {
                return recipeActionTooltip(card.record(), action);
            }
            Tooltip outputsTooltip = outputTooltipAt(hit, card, mouseX, mouseY);
            return outputsTooltip == null ? recipeTooltip(card.record()) : outputsTooltip;
        }

        private void renderSectionChrome(SceneCanvas canvas) {
            canvas.texture(CirrusTextures.Background.Border.dark, 0, 0, width(), sectionHeaderHeight);
        }

        private void drawSectionText(SceneCanvas.TextBatch text, Font font) {
            text.drawStringClipped(
                fitText(font, title.getString(), Math.max(0, width() - sectionHeaderPaddingLeft - sectionHeaderPaddingRight)),
                sectionHeaderPaddingLeft,
                4,
                Math.max(0, width() - sectionHeaderPaddingLeft - sectionHeaderPaddingRight),
                textPrimary,
                false
            );
        }

        private void renderCategoryChrome(SceneCanvas canvas, VirtualResultCategory category) {
            int bodyX = categoryInnerX();
            int bodyWidth = categoryInnerWidth();
            canvas.texture(
                CirrusTextures.Background.Border.light,
                bodyX,
                category.headerY(),
                bodyWidth,
                resultCategoryHeaderHeight
            );
        }

        private void renderText(SceneCanvas canvas, RenderPlan plan) {
            Font font = context().font();
            canvas.textBatch(text -> {
                if (plan.hasVisibleSectionHeader()) {
                    drawSectionText(text, font);
                }
                for (int categoryIndex = plan.firstCategory(); categoryIndex <= plan.lastCategory(); categoryIndex++) {
                    VirtualResultCategory category = categories.get(categoryIndex);
                    if (intersects(plan.clip(), category.headerY(), resultCategoryHeaderHeight)) {
                        drawCategoryText(text, font, category);
                    }
                }
                for (int i = 0; i < plan.cardCount(); i++) {
                    PlannedCard visible = plan.card(i);
                    ResultCard card = visible.card();
                    if (viewMode == ViewMode.grid) {
                        drawHighlightedText(text, font, plan.clip(), card, card.fittedShortLabel(font, visible.textWidth()), visible.textX(), visible.y() + 8, visible.textWidth());
                        drawVisibleText(text, font, plan.clip(), card.fittedSystemLabel(font, visible.textWidth()), visible.textX(), visible.y() + 22, visible.textWidth(), textMuted);
                    } else {
                        drawHighlightedText(text, font, plan.clip(), card, card.fittedShortLabel(font, visible.textWidth()), visible.textX(), visible.y() + 5, visible.textWidth());
                        drawVisibleText(text, font, plan.clip(), card.fittedRecipeIdLabel(font, visible.textWidth()), visible.textX(), visible.y() + 17, visible.textWidth(), textMuted);
                    }
                    drawSlotOverflow(text, font, visible, plan.clip());
                }
            });
        }

        private void drawCategoryText(SceneCanvas.TextBatch text, Font font, VirtualResultCategory category) {
            int bodyX = categoryInnerX();
            int bodyWidth = categoryInnerWidth();
            int titleX = bodyX + resultCategoryHeaderPaddingLeft;
            int countRight = categoryToggleX() - resultCategoryToggleGap;
            int countAvailable = Math.max(0, countRight - titleX);
            String countLabel = String.valueOf(category.records().size());
            String fittedCount = fitText(font, countLabel, countAvailable);
            int countX = Math.max(titleX, countRight - font.width(fittedCount));
            int titleWidth = Math.max(0, countX - resultCategoryCountPadding - titleX);
            text.drawStringClipped(
                fitText(font, category.title().getString(), titleWidth),
                titleX,
                category.headerY() + 4,
                titleWidth,
                textPrimary,
                false
            );
            int countY = category.headerY() + Math.max(0, (resultCategoryHeaderHeight - resultCategoryCountHeight) / 2);
            text.drawString(fittedCount, countX, countY + 3, textMuted, false);
        }

        private void renderSlotBackgrounds(SceneCanvas canvas, RenderPlan plan) {
            for (int i = 0; i < plan.cardCount(); i++) {
                PlannedCard card = plan.card(i);
                RecipeContentSlotsWidget.renderBackgrounds(canvas, card.outputs(), card.slotX(), card.slotY(), card.slotLimit(), card.slotGap(), true);
            }
        }

        private void renderActions(SceneCanvas canvas, RenderPlan plan, int mouseX, int mouseY) {
            for (int categoryIndex = plan.firstCategory(); categoryIndex <= plan.lastCategory(); categoryIndex++) {
                VirtualResultCategory category = categories.get(categoryIndex);
                if (intersects(plan.clip(), category.headerY(), resultCategoryHeaderHeight)) {
                    renderCategoryToggle(canvas, category, mouseX, mouseY);
                }
            }
            for (int i = 0; i < plan.cardCount(); i++) {
                PlannedCard card = plan.card(i);
                for (ResultCardAction action : ResultCardAction.buttons()) {
                    ActionRenderState state = actionRenderState(card, action, mouseX, mouseY);
                    CirrusControlChrome.renderButtonBackground(
                        canvas,
                        state.x(),
                        card.actionY(),
                        resultCardActionButtonSize,
                        resultCardActionButtonSize,
                        state.down(),
                        state.hovered(),
                        true
                    );
                }
            }
            for (ResultCardAction action : ResultCardAction.buttons()) {
                SizedTexture icon = action.icon();
                for (int i = 0; i < plan.cardCount(); i++) {
                    PlannedCard card = plan.card(i);
                    ActionRenderState state = actionRenderState(card, action, mouseX, mouseY);
                    int iconX = state.x() + CirrusControlChrome.centeredIconX(resultCardActionButtonSize, icon.width());
                    int iconY = card.actionY() + CirrusControlChrome.centeredIconY(resultCardActionButtonSize, icon.height(), state.down());
                    canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
                }
            }
        }

        private void renderCategoryToggle(SceneCanvas canvas, VirtualResultCategory category, int mouseX, int mouseY) {
            int x = categoryToggleX();
            int y = category.headerY() + Math.max(0, (resultCategoryHeaderHeight - resultCategoryToggleSize) / 2);
            boolean hovered = mouseX >= x
                && mouseX < x + resultCategoryToggleSize
                && mouseY >= y
                && mouseY < y + resultCategoryToggleSize;
            CirrusControlChrome.renderButtonBackground(canvas, x, y, resultCategoryToggleSize, resultCategoryToggleSize, false, hovered, true);
            SizedTexture icon = category.collapsed()
                ? hovered ? CirrusTextures.Icon.Small.arrowRightSelected : CirrusTextures.Icon.Small.arrowRight
                : hovered ? CirrusTextures.Icon.arrowDownSelected : CirrusTextures.Icon.arrowDown;
            canvas.texture(
                icon,
                x + CirrusControlChrome.centeredIconX(resultCategoryToggleSize, icon.width()),
                y + CirrusControlChrome.centeredIconY(resultCategoryToggleSize, icon.height(), false),
                icon.width(),
                icon.height()
            );
        }

        private void submitVisibleSlotContents(BatchableRecipeContentRenderer.Batch batch, RenderPlan plan) {
            for (int i = 0; i < plan.cardCount(); i++) {
                PlannedCard card = plan.card(i);
                    card.card().submitSlotVisuals(batch, plan.clip(), card.slotLimit(), card.slotX(), card.slotY(), card.slotGap());
            }
        }

        private PlannedCard planCard(PlannedCard planned, VirtualResultCategory category, int localIndex) {
            ResultCard card = card(category, localIndex);
            List<RecipeContent> outputs = displayOutputs(card);
            if (viewMode == ViewMode.grid) {
                card.ensureSlotVisuals(1);
                GridCell cell = gridCell(category, localIndex);
                int actionX = cell.x() + cell.width() - resultCardActionColumnWidth + resultCardActionButtonInset;
                int actionY = cell.y() + gridCardHeight - resultCardActionButtonSize - 4;
                int textX = cell.x() + 30;
                int textWidth = Math.max(0, cell.width() - 36 - resultCardActionColumnWidth);
                return planned.set(category, localIndex, card, outputs, cell.x(), cell.y(), cell.width(), gridCardHeight,
                    cell.x() + 7, cell.y() + 13, 1, 0, textX, textWidth, actionX, actionY);
            }
            card.ensureSlotVisuals(listOutputSlotLimit);
            int y = cardY(category, localIndex);
            int outputWidth = listOutputStripWidth(outputs.size());
            int textX = categoryCardsX() + listRowHorizontalPadding + outputWidth + (outputWidth == 0 ? 0 : 7);
            int textWidth = Math.max(0, categoryCardsWidth() - textX + categoryCardsX() - listRowHorizontalPadding - resultCardActionColumnWidth);
            int slotY = y + Math.max(0, (listRowHeight - RecipeContentSlotWidget.slotSize) / 2);
            int actionX = categoryCardsX() + categoryCardsWidth() - resultCardActionColumnWidth + resultCardActionButtonInset;
            int actionY = y + Math.max(0, (listRowHeight - resultCardActionButtonSize) / 2);
            return planned.set(category, localIndex, card, outputs, categoryCardsX(), y, categoryCardsWidth(), listRowHeight,
                categoryCardsX() + listRowHorizontalPadding, slotY, listOutputSlotLimit, listOutputSlotGap, textX, textWidth, actionX, actionY);
        }

        private void renderCardBackground(SceneCanvas canvas, PlannedCard card, int mouseX, int mouseY) {
            boolean hovered = mouseX >= card.x()
                && mouseX < card.x() + card.width()
                && mouseY >= card.y()
                && mouseY < card.y() + card.height();
            boolean pressed = isCardPressed(card.category(), card.localIndex(), null, mouseX, mouseY);
            canvas.texture(pressed ? CirrusTextures.Background.inset : hovered ? CirrusTextures.Background.Border.light : CirrusTextures.Background.flat,
                card.x(), card.y(), card.width(), card.height());
        }

        private void drawSlotOverflow(SceneCanvas.TextBatch text, Font font, PlannedCard card, LocalClip clip) {
            List<RecipeContent> outputs = card.outputs();
            int visible;
            int hidden;
            int slotX;
            int slotY;
            visible = Math.min(outputs.size(), card.slotLimit());
            hidden = Math.max(0, outputs.size() - visible);
            slotX = card.slotX() + Math.max(0, visible - 1) * (RecipeContentSlotWidget.slotSize + Math.max(0, card.slotGap()));
            slotY = card.slotY();
            if (visible <= 0 || hidden <= 0) {
                return;
            }
            String label = "+" + NumberUtils.toSuffix(hidden);
            int textY = slotY + RecipeContentSlotWidget.slotSize - 9;
            if (intersects(clip, textY, font.lineHeight)) {
                text.drawString(label, slotX + RecipeContentSlotWidget.slotSize - 1 - font.width(label), textY, 0xFFFFFFFF, true);
            }
        }

        private void drawVisibleText(SceneCanvas.TextBatch text, Font font, LocalClip clip, String label, int x, int y, int maxWidth, int color) {
            if (intersects(clip, y, font.lineHeight)) {
                text.drawStringClipped(label, x, y, maxWidth, color, false);
            }
        }

        /** Draws the primary label with matched needle ranges emphasized. */
        private void drawHighlightedText(SceneCanvas.TextBatch text, Font font, LocalClip clip, ResultCard card, String label, int x, int y, int maxWidth) {
            if (!intersects(clip, y, font.lineHeight)) {
                return;
            }
            List<int[]> ranges = card.highlightRangesFor(label);
            if (ranges.isEmpty()) {
                text.drawStringClipped(label, x, y, maxWidth, textPrimary, false);
                return;
            }
            int cursor = 0;
            int drawX = x;
            int budget = maxWidth;
            for (int[] range : ranges) {
                if (range[0] > cursor) {
                    String before = label.substring(cursor, range[0]);
                    drawX = drawClippedSegment(text, font, before, drawX, y, budget, textPrimary);
                    budget -= font.width(before);
                    if (budget <= 0) {
                        return;
                    }
                }
                String matched = label.substring(range[0], range[1]);
                drawX = drawClippedSegment(text, font, matched, drawX, y, budget, matchHighlightColor);
                budget -= font.width(matched);
                if (budget <= 0) {
                    return;
                }
                cursor = range[1];
            }
            if (cursor < label.length()) {
                drawClippedSegment(text, font, label.substring(cursor), drawX, y, budget, textPrimary);
            }
        }

        private int drawClippedSegment(SceneCanvas.TextBatch text, Font font, String segment, int x, int y, int maxWidth, int color) {
            if (maxWidth <= 0 || segment.isEmpty()) {
                return x;
            }
            text.drawStringClipped(segment, x, y, maxWidth, color, false);
            return x + Math.min(maxWidth, font.width(segment));
        }

        private void renderActions(SceneCanvas canvas, PlannedCard card, int mouseX, int mouseY) {
            int globalIndex = card.category().globalOffset() + card.localIndex();
            for (ResultCardAction action : ResultCardAction.buttons()) {
                int actionX = card.actionX() + action.buttonIndex() * (resultCardActionButtonSize + resultCardActionButtonGap);
                boolean hovered = isActionHovered(actionX, card.actionY(), mouseX, mouseY);
                boolean down = pressedTarget != null
                    && !pressedTargetDragged
                    && pressedTarget.matches(globalIndex, action)
                    && actionAt(card.category(), card.localIndex(), mouseX, mouseY) == action;
                CirrusControlChrome.renderButtonBackground(canvas, actionX, card.actionY(), resultCardActionButtonSize, resultCardActionButtonSize, down, hovered, true);
                SizedTexture icon = action.icon();
                int iconX = actionX + CirrusControlChrome.centeredIconX(resultCardActionButtonSize, icon.width());
                int iconY = card.actionY() + CirrusControlChrome.centeredIconY(resultCardActionButtonSize, icon.height(), down);
                canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
            }
        }

        private ActionRenderState actionRenderState(PlannedCard card, ResultCardAction action, int mouseX, int mouseY) {
            int globalIndex = card.category().globalOffset() + card.localIndex();
            int actionX = card.actionX() + action.buttonIndex() * (resultCardActionButtonSize + resultCardActionButtonGap);
            boolean hovered = isActionHovered(actionX, card.actionY(), mouseX, mouseY);
            boolean down = pressedTarget != null
                && !pressedTargetDragged
                && pressedTarget.matches(globalIndex, action)
                && actionAt(card.category(), card.localIndex(), mouseX, mouseY) == action;
            return new ActionRenderState(actionX, hovered, down);
        }

        private @Nullable Tooltip outputTooltipAt(ResultHit hit, ResultCard card, double localX, double localY) {
            List<RecipeContent> outputs = displayOutputs(card);
            if (outputs.isEmpty()) {
                return null;
            }
            if (viewMode == ViewMode.grid) {
                GridCell cell = gridCell(hit.category(), hit.localIndex());
                return RecipeContentSlotsWidget.tooltipAt(outputs, localX - cell.x() - 7, localY - cell.y() - 13, 1, 0);
            }
            int y = cardY(hit.category(), hit.localIndex()) + Math.max(0, (listRowHeight - RecipeContentSlotWidget.slotSize) / 2);
            return RecipeContentSlotsWidget.tooltipAt(outputs, localX - categoryCardsX() - listRowHorizontalPadding, localY - y, listOutputSlotLimit, listOutputSlotGap);
        }

        private List<RecipeContent> displayOutputs(ResultCard card) {
            List<RecipeContent> outputs = card.outputs();
            if (!outputs.isEmpty()) {
                return outputs;
            }
            RecipeContent preview = card.preview();
            return preview.isEmpty() ? List.of() : List.of(preview);
        }

        private @Nullable ResultHit hitAt(double localX, double localY) {
            if (localX < categoryCardsX() || localX >= categoryCardsX() + categoryCardsWidth()) {
                return null;
            }
            int categoryIndex = categoryAtY((int) Math.floor(localY));
            if (categoryIndex < 0) {
                return null;
            }
            VirtualResultCategory category = categories.get(categoryIndex);
            if (localY < category.cardsY() || localY >= category.cardsY() + category.cardsHeight()) {
                return null;
            }
            int localIndex = viewMode == ViewMode.grid
                ? gridIndexAt(category, localX, localY)
                : listIndexAt(category, localY);
            if (localIndex >= 0 && localIndex < category.records().size()) {
                return new ResultHit(category, localIndex);
            }
            return null;
        }

        private @Nullable VirtualResultCategory categoryHeaderAt(double localX, double localY) {
            int categoryIndex = categoryAtY((int) Math.floor(localY));
            if (categoryIndex < 0) {
                return null;
            }
            VirtualResultCategory category = categories.get(categoryIndex);
            if (localY < category.headerY() || localY >= category.headerY() + resultCategoryHeaderHeight) {
                return null;
            }
            int bodyX = categoryInnerX();
            int bodyWidth = categoryInnerWidth();
            int toggleX = categoryToggleX();
            if (localX >= toggleX && localX < toggleX + resultCategoryToggleSize) {
                return null;
            }
            return localX >= bodyX && localX < bodyX + bodyWidth ? category : null;
        }

        private @Nullable VirtualResultCategory categoryToggleAt(double localX, double localY) {
            int categoryIndex = categoryAtY((int) Math.floor(localY));
            if (categoryIndex < 0) {
                return null;
            }
            VirtualResultCategory category = categories.get(categoryIndex);
            int toggleY = category.headerY() + Math.max(0, (resultCategoryHeaderHeight - resultCategoryToggleSize) / 2);
            int toggleX = categoryToggleX();
            return localX >= toggleX
                && localX < toggleX + resultCategoryToggleSize
                && localY >= toggleY
                && localY < toggleY + resultCategoryToggleSize
                ? category
                : null;
        }

        private void toggleResultCategory(VirtualResultCategory category) {
            ScrollSnapshot anchor = scrollAnchorForCategory(category);
            if (category.collapsed()) {
                collapsedResultCategories.remove(category.stableKey());
            } else {
                collapsedResultCategories.add(category.stableKey());
            }
            preserveScrollOnNextApply = false;
            restoreSavedScrollOnNextApply = false;
            clearPendingScrollRestore();
            long requestId = ++queryRequestId;
            // Collapsing is view-only state: when the last applied result still
            // matches the current query, rebuild the sections widget from its
            // buckets instead of re-running search + sort over every record.
            QueryBuildResult last = lastAppliedResult;
            QueryBuildResult queryResult = last != null
                && last.query().equals(searchBox.text())
                && last.sortMode() == sortMode
                && last.sortDirection() == sortDirection
                ? QueryBuildResult.results(
                    requestId,
                    last.query(),
                    last.sortMode(),
                    last.sortDirection(),
                    last.visibleResultCount(),
                    last.buckets(),
                    anchor
                )
                : buildQueryResult(
                    requestId,
                    index,
                    searchBox.text(),
                    sortMode,
                    sortDirection,
                    anchor
                );
            applyQueryResult(queryResult, false);
        }

        private ScrollSnapshot scrollAnchorForCategory(VirtualResultCategory category) {
            int contentY = pageContentY();
            float headerSceneY = contentY + category.headerY();
            float currentY = scrollState().targetScrollY();
            float relativeY = headerSceneY - currentY;
            float nextHeaderY = headerYForCategory(category.stableKey());
            if (nextHeaderY < 0) {
                nextHeaderY = category.headerY();
            }
            return new ScrollSnapshot(scrollState().targetScrollX(), Math.max(0, contentY + nextHeaderY - relativeY));
        }

        private int headerYForCategory(String stableKey) {
            int y = sectionTitleOuterHeight;
            for (VirtualResultCategory existing : categories) {
                if (existing.stableKey().equals(stableKey)) {
                    return y + categoryInnerPadding;
                }
                y += categoryHeight(existing.records().size(), existing.collapsed()) + categoryGap;
            }
            return -1;
        }

        private @Nullable ResultCardAction actionAt(ResultHit hit, double localX, double localY) {
            return actionAt(hit.category(), hit.localIndex(), localX, localY);
        }

        private @Nullable ResultCardAction actionAt(VirtualResultCategory category, int localIndex, double localX, double localY) {
            ActionOrigin origin = actionOrigin(category, localIndex);
            for (ResultCardAction action : ResultCardAction.buttons()) {
                int x = origin.x() + action.buttonIndex() * (resultCardActionButtonSize + resultCardActionButtonGap);
                if (localX >= x
                    && localX < x + resultCardActionButtonSize
                    && localY >= origin.y()
                    && localY < origin.y() + resultCardActionButtonSize) {
                    return action;
                }
            }
            return null;
        }

        private boolean isActionHovered(int x, int y, int mouseX, int mouseY) {
            return mouseX >= x
                && mouseX < x + resultCardActionButtonSize
                && mouseY >= y
                && mouseY < y + resultCardActionButtonSize;
        }

        private boolean isCardPressed(ResultHit hit, @Nullable ResultCardAction action, int mouseX, int mouseY) {
            return isCardPressed(hit.category(), hit.localIndex(), action, mouseX, mouseY);
        }

        private boolean isCardPressed(VirtualResultCategory category, int localIndex, @Nullable ResultCardAction action, int mouseX, int mouseY) {
            int globalIndex = category.globalOffset() + localIndex;
            if (pressedTarget == null || pressedTargetDragged || !pressedTarget.matches(globalIndex, action)) {
                return false;
            }
            ResultHit hovered = hitAt(mouseX, mouseY);
            return hovered != null
                && hovered.globalIndex() == globalIndex
                && actionAt(category, localIndex, mouseX, mouseY) == action;
        }

        private ActionOrigin actionOrigin(ResultHit hit) {
            return actionOrigin(hit.category(), hit.localIndex());
        }

        private ActionOrigin actionOrigin(VirtualResultCategory category, int localIndex) {
            if (viewMode == ViewMode.grid) {
                GridCell cell = gridCell(category, localIndex);
                return new ActionOrigin(
                    cell.x() + cell.width() - resultCardActionColumnWidth + resultCardActionButtonInset,
                    cell.y() + gridCardHeight - resultCardActionButtonSize - 4
                );
            }
            return new ActionOrigin(
                categoryCardsX() + categoryCardsWidth() - resultCardActionColumnWidth + resultCardActionButtonInset,
                cardY(category, localIndex) + Math.max(0, (listRowHeight - resultCardActionButtonSize) / 2)
            );
        }

        private ResultCard card(ResultHit hit) {
            return card(hit.category(), hit.localIndex());
        }

        private ResultCard card(VirtualResultCategory category, int localIndex) {
            int index = category.globalOffset() + localIndex;
            ResultCard cached = cardCache[index];
            if (cached == null) {
                cached = new ResultCard(index, category.records().get(localIndex).record());
                cardCache[index] = cached;
            }
            return cached;
        }

        private VisibleRange visibleRange(LocalClip clip) {
            int localTop = clip.top();
            int localBottom = clip.bottom();
            int first = firstCategoryEndingAfter(localTop);
            int last = lastCategoryStartingBefore(localBottom);
            if (first < 0 || last < 0 || first > last) {
                return new VisibleRange(0, -1);
            }
            return new VisibleRange(first, last);
        }

        private int firstCategoryEndingAfter(int y) {
            int low = 0;
            int high = categories.size() - 1;
            int result = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                VirtualResultCategory category = categories.get(mid);
                if (category.y() + category.height() > y) {
                    result = mid;
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            return result;
        }

        private int lastCategoryStartingBefore(int y) {
            int low = 0;
            int high = categories.size() - 1;
            int result = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (categories.get(mid).y() < y) {
                    result = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return result;
        }

        private int categoryAtY(int y) {
            int candidate = firstCategoryEndingAfter(y);
            if (candidate < 0) {
                return -1;
            }
            VirtualResultCategory category = categories.get(candidate);
            return y >= category.y() && y < category.y() + category.height() ? candidate : -1;
        }

        private VisibleRows visibleRows(VirtualResultCategory category, LocalClip clip) {
            if (category.collapsed()) {
                return new VisibleRows(0, -1);
            }
            int rowCount = viewMode == ViewMode.grid
                ? (category.records().size() + gridBatchColumns - 1) / gridBatchColumns
                : category.records().size();
            if (rowCount <= 0) {
                return new VisibleRows(0, -1);
            }
            int rowHeight = viewMode == ViewMode.grid ? gridCardHeight : listRowHeight;
            int rowGap = viewMode == ViewMode.grid ? gridGap : listRowGap;
            int cardsTop = category.cardsY();
            int cardsBottom = cardsTop + category.cardsHeight();
            if (clip.bottom() <= cardsTop || clip.top() >= cardsBottom) {
                return new VisibleRows(0, -1);
            }
            int localTop = Math.max(0, clip.top() - cardsTop);
            int localBottom = Math.min(category.cardsHeight(), clip.bottom() - cardsTop);
            if (localBottom <= localTop) {
                return new VisibleRows(0, -1);
            }
            int stride = rowHeight + rowGap;
            int first = Math.clamp(localTop / stride, 0, rowCount - 1);
            int last = Math.clamp((localBottom - 1) / stride, 0, rowCount - 1);
            return new VisibleRows(first, last);
        }

        private boolean intersects(LocalClip clip, int y, int height) {
            return y < clip.bottom() && y + height > clip.top();
        }

        private LocalClip localClip(SceneCanvas canvas) {
            var clip = canvas.currentClip();
            if (clip == null) {
                return pageScrollClip();
            }
            float[] origin = canvas.transformPointLocal(0, 0);
            int x = (int) Math.floor(origin[0]);
            int y = (int) Math.floor(origin[1]);
            if (!clip.intersects(x, y, width(), height())) {
                return new LocalClip(0, 0);
            }
            int top = Math.max(0, (int) Math.floor(clip.y() - origin[1]));
            int bottom = Math.min(height(), (int) Math.ceil(clip.bottom() - origin[1]));
            return new LocalClip(top, bottom);
        }

        private LocalClip pageScrollClip() {
            float scrollY = scrollState().scrollY();
            int viewportHeight = scrollState().viewportHeight();
            if (viewportHeight <= 0) {
                int topInset = scrollState().viewportInsetTop();
                int bottomInset = scrollState().viewportInsetBottom();
                viewportHeight = Math.max(0, DatabasePage.this.height() - topInset - bottomInset);
            }
            int contentY = pageContentY();
            int top = Math.max(0, (int) Math.floor(scrollY - contentY));
            int bottom = Math.min(height(), (int) Math.ceil(scrollY + viewportHeight - contentY));
            return new LocalClip(top, bottom);
        }

        private Rect sceneClipRect(SceneCanvas canvas, LocalClip clip) {
            float[] origin = canvas.transformPointLocal(0, clip.top());
            float[] end = canvas.transformPointLocal(width(), clip.bottom());
            return new Rect(
                (int) Math.floor(origin[0]),
                (int) Math.floor(origin[1]),
                Math.max(0, (int) Math.ceil(end[0] - origin[0])),
                Math.max(0, (int) Math.ceil(end[1] - origin[1]))
            );
        }

        private int pageContentY() {
            int y = 0;
            Widget current = this;
            while (current != null && current != DatabasePage.this) {
                y += current.pos().y();
                current = current.parent();
            }
            return y;
        }

        private int categoryHeight(int count) {
            return categoryHeight(count, false);
        }

        private int categoryHeight(int count, boolean collapsed) {
            if (collapsed) {
                return categoryInnerPadding * 2 + resultCategoryHeaderHeight;
            }
            return categoryInnerPadding * 2 + resultCategoryHeaderHeight + categoryHeaderGap + resultBatchHeight(count);
        }

        private int categoryToggleX() {
            return categoryInnerX() + categoryInnerWidth() - resultCategoryHeaderPaddingRight - resultCategoryToggleSize;
        }

        private int categoryInnerX() {
            return categoryOuterPadding + categoryInnerPadding;
        }

        private int categoryInnerWidth() {
            return Math.max(0, width() - (categoryOuterPadding + categoryInnerPadding) * 2);
        }

        private int categoryCardsX() {
            return categoryInnerX();
        }

        private int categoryCardsWidth() {
            return categoryInnerWidth();
        }

        private int cardY(VirtualResultCategory category, int localIndex) {
            int row = viewMode == ViewMode.grid ? localIndex / gridBatchColumns : localIndex;
            int rowHeight = viewMode == ViewMode.grid ? gridCardHeight : listRowHeight;
            int rowGap = viewMode == ViewMode.grid ? gridGap : listRowGap;
            return category.cardsY() + row * (rowHeight + rowGap);
        }

        private GridCell gridCell(VirtualResultCategory category, int localIndex) {
            int cardWidth = Math.max(1, (categoryCardsWidth() - Math.max(0, gridBatchColumns - 1) * gridGap) / gridBatchColumns);
            int column = localIndex % gridBatchColumns;
            int row = localIndex / gridBatchColumns;
            return new GridCell(
                categoryCardsX() + column * (cardWidth + gridGap),
                category.cardsY() + row * (gridCardHeight + gridGap),
                cardWidth
            );
        }

        private int listIndexAt(VirtualResultCategory category, double localY) {
            int row = (int) ((localY - category.cardsY()) / (listRowHeight + listRowGap));
            int yInRow = (int) (localY - category.cardsY() - row * (listRowHeight + listRowGap));
            return yInRow >= 0 && yInRow < listRowHeight ? row : -1;
        }

        private int gridIndexAt(VirtualResultCategory category, double localX, double localY) {
            int cardWidth = Math.max(1, (categoryCardsWidth() - Math.max(0, gridBatchColumns - 1) * gridGap) / gridBatchColumns);
            int strideX = cardWidth + gridGap;
            int strideY = gridCardHeight + gridGap;
            int column = (int) ((localX - categoryCardsX()) / strideX);
            int row = (int) ((localY - category.cardsY()) / strideY);
            int xInCell = (int) (localX - categoryCardsX() - column * strideX);
            int yInCell = (int) (localY - category.cardsY() - row * strideY);
            if (column < 0 || column >= gridBatchColumns || xInCell < 0 || xInCell >= cardWidth || yInCell < 0 || yInCell >= gridCardHeight) {
                return -1;
            }
            return row * gridBatchColumns + column;
        }

        private record VirtualResultCategory(String stableKey, Component title, List<RecipeDatabaseResult> records, int globalOffset, int y, int height, boolean collapsed) {
            private int headerY() {
                return y + categoryInnerPadding;
            }

            private int cardsY() {
                return headerY() + resultCategoryHeaderHeight + categoryHeaderGap;
            }

            private int cardsHeight() {
                return Math.max(0, height - categoryInnerPadding * 2 - resultCategoryHeaderHeight - categoryHeaderGap);
            }
        }

        private record ResultHit(VirtualResultCategory category, int localIndex) {
            private int globalIndex() {
                return category.globalOffset() + localIndex;
            }
        }

        private record VisibleRange(int firstCategory, int lastCategory) {
        }

        private record VisibleRows(int first, int last) {
        }

        private record LocalClip(int top, int bottom) {
            private boolean isEmpty() {
                return bottom <= top;
            }
        }

        private final class RenderPlan {
            private LocalClip clip = new LocalClip(0, 0);
            private int firstCategory;
            private int lastCategory;
            private int cardCount;
            private PlannedCard[] cards = createCardStorage(initialPlannedCardCapacity);

            private RenderPlan prepare(LocalClip clip) {
                this.clip = clip;
                this.cardCount = 0;
                VisibleRange range = visibleRange(clip);
                this.firstCategory = range.firstCategory();
                this.lastCategory = range.lastCategory();
                if (hasVisibleCategories()) {
                    collectVisibleCards();
                }
                return this;
            }

            private void collectVisibleCards() {
                for (int categoryIndex = firstCategory; categoryIndex <= lastCategory; categoryIndex++) {
                    VirtualResultCategory category = categories.get(categoryIndex);
                    VisibleRows rows = visibleRows(category, clip);
                    for (int row = rows.first(); row <= rows.last(); row++) {
                        int first = viewMode == ViewMode.grid ? row * gridBatchColumns : row;
                        int last = Math.min(category.records().size() - 1, viewMode == ViewMode.grid ? first + gridBatchColumns - 1 : first);
                        for (int localIndex = first; localIndex <= last; localIndex++) {
                            add(category, localIndex);
                        }
                    }
                }
            }

            private void add(VirtualResultCategory category, int localIndex) {
                ensureCapacity(cardCount + 1);
                planCard(cards[cardCount++], category, localIndex);
            }

            private void ensureCapacity(int required) {
                if (required <= cards.length) {
                    return;
                }
                int oldLength = cards.length;
                int newLength = Math.max(required, oldLength + oldLength / 2);
                PlannedCard[] grown = createCardStorage(newLength);
                System.arraycopy(cards, 0, grown, 0, oldLength);
                cards = grown;
            }

            private PlannedCard[] createCardStorage(int size) {
                PlannedCard[] out = new PlannedCard[size];
                for (int i = 0; i < out.length; i++) {
                    out[i] = new PlannedCard();
                }
                return out;
            }

            private LocalClip clip() {
                return clip;
            }

            private boolean hasVisibleSectionHeader() {
                return false;
            }

            private boolean hasVisibleCategories() {
                return lastCategory >= firstCategory;
            }

            private boolean hasVisibleCards() {
                return cardCount > 0;
            }

            private int firstCategory() {
                return firstCategory;
            }

            private int lastCategory() {
                return lastCategory;
            }

            private int cardCount() {
                return cardCount;
            }

            private PlannedCard card(int index) {
                return cards[index];
            }
        }

        private final class PlannedCard {
            private VirtualResultCategory category;
            private int localIndex;
            private ResultCard card;
            private List<RecipeContent> outputs = List.of();
            private int x;
            private int y;
            private int width;
            private int height;
            private int slotX;
            private int slotY;
            private int slotLimit;
            private int slotGap;
            private int textX;
            private int textWidth;
            private int actionX;
            private int actionY;

            private PlannedCard set(
                VirtualResultCategory category,
                int localIndex,
                ResultCard card,
                List<RecipeContent> outputs,
                int x,
                int y,
                int width,
                int height,
                int slotX,
                int slotY,
                int slotLimit,
                int slotGap,
                int textX,
                int textWidth,
                int actionX,
                int actionY
            ) {
                this.category = category;
                this.localIndex = localIndex;
                this.card = card;
                this.outputs = outputs;
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
                this.slotX = slotX;
                this.slotY = slotY;
                this.slotLimit = slotLimit;
                this.slotGap = slotGap;
                this.textX = textX;
                this.textWidth = textWidth;
                this.actionX = actionX;
                this.actionY = actionY;
                return this;
            }

            private VirtualResultCategory category() {
                return category;
            }

            private int localIndex() {
                return localIndex;
            }

            private ResultCard card() {
                return card;
            }

            private List<RecipeContent> outputs() {
                return outputs;
            }

            private int x() {
                return x;
            }

            private int y() {
                return y;
            }

            private int width() {
                return width;
            }

            private int height() {
                return height;
            }

            private int slotX() {
                return slotX;
            }

            private int slotY() {
                return slotY;
            }

            private int slotLimit() {
                return slotLimit;
            }

            private int slotGap() {
                return slotGap;
            }

            private int textX() {
                return textX;
            }

            private int textWidth() {
                return textWidth;
            }

            private int actionX() {
                return actionX;
            }

            private int actionY() {
                return actionY;
            }
        }

        private final class ResultCard {
            private final int index;
            private final RecipeDatabaseRecord record;
            private final String shortLabel;
            private final String recipeIdLabel;
            private final String systemLabel;
            private @Nullable List<RecipeContent> outputs;
            private @Nullable RecipeContent preview;
            private int slotVisualLimit = Integer.MIN_VALUE;
            private RecipeContentVisual[] slotVisuals = new RecipeContentVisual[0];
            private int slotVisualCount;
            private int fittedShortLabelWidth = Integer.MIN_VALUE;
            private String fittedShortLabel = "";
            private int fittedRecipeIdLabelWidth = Integer.MIN_VALUE;
            private String fittedRecipeIdLabel = "";
            private int fittedSystemLabelWidth = Integer.MIN_VALUE;
            private String fittedSystemLabel = "";
            private int highlightVersion = Integer.MIN_VALUE;
            private String highlightSource;
            private List<int[]> highlightRanges = List.of();

            private ResultCard(int index, RecipeDatabaseRecord record) {
                this.index = index;
                this.record = record;
                this.shortLabel = shortRecipeLabel(record);
                this.recipeIdLabel = DatabasePage.recipeLabel(record);
                this.systemLabel = DatabasePage.systemLabel(record);
            }

            private int index() {
                return index;
            }

            private RecipeDatabaseRecord record() {
                return record;
            }

            private List<RecipeContent> outputs() {
                List<RecipeContent> current = outputs;
                if (current == null) {
                    current = outputPreviews(record.projection());
                    outputs = current;
                }
                return current;
            }

            private RecipeContent preview() {
                RecipeContent current = preview;
                if (current == null) {
                    current = DatabasePage.preview(record.projection());
                    preview = current;
                }
                return current;
            }

            private void ensureSlotVisuals(int limit) {
                if (slotVisualLimit != limit) {
                    slotVisualLimit = limit;
                    List<RecipeContent> contents = displayOutputs(this);
                    int count = Math.min(contents.size(), Math.max(0, limit));
                    if (slotVisuals.length < count) {
                        slotVisuals = new RecipeContentVisual[count];
                    }
                    slotVisualCount = count;
                    for (int i = 0; i < count; i++) {
                        slotVisuals[i] = visualCache.visual(contents.get(i));
                    }
                    for (int i = count; i < slotVisuals.length; i++) {
                        slotVisuals[i] = null;
                    }
                }
            }

            private void submitSlotVisuals(BatchableRecipeContentRenderer.Batch batch, LocalClip clip, int limit, int x, int y, int gap) {
                ensureSlotVisuals(limit);
                int visible = RecipeContentSlotsWidget.visibleSlotCount(slotVisualCount, limit);
                int stride = RecipeContentSlotsWidget.slotSize + Math.max(0, gap);
                for (int i = 0; i < visible; i++) {
                    RecipeContentVisual visual = slotVisuals[i];
                    if (visual != null && visual.notEmpty() && intersects(clip, y, RecipeContentSlotWidget.slotSize)) {
                        batch.addVisual(
                            visual,
                            x + i * stride + RecipeContentSlotsWidget.slotBorder,
                            y + RecipeContentSlotsWidget.slotBorder,
                            RecipeContentSlotsWidget.contentSize,
                            RecipeContentSlotsWidget.contentSize
                        );
                    }
                }
            }

            private String shortLabel() {
                return shortLabel;
            }

            private String recipeIdLabel() {
                return recipeIdLabel;
            }

            private String systemLabel() {
                return systemLabel;
            }

            private String fittedShortLabel(Font font, int width) {
                if (fittedShortLabelWidth != width) {
                    fittedShortLabelWidth = width;
                    fittedShortLabel = fitText(font, shortLabel, width);
                }
                return fittedShortLabel;
            }

            private String fittedRecipeIdLabel(Font font, int width) {
                if (fittedRecipeIdLabelWidth != width) {
                    fittedRecipeIdLabelWidth = width;
                    fittedRecipeIdLabel = fitText(font, recipeIdLabel, width);
                }
                return fittedRecipeIdLabel;
            }

            private String fittedSystemLabel(Font font, int width) {
                if (fittedSystemLabelWidth != width) {
                    fittedSystemLabelWidth = width;
                    fittedSystemLabel = fitText(font, systemLabel, width);
                }
                return fittedSystemLabel;
            }

            /** Case-insensitive match ranges of the current query needles within a fitted label, cached per label. */
            private List<int[]> highlightRangesFor(String fittedLabel) {
                int version = matchNeedlesVersion;
                if (highlightVersion != version || highlightSource != fittedLabel) {
                    highlightVersion = version;
                    highlightSource = fittedLabel;
                    highlightRanges = computeHighlightRanges(fittedLabel, matchNeedles);
                }
                return highlightRanges;
            }
        }

        private static List<int[]> computeHighlightRanges(String label, List<String> needles) {
            if (needles.isEmpty() || label == null || label.isEmpty()) {
                return List.of();
            }
            boolean[] hits = new boolean[label.length()];
            for (String needle : needles) {
                // regionMatches against the ORIGINAL label: case folding can
                // change string length, so ranges computed on a lowered copy
                // would not align with the rendered label
                int index = 0;
                while (index <= label.length() - needle.length()) {
                    if (label.regionMatches(true, index, needle, 0, needle.length())) {
                        java.util.Arrays.fill(hits, index, index + needle.length(), true);
                        index += needle.length();
                    } else {
                        index++;
                    }
                }
            }
            ArrayList<int[]> ranges = new ArrayList<>();
            int start = -1;
            for (int index = 0; index < hits.length; index++) {
                if (hits[index] && start < 0) {
                    start = index;
                } else if (!hits[index] && start >= 0) {
                    ranges.add(new int[]{start, index});
                    start = -1;
                }
            }
            if (start >= 0) {
                ranges.add(new int[]{start, hits.length});
            }
            return ranges;
        }

        private record GridCell(int x, int y, int width) {
        }

        private record ActionOrigin(int x, int y) {
        }

        private record ActionRenderState(int x, boolean hovered, boolean down) {
        }

        private record PressedResultCardTarget(int cardIndex, @Nullable ResultCardAction action, boolean openRecipeInCalculator) {
            private boolean matches(int cardIndex, @Nullable ResultCardAction action) {
                return this.cardIndex == cardIndex && this.action == action;
            }
        }
    }

    private Widget contentPreviewRow(Component label, List<RecipeContent> contents) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(5), minWidth(0), minHeight(RecipeContentSlotWidget.slotSize + 4));
        CirrusFittedTextWidget title = new CirrusFittedTextWidget(label, textMuted);
        title.textPadding(1, 1);
        title.useStyle(widthOf(54), heightOf(RecipeContentSlotWidget.slotSize), flexShrink(0));
        row.addWidget(title);

        WidgetGroup<Widget> strip = new WidgetGroup<>();
        strip.useStyle(flexRow(), alignItemsCenter(), gap(3), flexGrow(1), minWidth(0));
        if (!contents.isEmpty()) {
            RecipeContentSlotsWidget slots = new RecipeContentSlotsWidget(contents)
                .limit(detailContentSlotLimit)
                .gap(3)
                .slotBackground()
                .decorated();
            slots.useStyle(flexShrink(0));
            strip.addWidget(slots);
        } else {
            CirrusFittedTextWidget empty = new CirrusFittedTextWidget(CirrusLangs.Calculator.Database.empty.component(), textMuted);
            empty.useStyle(heightOf(RecipeContentSlotWidget.slotSize), flexGrow(1), minWidth(0));
            strip.addWidget(empty);
        }
        row.addWidget(strip);
        return row;
    }

    private Widget recipeContentPreviewGroups(
        @Nullable RecipeEntry<?> recipe,
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        WidgetGroup<Widget> panel = detailSubPanel((
            projected == null
                ? CirrusLangs.Calculator.Database.candidatesOriginal
                : CirrusLangs.Calculator.Database.candidatesProjected
        ).component());
        ExecutionRecipeLayoutPreviewWidget preview = ExecutionRecipeLayoutPreviewWidget.create(recipe, projection, projected);
        if (preview != null) {
            panel.addWidget(preview);
            return panel;
        }
        panel.addWidget(contentPreviewRow(CirrusLangs.Calculator.Database.detailInputs.component(), inputPreviews(projection, projected)));
        panel.addWidget(contentPreviewRow(CirrusLangs.Calculator.Database.detailOutputs.component(), outputPreviews(projection, projected)));
        return panel;
    }

    private boolean recipeContentPreviewHasContent(@Nullable RecipeProjection projection, @Nullable ProjectedRecipe projected) {
        return !inputPreviews(projection, projected).isEmpty() || !outputPreviews(projection, projected).isEmpty();
    }

    private List<EffectRowData> effectRowsForChannel(ProjectionPluginReport report, EffectChannel channel) {
        List<ProjectionEffect> effects = effectsForChannel(report, channel);
        return projectionEffectsMerged ? mergedEffectRows(effects) : rawEffectRows(effects);
    }

    private List<EffectRowData> rawEffectRows(List<ProjectionEffect> effects) {
        ArrayList<EffectRowData> rows = new ArrayList<>(effects.size());
        for (ProjectionEffect effect : effects) {
            rows.add(rawEffectRow(effect));
        }
        return List.copyOf(rows);
    }

    private EffectRowData rawEffectRow(ProjectionEffect effect) {
        if (!isContentEffect(effect)) {
            return new EffectRowData(
                null,
                null,
                effectActionIcon(effect),
                Component.literal(schemaEffectLabel(effect)),
                Component.literal(schemaEffectSummary(effect)),
                textMuted,
                effectTooltip(effect)
            );
        }
        return new EffectRowData(
            effectBefore(effect),
            effectAfter(effect),
            effectActionIcon(effect),
            Component.literal(effectLabel(effect)),
            Component.literal(effectAmountSummary(effect)),
            effectColor(effect),
            effectTooltip(effect)
        );
    }

    private List<EffectRowData> mergedEffectRows(List<ProjectionEffect> effects) {
        LinkedHashMap<EffectMergeKey, EffectMergeBucket> buckets = new LinkedHashMap<>();
        ArrayList<EffectRowData> passthrough = new ArrayList<>();
        for (ProjectionEffect effect : effects) {
            if (!isContentEffect(effect)) {
                passthrough.add(rawEffectRow(effect));
                continue;
            }
            EffectMergeKey key = new EffectMergeKey(effectChannel(effect), effectIndex(effect));
            buckets.computeIfAbsent(key, ignored -> new EffectMergeBucket()).add(effect);
        }

        ArrayList<EffectRowData> rows = new ArrayList<>(buckets.size() + passthrough.size());
        for (EffectMergeBucket bucket : buckets.values()) {
            rows.add(bucket.toRow());
        }
        rows.addAll(passthrough);
        return List.copyOf(rows);
    }

    private void addEffectGroup(WidgetGroup<Widget> parent, Component title, List<EffectRowData> rows) {
        if (rows.isEmpty()) {
            return;
        }
        WidgetGroup<Widget> group = new WidgetGroup<>();
        group.useStyle(
            flexColumn(),
            gap(2),
            padding(2),
            widthOf(TaffyDimension.percent(1f)),
            minWidth(0),
            background(CirrusTextures.Background.flat)
        );
        group.addWidget(detailGroupHeader(title, Component.literal(String.valueOf(rows.size()))));
        for (EffectRowData row : rows) {
            group.addWidget(effectRow(row));
        }
        parent.addWidget(group);
    }

    private Widget detailGroupHeader(Component title, Component count) {
        WidgetGroup<Widget> header = new WidgetGroup<>();
        header.useStyle(
            flexRow(),
            alignItemsCenter(),
            gap(3),
            heightOf(detailEffectGroupHeaderHeight),
            minWidth(0),
            background(CirrusTextures.Background.Border.light)
        );
        CirrusFittedTextWidget label = new CirrusFittedTextWidget(title, textPrimary);
        label.textPadding(4, 1);
        label.useStyle(flexGrow(1), heightOf(detailEffectGroupHeaderHeight), minWidth(0));
        header.addWidget(label);
        CirrusFittedTextWidget badge = new CirrusFittedTextWidget(count, textMuted);
        badge.textPadding(2, 1);
        badge.useStyle(widthOf(24), heightOf(12), flexShrink(0), background(CirrusTextures.Background.inset));
        header.addWidget(badge);
        return header;
    }

    private Widget effectRow(EffectRowData data) {
        WidgetGroup<Widget> row = new WidgetGroup<>();
        row.useStyle(flexRow(), alignItemsCenter(), gap(3), widthOf(TaffyDimension.percent(1f)), heightOf(detailEffectRowHeight), minWidth(0), flexShrink(0));
        row.addWidget(effectVisualStrip(data));

        WidgetGroup<Widget> text = new WidgetGroup<>();
        text.useStyle(flexColumn(), justifyCenter(), gap(1), flexGrow(1), minWidth(0), heightOf(22));
        CirrusFittedTextWidget label = new CirrusFittedTextWidget(data.title(), textPrimary);
        label.textPadding(1, 1);
        label.useStyle(heightOf(11), minWidth(0));
        text.addWidget(label);
        CirrusFittedTextWidget source = new CirrusFittedTextWidget(data.summary(), data.color());
        source.textPadding(1, 1);
        source.useStyle(heightOf(10), minWidth(0));
        text.addWidget(source);
        row.addWidget(text);
        row.onHoverTooltip((mouseX, mouseY) -> data.tooltip());
        return row;
    }

    private Widget effectVisualStrip(EffectRowData data) {
        WidgetGroup<Widget> strip = new WidgetGroup<>();
        strip.useStyle(flexRow(), alignItemsCenter(), justifyCenter(), gap(2), widthOf(56), heightOf(22), flexShrink(0));
        if (data.before() == null && data.after() != null) {
            strip.addWidget(new TextureIconWidget(data.actionIcon(), 14));
            strip.addWidget(effectSlot(data.after()));
            return strip;
        }
        if (data.before() != null && data.after() == null) {
            strip.addWidget(effectSlot(data.before()));
            strip.addWidget(new TextureIconWidget(data.actionIcon(), 14));
            return strip;
        }
        if (data.before() != null) {
            strip.addWidget(effectSlot(data.before()));
        }
        strip.addWidget(new TextureIconWidget(data.actionIcon(), 14));
        if (data.after() != null) {
            strip.addWidget(effectSlot(data.after()));
        }
        return strip;
    }

    private Widget effectSlot(RecipeContent content) {
        RecipeContentSlotWidget slot = new RecipeContentSlotWidget(content).slotBackground().decorated();
        slot.useStyle(sizeOf(RecipeContentSlotWidget.slotSize, RecipeContentSlotWidget.slotSize), flexShrink(0));
        return slot;
    }

    private record EffectRowData(
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        SizedTexture actionIcon,
        Component title,
        Component summary,
        int color,
        Tooltip tooltip
    ) {
    }

    private record EffectMergeKey(EffectChannel channel, int index) {
    }

    private final class EffectMergeBucket {
        private final ArrayList<ProjectionEffect> effects = new ArrayList<>();
        private EffectChannel channel;
        private int index;
        private @Nullable RecipeContent before;
        private @Nullable RecipeContent after;

        private void add(ProjectionEffect effect) {
            if (effects.isEmpty()) {
                channel = effectChannel(effect);
                index = effectIndex(effect);
                before = effectBefore(effect);
            }
            after = effectAfter(effect);
            effects.add(effect);
        }

        private EffectRowData toRow() {
            if (effects.size() == 1) {
                return rawEffectRow(effects.getFirst());
            }
            RecipeContent content = after != null ? after : before;
            Tooltip tooltip = mergedEffectTooltip(this);
            return new EffectRowData(
                before,
                after,
                effectActionIcon(before, after),
                Component.literal(effectLabel(channel, index, before, after)),
                Component.literal(effectAmountSummary(before, after)),
                effectColor(before, after),
                tooltip
            );
        }
    }

    private static Component pluginMeta(ProjectionEffect effect) {
        var source = effect.source();
        if (source.resolveEffectId() != null) {
            return Component.literal(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.pluginResolve)
                    + ": " + source.resolveEffectId()
                    + " · " + ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.pluginOrder)
                    + " " + source.resolveEffectOrder()
            );
        }
        return Component.literal(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.pluginClause)
                + ": " + source.clauseId()
                + " · " + phaseLabel(source.phase())
        );
    }

    private void activateRecipeCard(RecipeDatabaseRecord record, ResultCardAction action, boolean clickSound) {
        activateRecipeCard(record, action, RecipeDatabaseKeyMappings.openRecipeInCalculatorDown(), clickSound);
    }

    private void activateRecipeCard(
        RecipeDatabaseRecord record,
        ResultCardAction action,
        boolean openRecipeInCalculator,
        boolean clickSound
    ) {
        switch (action) {
            case setCalculatorTarget -> activateCalculatorRecipeAction(record, openRecipeInCalculator);
            case setExecutionTarget -> ExecutionTargetRecipeState.set(record.recipe(), 1);
        }
        if (clickSound) {
            CirrusUiSound.playClickSound();
        }
    }

    private void activateCalculatorRecipeAction(RecipeDatabaseRecord record, boolean openRecipeInCalculator) {
        CalculatorTargetRecipeState.set(record.recipe(), 1);
        if (openRecipeInCalculator) {
            Minecraft.getInstance().setScreen(new CalculatorScreen());
        }
    }

    private Tooltip recipeActionTooltip(RecipeDatabaseRecord record, ResultCardAction action) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(shortRecipeLabel(record)));
        tooltip.add(ExecutionOverlayUi.tooltipBody(action.labelEntry()));
        switch (action) {
            case setCalculatorTarget -> appendCalculatorActionTooltip(tooltip);
            case setExecutionTarget -> tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.actionSetExecutionDesc));
        }
        return tooltip;
    }

    private static void appendCalculatorActionTooltip(Tooltip tooltip) {
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.actionSetCalculatorDesc));
        tooltip.add(Component.empty());
        tooltip.add(keyUsage(
            null,
            CirrusLangs.Calculator.Database.shortcutSetCalculatorTarget.component()
        ));
        tooltip.add(keyUsage(
            RecipeDatabaseKeyMappings.openRecipeInCalculator,
            CirrusLangs.Calculator.Database.shortcutOpenCalculator.component()
        ));
    }

    private Tooltip recipeTooltip(RecipeDatabaseRecord record) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(shortRecipeLabel(record)));

        ProjectionReport report = projectionReport(record.projection());
        ProjectedRecipe projected = report == null ? null : report.projectedRecipe();
        RecipeContent previewContent = ExecutionRecipeTooltipPreview.firstPreviewContent(record.projection(), projected);
        TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(previewContent);
        if (stack != null) {
            tooltip.setStack(stack);
        }

        TooltipComponent preview = ExecutionRecipeTooltipPreview.create(
            record.recipe(),
            record.projection(),
            projected
        );
        if (preview != null) {
            tooltip.add(preview);
        }
        appendScreeningMarker(tooltip, record);
        tooltip.add(Component.empty());
        tooltip.add(keyUsage(
            null,
            CirrusLangs.Calculator.Database.shortcutDefaultClick.component()
        ));
        return tooltip;
    }

    /**
     * Marks recipes that the recipe screening classified: exclusion tags mean
     * "kept for browsing, excluded from planning", advisory tags mean
     * "deprioritized during planning".
     */
    private static void appendScreeningMarker(Tooltip tooltip, RecipeDatabaseRecord record) {
        boolean excluded = false;
        boolean advisory = false;
        for (String tag : record.searchDocument().rawText(CirrusSearchFields.tag)) {
            if (ClassificationTags.isExclusionTag(tag)) {
                excluded = true;
            } else if (ClassificationTags.RECOLOR.equals(tag)
                || ClassificationTags.EXCHANGE.equals(tag)
                || ClassificationTags.RECYCLING.equals(tag)
                || ClassificationTags.GENERATION.equals(tag)) {
                advisory = true;
            }
        }
        if (excluded) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.screeningExcluded));
        } else if (advisory) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.screeningAdvisory));
        }
    }

    private static MutableComponent keyUsage(@Nullable KeyMapping keyMapping, Component usage) {
        Component key = keyMapping == null
            ? InputConstants.Type.MOUSE.getOrCreate(0).getDisplayName()
            : RecipeDatabaseKeyMappings.keyName(keyMapping);
        return Component.empty()
            .append(Component.literal("[")
                .withColor(ExecutionOverlayUi.tooltipTextSecondary))
            .append(key.copy().withColor(ExecutionOverlayUi.tooltipAccent))
            .append(Component.literal("] ")
                .withColor(ExecutionOverlayUi.tooltipTextSecondary))
            .append(usage.copy().withColor(ExecutionOverlayUi.tooltipTextSecondary));
    }

    private void appendEffectPreview(RichTooltipComponent.Builder builder, @Nullable ProjectionReport report) {
        if (report == null) {
            return;
        }
        builder.gap(2);
        builder.section(ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effects), ExecutionOverlayUi.warning);
        if (report.effects().isEmpty()) {
            builder.text(ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectsNone), ExecutionOverlayUi.tooltipTextSecondary, false);
            return;
        }
        int shown = 0;
        for (ProjectionEffect effect : report.effects()) {
            if (shown >= tooltipEffectLimit) {
                break;
            }
            if (!isContentEffect(effect)) {
                continue;
            }
            builder.contentIconText(
                effectContent(effect),
                effectColor(effect),
                effectLabel(effect),
                ExecutionOverlayUi.tooltipTextPrimary,
                effectSourceLabel(effect),
                ExecutionOverlayUi.tooltipTextSecondary
            );
            shown++;
        }
        int remaining = Math.max(0, report.effects().size() - shown);
        if (remaining > 0) {
            builder.text(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectsMore, remaining),
                ExecutionOverlayUi.tooltipTextSecondary,
                false
            );
        }
    }

    private void addStatus(Component text, int color) {
        results.addWidget(CirrusText.label(text, color));
    }

    private void clearResults() {
        results.clear();
        indexProgressStatusLabel = null;
        indexProgressStatusText = "";
    }

    private void deferAfterMouseDispatch(Runnable action) {
        if (action == null) {
            return;
        }
        if (!lifecycle().mounted()) {
            action.run();
            return;
        }
        scene().delay(1, () -> {
            if (lifecycle().mounted()) {
                action.run();
            }
        });
    }

    private List<RecipeDatabaseResult> sort(List<RecipeDatabaseResult> matches) {
        return sort(matches, sortMode, sortDirection);
    }

    private static List<RecipeDatabaseResult> sort(List<RecipeDatabaseResult> matches, SortMode sortMode, SortDirection sortDirection) {
        int size = matches.size();
        if (size < 2) {
            return matches;
        }
        // Sort keys are extracted once per record (recipe id resolution can be
        // expensive) instead of once per comparison.
        record SortKeys(RecipeDatabaseResult result, int matchScore, String k1, @Nullable String k2, @Nullable String k3) {
        }
        SortKeys[] decorated = new SortKeys[size];
        // key extraction (label lowercasing) is per-record independent: fan it
        // out on the shared compute pool for the large uncapped result sets
        CirrusUiAsync.parallelFor(size, index -> {
            RecipeDatabaseResult result = matches.get(index);
            RecipeDatabaseRecord record = result.record();
            decorated[index] = switch (sortMode) {
                case relevance -> new SortKeys(result, result.matchScore(), recipeLabel(record).toLowerCase(Locale.ROOT), null, null);
                case name -> new SortKeys(result, 0, recipeLabel(record).toLowerCase(Locale.ROOT), null, null);
                case database -> new SortKeys(result, 0, record.database().name().toLowerCase(Locale.ROOT), recipeLabel(record).toLowerCase(Locale.ROOT), null);
                case system -> new SortKeys(result, 0, systemGroupKey(record), systemLabel(record), recipeLabel(record).toLowerCase(Locale.ROOT));
            };
        });
        Comparator<SortKeys> comparator = switch (sortMode) {
            case relevance -> Comparator
                .comparingInt(SortKeys::matchScore).reversed()
                .thenComparing(SortKeys::k1);
            case name -> Comparator.comparing(SortKeys::k1);
            case database -> Comparator
                .comparing(SortKeys::k1)
                .thenComparing(SortKeys::k2);
            case system -> Comparator
                .comparing(SortKeys::k1)
                .thenComparing(SortKeys::k2)
                .thenComparing(SortKeys::k3);
        };
        if (sortDirection == SortDirection.descending) {
            comparator = comparator.reversed();
        }
        Arrays.sort(decorated, comparator);
        ArrayList<RecipeDatabaseResult> sorted = new ArrayList<>(size);
        for (SortKeys keys : decorated) {
            sorted.add(keys.result());
        }
        return List.copyOf(sorted);
    }

    private void toggleViewMode() {
        if (!browserChromeVisible()) {
            return;
        }
        rememberScrollPosition();
        viewMode = viewMode == ViewMode.grid ? ViewMode.list : ViewMode.grid;
        syncControls();
        requestApplyQueryWithSavedScroll();
    }

    private void cycleSortMode() {
        if (!browserChromeVisible()) {
            return;
        }
        rememberScrollPosition();
        sortMode = switch (sortMode) {
            case relevance -> SortMode.name;
            case name -> SortMode.database;
            case database -> SortMode.system;
            case system -> SortMode.relevance;
        };
        syncControls();
        requestApplyQueryWithSavedScroll();
    }

    private void toggleSortDirection() {
        if (!browserChromeVisible()) {
            return;
        }
        rememberScrollPosition();
        sortDirection = sortDirection == SortDirection.ascending
            ? SortDirection.descending
            : SortDirection.ascending;
        syncControls();
        requestApplyQueryWithSavedScroll();
    }

    private void syncControls() {
        if (!browserChromeVisible()) {
            syncProjectionDetailChrome();
            return;
        }
        sortButton.icon(sortMode.icon(), sortMode.hoverIcon());
        syncCreateDatabaseButtonIcon();
        databaseChoice.value(activeDatabaseId);
        boolean editable = activeDatabaseEditable();
        syncToolbarButton(sourcesButton, editable, databaseScopedIndex && !systems.isEmpty());
        boolean canDelete = editable;
        syncToolbarButton(deleteDatabaseButton, editable, canDelete);
        boolean confirmDelete = canDelete && deleteConfirmPendingForActive();
        syncDeleteConfirmPopup(confirmDelete);
        updateCreateDatabaseConfirmState();
        refreshCreateDatabaseTooltipIfHovered();
        refreshViewTooltipIfHovered();
        refreshSortTooltipIfHovered();
        refreshSortDirectionTooltipIfHovered();
    }

    private boolean browserChromeVisible() {
        return pageChrome.browserToolbar();
    }

    private void syncProjectionDetailChrome() {
        createDatabaseEditorOpen = false;
        pendingDeleteDatabaseId = null;
        sourcesPageOpen = false;
        selectedSystemRecipeSource = null;
        syncWidget(toolbar, false, false);
        syncWidget(searchBox, false, false);
        syncWidget(databaseChoice, false, false);
        syncToolbarButton(createDatabaseButton, false, false);
        syncWidget(createDatabaseEditor, false, false);
        syncWidget(createDatabaseNameField, false, false);
        syncToolbarButton(confirmCreateDatabaseButton, false, false);
        syncToolbarButton(deleteDatabaseButton, false, false);
        syncToolbarButton(sourcesButton, false, false);
        syncToolbarButton(viewButton, false, false);
        syncToolbarButton(sortButton, false, false);
        syncToolbarButton(sortDirectionButton, false, false);
        syncDeleteConfirmPopup(false);
        updateCreateDatabaseConfirmState();
    }

    private static void syncToolbarButton(CirrusButtonWidget button, boolean visible, boolean enabled) {
        boolean active = visible && enabled;
        button.enabled(active);
        button.useStyle(visible ? displayDefault() : displayNone());
        button.setVisible(visible).setActive(active).setInteractive(active);
    }

    private static void syncWidget(Widget widget, boolean visible, boolean active) {
        widget.useStyle(visible ? displayDefault() : displayNone());
        widget.setVisible(visible).setActive(active).setInteractive(active);
    }

    private void syncDeleteConfirmPopup(boolean visible) {
        deleteConfirmPopup.useStyle(visible ? displayFlex() : displayNone());
        deleteConfirmPopup.setVisible(visible).setActive(visible).setInteractive(visible);
        confirmDeleteDatabaseButton.setVisible(visible).setActive(visible).setInteractive(visible);
        deleteConfirmDatabaseLabel.text(ExecutionOverlayUi.tooltipBody(
            CirrusLangs.Calculator.Database.active,
            displayName(workspace.activeDatabase()).getString()
        ));
    }

    private void toggleCreateDatabaseEditor() {
        if (!browserChromeVisible()) {
            return;
        }
        setCreateDatabaseEditorOpen(!createDatabaseEditorOpen);
    }

    private void setCreateDatabaseEditorOpen(boolean open) {
        if (!browserChromeVisible()) {
            open = false;
        }
        boolean changed = createDatabaseEditorOpen != open;
        createDatabaseEditorOpen = open;
        syncCreateDatabaseButtonIcon();
        createDatabaseEditor.useStyle(open ? displayFlex() : displayNone());
        createDatabaseEditor.setVisible(open).setActive(open).setInteractive(open);
        createDatabaseNameField.setVisible(open).setActive(open).setInteractive(open);
        confirmCreateDatabaseButton.setVisible(open).setActive(open).setInteractive(open);
        syncWidget(databaseChoice, browserChromeVisible(), browserChromeVisible());
        if (open && changed) {
            createDatabaseNameField.setText("");
            if (createDatabaseNameField.lifecycle().mounted()) {
                createDatabaseNameField.scene().requestFocus(createDatabaseNameField);
            }
        } else if (createDatabaseNameField.lifecycle().mounted() && createDatabaseNameField.focused()) {
            createDatabaseNameField.scene().requestUnfocus(createDatabaseNameField);
        }
        updateCreateDatabaseConfirmState();
        refreshCreateDatabaseTooltipIfHovered();
    }

    private void syncCreateDatabaseButtonIcon() {
        if (createDatabaseEditorOpen) {
            createDatabaseButton.icon(CirrusTextures.Icon.Small.minus, CirrusTextures.Icon.Small.minusSelected);
        } else {
            createDatabaseButton.icon(CirrusTextures.Icon.Small.plus, CirrusTextures.Icon.Small.plusSelected);
        }
    }

    private void updateCreateDatabaseConfirmState() {
        confirmCreateDatabaseButton.enabled(createDatabaseEditorOpen && hasPendingDatabaseName());
    }

    private boolean hasPendingDatabaseName() {
        return !createDatabaseNameField.text().strip().isEmpty();
    }

    private void confirmCreateDatabase() {
        if (!browserChromeVisible()) {
            return;
        }
        String name = createDatabaseNameField.text().strip();
        if (name.isEmpty()) {
            updateCreateDatabaseConfirmState();
            return;
        }
        if (createDatabase(name)) {
            setCreateDatabaseEditorOpen(false);
        }
    }

    private boolean createDatabase(String name) {
        if (!browserChromeVisible()) {
            return false;
        }
        rememberScrollPosition();
        RecipeDatabaseWorkspace next;
        if (repository != null) {
            try {
                next = repository.createDatabase(name);
            } catch (java.io.IOException ignored) {
                return false;
            }
        } else {
            next = workspace.withNewDatabase(name);
        }
        workspace = next;
        CustomRecipeDatabase active = workspace.activeDatabase();
        activeDatabaseId = active.id();
        pendingDeleteDatabaseId = null;
        selectedRecipeDetail = null;
        selectedSystemRecipeSource = null;
        sourcesPageOpen = false;
        activeIndexDirty = false;
        refreshDatabaseChoiceOptions();
        syncSearchSuggestionSource();
        syncControls();
        restoreSavedScrollOnNextApply = true;
        rebuildActiveIndexNow();
        return true;
    }

    private void setActiveDatabase(String databaseId) {
        if (!browserChromeVisible()) {
            return;
        }
        rememberScrollPosition();
        activeDatabaseId = databaseId == null || databaseId.isBlank()
            ? workspace.activeDatabaseId()
            : databaseId;
        workspace = workspace.withActiveDatabase(activeDatabaseId);
        activeDatabaseId = workspace.activeDatabaseId();
        if (repository != null) {
            try {
                repository.saveActiveDatabase(activeDatabaseId);
            } catch (java.io.IOException ignored) {
            }
        }
        pendingDeleteDatabaseId = null;
        selectedRecipeDetail = null;
        selectedSystemRecipeSource = null;
        sourcesPageOpen = false;
        activeIndexDirty = false;
        syncSearchSuggestionSource();
        syncControls();
        restoreSavedScrollOnNextApply = true;
        rebuildActiveIndexNow();
    }

    private void openSourcesPage() {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            rememberScrollPosition();
            selectedRecipeDetail = null;
            selectedSystemRecipeSource = null;
            sourcesPageOpen = false;
            syncSearchSuggestionSource();
            syncControls();
            requestApplyQueryWithSavedScroll();
            return;
        }
        rememberScrollPosition();
        selectedRecipeDetail = null;
        selectedSystemRecipeSource = null;
        sourcesPageOpen = true;
        syncSearchSuggestionSource();
        requestApplyQueryWithSavedScroll();
    }

    private void leaveSourcesPage() {
        if (!browserChromeVisible()) {
            rememberScrollPosition();
            sourcesPageOpen = false;
            selectedSystemRecipeSource = null;
            syncSearchSuggestionSource();
            syncControls();
            requestApplyQueryWithSavedScroll();
            return;
        }
        rememberScrollPosition();
        sourcesPageOpen = false;
        selectedSystemRecipeSource = null;
        syncSearchSuggestionSource();
        requestApplyQueryWithSavedScroll();
    }

    private void openSystemRecipeSource(Namespace source) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            rememberScrollPosition();
            selectedRecipeDetail = null;
            selectedSystemRecipeSource = null;
            sourcesPageOpen = false;
            syncSearchSuggestionSource();
            syncControls();
            requestApplyQueryWithSavedScroll();
            return;
        }
        rememberScrollPosition();
        selectedRecipeDetail = null;
        sourcesPageOpen = true;
        selectedSystemRecipeSource = source;
        ensureSystemCatalogIndex();
        syncSearchSuggestionSource();
        requestApplyQueryWithSavedScroll();
    }

    private void ensureSystemCatalogIndex() {
        if (!databaseScopedIndex || !systemCatalogIndex.records().isEmpty()) {
            return;
        }
        systemCatalogIndex = buildSystemCatalogIndex(systems, systemTags);
    }

    private void leaveSystemRecipeSource() {
        if (!browserChromeVisible()) {
            rememberScrollPosition();
            selectedSystemRecipeSource = null;
            systemRecipeDragSelection = null;
            systemRecipeDragged = false;
            syncSearchSuggestionSource();
            syncControls();
            requestApplyQueryWithSavedScroll();
            return;
        }
        rememberScrollPosition();
        selectedSystemRecipeSource = null;
        systemRecipeDragSelection = null;
        systemRecipeDragged = false;
        syncSearchSuggestionSource();
        if (activeIndexDirty) {
            restoreSavedScrollOnNextApply = true;
            requestRebuildActiveIndex();
        } else {
            requestApplyQueryWithSavedScroll();
        }
    }

    private void openRecipeDetail(RecipeDatabaseRecord record) {
        openRecipeDetail(record, true);
    }

    private void openRecipeDetail(RecipeDatabaseRecord record, boolean clickSound) {
        rememberScrollPosition();
        selectedRecipeDetail = record;
        selectedSystemRecipeSource = null;
        sourcesPageOpen = false;
        selectedProjectionPluginId = null;
        syncSearchSuggestionSource();
        if (clickSound) {
            CirrusUiSound.playClickSound();
        }
        requestApplyQueryWithSavedScroll();
    }

    private static @Nullable RecipeDatabaseRecord findRecipeRecord(RecipeDatabaseIndex index, RecipeEntry<?> recipe) {
        if (index == null || recipe == null) {
            return null;
        }
        for (RecipeDatabaseRecord record : index.records()) {
            if (recipe.equals(record.recipe())) {
                return record;
            }
        }
        return null;
    }

    private void leaveRecipeDetail() {
        rememberScrollPosition();
        selectedRecipeDetail = null;
        requestApplyQueryWithSavedScroll();
    }

    private void closeRecipeDetail() {
        if (recipeDetailBackAction != null) {
            rememberScrollPosition();
            recipeDetailBackAction.run();
            return;
        }
        leaveRecipeDetail();
    }

    private void toggleSystemSource(Namespace source) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        CustomRecipeDatabase active = workspace.activeDatabase();
        setSystemSourceEnabled(source, !active.sourceEnabled(source));
    }

    private void setSystemSourceEnabled(Namespace source, boolean enabled) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        rememberScrollPosition();
        CustomRecipeDatabase nextActive = workspace.activeDatabase().withSourceEnabled(source, enabled);
        workspace = workspace.withDatabase(nextActive);
        activeDatabaseId = workspace.activeDatabaseId();
        if (repository != null) {
            try {
                repository.saveWorkspace(workspace);
            } catch (java.io.IOException ignored) {
            }
        }
        syncControls();
        requestDatabaseContentRefreshWithSavedScroll();
    }

    private void setRecipeSelected(DatabaseRecipeRef ref, boolean selected) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        rememberScrollPosition();
        CustomRecipeDatabase nextActive = workspace.activeDatabase().withRecipeSelected(ref, selected);
        workspace = workspace.withDatabase(nextActive);
        activeDatabaseId = workspace.activeDatabaseId();
        if (repository != null) {
            try {
                repository.saveWorkspace(workspace);
            } catch (java.io.IOException ignored) {
            }
        }
        syncControls();
        requestDatabaseContentRefreshWithSavedScroll();
    }

    private void setRecipeSelectionsSelected(List<SystemRecipeSelection> selections, boolean selected) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        rememberScrollPosition();
        CustomRecipeDatabase current = workspace.activeDatabase();
        CustomRecipeDatabase next = current;
        for (SystemRecipeSelection selection : selections) {
            next = next.withRecipeSelected(selection.ref(), selected);
        }
        if (next.equals(current)) {
            return;
        }
        workspace = workspace.withDatabase(next);
        activeDatabaseId = workspace.activeDatabaseId();
        if (repository != null) {
            try {
                repository.saveWorkspace(workspace);
            } catch (java.io.IOException ignored) {
            }
        }
        syncControls();
        requestDatabaseContentRefreshWithSavedScroll();
    }

    private void requestDatabaseContentRefresh() {
        if (selectedSystemRecipeSource != null) {
            activeIndexDirty = true;
            return;
        }
        requestRebuildActiveIndex();
    }

    private void requestDatabaseContentRefreshWithSavedScroll() {
        restoreSavedScrollOnNextApply = true;
        requestDatabaseContentRefresh();
    }

    private void applyRecipeSelection(DatabaseRecipeRef ref, boolean selected, boolean clickSound) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        if (workspace.activeDatabase().contains(ref) == selected) {
            return;
        }
        setRecipeSelected(ref, selected);
        if (clickSound) {
            CirrusUiSound.playClickSound();
        }
    }

    private void requestDeleteActiveDatabase() {
        if (!browserChromeVisible() || workspace.activeDatabase().builtin()) {
            pendingDeleteDatabaseId = null;
            syncControls();
            return;
        }
        pendingDeleteDatabaseId = activeDatabaseId;
        syncControls();
    }

    private void cancelDeleteActiveDatabase() {
        pendingDeleteDatabaseId = null;
        syncControls();
    }

    private void confirmDeleteActiveDatabase() {
        if (!browserChromeVisible() || !deleteConfirmPendingForActive() || workspace.activeDatabase().builtin()) {
            syncControls();
            return;
        }
        RecipeDatabaseWorkspace next;
        if (repository != null) {
            try {
                next = repository.deleteDatabase(activeDatabaseId);
            } catch (java.io.IOException ignored) {
                pendingDeleteDatabaseId = null;
                syncControls();
                return;
            }
        } else {
            next = workspace.withoutDatabase(activeDatabaseId);
        }
        workspace = next;
        activeDatabaseId = workspace.activeDatabaseId();
        pendingDeleteDatabaseId = null;
        selectedRecipeDetail = null;
        selectedSystemRecipeSource = null;
        refreshDatabaseChoiceOptions();
        syncControls();
        restoreSavedScrollOnNextApply = true;
        rebuildActiveIndexNow();
    }

    private boolean deleteConfirmPendingForActive() {
        return pendingDeleteDatabaseId != null && pendingDeleteDatabaseId.equals(activeDatabaseId);
    }

    private boolean activeDatabaseEditable() {
        return browserChromeVisible() && !workspace.activeDatabase().builtin();
    }

    private void refreshDatabaseChoiceOptions() {
        databaseChoice.clearOptions();
        for (CustomRecipeDatabase database : workspace.customDatabases()) {
            databaseChoice.option(database.id(), displayName(database));
        }
        databaseChoice.value(activeDatabaseId);
    }

    private void requestRebuildActiveIndex() {
        if (!lifecycle().mounted()) {
            rebuildActiveIndexNow();
            return;
        }
        if (rebuildActiveIndexScheduled) {
            return;
        }
        rebuildActiveIndexScheduled = true;
        scene().nextTick(() -> {
            rebuildActiveIndexScheduled = false;
            if (lifecycle().mounted()) {
                rebuildActiveIndexNow();
            }
        });
    }

    public void debugToggleSystemSourceGroup(String root) {
        toggleSystemSourceGroup(root);
    }

    private void requestApplyQuery() {
        if (!lifecycle().mounted()) {
            applyQuery();
            return;
        }
        if (applyQueryScheduled) {
            return;
        }
        applyQueryScheduled = true;
        scene().nextTick(() -> {
            applyQueryScheduled = false;
            if (lifecycle().mounted()) {
                applyQuery();
            }
        });
    }

    private void syncSearchSuggestionSource() {
        if (sourcesPageOpen && selectedSystemRecipeSource == null) {
            searchBox.suggestionSource(new SystemSourceSuggestionSource(systems));
        } else {
            searchBox.suggestionSource(new RecipeDatabaseSuggestionSource(index));
        }
    }

    private void rebuildActiveIndexNow() {
        activeIndexDirty = false;
        if (!databaseScopedIndex) {
            applyQuery();
            return;
        }
        explicitEntries = resolveExplicitEntries(workspace, systems, explicitEntries);
        if (asyncIndexBuildEnabled) {
            startIndexBuild(false);
            return;
        }
        index = buildActiveIndex(workspace, systems, systemTags, explicitEntries, systemCatalogIndex);
        searchBox.suggestionSource(new RecipeDatabaseSuggestionSource(index));
        applyQuery();
    }

    private void startIndexBuild(boolean includeSystemCatalog) {
        if (!asyncIndexBuildEnabled) {
            rebuildActiveIndexNow();
            return;
        }
        long requestId = ++indexBuildRequestId;
        pendingIndexBuild = null;
        RecipeDatabaseWorkspace workspaceSnapshot = workspace;
        List<RecipeSystem<?>> systemSnapshot = List.copyOf(systems);
        Map<Namespace, List<Namespace>> tagSnapshot = Map.copyOf(systemTags);
        Map<String, List<RecipeEntry<?>>> explicitSnapshot = Map.copyOf(explicitEntries);
        DatabaseIndexBuildProgress progress = new DatabaseIndexBuildProgress();
        progress.update(RecipeDatabaseIndex.BuildPhase.QUEUED, 0, 0);
        indexBuildProgress = progress;
        CompletableFuture<IndexBuildResult> future = CirrusUiAsync.databaseIndexTask(() -> {
            progress.update(RecipeDatabaseIndex.BuildPhase.RESOLVING_EXPLICIT, 0, 0);
            Map<String, List<RecipeEntry<?>>> resolvedExplicit = resolveExplicitEntries(workspaceSnapshot, systemSnapshot, explicitSnapshot);
            progress.update(RecipeDatabaseIndex.BuildPhase.RESOLVING_EXPLICIT, 1, 1);
            progress.update(includeSystemCatalog
                ? RecipeDatabaseIndex.BuildPhase.BUILDING_CATALOG
                : RecipeDatabaseIndex.BuildPhase.BUILDING_ACTIVE, 0, 0);
            boolean needsCatalog = includeSystemCatalog || systemCatalogIndex.records().isEmpty();
            RecipeDatabaseIndex catalog = needsCatalog
                ? buildSystemCatalogIndex(systemSnapshot, tagSnapshot, progress)
                : systemCatalogIndex;
            if (needsCatalog) {
                progress.update(RecipeDatabaseIndex.BuildPhase.BUILDING_ACTIVE, 0, 0);
            }
            RecipeDatabaseIndex active = buildActiveIndex(workspaceSnapshot, systemSnapshot, tagSnapshot, resolvedExplicit, catalog, progress);
            progress.update(RecipeDatabaseIndex.BuildPhase.DONE, 1, 1);
            return new IndexBuildResult(requestId, needsCatalog, catalog, active, resolvedExplicit);
        });
        pendingIndexBuild = future;
        showIndexProgressStatus();
    }

    private void applyCompletedIndexBuild() {
        CompletableFuture<IndexBuildResult> future = pendingIndexBuild;
        if (future == null) {
            return;
        }
        if (!future.isDone()) {
            updateIndexProgressStatus();
            return;
        }
        pendingIndexBuild = null;
        indexBuildProgress = null;
        IndexBuildResult result;
        try {
            result = future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            logger.error(
                "Failed to build recipe database index: database={}, systems={}, explicitDatabases={}",
                workspace.activeDatabaseId(),
                systems.size(),
                explicitEntries.size(),
                cause
            );
            indexProgressStatusText = "Recipe database index build failed: " + cause.getClass().getSimpleName();
            indexProgressStatusLabel = CirrusText.label(Component.literal(indexProgressStatusText), textMuted);
            results.addWidget(indexProgressStatusLabel);
            return;
        }
        if (result.requestId() != indexBuildRequestId) {
            return;
        }
        if (result.includeSystemCatalog()) {
            systemCatalogIndex = result.systemCatalogIndex();
        }
        explicitEntries = result.explicitEntries();
        index = result.activeIndex();
        searchBox.suggestionSource(new RecipeDatabaseSuggestionSource(index));
        applyQuery();
    }

    private boolean shouldShowIndexProgress() {
        return browserChromeVisible()
            && selectedRecipeDetail == null
            && selectedSystemRecipeSource == null
            && !sourcesPageOpen
            && pendingIndexBuild != null;
    }

    private void showIndexProgressStatus() {
        if (!shouldShowIndexProgress()) {
            return;
        }
        String text = currentIndexProgressText();
        indexProgressStatusText = text;
        indexProgressStatusLabel = CirrusText.label(Component.literal(text), textMuted);
        results.addWidget(indexProgressStatusLabel);
    }

    private void updateIndexProgressStatus() {
        if (!shouldShowIndexProgress()) {
            indexProgressStatusLabel = null;
            indexProgressStatusText = "";
            return;
        }
        if (indexProgressStatusLabel == null) {
            if (results.children().isEmpty()) {
                showIndexProgressStatus();
            }
            return;
        }
        String text = currentIndexProgressText();
        if (!text.equals(indexProgressStatusText)) {
            indexProgressStatusText = text;
            indexProgressStatusLabel.setText(Component.literal(text));
        }
    }

    private String currentIndexProgressText() {
        DatabaseIndexBuildProgress progress = indexBuildProgress;
        if (progress == null) {
            return CirrusLangs.Calculator.Database.indexing.component().getString();
        }
        return progress.text();
    }

    private void refreshSortTooltipIfHovered() {
        if (!sortButton.hovered()) {
            return;
        }
        sortButton.scene().setHoverTooltip(sortTooltip());
    }

    private void refreshSortDirectionTooltipIfHovered() {
        if (!sortDirectionButton.hovered()) {
            return;
        }
        sortDirectionButton.scene().setHoverTooltip(sortDirectionTooltip());
    }

    private void refreshViewTooltipIfHovered() {
        if (!viewButton.hovered()) {
            return;
        }
        viewButton.scene().setHoverTooltip(viewTooltip());
    }

    private void refreshCreateDatabaseTooltipIfHovered() {
        if (!createDatabaseButton.hovered()) {
            return;
        }
        createDatabaseButton.scene().setHoverTooltip(createDatabaseTooltip());
    }

    private void toggleSystemSourceGroup(String root) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        List<Namespace> sources = groupedSourcesByRoot().get(root);
        if (sources == null || sources.isEmpty()) {
            return;
        }
        boolean allEnabled = true;
        CustomRecipeDatabase active = workspace.activeDatabase();
        for (Namespace source : sources) {
            if (!active.sourceEnabled(source)) {
                allEnabled = false;
                break;
            }
        }
        setSystemSourcesEnabled(sources, !allEnabled);
    }

    private void setSystemSourcesEnabled(List<Namespace> sources, boolean enabled) {
        if (!browserChromeVisible() || !activeDatabaseEditable()) {
            syncControls();
            return;
        }
        rememberScrollPosition();
        CustomRecipeDatabase nextActive = workspace.activeDatabase();
        for (Namespace source : sources) {
            nextActive = nextActive.withSourceEnabled(source, enabled);
        }
        workspace = workspace.withDatabase(nextActive);
        activeDatabaseId = workspace.activeDatabaseId();
        if (repository != null) {
            try {
                repository.saveWorkspace(workspace);
            } catch (java.io.IOException ignored) {
            }
        }
        syncControls();
        requestDatabaseContentRefreshWithSavedScroll();
    }

    private Tooltip viewTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(() -> ExecutionOverlayUi.tooltipTitle(viewMode.labelEntry()));
        tooltip.add(() -> ExecutionOverlayUi.tooltipBody(viewMode.descriptionEntry()));
        tooltip.add(() -> ExecutionOverlayUi.tooltipAccentText(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.next, ExecutionOverlayUi.tr(nextViewMode().labelEntry()))
        ));
        return tooltip;
    }

    private ViewMode nextViewMode() {
        return viewMode == ViewMode.grid ? ViewMode.list : ViewMode.grid;
    }

    private Tooltip searchTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.search));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.searchDesc));
        return tooltip;
    }

    private Tooltip databaseChoiceTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.select));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.selectDesc));
        if (!activeDatabaseEditable()) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.readOnlyBuiltinDesc));
        }
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.active, displayName(workspace.activeDatabase()).getString())
        ));
        return tooltip;
    }

    private Tooltip createDatabaseTooltip() {
        Tooltip tooltip = Tooltip.create();
        if (createDatabaseEditorOpen) {
            tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.createCancel));
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.createCancelDesc));
        } else {
            tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.create));
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.createDesc));
        }
        return tooltip;
    }

    private Tooltip createDatabaseNameTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.createNamePlaceholder));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.createNameDesc));
        return tooltip;
    }

    private Tooltip confirmCreateDatabaseTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.createConfirm));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.createConfirmDesc));
        return tooltip;
    }

    private Tooltip deleteDatabaseTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.delete));
        if (workspace.activeDatabase().builtin()) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.deleteBuiltinDesc));
        } else if (deleteConfirmPendingForActive()) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.deleteConfirmDesc));
        } else {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.deleteDesc));
        }
        return tooltip;
    }

    private Tooltip confirmDeleteDatabaseTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.deleteConfirm));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.deleteConfirmDesc));
        return tooltip;
    }

    private Tooltip cancelDeleteDatabaseTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.deleteCancel));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.deleteCancelDesc));
        return tooltip;
    }

    private Tooltip sourcesTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.sources));
        tooltip.add(ExecutionOverlayUi.tooltipBody(
            activeDatabaseEditable()
                ? CirrusLangs.Calculator.Database.sourcesDesc
                : CirrusLangs.Calculator.Database.sourcesBuiltinDesc
        ));
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.active, displayName(workspace.activeDatabase()).getString())
        ));
        return tooltip;
    }

    private Tooltip sortTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.sort));
        tooltip.add(() -> ExecutionOverlayUi.tooltipAccentText(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.current, sortMode.label().getString())
        ));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sortDesc));
        tooltip.add(() -> ExecutionOverlayUi.tooltipAccentText(ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.next, nextSortMode().label().getString())));
        return tooltip;
    }

    private Tooltip sortDirectionTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(() -> ExecutionOverlayUi.tooltipTitle(sortDirection.labelEntry()));
        tooltip.add(() -> ExecutionOverlayUi.tooltipAccentText(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.current, ExecutionOverlayUi.tr(sortDirection.labelEntry()))
        ));
        return tooltip;
    }

    private Tooltip allResultsTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Calculator.Database.allResults));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.allResultsDesc));
        return tooltip;
    }

    private Tooltip sourceRecipePageTooltip(Namespace source) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(sourceButtonLabel(source)));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sourceRecipesDesc));
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(source.toString()));
        return tooltip;
    }

    private Tooltip systemRecipeBulkTooltip(boolean selected) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(
            selected
                ? CirrusLangs.Calculator.Database.bulkSelect
                : CirrusLangs.Calculator.Database.bulkClear
        ));
        tooltip.add(ExecutionOverlayUi.tooltipBody(
            selected
                ? CirrusLangs.Calculator.Database.bulkSelectDesc
                : CirrusLangs.Calculator.Database.bulkClearDesc
        ));
        return tooltip;
    }

    private SortMode nextSortMode() {
        return switch (sortMode) {
            case relevance -> SortMode.name;
            case name -> SortMode.database;
            case database -> SortMode.system;
            case system -> SortMode.relevance;
        };
    }

    private static RecipeContent preview(RecipeProjection projection) {
        return outputPreviews(projection).stream().findFirst().orElseGet(RecipeContent::empty);
    }

    private static List<RecipeContent> inputPreviews(@Nullable RecipeProjection projection, @Nullable ProjectedRecipe projected) {
        if (projected == null) {
            return inputPreviews(projection);
        }
        ArrayList<RecipeContent> inputs = new ArrayList<>();
        for (InputContent input : projected.inputs()) {
            if (isVisibleRecipePreviewContent(input)) {
                inputs.add(input);
            }
        }
        return List.copyOf(inputs);
    }

    private static List<RecipeContent> inputPreviews(@Nullable RecipeProjection projection) {
        if (projection == null) {
            return List.of();
        }
        ArrayList<RecipeContent> inputs = new ArrayList<>();
        for (ClausedContent<InputContent> input : projection.inputs()) {
            if (isVisibleRecipePreviewContent(input.content())) {
                inputs.add(input.content());
            }
        }
        return List.copyOf(inputs);
    }

    private static List<RecipeContent> outputPreviews(@Nullable RecipeProjection projection, @Nullable ProjectedRecipe projected) {
        if (projected == null) {
            return outputPreviews(projection);
        }
        ArrayList<RecipeContent> outputs = new ArrayList<>();
        for (OutputContent output : projected.outputs()) {
            if (isVisibleRecipePreviewContent(output)) {
                outputs.add(output);
            }
        }
        return List.copyOf(outputs);
    }

    private static List<RecipeContent> outputPreviews(@Nullable RecipeProjection projection) {
        if (projection == null) {
            return List.of();
        }
        ArrayList<RecipeContent> outputs = new ArrayList<>();
        for (ClausedContent<OutputContent> output : projection.outputs()) {
            if (isVisibleRecipePreviewContent(output.content())) {
                outputs.add(output.content());
            }
        }
        return List.copyOf(outputs);
    }

    private static boolean isVisibleRecipePreviewContent(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return !(content instanceof ValueContent<?> value && value.type() == CirrusTypes.Contents.ENERGY);
    }

    private @Nullable ProjectionReport projectionReport(@Nullable RecipeProjection projection) {
        if (projection == null) {
            return null;
        }
        return projectionReport(projection, projectionContext());
    }

    private @Nullable ProjectionReport projectionReport(RecipeProjection projection, ExecutionContext context) {
        try {
            return new ProjectionEngine().projectReport(context, projection);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ExecutionContext projectionContext() {
        return projectionContext(Map.of());
    }

    private ExecutionContext projectionContext(Map<Namespace, Object> overrides) {
        ExecutionContext databaseContext = ProjectionContexts.database(globalProjectionContext, activeDatabaseProjectionContext());
        for (ProjectionPlugin plugin : projectionPlugins) {
            Object override = overrides.get(plugin.id());
            databaseContext.set(plugin.enabledKey(), override instanceof Boolean enabled ? enabled : projectionPluginEnabled(plugin));
        }
        for (ProjectionContextOption<?> option : projectionContextOptions) {
            applyProjectionContextValue(databaseContext, option, overrides.getOrDefault(option.id(), projectionContextValues.get(option.id())));
        }
        if (calculatorProjectionContext != null) {
            return ProjectionContexts.calculator(databaseContext, calculatorProjectionContext);
        }
        return databaseContext;
    }

    private DatabaseProjectionContext activeDatabaseProjectionContext() {
        CustomRecipeDatabase active = workspace.activeDatabase();
        return new DatabaseProjectionContext(
            active.id(),
            displayName(active).getString(),
            active.builtin()
        );
    }

    private <T> void applyProjectionContextValue(ExecutionContext context, ProjectionContextOption<T> option) {
        applyProjectionContextValue(context, option, projectionContextValues.get(option.id()));
    }

    private <T> void applyProjectionContextValue(ExecutionContext context, ProjectionContextOption<T> option, Object value) {
        context.set(option.key(), option.normalize(value));
    }

    private enum EffectChannel {
        input,
        output
    }

    private static boolean isContentEffect(ProjectionEffect effect) {
        return effect instanceof InputEffect || effect instanceof OutputEffect;
    }

    private static @Nullable EffectChannel effectChannel(ProjectionEffect effect) {
        if (effect instanceof InputEffect) return EffectChannel.input;
        if (effect instanceof OutputEffect) return EffectChannel.output;
        return null;
    }

    private static int effectIndex(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.index();
        if (effect instanceof OutputEffect output) return output.index();
        return -1;
    }

    private static @Nullable RecipeContent effectBefore(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.before();
        if (effect instanceof OutputEffect output) return output.before();
        return null;
    }

    private static @Nullable RecipeContent effectAfter(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.after();
        if (effect instanceof OutputEffect output) return output.after();
        return null;
    }

    private static long effectBeforeAmount(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.beforeAmount();
        if (effect instanceof OutputEffect output) return output.beforeAmount();
        return 0L;
    }

    private static long effectAfterAmount(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.afterAmount();
        if (effect instanceof OutputEffect output) return output.afterAmount();
        return 0L;
    }

    private static long effectAmountDelta(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.amountDelta();
        if (effect instanceof OutputEffect output) return output.amountDelta();
        return 0L;
    }

    private static @Nullable Double effectAmountFactor(ProjectionEffect effect) {
        if (effect instanceof InputEffect input) return input.amountFactor();
        if (effect instanceof OutputEffect output) return output.amountFactor();
        return null;
    }

    private static RecipeContent effectContent(ProjectionEffect effect) {
        RecipeContent content = effectAfter(effect) != null ? effectAfter(effect) : effectBefore(effect);
        return content == null ? RecipeContent.empty() : content;
    }

    private static SizedTexture effectActionIcon(ProjectionEffect effect) {
        if (effect instanceof SchemaEffect) {
            return CirrusTextures.Icon.setting;
        }
        return effectActionIcon(effectBefore(effect), effectAfter(effect));
    }

    private static SizedTexture effectActionIcon(@Nullable RecipeContent before, @Nullable RecipeContent after) {
        if (before == null && after != null) {
            return CirrusTextures.Icon.Small.plus;
        }
        if (before != null && after == null) {
            return CirrusTextures.Icon.Small.minus;
        }
        return CirrusTextures.Icon.arrowRight;
    }

    private static int effectColor(ProjectionEffect effect) {
        return effectColor(effectBefore(effect), effectAfter(effect));
    }

    private static int effectColor(@Nullable RecipeContent before, @Nullable RecipeContent after) {
        if (before == null && after != null) {
            return ExecutionOverlayUi.tooltipSuccess;
        }
        if (before != null && after == null) {
            return ExecutionOverlayUi.tooltipDanger;
        }
        return ExecutionOverlayUi.tooltipAccent;
    }

    private static String effectLabel(
        EffectChannel channel,
        int index,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after
    ) {
        String translatedChannel = ExecutionOverlayUi.tr(
            channel == EffectChannel.input
                ? CirrusLangs.Calculator.Database.Effect.input
                : CirrusLangs.Calculator.Database.Effect.output
        );
        int displayIndex = index + 1;
        if (before == null && after != null) {
            return ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.Effect.added, translatedChannel, displayIndex);
        }
        if (before != null && after == null) {
            return ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.Effect.removed, translatedChannel, displayIndex);
        }
        return ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.Effect.replaced, translatedChannel, displayIndex);
    }

    private static String effectLabel(ProjectionEffect effect) {
        EffectChannel channel = effectChannel(effect);
        if (channel == null) return effectSourceLabel(effect);
        return effectLabel(channel, effectIndex(effect), effectBefore(effect), effectAfter(effect));
    }

    private static String effectTargetLabel(ProjectionEffect effect) {
        EffectChannel channel = effectChannel(effect);
        if (channel == null) {
            return schemaEffectLabel(effect);
        }
        return effectTargetLabel(channel, effectIndex(effect));
    }

    private static String effectTargetLabel(EffectChannel channel, int index) {
        String translatedChannel = ExecutionOverlayUi.tr(
            channel == EffectChannel.input
                ? CirrusLangs.Calculator.Database.Effect.input
                : CirrusLangs.Calculator.Database.Effect.output
        );
        return translatedChannel + " #" + (index + 1);
    }

    private static String schemaEffectLabel(ProjectionEffect effect) {
        if (effect instanceof ProjectionSchemaPut<?>) {
            return ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.projection);
        }
        if (effect instanceof InputSchemaPut<?> input) {
            return effectTargetLabel(EffectChannel.input, input.index());
        }
        if (effect instanceof OutputSchemaPut<?> output) {
            return effectTargetLabel(EffectChannel.output, output.index());
        }
        return phaseLabel(effect.source().phase());
    }

    private static String schemaEffectSummary(ProjectionEffect effect) {
        if (effect instanceof ProjectionSchemaPut<?> schema) {
            return schemaSummary(schema.key(), schema.value());
        }
        if (effect instanceof InputSchemaPut<?> schema) {
            return schemaSummary(schema.key(), schema.value());
        }
        if (effect instanceof OutputSchemaPut<?> schema) {
            return schemaSummary(schema.key(), schema.value());
        }
        return phaseLabel(effect.source().phase());
    }

    private static String schemaSummary(ProjectionSchemaKey<?> key, Object value) {
        String valueText = switch (value) {
            case GridSize size -> size.width() + "x" + size.height();
            case GridIndex index -> "#" + (index.index() + 1);
            default -> String.valueOf(value);
        };
        return schemaKeyName(key) + " " + valueText;
    }

    private static String schemaKeyName(ProjectionSchemaKey<?> key) {
        String path = key.id().path();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String effectAmountSummary(ProjectionEffect effect) {
        return effectAmountSummary(effectBefore(effect), effectAfter(effect));
    }

    private static String effectAmountSummary(@Nullable RecipeContent before, @Nullable RecipeContent after) {
        long beforeAmount = before == null ? 0L : before.amount();
        long afterAmount = after == null ? 0L : after.amount();
        if (before == null && after != null) {
            return "+" + afterAmount;
        }
        if (before != null && after == null) {
            return "-" + beforeAmount;
        }
        String value = beforeAmount + " -> " + afterAmount;
        Double factor = amountFactor(beforeAmount, afterAmount);
        if (factor != null && Math.abs(factor - 1.0D) > 0.00001D) {
            value += " x" + trimDouble(factor);
        }
        return value;
    }

    private static @Nullable Double amountFactor(long before, long after) {
        return before <= 0L ? null : (double) after / (double) before;
    }

    private static String trimDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String effectSourceLabel(ProjectionEffect effect) {
        var source = effect.source();
        if (source.resolveEffectId() != null) {
            return source.resolveEffectId().toString();
        }
        return source.clauseId().toString();
    }

    private static String effectSourceMeta(ProjectionEffect effect) {
        return effectSourceLabel(effect) + " · " + phaseLabel(effect.source().phase());
    }

    private Tooltip projectionEffectMergeTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(CirrusLangs.Calculator.Database.effectsMerge.component(), Tooltip.title);
        tooltip.add(CirrusLangs.Calculator.Database.effectsMergeDesc.component());
        tooltip.add(CirrusLangs.Calculator.Settings.switchState.component(
            (projectionEffectsMerged
                ? CirrusLangs.Calculator.Settings.switchOn
                : CirrusLangs.Calculator.Settings.switchOff).string()
        ));
        return tooltip;
    }

    private Tooltip mergedEffectTooltip(EffectMergeBucket bucket) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(Component.literal(effectLabel(bucket.channel, bucket.index, bucket.before, bucket.after)), Tooltip.title);
        RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effects),
            ExecutionOverlayUi.tooltipTextSecondary,
            Integer.toString(bucket.effects.size()),
            ExecutionOverlayUi.tooltipAccent
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectBefore),
            ExecutionOverlayUi.tooltipTextSecondary,
            Long.toString(amountOf(bucket.before)),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectAfter),
            ExecutionOverlayUi.tooltipTextSecondary,
            Long.toString(amountOf(bucket.after)),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectDelta),
            ExecutionOverlayUi.tooltipTextSecondary,
            Long.toString(amountOf(bucket.after) - amountOf(bucket.before)),
            effectColor(bucket.before, bucket.after)
        );
        int shown = 0;
        for (ProjectionEffect effect : bucket.effects) {
            if (shown >= tooltipEffectLimit || !isContentEffect(effect)) {
                continue;
            }
            builder.text(
                effectLabel(effect) + "  " + effectAmountSummary(effect),
                ExecutionOverlayUi.tooltipTextSecondary,
                false
            );
            shown++;
        }
        tooltip.add(builder.build());
        return tooltip;
    }

    private static long amountOf(@Nullable RecipeContent content) {
        return content == null ? 0L : content.amount();
    }

    private static String phaseLabel(dev.vfyjxf.cirrus.api.recipe.projection.effect.ProjectionEffectPhase phase) {
        return switch (phase) {
            case projectionTransformer -> ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.Effect.projectionTransformer);
            case contentTransformer -> ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.Effect.contentTransformer);
            case resolveEffect -> ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.Effect.resolveEffect);
        };
    }

    private Tooltip effectTooltip(ProjectionEffect effect) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(effectSourceLabel(effect)));
        RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectSource),
            ExecutionOverlayUi.tooltipTextSecondary,
            effectSourceLabel(effect),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectPhase),
            ExecutionOverlayUi.tooltipTextSecondary,
            phaseLabel(effect.source().phase()),
            ExecutionOverlayUi.tooltipAccent
        );
        if (isContentEffect(effect)) {
            builder.kv(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectBefore),
                ExecutionOverlayUi.tooltipTextSecondary,
                Long.toString(effectBeforeAmount(effect)),
                ExecutionOverlayUi.tooltipTextPrimary
            );
            builder.kv(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectAfter),
                ExecutionOverlayUi.tooltipTextSecondary,
                Long.toString(effectAfterAmount(effect)),
                ExecutionOverlayUi.tooltipTextPrimary
            );
            builder.kv(
                ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectDelta),
                ExecutionOverlayUi.tooltipTextSecondary,
                Long.toString(effectAmountDelta(effect)),
                effectColor(effect)
            );
            Double factor = effectAmountFactor(effect);
            if (factor != null) {
                builder.kv(
                    ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.effectFactor),
                    ExecutionOverlayUi.tooltipTextSecondary,
                    trimDouble(factor),
                    ExecutionOverlayUi.tooltipAccent
                );
            }
        }
        tooltip.add(builder.build());
        return tooltip;
    }

    private static String recipeLabel(RecipeDatabaseRecord record) {
        return record.recipe().id() == null ? record.recipe().toString() : record.recipe().id().toString();
    }

    private static String systemLabel(RecipeDatabaseRecord record) {
        return record.recipe().systemId().toString();
    }

    private static String systemGroupKey(RecipeDatabaseRecord record) {
        List<Namespace> tags = record.systemTags();
        return tags.isEmpty() ? systemLabel(record) : tags.getFirst().toString();
    }

    private static String shortRecipeLabel(RecipeDatabaseRecord record) {
        String label = recipeLabel(record);
        int slash = label.indexOf(':');
        return slash >= 0 && slash + 1 < label.length() ? label.substring(slash + 1) : label;
    }

    private static String shortNamespace(Namespace id) {
        String path = id.path();
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash + 1 < path.length() ? id.root() + ":" + path.substring(slash + 1) : id.toString();
    }

    private static Component displayName(CustomRecipeDatabase database) {
        return database.builtin()
            ? CirrusLangs.Calculator.Database.builtin.component()
            : Component.literal(database.name());
    }

    private Tooltip sourceTooltip(Namespace source) {
        CustomRecipeDatabase active = workspace.activeDatabase();
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(source.toString()));
        if (!active.hasSource(source)) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sourceAvailable));
        } else if (active.sourceDisabled(source)) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sourceDisabled));
        } else if (active.sourceEnabled(source)) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sourceEnabled));
        } else {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.sourceAvailable));
        }
        return tooltip;
    }

    private Tooltip systemRecipeSelectionTooltip(RecipeDatabaseRecord record, DatabaseRecipeRef ref) {
        Tooltip tooltip = recipeTooltip(record);
        CustomRecipeDatabase active = workspace.activeDatabase();
        if (active.contains(ref)) {
            tooltip.add(ExecutionOverlayUi.tooltipAccentText(ExecutionOverlayUi.tr(CirrusLangs.Calculator.Database.recipeSelected)));
        } else if (active.sourceEnabled(ref.system())) {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.recipeIncludedBySource));
        } else {
            tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Calculator.Database.recipeAvailable));
        }
        return tooltip;
    }

    private Component systemRecipeSelectionStatus(DatabaseRecipeRef ref) {
        CustomRecipeDatabase active = workspace.activeDatabase();
        if (active.contains(ref)) {
            return CirrusLangs.Calculator.Database.recipeStatusSelected.component();
        }
        if (active.sourceEnabled(ref.system())) {
            return CirrusLangs.Calculator.Database.recipeStatusIncludedBySource.component();
        }
        return CirrusLangs.Calculator.Database.recipeStatusAvailable.component();
    }

    private int systemRecipeSelectionStatusColor(DatabaseRecipeRef ref) {
        return 0xFF3F6E96;
    }

    private static String sourceButtonLabel(Namespace source) {
        return source.toString();
    }

    private static @Nullable Namespace parseNamespace(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return null;
        }
        return Namespace.maybe(sourceId.strip());
    }

    private static @Nullable DatabaseRecipeRef parseRecipeRef(String sourceId, String recipeId) {
        Namespace source = parseNamespace(sourceId);
        if (source == null || recipeId == null || recipeId.isBlank()) {
            return null;
        }
        ResourceLocation recipe = ResourceLocation.tryParse(recipeId.strip());
        return recipe == null ? null : new DatabaseRecipeRef(source, recipe);
    }

    private static final class SystemRecipeSelection {
        private final RecipeDatabaseRecord record;
        private final DatabaseRecipeRef ref;
        private final String label;
        private @Nullable List<RecipeContent> outputPreviews;
        private int fittedLabelWidth = Integer.MIN_VALUE;
        private String fittedLabel = "";

        private SystemRecipeSelection(RecipeDatabaseRecord record, DatabaseRecipeRef ref) {
            this.record = record;
            this.ref = ref;
            this.label = recipeLabel(record);
        }

        private RecipeDatabaseRecord record() {
            return record;
        }

        private DatabaseRecipeRef ref() {
            return ref;
        }

        private List<RecipeContent> outputPreviews() {
            List<RecipeContent> current = outputPreviews;
            if (current == null) {
                current = DatabasePage.outputPreviews(record.projection());
                outputPreviews = current;
            }
            return current;
        }

        private String fittedLabel(Font font, int width) {
            if (fittedLabelWidth != width) {
                fittedLabelWidth = width;
                fittedLabel = fitText(font, label, width);
            }
            return fittedLabel;
        }
    }

    private static RecipeDatabaseIndex buildActiveIndex(
        RecipeDatabaseWorkspace workspace,
        List<RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries,
        RecipeDatabaseIndex systemCatalogIndex
    ) {
        return buildActiveIndex(
            workspace,
            systems,
            systemTags,
            explicitEntries,
            systemCatalogIndex,
            RecipeDatabaseIndex.BuildProgress.none()
        );
    }

    private static RecipeDatabaseIndex buildActiveIndex(
        RecipeDatabaseWorkspace workspace,
        List<RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries,
        RecipeDatabaseIndex systemCatalogIndex,
        RecipeDatabaseIndex.BuildProgress progress
    ) {
        CustomRecipeDatabase active = workspace.activeDatabase();
        if (active.builtin()) {
            return systemCatalogIndex;
        }
        progress.update(RecipeDatabaseIndex.BuildPhase.BUILDING_ACTIVE, 0, 1);
        RecipeDatabaseIndex derived = RecipeDatabaseIndex.derive(
            systemCatalogIndex,
            active.definition(0),
            active.enabledSources(),
            active.recipes()
        );
        progress.update(RecipeDatabaseIndex.BuildPhase.BUILDING_ACTIVE, 1, 1);
        return derived;
    }

    /**
     * Builds the system-catalog index in the background so the first page open
     * does not pay the full resolve cost. Scheduled by the client runtime after
     * the routing graph prewarm finishes; a later page open reuses the cached
     * catalog via {@link RecipeDatabaseCatalogCache}.
     */
    public static void prewarmSystemCatalogIndex(String reason) {
        try {
            RecipeApi api = RecipeApi.instance();
            List<RecipeSystem<?>> systems = new ArrayList<>();
            Map<Namespace, List<Namespace>> systemTags = new HashMap<>();
            for (RecipeSystem<?> system : api.allSystems()) {
                systems.add(system);
                ArrayList<Namespace> tags = new ArrayList<>();
                api.getSystemTags(system.systemId()).forEach(tags::add);
                if (!tags.isEmpty()) {
                    systemTags.put(system.systemId(), List.copyOf(tags));
                }
            }
            CirrusUiAsync.databaseIndexTask(() -> {
                long startNanos = System.nanoTime();
                RecipeDatabaseIndex index = RecipeDatabaseCatalogCache.getOrBuild(
                    systems,
                    systemTags,
                    RecipeDatabaseIndex.BuildProgress.none(),
                    RecipeDatabaseFailureRegistry.shared()
                );
                logger.info(
                    "database.index.prewarm.finish reason={} elapsedMs={} records={}",
                    reason,
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    index.records().size()
                );
                return null;
            });
        } catch (RuntimeException exception) {
            logger.warn("database.index.prewarm.failed reason={} cause={}", reason, exception.toString());
        }
    }

    private static RecipeDatabaseIndex buildSystemCatalogIndex(
        List<RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags
    ) {
        return buildSystemCatalogIndex(systems, systemTags, RecipeDatabaseIndex.BuildProgress.none());
    }

    private static RecipeDatabaseIndex buildSystemCatalogIndex(
        List<RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        RecipeDatabaseIndex.BuildProgress progress
    ) {
        return RecipeDatabaseCatalogCache.getOrBuild(
            systems,
            systemTags,
            progress,
            RecipeDatabaseFailureRegistry.shared()
        );
    }

    private static RecipeDatabaseIndex calculatorProjectionIndex(RecipeEntry<?> recipe) {
        if (recipe == null) {
            return RecipeDatabaseIndex.ofRecords(List.of());
        }
        DatabaseDefinition database = new DatabaseDefinition(
            "calculator_projection",
            "Calculator Projection",
            DatabaseKind.custom,
            true,
            0,
            List.of()
        );
        return RecipeDatabaseIndex.build(
            List.of(database),
            Lists.immutable.of(recipe.system()),
            calculatorProjectionSystemTags(recipe),
            Map.of(database.id(), List.of(recipe)),
            RecipeDatabaseIndex.BuildProgress.none(),
            null
        );
    }

    private static Map<Namespace, List<Namespace>> calculatorProjectionSystemTags(RecipeEntry<?> recipe) {
        try {
            ArrayList<Namespace> tags = new ArrayList<>();
            RecipeApi.instance().getSystemTags(recipe.systemId()).forEach(tags::add);
            return tags.isEmpty() ? Map.of() : Map.of(recipe.systemId(), List.copyOf(tags));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Map<String, List<RecipeEntry<?>>> resolveExplicitEntries(
        RecipeDatabaseWorkspace workspace,
        List<RecipeSystem<?>> systems,
        Map<String, List<RecipeEntry<?>>> fallbackEntries
    ) {
        LinkedHashMap<String, List<RecipeEntry<?>>> out = new LinkedHashMap<>();
        for (CustomRecipeDatabase database : workspace.customDatabases()) {
            ArrayList<RecipeEntry<?>> entries = new ArrayList<>();
            for (DatabaseRecipeRef ref : database.recipes()) {
                if (!database.recipeEnabled(ref)) {
                    continue;
                }
                RecipeEntry<?> entry = resolveRecipeRef(systems, ref);
                if (entry == null) {
                    entry = fallbackRecipeEntry(fallbackEntries, database.definitionId(), ref);
                }
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (!entries.isEmpty()) {
                out.put(database.definitionId(), List.copyOf(entries));
            }
        }
        return Map.copyOf(out);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable RecipeEntry<?> resolveRecipeRef(List<RecipeSystem<?>> systems, DatabaseRecipeRef ref) {
        for (RecipeSystem system : systems) {
            if (!system.systemId().equals(ref.system())) {
                continue;
            }
            Object recipe = system.findRecipe(ref.recipe());
            if (recipe == null) {
                recipe = system.findRecipe(ref.recipe().toString());
            }
            if (recipe != null && system.basicType().isInstance(recipe)) {
                return RecipeEntry.of(system, recipe);
            }
        }
        return null;
    }

    private static @Nullable RecipeEntry<?> fallbackRecipeEntry(
        Map<String, List<RecipeEntry<?>>> fallbackEntries,
        String definitionId,
        DatabaseRecipeRef ref
    ) {
        for (RecipeEntry<?> entry : fallbackEntries.getOrDefault(definitionId, List.of())) {
            DatabaseRecipeRef entryRef = DatabaseRecipeRef.fromEntry(entry);
            if (ref.equals(entryRef)) {
                return entry;
            }
        }
        return null;
    }

    private static DatabaseContext fixedContext(RecipeDatabaseIndex index) {
        return new DatabaseContext(
            Checks.checkNotNull(index, "index"),
            RecipeDatabaseWorkspace.defaults(),
            null,
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            ProjectionContexts.global(),
            null,
            null,
            DatabasePageChrome.databaseBrowser,
            false,
            false
        );
    }

    private static DatabaseContext calculatorProjectionLocalContext(RecipeDatabaseIndex index) {
        return new DatabaseContext(
            Checks.checkNotNull(index, "index"),
            RecipeDatabaseWorkspace.defaults(),
            null,
            List.of(),
            Map.of(),
            Map.of(),
            projectionPluginsFromApi(),
            projectionContextOptionsFromApi(),
            ProjectionContexts.global(),
            null,
            null,
            DatabasePageChrome.databaseBrowser,
            false,
            false
        );
    }

    private static DatabaseContext workspaceContext(
        RecipeDatabaseWorkspace workspace,
        List<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries,
        @Nullable FileRecipeDatabaseRepository repository
    ) {
        return new DatabaseContext(
            RecipeDatabaseIndex.ofRecords(List.of()),
            Checks.checkNotNull(workspace, "workspace"),
            repository,
            copySystems(systems),
            copySystemTags(systemTags),
            copyExplicitEntries(explicitEntries),
            projectionPluginsFromApi(),
            projectionContextOptionsFromApi(),
            ProjectionContexts.global(),
            null,
            null,
            DatabasePageChrome.databaseBrowser,
            true,
            false
        );
    }

    private static List<RecipeSystem<?>> copySystems(List<? extends RecipeSystem<?>> systems) {
        if (systems == null || systems.isEmpty()) {
            return List.of();
        }
        ArrayList<RecipeSystem<?>> out = new ArrayList<>();
        for (RecipeSystem<?> system : systems) {
            if (system != null) {
                out.add(system);
            }
        }
        return List.copyOf(out);
    }

    private static Map<Namespace, List<Namespace>> copySystemTags(Map<Namespace, List<Namespace>> tags) {
        if (tags == null || tags.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Namespace, List<Namespace>> out = new LinkedHashMap<>();
        for (Map.Entry<Namespace, List<Namespace>> entry : tags.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                out.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(out);
    }

    private static Map<String, List<RecipeEntry<?>>> copyExplicitEntries(Map<String, List<RecipeEntry<?>>> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<RecipeEntry<?>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<RecipeEntry<?>>> entry : entries.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                out.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(out);
    }

    private static DatabaseContext buildContextFromApi() {
        try {
            RecipeApi api = RecipeApi.instance();
            FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(defaultRepositoryRoot());
            RecipeDatabaseWorkspace workspace = repository.loadWorkspace();
            List<RecipeSystem<?>> systems = new ArrayList<>();
            Map<Namespace, List<Namespace>> systemTags = new HashMap<>();
            for (RecipeSystem<?> system : api.allSystems()) {
                systems.add(system);
                ArrayList<Namespace> tags = new ArrayList<>();
                api.getSystemTags(system.systemId()).forEach(tags::add);
                if (!tags.isEmpty()) {
                    systemTags.put(system.systemId(), List.copyOf(tags));
                }
            }
            return workspaceContext(workspace, systems, systemTags, Map.of(), repository)
                .withAsyncIndexBuild(true);
        } catch (RuntimeException exception) {
            logger.error("Failed to initialize recipe database browser context", exception);
            return fixedContext(RecipeDatabaseIndex.ofRecords(List.of()));
        }
    }

    private static List<ProjectionContextOption<?>> projectionContextOptionsFromApi() {
        try {
            ArrayList<ProjectionContextOption<?>> out = new ArrayList<>();
            RecipeApi.instance().projectionContextOptions().forEach(out::add);
            return List.copyOf(out);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<ProjectionPlugin> projectionPluginsFromApi() {
        try {
            ArrayList<ProjectionPlugin> out = new ArrayList<>();
            RecipeApi.instance().projectionPlugins().forEach(out::add);
            return List.copyOf(out);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public static java.nio.file.Path defaultRepositoryRoot() {
        return RecipePathSettings.defaultDatabaseRepositoryRoot();
    }

    private record DatabaseContext(
        RecipeDatabaseIndex index,
        RecipeDatabaseWorkspace workspace,
        @Nullable FileRecipeDatabaseRepository repository,
        List<RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        Map<String, List<RecipeEntry<?>>> explicitEntries,
        List<ProjectionPlugin> projectionPlugins,
        List<ProjectionContextOption<?>> projectionContextOptions,
        ExecutionContext globalProjectionContext,
        @Nullable CalculatorProjectionContext calculatorProjectionContext,
        @Nullable Runnable recipeDetailBackAction,
        DatabasePageChrome pageChrome,
        boolean databaseScopedIndex,
        boolean asyncIndexBuild
    ) {
        private DatabaseContext {
            Checks.checkNotNull(globalProjectionContext, "globalProjectionContext");
            Checks.checkNotNull(pageChrome, "pageChrome");
        }

        private DatabaseContext withCalculatorProjectionContext(CalculatorProjectionContext calculatorProjectionContext) {
            return new DatabaseContext(
                index,
                workspace,
                repository,
                systems,
                systemTags,
                explicitEntries,
                projectionPlugins,
                projectionContextOptions,
                globalProjectionContext,
                Checks.checkNotNull(calculatorProjectionContext, "calculatorProjectionContext"),
                recipeDetailBackAction,
                pageChrome,
                databaseScopedIndex,
                asyncIndexBuild
            );
        }

        private DatabaseContext withRecipeDetailBackAction(Runnable recipeDetailBackAction) {
            return new DatabaseContext(
                index,
                workspace,
                repository,
                systems,
                systemTags,
                explicitEntries,
                projectionPlugins,
                projectionContextOptions,
                globalProjectionContext,
                calculatorProjectionContext,
                Checks.checkNotNull(recipeDetailBackAction, "recipeDetailBackAction"),
                pageChrome,
                databaseScopedIndex,
                asyncIndexBuild
            );
        }

        private DatabaseContext withPageChrome(DatabasePageChrome pageChrome) {
            return new DatabaseContext(
                index,
                workspace,
                repository,
                systems,
                systemTags,
                explicitEntries,
                projectionPlugins,
                projectionContextOptions,
                globalProjectionContext,
                calculatorProjectionContext,
                recipeDetailBackAction,
                Checks.checkNotNull(pageChrome, "pageChrome"),
                databaseScopedIndex,
                asyncIndexBuild
            );
        }

        private DatabaseContext withAsyncIndexBuild(boolean asyncIndexBuild) {
            return new DatabaseContext(
                index,
                workspace,
                repository,
                systems,
                systemTags,
                explicitEntries,
                projectionPlugins,
                projectionContextOptions,
                globalProjectionContext,
                calculatorProjectionContext,
                recipeDetailBackAction,
                pageChrome,
                databaseScopedIndex,
                asyncIndexBuild
            );
        }

        private DatabaseContext withFixedIndex(RecipeDatabaseIndex index) {
            return new DatabaseContext(
                Checks.checkNotNull(index, "index"),
                workspace,
                repository,
                systems,
                systemTags,
                explicitEntries,
                projectionPlugins,
                projectionContextOptions,
                globalProjectionContext,
                calculatorProjectionContext,
                recipeDetailBackAction,
                pageChrome,
                false,
                false
            );
        }
    }

    private enum DatabasePageChrome {
        databaseBrowser(true),
        projectionDetail(false);

        private final boolean browserToolbar;

        DatabasePageChrome(boolean browserToolbar) {
            this.browserToolbar = browserToolbar;
        }

        private boolean browserToolbar() {
            return browserToolbar;
        }
    }

    private record ScrollSnapshot(float x, float y) {
    }

    private static final class DatabaseIndexBuildProgress implements RecipeDatabaseIndex.BuildProgress {
        private volatile RecipeDatabaseIndex.BuildPhase phase = RecipeDatabaseIndex.BuildPhase.QUEUED;
        private volatile int done;
        private volatile int total;

        @Override
        public void update(RecipeDatabaseIndex.BuildPhase phase, int done, int total) {
            this.phase = phase == null ? RecipeDatabaseIndex.BuildPhase.QUEUED : phase;
            this.done = Math.max(0, done);
            this.total = Math.max(0, total);
        }

        private String text() {
            String label = switch (phase) {
                case QUEUED -> "queued";
                case RESOLVING_EXPLICIT -> "resolving saved recipes";
                case BUILDING_CATALOG -> "building system catalog";
                case BUILDING_ACTIVE -> "building active database";
                case COLLECTING -> "collecting recipes";
                case RESOLVING_RECORDS -> "resolving recipes";
                case DEDUPING_RECORDS -> "deduping recipes";
                case BUILDING_SEARCH_INDEX -> "building search index";
                case DONE -> "done";
            };
            int currentTotal = total;
            if (currentTotal > 0) {
                return CirrusLangs.Calculator.Database.indexing.component().getString()
                    + " - " + label + " " + Math.min(done, currentTotal) + "/" + currentTotal;
            }
            return CirrusLangs.Calculator.Database.indexing.component().getString() + " - " + label;
        }
    }

    private record IndexBuildResult(
        long requestId,
        boolean includeSystemCatalog,
        RecipeDatabaseIndex systemCatalogIndex,
        RecipeDatabaseIndex activeIndex,
        Map<String, List<RecipeEntry<?>>> explicitEntries
    ) {
    }

    private record QueryBuildResult(
        long requestId,
        String query,
        SortMode sortMode,
        SortDirection sortDirection,
        boolean invalidQuery,
        int visibleResultCount,
        List<ResultCategoryBucket> buckets,
        @Nullable ScrollSnapshot scrollToRestore
    ) {
        private static QueryBuildResult invalid(
            long requestId,
            String query,
            SortMode sortMode,
            SortDirection sortDirection,
            @Nullable ScrollSnapshot scrollToRestore
        ) {
            return new QueryBuildResult(requestId, query, sortMode, sortDirection, true, 0, List.of(), scrollToRestore);
        }

        private static QueryBuildResult results(
            long requestId,
            String query,
            SortMode sortMode,
            SortDirection sortDirection,
            int visibleResultCount,
            List<ResultCategoryBucket> buckets,
            @Nullable ScrollSnapshot scrollToRestore
        ) {
            return new QueryBuildResult(
                requestId,
                query,
                sortMode,
                sortDirection,
                false,
                visibleResultCount,
                List.copyOf(buckets),
                scrollToRestore
            );
        }
    }

    private static final class SystemSourceSuggestionSource implements SearchSuggestionSource {
        private final List<String> candidates;

        private SystemSourceSuggestionSource(List<RecipeSystem<?>> systems) {
            ArrayList<String> out = new ArrayList<>();
            for (RecipeSystem<?> system : systems) {
                Namespace id = system.systemId();
                addCandidate(out, id.toString());
                addCandidate(out, id.root());
                addCandidate(out, id.path());
            }
            out.sort(String::compareTo);
            this.candidates = List.copyOf(out);
        }

        @Override
        public List<SearchSuggestion> suggest(String query, int cursor, int limit) {
            int max = Math.max(0, limit);
            if (max == 0) {
                return List.of();
            }
            String input = query == null ? "" : query;
            int clampedCursor = Math.clamp(cursor, 0, input.length());
            int start = systemQueryTokenStart(input, clampedCursor);
            int end = systemQueryTokenEnd(input, clampedCursor);
            String prefix = input.substring(start, clampedCursor).toLowerCase(Locale.ROOT);
            ArrayList<SearchSuggestion> out = new ArrayList<>(Math.min(max, candidates.size()));
            for (String candidate : candidates) {
                String lower = candidate.toLowerCase(Locale.ROOT);
                if (!prefix.isEmpty() && !lower.contains(prefix)) {
                    continue;
                }
                out.add(new SearchSuggestion(candidate, candidate, start, end));
                if (out.size() >= max) {
                    break;
                }
            }
            return List.copyOf(out);
        }

        private static void addCandidate(ArrayList<String> out, String value) {
            if (value == null || value.isBlank() || out.contains(value)) {
                return;
            }
            out.add(value);
        }

        private static int systemQueryTokenStart(String input, int cursor) {
            int start = Math.max(0, Math.min(cursor, input.length()));
            while (start > 0 && !Character.isWhitespace(input.charAt(start - 1))) {
                start--;
            }
            return start;
        }

        private static int systemQueryTokenEnd(String input, int cursor) {
            int end = Math.max(0, Math.min(cursor, input.length()));
            while (end < input.length() && !Character.isWhitespace(input.charAt(end))) {
                end++;
            }
            return end;
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

    private enum ViewMode {
        grid(CirrusLangs.Calculator.Database.viewGrid, CirrusLangs.Calculator.Database.viewGridDesc),
        list(CirrusLangs.Calculator.Database.viewList, CirrusLangs.Calculator.Database.viewListDesc);

        private final LangEntry label;
        private final LangEntry description;

        ViewMode(LangEntry label, LangEntry description) {
            this.label = label;
            this.description = description;
        }

        private LangEntry labelEntry() {
            return label;
        }

        private LangEntry descriptionEntry() {
            return description;
        }
    }

    private enum SortMode {
        relevance(
            CirrusLangs.Calculator.Database.sortRelevance,
            CirrusTextures.Icon.Calc.Sort.number,
            CirrusTextures.Icon.Calc.Sort.numberPressed
        ),
        name(
            CirrusLangs.Calculator.Database.sortName,
            CirrusTextures.Icon.Calc.Sort.id,
            CirrusTextures.Icon.Calc.Sort.idPressed
        ),
        database(
            CirrusLangs.Calculator.Database.sortDatabase,
            CirrusTextures.Icon.databaseUnselect,
            CirrusTextures.Icon.databaseSelect
        ),
        system(
            CirrusLangs.Calculator.Database.sortSystem,
            CirrusTextures.Icon.contextUnselect,
            CirrusTextures.Icon.contextSelect
        );

        private final LangEntry label;
        private final SizedTexture icon;
        private final SizedTexture hoverIcon;

        SortMode(LangEntry label, SizedTexture icon, SizedTexture hoverIcon) {
            this.label = label;
            this.icon = icon;
            this.hoverIcon = hoverIcon;
        }

        private Component label() {
            return label.component();
        }

        private SizedTexture icon() {
            return icon;
        }

        private SizedTexture hoverIcon() {
            return hoverIcon;
        }
    }

    private enum SortDirection {
        ascending(CirrusLangs.Calculator.Database.sortAscending),
        descending(CirrusLangs.Calculator.Database.sortDescending);

        private final LangEntry label;

        SortDirection(LangEntry label) {
            this.label = label;
        }

        private LangEntry labelEntry() {
            return label;
        }
    }

    private enum ResultCardAction {
        setCalculatorTarget(
            0,
            CirrusTextures.Icon.calculatorTarget,
            CirrusLangs.Calculator.Database.actionSetCalculator
        ),
        setExecutionTarget(
            1,
            CirrusTextures.Icon.start,
            CirrusLangs.Calculator.Database.actionSetExecution
        );

        private static final ResultCardAction[] buttons = {setCalculatorTarget, setExecutionTarget};

        private final int buttonIndex;
        private final SizedTexture icon;
        private final LangEntry label;

        ResultCardAction(int buttonIndex, SizedTexture icon, LangEntry label) {
            this.buttonIndex = buttonIndex;
            this.icon = icon;
            this.label = label;
        }

        private int buttonIndex() {
            return buttonIndex;
        }

        private SizedTexture icon() {
            return icon;
        }

        private LangEntry labelEntry() {
            return label;
        }

        private static ResultCardAction[] buttons() {
            return buttons;
        }
    }
}
